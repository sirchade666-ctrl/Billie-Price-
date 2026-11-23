package com.example.engine

import com.example.data.local.AudioClipEntity
import com.example.data.local.EmfAnomalyEntity
import com.example.data.local.FlaggedAudioSegmentEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sqrt

data class EmfTimelineSample(
    val offsetMs: Long,
    val magnitudeUt: Float,
    val deltaUt: Float
)

data class EvpDetectionCandidate(
    val startOffsetMs: Long,
    val endOffsetMs: Long,
    val peakAudioDb: Float,
    val snrDb: Float,
    val acousticClass: String,
    val emfMagnitudeUt: Float,
    val emfDeltaUt: Float,
    val confidenceScore: Int,
    val notes: String
)

class EvpAudioAnalyzer {

    companion object {
        private const val SAMPLE_RATE = DigitalAudioRecorder.SAMPLE_RATE
        private const val FRAME_MS = 100
        private const val FRAME_SAMPLES = (SAMPLE_RATE * FRAME_MS) / 1000 // ~2205 samples
    }

    suspend fun analyzeClipAndExtractSegments(
        clip: AudioClipEntity,
        emfTimeline: List<EmfTimelineSample>,
        sessionAnomalies: List<EmfAnomalyEntity>,
        thresholdUt: Float,
        segmentsOutputDir: File
    ): List<FlaggedAudioSegmentEntity> = withContext(Dispatchers.IO) {
        val wavFile = File(clip.filePath)
        if (!wavFile.exists() || wavFile.length() <= 44L) return@withContext emptyList()

        val pcmSamples = readWavMono16Samples(wavFile)
        if (pcmSamples.size < FRAME_SAMPLES * 3) return@withContext emptyList()

        if (!segmentsOutputDir.exists()) {
            segmentsOutputDir.mkdirs()
        }

        val frameCount = pcmSamples.size / FRAME_SAMPLES
        val frameRms = FloatArray(frameCount)
        val framePeak = FloatArray(frameCount)
        val frameZcr = FloatArray(frameCount)

        for (f in 0 until frameCount) {
            val start = f * FRAME_SAMPLES
            var sumSq = 0.0
            var peak = 0f
            var zeroCrossings = 0
            var prev = pcmSamples[start].toInt()
            for (i in start until start + FRAME_SAMPLES) {
                val v = pcmSamples[i].toInt()
                val absV = abs(v).toFloat()
                if (absV > peak) peak = absV
                sumSq += v.toDouble() * v.toDouble()
                if ((prev >= 0 && v < 0) || (prev < 0 && v >= 0)) {
                    zeroCrossings++
                }
                prev = v
            }
            frameRms[f] = sqrt(sumSq / FRAME_SAMPLES).toFloat()
            framePeak[f] = peak
            frameZcr[f] = zeroCrossings.toFloat() / FRAME_SAMPLES.toFloat()
        }

        // Compute adaptive baseline noise floor using lower-60% RMS median estimate
        val sortedRms = frameRms.sorted()
        val noiseFloorRms = sortedRms[(sortedRms.size * 0.4f).toInt().coerceIn(0, sortedRms.lastIndex)]
            .coerceAtLeast(140f)

        val candidates = mutableListOf<EvpDetectionCandidate>()
        var f = 0
        while (f < frameCount) {
            val rms = frameRms[f]
            val snrLinear = rms / noiseFloorRms
            val snrDb = 20f * log10(snrLinear.coerceAtLeast(1f))
            val crest = if (rms > 1f) framePeak[f] / rms else 1f
            val zcr = frameZcr[f]

            val isUnusualAudio = (snrDb >= 6.5f) || (snrDb >= 4.0f && crest >= 3.8f)

            if (isUnusualAudio) {
                val segStartFrame = f
                var segEndFrame = f
                var maxRms = rms
                var maxSnrDb = snrDb
                var avgZcr = zcr
                var framesInEvent = 1

                while (segEndFrame + 1 < frameCount) {
                    val nextRms = frameRms[segEndFrame + 1]
                    val nextSnr = 20f * log10((nextRms / noiseFloorRms).coerceAtLeast(1f))
                    if (nextSnr >= 4.2f) {
                        segEndFrame++
                        if (nextRms > maxRms) maxRms = nextRms
                        if (nextSnr > maxSnrDb) maxSnrDb = nextSnr
                        avgZcr += frameZcr[segEndFrame]
                        framesInEvent++
                    } else {
                        break
                    }
                }
                avgZcr /= framesInEvent.toFloat()

                val rawStartMs = segStartFrame.toLong() * FRAME_MS
                val rawEndMs = (segEndFrame + 1).toLong() * FRAME_MS

                // Find concurrent EMF reading within +-1800ms window
                val concurrentEmf = findMaxConcurrentEmf(
                    clipCreatedAtMs = clip.createdAtMs,
                    clipDurationMs = clip.durationMs,
                    windowStartMs = (rawStartMs - 1800L).coerceAtLeast(0L),
                    windowEndMs = rawEndMs + 1800L,
                    emfTimeline = emfTimeline,
                    sessionAnomalies = sessionAnomalies
                )

                val absDelta = abs(concurrentEmf.second)
                val significantEmf = absDelta >= (thresholdUt * 0.65f)

                if (significantEmf) {
                    val acousticClass = when {
                        avgZcr > 0.28f -> "High-Freq Whisper Burst"
                        crest > 4.2f && framesInEvent <= 3 -> "Percussive / Knock Transient"
                        else -> "Mid-Band Vocal Formant"
                    }
                    val peakDb = (20f * log10((maxRms / 32767f).coerceAtLeast(1e-5f))).coerceIn(-60f, 0f)
                    val confidence = (
                        35 +
                            (maxSnrDb * 2.4f).toInt().coerceAtMost(35) +
                            ((absDelta / thresholdUt) * 18f).toInt().coerceAtMost(30)
                        ).coerceIn(25, 99)

                    // Pad segment with 700ms pre-roll and 1100ms post-roll for listening context
                    val paddedStartMs = (rawStartMs - 700L).coerceAtLeast(0L)
                    val paddedEndMs = (rawEndMs + 1100L).coerceAtMost((frameCount.toLong() * FRAME_MS))

                    candidates.add(
                        EvpDetectionCandidate(
                            startOffsetMs = paddedStartMs,
                            endOffsetMs = paddedEndMs,
                            peakAudioDb = peakDb,
                            snrDb = maxSnrDb,
                            acousticClass = acousticClass,
                            emfMagnitudeUt = concurrentEmf.first,
                            emfDeltaUt = concurrentEmf.second,
                            confidenceScore = confidence,
                            notes = "SNR +${"%.1f".format(Locale.US, maxSnrDb)} dB concurrent with Δ${"%.1f".format(Locale.US, concurrentEmf.second)} μT EMF shift"
                        )
                    )
                    // Skip past padded end to avoid duplicate overlapping segments
                    f = (paddedEndMs / FRAME_MS).toInt() + 1
                    continue
                }
            }
            f++
        }

        // Write each flagged segment out as a standalone playable .wav file
        val savedSegments = mutableListOf<FlaggedAudioSegmentEntity>()
        val baseName = clip.fileName.substringBeforeLast(".")
        candidates.forEachIndexed { idx, cand ->
            val startSample = ((cand.startOffsetMs * SAMPLE_RATE) / 1000L).toInt().coerceIn(0, pcmSamples.size - 1)
            val endSample = ((cand.endOffsetMs * SAMPLE_RATE) / 1000L).toInt().coerceIn(startSample + 1, pcmSamples.size)
            val segName = "${baseName}_EVP_SEG_${idx + 1}.wav"
            val segFile = File(segmentsOutputDir, segName)
            writeWavSlice(segFile, pcmSamples, startSample, endSample)

            savedSegments.add(
                FlaggedAudioSegmentEntity(
                    clipId = clip.id,
                    sessionId = clip.sessionId,
                    segmentFileName = segName,
                    segmentFilePath = segFile.absolutePath,
                    timestampMs = clip.createdAtMs + cand.startOffsetMs,
                    startOffsetMs = cand.startOffsetMs,
                    endOffsetMs = cand.endOffsetMs,
                    peakAudioDb = cand.peakAudioDb,
                    snrDb = cand.snrDb,
                    acousticClass = cand.acousticClass,
                    emfMagnitudeUt = cand.emfMagnitudeUt,
                    emfDeltaUt = cand.emfDeltaUt,
                    confidenceScore = cand.confidenceScore,
                    notes = cand.notes
                )
            )
        }

        savedSegments
    }

