package com.example.engine

import com.example.data.local.FlaggedAudioSegmentEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

data class WaveformBin(
    val peakNorm: Float,
    val rmsNorm: Float,
    val peakDb: Float
)

data class WaveformHighlightRegion(
    val startMs: Long,
    val endMs: Long,
    val label: String,
    val isSilence: Boolean,
    val peakDb: Float
)

data class EvpWaveformData(
    val filePath: String,
    val sampleRate: Int,
    val durationMs: Long,
    val bins: List<WaveformBin>,
    val noiseFloorNorm: Float,
    val noiseFloorDb: Float,
    val peakRegions: List<WaveformHighlightRegion>,
    val silenceRegions: List<WaveformHighlightRegion>,
    val phoneticSegments: List<PhoneticSpeechSegment> = emptyList()
)

object EvpWaveformExtractor {

    private const val DEFAULT_TARGET_BINS = 720
    private const val MIN_BINS = 48

    suspend fun extractFromWav(
        file: File,
        fallbackDurationMs: Long = 0L,
        targetBins: Int = DEFAULT_TARGET_BINS
    ): EvpWaveformData? = withContext(Dispatchers.IO) {
        extractFromWavBlocking(file, fallbackDurationMs, targetBins)
    }

    fun extractFromWavBlocking(
        file: File,
        fallbackDurationMs: Long = 0L,
        targetBins: Int = DEFAULT_TARGET_BINS
    ): EvpWaveformData? {
        if (!file.exists() || file.length() <= 44L) return null

        val parsed = runCatching { parseWavPcm16(file) }.getOrNull() ?: return null
        val samples = parsed.monoSamples
        if (samples.isEmpty()) return null

        val sampleRate = parsed.sampleRate.coerceAtLeast(8000)
        val computedDurationMs = ((samples.size.toLong() * 1000L) / sampleRate).coerceAtLeast(1L)
        val durationMs = if (fallbackDurationMs > 0L && abs(computedDurationMs - fallbackDurationMs) < 500L) {
            fallbackDurationMs
        } else {
            computedDurationMs
        }

        val binCount = samples.size.coerceIn(MIN_BINS, targetBins.coerceAtLeast(MIN_BINS))
        val bins = ArrayList<WaveformBin>(binCount)
        val rmsList = ArrayList<Float>(binCount)

        for (b in 0 until binCount) {
            val startIdx = ((b.toLong() * samples.size) / binCount).toInt()
            val endIdx = (((b + 1).toLong() * samples.size) / binCount).toInt().coerceIn(startIdx + 1, samples.size)
            var maxAbs = 0
            var sumSq = 0.0
            val count = (endIdx - startIdx).coerceAtLeast(1)
            for (i in startIdx until endIdx) {
                val s = abs(samples[i].toInt())
                if (s > maxAbs) maxAbs = s
                sumSq += s.toDouble() * s.toDouble()
            }
            val peakNorm = (maxAbs / 32767f).coerceIn(0f, 1f)
            val rmsNorm = (sqrt(sumSq / count).toFloat() / 32767f).coerceIn(0f, 1f)
            val peakDb = if (peakNorm > 1e-4f) {
                (20f * log10(peakNorm)).coerceIn(-60f, 0f)
            } else {
                -60f
            }
            bins.add(WaveformBin(peakNorm = peakNorm, rmsNorm = rmsNorm, peakDb = peakDb))
            rmsList.add(rmsNorm)
        }

        val sortedRms = rmsList.sorted()
        val noiseFloorNorm = sortedRms[(sortedRms.size * 0.35f).toInt().coerceIn(0, sortedRms.lastIndex)]
            .coerceIn(0.004f, 0.35f)
        val noiseFloorDb = (20f * log10(noiseFloorNorm.coerceAtLeast(1e-4f))).coerceIn(-60f, 0f)

        val peakRegions = detectPeakRegions(bins, durationMs, noiseFloorNorm)
        val silenceRegions = detectSilenceRegions(bins, durationMs, noiseFloorNorm)
        val phoneticSegments = EvpPhoneticAnalyzer.analyze(samples, sampleRate, durationMs)

        return EvpWaveformData(
            filePath = file.absolutePath,
            sampleRate = sampleRate,
            durationMs = durationMs,
            bins = bins,
            noiseFloorNorm = noiseFloorNorm,
            noiseFloorDb = noiseFloorDb,
            peakRegions = peakRegions,
            silenceRegions = silenceRegions,
            phoneticSegments = phoneticSegments
        )
    }

