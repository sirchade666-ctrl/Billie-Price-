package com.example.engine

import com.example.data.model.NoiseSuppressionLevel
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Real-time software DSP noise suppression chain tuned for paranormal EVP captures.
 * Combines:
 *  1) Sub-bass rumble/DC high-pass filter (75 Hz)
 *  2) Narrow 50 Hz, 60 Hz, and 120 Hz AC mains hum notch filters
 *  3) Broadband static/hiss upper roll-off low-pass filter
 *  4) Adaptive minimum-tracking noise floor estimator with soft-knee downward expansion
 *     that attenuates background hiss while preserving sudden knocks and whispered formants.
 */
class EvpNoiseSuppressionFilter(
    private val sampleRate: Int = DigitalAudioRecorder.SAMPLE_RATE
) {
    @Volatile
    private var level: NoiseSuppressionLevel = NoiseSuppressionLevel.BALANCED

    @Volatile
    private var mainsHumNotchEnabled: Boolean = true

    private val rumbleHpf = BiquadFilter()
    private val notch50 = BiquadFilter()
    private val notch60 = BiquadFilter()
    private val notch120 = BiquadFilter()
    private val hissLpf = BiquadFilter()

    private var scratch = FloatArray(2048)
    private var floorInitialized = false
    private var adaptiveNoiseFloorRms = 180f
    private var currentGain = 1f

    init {
        reconfigureFilters()
    }

    @Synchronized
    fun configure(
        newLevel: NoiseSuppressionLevel,
        humNotchEnabled: Boolean
    ) {
        val changed = (level != newLevel) || (mainsHumNotchEnabled != humNotchEnabled)
        level = newLevel
        mainsHumNotchEnabled = humNotchEnabled
        if (changed) {
            reconfigureFilters()
        }
    }

    @Synchronized
    fun reset() {
        rumbleHpf.reset()
        notch50.reset()
        notch60.reset()
        notch120.reset()
        hissLpf.reset()
        floorInitialized = false
        adaptiveNoiseFloorRms = 180f
        currentGain = 1f
    }

    private fun reconfigureFilters() {
        val sr = sampleRate.toDouble()
        rumbleHpf.configureHighPass(sr, freqHz = 75.0, q = 0.707)
        notch50.configureNotch(sr, freqHz = 50.0, q = 7.0)
        notch60.configureNotch(sr, freqHz = 60.0, q = 7.0)
        notch120.configureNotch(sr, freqHz = 120.0, q = 8.0)
        hissLpf.configureLowPass(sr, freqHz = level.highCutHz.toDouble(), q = 0.707)
    }

    /**
     * Filters [count] mono 16-bit PCM samples in place and returns the estimated
     * noise reduction in dB for live UI telemetry.
     */
    @Synchronized
    fun processBlock(samples: ShortArray, count: Int): Float {
        if (count <= 0 || level == NoiseSuppressionLevel.OFF) return 0f

        if (scratch.size < count) {
            scratch = FloatArray(count)
        }

        var rawSumSq = 0.0
        var postFilterSumSq = 0.0

        val useNotch = mainsHumNotchEnabled
        val useHpf = true
        val useLpf = level != NoiseSuppressionLevel.OFF

        for (i in 0 until count) {
            val raw = samples[i].toFloat()
            rawSumSq += raw.toDouble() * raw.toDouble()

            var y = raw
            if (useHpf) {
                y = rumbleHpf.process(y)
            }
            if (useNotch) {
                y = notch50.process(y)
                y = notch60.process(y)
                y = notch120.process(y)
            }
            if (useLpf) {
                y = hissLpf.process(y)
            }
            scratch[i] = y
            postFilterSumSq += y.toDouble() * y.toDouble()
        }

        val rawRms = sqrt(rawSumSq / count).toFloat()
        val postFilterRms = sqrt(postFilterSumSq / count).toFloat()

        if (!floorInitialized) {
            adaptiveNoiseFloorRms = postFilterRms.coerceIn(75f, 3500f)
            floorInitialized = true
        } else {
            adaptiveNoiseFloorRms = if (postFilterRms < adaptiveNoiseFloorRms) {
                (adaptiveNoiseFloorRms * 0.78f + postFilterRms * 0.22f).coerceAtLeast(65f)
            } else {
                // Slow upward drift capped tightly so voices/knocks do not raise the noise floor
                val capped = min(postFilterRms, adaptiveNoiseFloorRms * 1.4f)
                (adaptiveNoiseFloorRms * 0.985f + capped * 0.015f).coerceAtMost(4500f)
            }
        }

        val kneeLow = adaptiveNoiseFloorRms * 1.05f
        val kneeHigh = (adaptiveNoiseFloorRms * level.thresholdMultiplier).coerceAtLeast(kneeLow + 20f)
        val floorGain = level.floorGain

        val targetGain = when {
            postFilterRms >= kneeHigh -> 1.0f
            postFilterRms <= kneeLow -> floorGain
            else -> {
                val t = ((postFilterRms - kneeLow) / (kneeHigh - kneeLow)).coerceIn(0f, 1f)
                // Smoothstep curve prevents abrupt gating transitions
                val smoothT = t * t * (3f - 2f * t)
                floorGain + (1.0f - floorGain) * smoothT
            }
        }

        // Sample-rate calibrated attack (~5ms) & release (~95ms)
        val attackCoeff = 1f - kotlin.math.exp(-1f / (0.005f * sampleRate)).toFloat()
        val releaseCoeff = 1f - kotlin.math.exp(-1f / (0.095f * sampleRate)).toFloat()

        var outSumSq = 0.0
        for (i in 0 until count) {
            val coeff = if (targetGain > currentGain) attackCoeff else releaseCoeff
            currentGain += (targetGain - currentGain) * coeff
            val scaled = (scratch[i] * currentGain)
                .coerceIn(Short.MIN_VALUE.toFloat(), Short.MAX_VALUE.toFloat())
            samples[i] = scaled.toInt().toShort()
            outSumSq += scaled.toDouble() * scaled.toDouble()
        }

        val outRms = sqrt(outSumSq / count).toFloat().coerceAtLeast(1f)
        return (20f * log10((rawRms / outRms).coerceAtLeast(1f))).coerceIn(0f, 36f)
    }

    private class BiquadFilter {
        private var b0 = 1.0
        private var b1 = 0.0
        private var b2 = 0.0
        private var a1 = 0.0
        private var a2 = 0.0
        private var z1 = 0.0
        private var z2 = 0.0

        fun reset() {
            z1 = 0.0
            z2 = 0.0
        }

        fun configureHighPass(sampleRate: Double, freqHz: Double, q: Double) {
            val w0 = 2.0 * PI * freqHz / sampleRate
            val cosW0 = cos(w0)
            val alpha = sin(w0) / (2.0 * q)
            val a0 = 1.0 + alpha
            b0 = ((1.0 + cosW0) / 2.0) / a0
            b1 = (-(1.0 + cosW0)) / a0
            b2 = ((1.0 + cosW0) / 2.0) / a0
            a1 = (-2.0 * cosW0) / a0
            a2 = (1.0 - alpha) / a0
        }

        fun configureLowPass(sampleRate: Double, freqHz: Double, q: Double) {
            val clamped = freqHz.coerceIn(200.0, sampleRate * 0.45)
            val w0 = 2.0 * PI * clamped / sampleRate
            val cosW0 = cos(w0)
            val alpha = sin(w0) / (2.0 * q)
            val a0 = 1.0 + alpha
            b0 = ((1.0 - cosW0) / 2.0) / a0
            b1 = (1.0 - cosW0) / a0
            b2 = ((1.0 - cosW0) / 2.0) / a0
            a1 = (-2.0 * cosW0) / a0
            a2 = (1.0 - alpha) / a0
        }

        fun configureNotch(sampleRate: Double, freqHz: Double, q: Double) {
            val w0 = 2.0 * PI * freqHz / sampleRate
            val cosW0 = cos(w0)
            val alpha = sin(w0) / (2.0 * q)
            val a0 = 1.0 + alpha
            b0 = 1.0 / a0
            b1 = (-2.0 * cosW0) / a0
            b2 = 1.0 / a0
            a1 = (-2.0 * cosW0) / a0
            a2 = (1.0 - alpha) / a0
        }

        fun process(input: Float): Float {
            val x = input.toDouble()
            val out = b0 * x + z1
            z1 = b1 * x - a1 * out + z2
            z2 = b2 * x - a2 * out
            return out.toFloat()
        }
    }
}