    private fun findMaxConcurrentEmf(
        clipCreatedAtMs: Long,
        clipDurationMs: Long,
        windowStartMs: Long,
        windowEndMs: Long,
        emfTimeline: List<EmfTimelineSample>,
        sessionAnomalies: List<EmfAnomalyEntity>
    ): Pair<Float, Float> {
        var bestMag = 0f
        var bestDelta = 0f

        for (sample in emfTimeline) {
            if (sample.offsetMs in windowStartMs..windowEndMs) {
                if (abs(sample.deltaUt) > abs(bestDelta)) {
                    bestDelta = sample.deltaUt
                    bestMag = sample.magnitudeUt
                }
            }
        }

        // Also check persisted session anomalies whose timestamp falls near the clip window
        val clipStartEpoch = clipCreatedAtMs - clipDurationMs
        for (a in sessionAnomalies) {
            val offset = a.timestampMs - clipStartEpoch
            if (offset in (windowStartMs - 2000L)..(windowEndMs + 2000L)) {
                if (abs(a.deltaUt) > abs(bestDelta)) {
                    bestDelta = a.deltaUt
                    bestMag = a.magnitudeUt
                }
            }
        }

        return bestMag to bestDelta
    }

    private fun readWavMono16Samples(file: File): ShortArray {
        val bytes = file.readBytes()
        if (bytes.size <= 44) return ShortArray(0)
        val dataBytes = bytes.size - 44
        val sampleCount = dataBytes / 2
        val shorts = ShortArray(sampleCount)
        ByteBuffer.wrap(bytes, 44, sampleCount * 2)
            .order(ByteOrder.LITTLE_ENDIAN)
            .asShortBuffer()
            .get(shorts)
        return shorts
    }

