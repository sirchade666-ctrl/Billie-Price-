package com.example.engine

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

data class PhoneticSpeechSegment(
    val startMs: Long,
    val endMs: Long,
    val patternGroup: String?,
    val repeatCount: Int,
    val phoneticTag: String,
    val confidenceScore: Int,
    val similarityPct: Int,
    val isRepeatedPattern: Boolean
)

/**
 * Lightweight acoustic-phonetic pattern analyzer for EVP WAV captures.
 * Extracts short-window vocal-formant & sibilant Goertzel spectral fingerprints
 * (280 Hz - 4200 Hz) plus zero-crossing rate, groups speech-like bursts into
 * phonetic segments, and detects repeated sound patterns across the recording.
 */
object EvpPhoneticAnalyzer {

    private const val FRAME_MS = 40
    private val PROBE_FREQS_HZ = doubleArrayOf(
        280.0, 380.0,   // Fundamental / low voiced resonance
        580.0, 820.0,   // F1 vowel formant region
        1350.0, 1850.0, // F2 articulation formant region
        3050.0, 4150.0  // Fricative / whispered sibilance
    )

    private data class FrameFeature(
        val rms: Float,
        val zcr: Float,
        val bandVec: FloatArray, // length 8, L2-normalized
        val phonemeSymbol: String
    )

    private data class RawSegment(
        val startFrame: Int,
        val endFrame: Int,
        val meanVec: FloatArray,
        val peakRms: Float,
        val syllablePeaks: Int,
        val vocalBandRatio: Float,
        val phoneticTag: String
    )