    /**
     * Downsamples the visible fraction [startFraction, endFraction] of [bins] into up to [maxDisplayBars]
     * using max-peak & RMS aggregation so transients stay sharp at every zoom level.
     */
    fun sliceVisibleWindow(
        bins: List<WaveformBin>,
        startFraction: Float,
        endFraction: Float,
        maxDisplayBars: Int = 180
    ): List<WaveformBin> {
        if (bins.isEmpty()) return emptyList()
        val safeStart = startFraction.coerceIn(0f, 0.99f)
        val safeEnd = endFraction.coerceIn(safeStart + 0.005f, 1f)
        val startIdx = (safeStart * bins.size).toInt().coerceIn(0, bins.lastIndex)
        val endIdx = (safeEnd * bins.size).toInt().coerceIn(startIdx + 1, bins.size)
        val visibleCount = endIdx - startIdx
        if (visibleCount <= maxDisplayBars) {
            return bins.subList(startIdx, endIdx)
        }
        val result = ArrayList<WaveformBin>(maxDisplayBars)
        for (col in 0 until maxDisplayBars) {
            val s = startIdx + ((col.toLong() * visibleCount) / maxDisplayBars).toInt()
            val e = (startIdx + (((col + 1).toLong() * visibleCount) / maxDisplayBars).toInt())
                .coerceIn(s + 1, endIdx)
            var maxPeak = 0f
            var sumRmsSq = 0.0
            var maxDb = -60f
            for (idx in s until e) {
                val bin = bins[idx]
                if (bin.peakNorm > maxPeak) maxPeak = bin.peakNorm
                if (bin.peakDb > maxDb) maxDb = bin.peakDb
                sumRmsSq += bin.rmsNorm * bin.rmsNorm
            }
            val rms = sqrt(sumRmsSq / (e - s).coerceAtLeast(1)).toFloat()
            result.add(WaveformBin(peakNorm = maxPeak, rmsNorm = rms, peakDb = maxDb))
        }
        return result
    }

    private fun detectPeakRegions(
        bins: List<WaveformBin>,
        durationMs: Long,
        noiseFloorNorm: Float
    ): List<WaveformHighlightRegion> {
        if (bins.isEmpty() || durationMs <= 0L) return emptyList()
        val threshold = max(noiseFloorNorm * 2.2f, 0.08f)
        val minSeparationBins = ((600f / durationMs.toFloat()) * bins.size).toInt().coerceAtLeast(3)

        val candidates = bins.indices
            .filter { bins[it].peakNorm >= threshold }
            .sortedByDescending { bins[it].peakNorm }

        val selectedCenters = mutableListOf<Int>()
        for (idx in candidates) {
            if (selectedCenters.none { abs(it - idx) < minSeparationBins }) {
                selectedCenters.add(idx)
                if (selectedCenters.size >= 5) break
            }
        }

        val halfWindowMs = max(220L, min(850L, durationMs / 10L))
        return selectedCenters
            .sorted()
            .mapIndexed { rank, binIdx ->
                val centerMs = ((binIdx.toFloat() / bins.size.toFloat()) * durationMs).toLong()
                WaveformHighlightRegion(
                    startMs = (centerMs - halfWindowMs).coerceAtLeast(0L),
                    endMs = (centerMs + halfWindowMs).coerceAtMost(durationMs),
                    label = "Peak ${rank + 1}",
                    isSilence = false,
                    peakDb = bins[binIdx].peakDb
                )
            }
    }

    private fun detectSilenceRegions(
        bins: List<WaveformBin>,
        durationMs: Long,
        noiseFloorNorm: Float
    ): List<WaveformHighlightRegion> {
        if (bins.isEmpty() || durationMs < 400L) return emptyList()
        val quietCeiling = max(noiseFloorNorm * 1.45f, 0.025f)
        val minRunBins = ((350f / durationMs.toFloat()) * bins.size).toInt().coerceAtLeast(4)

        val runs = mutableListOf<WaveformHighlightRegion>()
        var i = 0
        while (i < bins.size) {
            if (bins[i].rmsNorm <= quietCeiling && bins[i].peakNorm <= quietCeiling * 2.2f) {
                val start = i
                var maxDbInRun = bins[i].peakDb
                while (i + 1 < bins.size &&
                    bins[i + 1].rmsNorm <= quietCeiling &&
                    bins[i + 1].peakNorm <= quietCeiling * 2.2f
                ) {
                    i++
                    if (bins[i].peakDb > maxDbInRun) maxDbInRun = bins[i].peakDb
                }
                val len = i - start + 1
                if (len >= minRunBins) {
                    val startMs = ((start.toFloat() / bins.size.toFloat()) * durationMs).toLong()
                    val endMs = (((i + 1).toFloat() / bins.size.toFloat()) * durationMs).toLong()
                        .coerceIn(startMs + 100L, durationMs)
                    runs.add(
                        WaveformHighlightRegion(
                            startMs = startMs,
                            endMs = endMs,
                            label = "Quiet ${runs.size + 1}",
                            isSilence = true,
                            peakDb = maxDbInRun
                        )
                    )
                }
            }
            i++
        }

        // Prefer longest quiet intervals (up to 4), sorted chronologically
        return runs
            .sortedByDescending { it.endMs - it.startMs }
            .take(4)
            .sortedBy { it.startMs }
            .mapIndexed { idx, reg -> reg.copy(label = "Quiet ${idx + 1}") }
    }