    private fun writeWavSlice(
        dest: File,
        samples: ShortArray,
        startSample: Int,
        endSample: Int
    ) {
        val count = (endSample - startSample).coerceAtLeast(1)
        val totalAudioLen = count.toLong() * 2L
        val totalDataLen = totalAudioLen + 36L
        val byteRate = SAMPLE_RATE * 2
        val header = ByteArray(44)

        header[0] = 'R'.code.toByte(); header[1] = 'I'.code.toByte(); header[2] = 'F'.code.toByte(); header[3] = 'F'.code.toByte()
        writeIntLE(header, 4, totalDataLen.toInt())
        header[8] = 'W'.code.toByte(); header[9] = 'A'.code.toByte(); header[10] = 'V'.code.toByte(); header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte(); header[13] = 'm'.code.toByte(); header[14] = 't'.code.toByte(); header[15] = ' '.code.toByte()
        writeIntLE(header, 16, 16)
        writeShortLE(header, 20, 1)
        writeShortLE(header, 22, 1)
        writeIntLE(header, 24, SAMPLE_RATE)
        writeIntLE(header, 28, byteRate)
        writeShortLE(header, 32, 2)
        writeShortLE(header, 34, 16)
        header[36] = 'd'.code.toByte(); header[37] = 'a'.code.toByte(); header[38] = 't'.code.toByte(); header[39] = 'a'.code.toByte()
        writeIntLE(header, 40, totalAudioLen.toInt())

        RandomAccessFile(dest, "rw").use { raf ->
            raf.setLength(0L)
            raf.write(header)
            val buf = ByteArray(count * 2)
            for (i in 0 until count) {
                val s = samples[startSample + i].toInt()
                buf[i * 2] = (s and 0xFF).toByte()
                buf[i * 2 + 1] = ((s shr 8) and 0xFF).toByte()
            }
            raf.write(buf)
        }
    }

    private fun writeIntLE(buf: ByteArray, offset: Int, value: Int) {
        buf[offset] = (value and 0xFF).toByte()
        buf[offset + 1] = ((value shr 8) and 0xFF).toByte()
        buf[offset + 2] = ((value shr 16) and 0xFF).toByte()
        buf[offset + 3] = ((value shr 24) and 0xFF).toByte()
    }

    private fun writeShortLE(buf: ByteArray, offset: Int, value: Short) {
        val v = value.toInt()
        buf[offset] = (v and 0xFF).toByte()
        buf[offset + 1] = ((v shr 8) and 0xFF).toByte()
    }
}