    fun analyze(
        samples: ShortArray,
        sampleRate: Int,
        durationMs: Long
    ): List<PhoneticSpeechSegment> {
        if (samples.isEmpty() || sampleRate < 8000 || durationMs < 200L) return emptyList()

        val frameSamples = ((sampleRate * FRAME_MS) / 1000).coerceAtLeast(160)
        val frameCount = samples.size / frameSamples
        if (frameCount < 4) return emptyList()

        val frames = ArrayList<FrameFeature>(frameCount)
        val rmsValues = FloatArray(frameCount)

        for (f in 0 until frameCount) {
            val start = f * frameSamples
            var sumSq = 0.0
            var zc = 0
            var prev = samples[start].toInt()
            for (i in start until start + frameSamples) {
                val v = samples[i].toInt()
                sumSq += v.toDouble() * v.toDouble()
                if ((prev >= 0 && v < 0) || (prev < 0 && v >= 0)) zc++
                prev = v
            }
            val rms = sqrt(sumSq / frameSamples).toFloat()
            val zcr = zc.toFloat() / frameSamples.toFloat()
            rmsValues[f] = rms

            val rawBands = FloatArray(PROBE_FREQS_HZ.size)
            for (bIdx in PROBE_FREQS_HZ.indices) {
                val freq = PROBE_FREQS_HZ[bIdx]
                if (freq < sampleRate * 0.45) {
                    rawBands[bIdx] = goertzelMagnitude(samples, start, frameSamples, freq, sampleRate)
                }
            }
            val normBands = normalizeL2(rawBands)
            val symbol = classifyFramePhoneme(normBands, zcr)
            frames.add(FrameFeature(rms = rms, zcr = zcr, bandVec = normBands, phonemeSymbol = symbol))
        }

        val sortedRms = rmsValues.sorted()
        val noiseFloor = sortedRms[(sortedRms.size * 0.35f).toInt().coerceIn(0, sortedRms.lastIndex)]
            .coerceAtLeast(140f)
        val activeThreshold = max(noiseFloor * 1.65f, 210f)

        // Group active frames into speech-like utterances (bridging up to 2 silent frames = 80ms)
        val rawSegments = mutableListOf<RawSegment>()
        var idx = 0
        while (idx < frameCount) {
            if (frames[idx].rms >= activeThreshold) {
                val segStart = idx
                var segEnd = idx
                var gap = 0
                while (segEnd + 1 < frameCount) {
                    val nextActive = frames[segEnd + 1].rms >= (activeThreshold * 0.85f)
                    if (nextActive) {
                        segEnd++
                        gap = 0
                    } else if (gap < 2 && segEnd + 2 < frameCount && frames[segEnd + 2].rms >= activeThreshold) {
                        gap++
                        segEnd++
                    } else {
                        break
                    }
                }

                // Trim trailing gap frames
                while (segEnd > segStart && frames[segEnd].rms < (activeThreshold * 0.85f)) {
                    segEnd--
                }

                val lenFrames = segEnd - segStart + 1
                if (lenFrames >= 3) { // >= 120ms
                    rawSegments.add(buildRawSegment(frames, segStart, segEnd))
                }
                idx = segEnd + 1
            } else {
                idx++
            }
        }

        if (rawSegments.isEmpty()) return emptyList()

        // Cross-segment cosine similarity + internal rhythmic repetition detection
        val n = rawSegments.size
        val bestMatchSim = FloatArray(n)
        val repeatCounts = IntArray(n) { 1 }
        val groupLabels = arrayOfNulls<String>(n)
        var nextGroupChar = 'A'

        for (i in 0 until n) {
            for (j in i + 1 until n) {
                val sim = cosineSimilarity(rawSegments[i].meanVec, rawSegments[j].meanVec)
                if (sim >= 0.84f) {
                    if (sim > bestMatchSim[i]) bestMatchSim[i] = sim
                    if (sim > bestMatchSim[j]) bestMatchSim[j] = sim
                    repeatCounts[i]++
                    repeatCounts[j]++

                    val existing = groupLabels[i] ?: groupLabels[j]
                    val label = existing ?: "Pattern ${nextGroupChar++}".also {
                        if (nextGroupChar > 'Z') nextGroupChar = 'A'
                    }
                    groupLabels[i] = label
                    groupLabels[j] = label
                }
            }
        }

        // Also check internal repetition within longer multi-syllable segments (>= 7 frames / 280ms)
        for (i in 0 until n) {
            val seg = rawSegments[i]
            val span = seg.endFrame - seg.startFrame + 1
            if (span >= 8 && groupLabels[i] == null) {
                val mid = seg.startFrame + span / 2
                val firstHalf = averageVec(frames, seg.startFrame, mid - 1)
                val secondHalf = averageVec(frames, mid, seg.endFrame)
                val internalSim = cosineSimilarity(firstHalf, secondHalf)
                if (internalSim >= 0.86f && seg.syllablePeaks >= 2) {
                    bestMatchSim[i] = max(bestMatchSim[i], internalSim)
                    repeatCounts[i] = max(repeatCounts[i], 2)
                    groupLabels[i] = "Echo ${nextGroupChar++}"
                    if (nextGroupChar > 'Z') nextGroupChar = 'A'
                }
            }
        }

        val results = rawSegments.mapIndexedNotNull { i, seg ->
            val startMs = ((seg.startFrame.toFloat() / frameCount.toFloat()) * durationMs).toLong()
                .coerceIn(0L, durationMs - 1L)
            val endMs = (((seg.endFrame + 1).toFloat() / frameCount.toFloat()) * durationMs).toLong()
                .coerceIn(startMs + 80L, durationMs)
            val isRepeated = repeatCounts[i] >= 2
            val simPct = (bestMatchSim[i] * 100f).toInt().coerceIn(0, 99)

            // Vocal/phonetic likelihood score (0..99)
            val snrBoost = ((seg.peakRms / noiseFloor).coerceIn(1f, 8f) * 5f).toInt()
            val formantScore = (seg.vocalBandRatio * 42f).toInt().coerceIn(5, 42)
            val cadenceScore = (min(seg.syllablePeaks, 4) * 6)
            val repeatBoost = if (isRepeated) 24 else 0
            val confidence = (18 + snrBoost + formantScore + cadenceScore + repeatBoost).coerceIn(25, 99)

            if (confidence < 48 && !isRepeated) {
                null
            } else {
                PhoneticSpeechSegment(
                    startMs = startMs,
                    endMs = endMs,
                    patternGroup = groupLabels[i],
                    repeatCount = repeatCounts[i],
                    phoneticTag = seg.phoneticTag,
                    confidenceScore = confidence,
                    similarityPct = simPct,
                    isRepeatedPattern = isRepeated
                )
            }
        }

        // Prioritize repeated patterns and highest confidence segments (cap at 10 for clean UI)
        return results
            .sortedWith(
                compareByDescending<PhoneticSpeechSegment> { it.isRepeatedPattern }
                    .thenByDescending { it.confidenceScore }
                    .thenBy { it.startMs }
            )
            .take(10)
            .sortedBy { it.startMs }
    }