    fun buildFlaggedMarkerRegions(
        segments: List<FlaggedAudioSegmentEntity>,
        clipId: Long
    ): List<WaveformHighlightRegion> {
        return segments
            .filter { it.clipId == clipId }
            .sortedBy { it.startOffsetMs }
            .mapIndexed { idx, seg ->
                WaveformHighlightRegion(
                    startMs = seg.startOffsetMs,
                    endMs = seg.endOffsetMs,
                    label = "EVP #${idx + 1}",
                    isSilence = false,
                    peakDb = seg.peakAudioDb
                )
            }
    }

    private data class ParsedWav(
        val sampleRate: Int,
        val monoSamples: ShortArray
    )

    private fun parseWavPcm16(file: File): ParsedWav? {
        RandomAccessFile(file, "r").use { raf ->
            val fileLen = raf.length()
            if (fileLen < 44L) return null

            val header = ByteArray(12)
            raf.readFully(header)
            val riff = String(header, 0, 4, Charsets.US_ASCII)
            val wave = String(header, 8, 4, Charsets.US_ASCII)
            if (riff != "RIFF" || wave != "WAVE") return null

            var channels = 1
            var sampleRate = DigitalAudioRecorder.SAMPLE_RATE
            var bitsPerSample = 16
            var dataOffset = -1L
            var dataSize = 0L

            val chunkHdr = ByteArray(8)
            while (raf.filePointer + 8 <= fileLen) {
                raf.readFully(chunkHdr)
                val chunkId = String(chunkHdr, 0, 4, Charsets.US_ASCII)
                val chunkSize = ByteBuffer.wrap(chunkHdr, 4, 4)
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .int
                    .toLong() and 0xFFFFFFFFL

                if (chunkId == "fmt ") {
                    val toRead = chunkSize.coerceIn(16L, 64L).toInt()
                    val fmtBuf = ByteArray(toRead)
                    raf.readFully(fmtBuf)
                    val bb = ByteBuffer.wrap(fmtBuf).order(ByteOrder.LITTLE_ENDIAN)
                    bb.short // audioFormat
                    channels = bb.short.toInt().coerceIn(1, 8)
                    sampleRate = bb.int.coerceIn(8000, 192000)
                    bb.int // byteRate
                    bb.short // blockAlign
                    bitsPerSample = bb.short.toInt()
                    val remaining = chunkSize - toRead
                    if (remaining > 0) raf.seek(raf.filePointer + remaining)
                } else if (chunkId == "data") {
                    dataOffset = raf.filePointer
                    dataSize = min(chunkSize, fileLen - dataOffset)
                    break
                } else {
                    val nextPos = raf.filePointer + chunkSize + (chunkSize and 1L)
                    if (nextPos > fileLen) break
                    raf.seek(nextPos)
                }
            }

            if (dataOffset < 0L) {
                dataOffset = 44L
                dataSize = (fileLen - 44L).coerceAtLeast(0L)
            }
            if (dataSize <= 2L || bitsPerSample != 16) return null

            // Cap read size if exceptionally large (> 32 MB) by striding, though EVP clips are typically < 10 MB
            val maxBytes = 32 * 1024 * 1024L
            val bytesToRead = min(dataSize, maxBytes).toInt()
            val raw = ByteArray(bytesToRead)
            raf.seek(dataOffset)
            raf.readFully(raw)

            val shortCount = bytesToRead / 2
            val shortBuf = ShortArray(shortCount)
            ByteBuffer.wrap(raw, 0, shortCount * 2)
                .order(ByteOrder.LITTLE_ENDIAN)
                .asShortBuffer()
                .get(shortBuf)

            val mono = if (channels == 1) {
                shortBuf
            } else {
                val frames = shortCount / channels
                ShortArray(frames) { f ->
                    var peak = 0
                    val base = f * channels
                    for (c in 0 until channels) {
                        val v = shortBuf[base + c].toInt()
                        if (abs(v) > abs(peak)) peak = v
                    }
                    peak.toShort()
                }
            }
            return ParsedWav(sampleRate = sampleRate, monoSamples = mono)
        }
    }
}
