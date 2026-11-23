package com.example.engine

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.example.data.model.SpiritBoxMode
import com.example.data.model.SpiritBoxState
import com.example.data.model.SweepDirection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

class SpiritBoxDspEngine(
    private val scope: CoroutineScope
) {
    companion object {
        const val SAMPLE_RATE = 22050
        private const val CHUNK_SAMPLES = 512
        private const val DELAY_BUFFER_SIZE = SAMPLE_RATE // 1 second circular delay line
    }

    private val _state = MutableStateFlow(SpiritBoxState())
    val state: StateFlow<SpiritBoxState> = _state.asStateFlow()

    private val _spectrumBars = MutableStateFlow(FloatArray(36) { 0.04f })
    val spectrumBars: StateFlow<FloatArray> = _spectrumBars.asStateFlow()

    private var dspJob: Job? = null
    private var audioTrack: AudioTrack? = null

    @Volatile
    private var latestEmfDeltaUt: Float = 0f

    // Ring buffer tapped by DigitalAudioRecorder when mixing Spirit Box output into WAV
    private val tapLock = Any()
    private val tapBuffer = ShortArray(SAMPLE_RATE)
    private var tapWritePos = 0
    private var tapReadPos = 0

    fun updateEmfDelta(deltaUt: Float) {
        latestEmfDeltaUt = deltaUt
    }

    fun setMode(mode: SpiritBoxMode) {
        _state.value = _state.value.copy(mode = mode)
    }

    fun setSweepRateMs(ms: Int) {
        _state.value = _state.value.copy(sweepRateMs = ms.coerceIn(50, 400))
    }

    fun setDirection(direction: SweepDirection) {
        _state.value = _state.value.copy(direction = direction)
    }

    fun setEchoDepth(depth: Float) {
        _state.value = _state.value.copy(echoDepth = depth.coerceIn(0f, 0.85f))
    }

    fun setSquelch(squelch: Float) {
        _state.value = _state.value.copy(squelchLevel = squelch.coerceIn(0f, 0.9f))
    }

    fun setGain(gain: Float) {
        _state.value = _state.value.copy(gain = gain.coerceIn(0.1f, 1f))
    }

    fun setEmfCoupled(enabled: Boolean) {
        _state.value = _state.value.copy(emfCoupled = enabled)
    }

    fun toggleRunning() {
        if (_state.value.isRunning) stop() else start()
    }

    fun start() {
        if (_state.value.isRunning) return
        _state.value = _state.value.copy(isRunning = true)

        dspJob = scope.launch(Dispatchers.Default) {
            val minBuf = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(CHUNK_SAMPLES * 4)

            val track = runCatching {
                AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setSampleRate(SAMPLE_RATE)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(minBuf)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
            }.getOrNull()

            audioTrack = track
            track?.play()

            val pcmChunk = ShortArray(CHUNK_SAMPLES)
            val delayLine = FloatArray(DELAY_BUFFER_SIZE)
            var delayWriteIdx = 0

            var phaseCarrier = 0.0
            var phaseSub = 0.0
            var phaseFormant = 0.0
            var pinkB0 = 0f
            var pinkB1 = 0f
            var pinkB2 = 0f

            var samplesSinceStep = 0
            var currentFreq = 87.5f
            var stepBurstIntensity = 0.6f
            var stepCarrierHz = 420f
            var stepFormantHz = 1150f

            while (isActive && _state.value.isRunning) {
                val s = _state.value
                val stepSamples = ((s.sweepRateMs / 1000f) * SAMPLE_RATE).toInt().coerceAtLeast(512)
                val emfAbs = abs(latestEmfDeltaUt)
                val emfNormalized = (emfAbs / 20f).coerceIn(0f, 1.5f)

                val delaySamples = when (s.mode) {
                    SpiritBoxMode.FRANKS_BOX -> (SAMPLE_RATE * 0.08f).toInt()
                    SpiritBoxMode.GEOPORT -> (SAMPLE_RATE * (0.14f + s.echoDepth * 0.24f)).toInt()
                }.coerceIn(256, DELAY_BUFFER_SIZE - 1)

                val feedback = when (s.mode) {
                    SpiritBoxMode.FRANKS_BOX -> s.echoDepth * 0.28f
                    SpiritBoxMode.GEOPORT -> 0.22f + s.echoDepth * 0.52f
                }

                // Squelch gating: if EMF coupled and EMF spike occurs, open gate dynamically
                val effectiveThreshold = s.squelchLevel * 0.65f
                val gateScore = if (s.emfCoupled) {
                    0.32f + emfNormalized * 0.65f + (if (stepBurstIntensity > 0.7f) 0.25f else 0f)
                } else {
                    0.55f + (if (stepBurstIntensity > 0.5f) 0.2f else 0f)
                }
                val gateOpen = gateScore >= effectiveThreshold
                if (gateOpen != s.currentGateOpen) {
                    _state.value = _state.value.copy(currentGateOpen = gateOpen)
                }

                val bars = FloatArray(36)
                for (i in 0 until CHUNK_SAMPLES) {
                    samplesSinceStep++
                    if (samplesSinceStep >= stepSamples) {
                        samplesSinceStep = 0
                        currentFreq = when (s.direction) {
                            SweepDirection.FORWARD -> {
                                val next = currentFreq + 0.2f
                                if (next > 108.0f) 87.5f else next
                            }
                            SweepDirection.REVERSE -> {
                                val next = currentFreq - 0.2f
                                if (next < 87.5f) 108.0f else next
                            }
                            SweepDirection.RANDOM -> {
                                87.5f + Random.nextInt(103) * 0.2f
                            }
                        }
                        // Each sweep step gets a distinct RF burst envelope & formant target
                        stepBurstIntensity = 0.25f + Random.nextFloat() * 0.75f
                        val emfPitchShift = if (s.emfCoupled) emfNormalized * 260f else 0f
                        stepCarrierHz = 180f + ((currentFreq - 87.5f) / 20.5f) * 620f + emfPitchShift
                        stepFormantHz = 450f + Random.nextFloat() * 1450f + emfPitchShift * 0.8f
                        _state.value = _state.value.copy(currentFrequencyMhz = currentFreq)
                    }

                    // White + Pink noise synthesis
                    val white = Random.nextFloat() * 2f - 1f
                    pinkB0 = 0.99765f * pinkB0 + white * 0.0990460f
                    pinkB1 = 0.96300f * pinkB1 + white * 0.2965164f
                    pinkB2 = 0.57000f * pinkB2 + white * 1.0526913f
                    val pink = ((pinkB0 + pinkB1 + pinkB2 + white * 0.1848f) * 0.22f).coerceIn(-1f, 1f)

                    // Envelope for chopped sweep steps (Frank's Box choppy cadence vs Geoport smoother swell)
                    val stepProgress = samplesSinceStep.toFloat() / stepSamples.toFloat()
                    val stepEnv = if (s.mode == SpiritBoxMode.FRANKS_BOX) {
                        val attack = (stepProgress * 12f).coerceAtMost(1f)
                        val decay = ((1f - stepProgress) * 5f).coerceAtMost(1f)
                        attack * decay
                    } else {
                        sin(stepProgress * PI).toFloat().coerceIn(0.15f, 1f)
                    }

                    val carrier = sin(phaseCarrier).toFloat()
                    val subGeophone = sin(phaseSub).toFloat()
                    val formant = sin(phaseFormant).toFloat() * sin(phaseCarrier * 0.5).toFloat()

                    phaseCarrier += 2.0 * PI * stepCarrierHz / SAMPLE_RATE
                    phaseSub += 2.0 * PI * (68f + (if (s.emfCoupled) emfNormalized * 42f else 0f)) / SAMPLE_RATE
                    phaseFormant += 2.0 * PI * stepFormantHz / SAMPLE_RATE

                    if (phaseCarrier > 2.0 * PI) phaseCarrier -= 2.0 * PI
                    if (phaseSub > 2.0 * PI) phaseSub -= 2.0 * PI
                    if (phaseFormant > 2.0 * PI) phaseFormant -= 2.0 * PI

                    val drySignal = when (s.mode) {
                        SpiritBoxMode.FRANKS_BOX -> {
                            val noisePart = white * 0.36f + pink * 0.32f
                            val sweepCarrier = carrier * 0.22f + formant * 0.24f
                            (noisePart + sweepCarrier * stepBurstIntensity) * stepEnv
                        }
                        SpiritBoxMode.GEOPORT -> {
                            val geophone = subGeophone * 0.32f
                            val harmonicChamber = (formant * 0.36f + carrier * 0.18f) * stepEnv
                            val rawFloor = pink * 0.24f + white * 0.08f
                            geophone + harmonicChamber * stepBurstIntensity + rawFloor
                        }
                    }

                    val gatedDry = if (gateOpen) drySignal else drySignal * 0.08f
                    val readIdx = (delayWriteIdx - delaySamples + DELAY_BUFFER_SIZE) % DELAY_BUFFER_SIZE
                    val delayed = delayLine[readIdx]
                    val mixed = (gatedDry + delayed * feedback).coerceIn(-0.98f, 0.98f)
                    delayLine[delayWriteIdx] = (gatedDry + delayed * feedback * 0.72f).coerceIn(-0.95f, 0.95f)
                    delayWriteIdx = (delayWriteIdx + 1) % DELAY_BUFFER_SIZE

                    val finalSample = (mixed * s.gain).coerceIn(-0.98f, 0.98f)
                    val pcmShort = (finalSample * Short.MAX_VALUE).toInt().toShort()
                    pcmChunk[i] = pcmShort

                    val barIdx = (i * 36) / CHUNK_SAMPLES
                    val mag = abs(finalSample)
                    if (mag > bars[barIdx]) {
                        bars[barIdx] = mag
                    }
                }

                // Push to tap ring buffer for recorder mix
                synchronized(tapLock) {
                    for (sVal in pcmChunk) {
                        tapBuffer[tapWritePos] = sVal
                        tapWritePos = (tapWritePos + 1) % tapBuffer.size
                    }
                }

                _spectrumBars.value = bars
                track?.write(pcmChunk, 0, pcmChunk.size)
            }

            runCatching {
                track?.stop()
                track?.release()
            }
            audioTrack = null
            _spectrumBars.value = FloatArray(36) { 0.03f }
        }
    }

    fun readMixedSamples(destCount: Int): ShortArray {
        val out = ShortArray(destCount)
        if (!_state.value.isRunning) return out
        synchronized(tapLock) {
            for (i in 0 until destCount) {
                if (tapReadPos != tapWritePos) {
                    out[i] = tapBuffer[tapReadPos]
                    tapReadPos = (tapReadPos + 1) % tapBuffer.size
                } else {
                    out[i] = 0
                }
            }
        }
        return out
    }

    fun resetTapCursor() {
        synchronized(tapLock) {
            tapReadPos = tapWritePos
        }
    }

    fun stop() {
        _state.value = _state.value.copy(isRunning = false)
        dspJob?.cancel()
        dspJob = null
    }
}