    private fun buildRawSegment(
        frames: List<FrameFeature>,
        startFrame: Int,
        endFrame: Int
    ): RawSegment {
        val meanVec = averageVec(frames, startFrame, endFrame)
        var peakRms = 0f
        var peaks = 0
        var prevRms = 0f
        var rising = true

        val compressedSymbols = mutableListOf<String>()
        for (i in startFrame..endFrame) {
            val f = frames[i]
            if (f.rms > peakRms) peakRms = f.rms
            if (i > startFrame) {
                if (f.rms < prevRms && rising) {
                    peaks++
                    rising = false
                } else if (f.rms > prevRms) {
                    rising = true
                }
            }
            prevRms = f.rms
            if (compressedSymbols.lastOrNull() != f.phonemeSymbol) {
                compressedSymbols.add(f.phonemeSymbol)
            }
        }
        if (rising) peaks++

        // Sum of voiced F1/F2 energies relative to overall L2 vector
        val vocalEnergy = sqrt(
            (meanVec[0] * meanVec[0] + meanVec[1] * meanVec[1] +
                meanVec[2] * meanVec[2] + meanVec[3] * meanVec[3] +
                meanVec[4] * meanVec[4] + meanVec[5] * meanVec[5]).toDouble()
        ).toFloat().coerceIn(0f, 1f)

        val phoneticTag = compressedSymbols.take(4).joinToString("-")
        return RawSegment(
            startFrame = startFrame,
            endFrame = endFrame,
            meanVec = meanVec,
            peakRms = peakRms,
            syllablePeaks = peaks.coerceAtLeast(1),
            vocalBandRatio = vocalEnergy,
            phoneticTag = phoneticTag
        )
    }

    private fun classifyFramePhoneme(normBands: FloatArray, zcr: Float): String {
        val low = normBands[0] + normBands[1]
        val midVowel = normBands[2] + normBands[3]
        val midArtic = normBands[4] + normBands[5]
        val highSib = normBands[6] + normBands[7]

        return when {
            zcr >= 0.24f || highSib > (midVowel + low) * 0.9f -> "sh"
            midArtic >= low && midArtic >= midVowel && zcr >= 0.11f -> "t/k"
            low > midVowel * 1.15f && zcr < 0.09f -> "m/n"
            midVowel >= low -> "ah"
            else -> "oh"
        }
    }

    private fun averageVec(frames: List<FrameFeature>, start: Int, end: Int): FloatArray {
        val acc = FloatArray(PROBE_FREQS_HZ.size)
        val count = (end - start + 1).coerceAtLeast(1)
        for (i in start..end) {
            val v = frames[i].bandVec
            for (k in acc.indices) {
                acc[k] += v[k]
            }
        }
        for (k in acc.indices) {
            acc[k] /= count.toFloat()
        }
        return normalizeL2(acc)
    }

    private fun normalizeL2(vec: FloatArray): FloatArray {
        var sumSq = 0.0
        for (v in vec) sumSq += v.toDouble() * v.toDouble()
        val norm = sqrt(sumSq).toFloat()
        if (norm <= 1e-6f) return vec
        return FloatArray(vec.size) { idx -> vec[idx] / norm }
    }

    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        var dot = 0.0
        var magA = 0.0
        var magB = 0.0
        for (i in a.indices) {
            dot += a[i].toDouble() * b[i].toDouble()
            magA += a[i].toDouble() * a[i].toDouble()
            magB += b[i].toDouble() * b[i].toDouble()
        }
        val denom = sqrt(magA * magB)
        return if (denom <= 1e-9) 0f else (dot / denom).toFloat().coerceIn(-1f, 1f)
    }

    private fun goertzelMagnitude(
        samples: ShortArray,
        offset: Int,
        length: Int,
        targetFreqHz: Double,
        sampleRate: Int
    ): Float {
        val omega = 2.0 * PI * targetFreqHz / sampleRate.toDouble()
        val coeff = 2.0 * cos(omega)
        var q0 = 0.0
        var q1 = 0.0
        var q2 = 0.0
        val end = min(samples.size, offset + length)
        for (i in offset until end) {
            q0 = coeff * q1 - q2 + samples[i].toDouble()
            q2 = q1
            q1 = q0
        }
        val power = (q1 * q1 + q2 * q2 - q1 * q2 * coeff).coerceAtLeast(0.0)
        return sqrt(power).toFloat() / length.coerceAtLeast(1).toFloat()
    }
}
