package com.example.engine

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.media.PlaybackParams
import android.os.Build
import com.example.data.model.AudioMarker
import com.example.data.model.NoiseSuppressionLevel
import com.example.data.model.PlaybackState
import com.example.data.model.RecorderState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.RandomAccessFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sqrt

data class RecordedClipResult(
    val file: File,
    val durationMs: Long,
    val createdAtMs: Long,
    val modeLabel: String,
    val peakEmfUt: Float,
    val peakEmfDeltaUt: Float = 0f,
    val minTempC: Float? = null,
    val tempDropC: Float = 0f,
    val markersSummary: String,
    val emfTimeline: List<EmfTimelineSample>
)

class DigitalAudioRecorder(
    private val scope: CoroutineScope,
    private val spiritBoxEngine: SpiritBoxDspEngine
) {
    companion object {
        const val SAMPLE_RATE = SpiritBoxDspEngine.SAMPLE_RATE
    }

    private val _state = MutableStateFlow(RecorderState())
    val state: StateFlow<RecorderState> = _state.asStateFlow()

    private val _liveWaveform = MutableStateFlow<List<Float>>(List(48) { 0.05f })
    val liveWaveform: StateFlow<List<Float>> = _liveWaveform.asStateFlow()

    private val noiseFilter = EvpNoiseSuppressionFilter(SAMPLE_RATE)

    private var recordJob: Job? = null
    private var outputFile: File? = null
    private var clipCreatedAtMs: Long = 0L
    private var accumulatedActiveMs: Long = 0L
    private var peakEmfDuringClip: Float = 0f
    private var peakEmfDeltaDuringClip: Float = 0f
    private var startTempDuringClip: Float? = null
    private var minTempDuringClip: Float? = null
    private var maxTempDropDuringClip: Float = 0f

    private val emfTimelineLock = Any()
    private val emfTimelineBuffer = mutableListOf<EmfTimelineSample>()
    private var lastTimelineSampleMs = -100L

    @Volatile
    private var currentEmfMagnitudeUt: Float = 0f

    @Volatile
    private var currentEmfDeltaUt: Float = 0f

    @Volatile
    private var currentTempC: Float? = null

    @Volatile
    private var currentTempDeltaC: Float = 0f

    @Volatile
    private var activeEmfThresholdUt: Float = 6.0f

    fun updatePermission(granted: Boolean) {
        _state.value = _state.value.copy(hasMicPermission = granted)
    }

    fun setMixSpiritBox(mix: Boolean) {
        _state.value = _state.value.copy(mixSpiritBox = mix)
    }

    fun setNoiseSuppressionLevel(level: NoiseSuppressionLevel) {
        _state.value = _state.value.copy(noiseSuppressionLevel = level)
        noiseFilter.configure(level, _state.value.mainsHumNotchEnabled)
    }

    fun setMainsHumNotchEnabled(enabled: Boolean) {
        _state.value = _state.value.copy(mainsHumNotchEnabled = enabled)
        noiseFilter.configure(_state.value.noiseSuppressionLevel, enabled)
    }

    fun updateEmfThreshold(thresholdUt: Float) {
        activeEmfThresholdUt = thresholdUt
    }

    fun updateCurrentEmf(magnitudeUt: Float, deltaUt: Float) {
        currentEmfMagnitudeUt = magnitudeUt
        currentEmfDeltaUt = deltaUt
        if (_state.value.isRecording) {
            if (magnitudeUt > peakEmfDuringClip) {
                peakEmfDuringClip = magnitudeUt
            }
            if (abs(deltaUt) > peakEmfDeltaDuringClip) {
                peakEmfDeltaDuringClip = abs(deltaUt)
            }
        }
    }

    fun updateCurrentTemperature(tempC: Float, tempDeltaC: Float) {
        currentTempC = tempC
        currentTempDeltaC = tempDeltaC
        if (_state.value.isRecording) {
            val startT = startTempDuringClip ?: tempC.also { startTempDuringClip = it }
            val minT = minTempDuringClip
            if (minT == null || tempC < minT) {
                minTempDuringClip = tempC
            }
            val dropFromClipStart = tempC - startT
            val worstDrop = minOf(dropFromClipStart, tempDeltaC)
            if (worstDrop < maxTempDropDuringClip) {
                maxTempDropDuringClip = worstDrop
            }
        }
    }

    fun addMarker(label: String, emfDeltaUt: Float) {
        if (!_state.value.isRecording) return
        val marker = AudioMarker(
            offsetMs = _state.value.elapsedMs,
            label = label,
            emfDeltaUt = emfDeltaUt
        )
        _state.value = _state.value.copy(
            pendingMarkers = _state.value.pendingMarkers + marker
        )
    }

    @SuppressLint("MissingPermission")
    fun startRecording(
        recordingsDir: File,
        onError: (String) -> Unit
    ) {
        if (_state.value.isRecording) return
        if (!_state.value.hasMicPermission) {
            onError("Microphone permission is required to record audio.")
            return
        }

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val prefix = if (_state.value.mixSpiritBox && spiritBoxEngine.state.value.isRunning) {
            "SPIRITBOX"
        } else {
            "EVP"
        }
        val fileName = "${prefix}_$timestamp.wav"
        val file = File(recordingsDir, fileName)
        outputFile = file
        peakEmfDuringClip = currentEmfMagnitudeUt
        peakEmfDeltaDuringClip = abs(currentEmfDeltaUt)
        startTempDuringClip = currentTempC
        minTempDuringClip = currentTempC
        maxTempDropDuringClip = currentTempDeltaC.coerceAtMost(0f)
        accumulatedActiveMs = 0L
        clipCreatedAtMs = System.currentTimeMillis()
        lastTimelineSampleMs = -100L
        synchronized(emfTimelineLock) {
            emfTimelineBuffer.clear()
        }
        spiritBoxEngine.resetTapCursor()
        noiseFilter.reset()
        noiseFilter.configure(_state.value.noiseSuppressionLevel, _state.value.mainsHumNotchEnabled)

        _state.value = _state.value.copy(
            isRecording = true,
            isPaused = false,
            elapsedMs = 0L,
            noiseReductionDb = 0f,
            activeFileName = fileName,
            pendingMarkers = emptyList(),
            concurrentFlagCount = 0,
            liveEvpCandidateActive = false
        )

        recordJob = scope.launch(Dispatchers.IO) {
            val minBuffer = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(2048)

            val recorder = runCatching {
                AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    minBuffer * 2
                )
            }.getOrNull()

            if (recorder == null || recorder.state != AudioRecord.STATE_INITIALIZED) {
                runCatching { recorder?.release() }
                _state.value = _state.value.copy(isRecording = false, isPaused = false, activeFileName = null)
                onError("Unable to initialize microphone hardware on this device.")
                return@launch
            }

            val raf = runCatching { RandomAccessFile(file, "rw") }.getOrNull()
            if (raf == null) {
                recorder.release()
                _state.value = _state.value.copy(isRecording = false, isPaused = false, activeFileName = null)
                onError("Unable to open local storage file for writing.")
                return@launch
            }

            var totalAudioBytes = 0L
            try {
                raf.write(ByteArray(44))
                recorder.startRecording()

                val shortBuffer = ShortArray(1024)
                val byteBuffer = ByteArray(shortBuffer.size * 2)
                val waveDeque = ArrayDeque<Float>(48)
                repeat(48) { waveDeque.addLast(0.04f) }

                var lastTimerTick = System.currentTimeMillis()
                var adaptiveNoiseFloorRms = 220f
                var lastLiveFlagMs = -2000L

                while (isActive && _state.value.isRecording) {
                    val now = System.currentTimeMillis()
                    if (_state.value.isPaused) {
                        lastTimerTick = now
                        delay(50)
                        continue
                    }

                    val readCount = recorder.read(shortBuffer, 0, shortBuffer.size)
                    if (readCount > 0) {
                        accumulatedActiveMs += (now - lastTimerTick).coerceIn(0L, 250L)
                        lastTimerTick = now

                        if (accumulatedActiveMs - lastTimelineSampleMs >= 100L) {
                            lastTimelineSampleMs = accumulatedActiveMs
                            synchronized(emfTimelineLock) {
                                emfTimelineBuffer.add(
                                    EmfTimelineSample(
                                        offsetMs = accumulatedActiveMs,
                                        magnitudeUt = currentEmfMagnitudeUt,
                                        deltaUt = currentEmfDeltaUt
                                    )
                                )
                            }
                        }

                        // Filter raw microphone audio to reduce background hum/static before Spirit Box mixing
                        val reductionDb = noiseFilter.processBlock(shortBuffer, readCount)

                        val shouldMix = _state.value.mixSpiritBox && spiritBoxEngine.state.value.isRunning
                        val spiritSamples = if (shouldMix) {
                            spiritBoxEngine.readMixedSamples(readCount)
                        } else {
                            null
                        }

                        var sumSquares = 0.0
                        var micSumSquares = 0.0
                        for (i in 0 until readCount) {
                            val micSample = shortBuffer[i].toInt()
                            micSumSquares += micSample.toDouble() * micSample.toDouble()
                            val blended = if (spiritSamples != null) {
                                (micSample * 0.72f + spiritSamples[i].toInt() * 0.55f)
                                    .toInt()
                                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                            } else {
                                micSample
                            }
                            val s = blended.toShort()
                            byteBuffer[i * 2] = (s.toInt() and 0xFF).toByte()
                            byteBuffer[i * 2 + 1] = ((s.toInt() shr 8) and 0xFF).toByte()
                            sumSquares += s.toDouble() * s.toDouble()
                        }

                        raf.write(byteBuffer, 0, readCount * 2)
                        totalAudioBytes += readCount * 2

                        val rms = sqrt(sumSquares / readCount).toFloat()
                        val micRms = sqrt(micSumSquares / readCount).toFloat()
                        adaptiveNoiseFloorRms = (adaptiveNoiseFloorRms * 0.94f + micRms * 0.06f).coerceAtLeast(120f)
                        val micSnrDb = 20f * log10((micRms / adaptiveNoiseFloorRms).coerceAtLeast(1f))
                        val emfConcurrent = abs(currentEmfDeltaUt) >= (activeEmfThresholdUt * 0.65f)
                        val isConcurrentCandidate = micSnrDb >= 6.5f && emfConcurrent

                        var flagCount = _state.value.concurrentFlagCount
                        if (isConcurrentCandidate && accumulatedActiveMs - lastLiveFlagMs >= 1600L) {
                            lastLiveFlagMs = accumulatedActiveMs
                            flagCount += 1
                        }

                        val norm = (rms / 12000f).coerceIn(0f, 1f)
                        val db = if (rms > 1f) (20f * log10(rms / 32767f)).coerceIn(-60f, 0f) else -60f

                        if (waveDeque.size >= 48) waveDeque.removeFirst()
                        waveDeque.addLast(norm.coerceAtLeast(0.03f))
                        _liveWaveform.value = waveDeque.toList()

                        _state.value = _state.value.copy(
                            elapsedMs = accumulatedActiveMs,
                            currentDb = db,
                            normalizedLevel = norm,
                            noiseReductionDb = reductionDb,
                            concurrentFlagCount = flagCount,
                            liveEvpCandidateActive = isConcurrentCandidate
                        )
                    } else {
                        delay(15)
                    }
                }
            } finally {
                runCatching {
                    recorder.stop()
                    recorder.release()
                }
                runCatching {
                    writeWavHeader(raf, totalAudioBytes, SAMPLE_RATE, 1, 16)
                    raf.close()
                }
            }
        }
    }

    fun togglePause() {
        if (!_state.value.isRecording) return
        _state.value = _state.value.copy(isPaused = !_state.value.isPaused)
    }

    suspend fun stopRecording(): RecordedClipResult? {
        if (!_state.value.isRecording) return null
        val duration = _state.value.elapsedMs.coerceAtLeast(300L)
        val markers = _state.value.pendingMarkers
        val modeLabel = if (_state.value.mixSpiritBox && spiritBoxEngine.state.value.isRunning) {
            "${spiritBoxEngine.state.value.mode.label} + Mic"
        } else {
            "Direct EVP Mic"
        }
        val file = outputFile
        val createdAt = clipCreatedAtMs

        _state.value = _state.value.copy(
            isRecording = false,
            isPaused = false,
            elapsedMs = 0L,
            currentDb = -60f,
            normalizedLevel = 0f,
            noiseReductionDb = 0f,
            activeFileName = null,
            pendingMarkers = emptyList(),
            concurrentFlagCount = 0,
            liveEvpCandidateActive = false
        )
        recordJob?.join()
        recordJob = null
        _liveWaveform.value = List(48) { 0.05f }

        if (file == null || !file.exists() || file.length() <= 44L) {
            return null
        }

        val timelineSnapshot = synchronized(emfTimelineLock) {
            emfTimelineBuffer.toList()
        }

        val markerText = if (markers.isEmpty()) {
            "No markers"
        } else {
            markers.joinToString("; ") { m ->
                val sec = m.offsetMs / 1000.0
                "${"%.1f".format(Locale.US, sec)}s ${m.label} (Δ${"%.1f".format(Locale.US, m.emfDeltaUt)}μT)"
            }
        }

        return RecordedClipResult(
            file = file,
            durationMs = duration,
            createdAtMs = createdAt,
            modeLabel = modeLabel,
            peakEmfUt = peakEmfDuringClip,
            peakEmfDeltaUt = peakEmfDeltaDuringClip,
            minTempC = minTempDuringClip,
            tempDropC = maxTempDropDuringClip,
            markersSummary = markerText,
            emfTimeline = timelineSnapshot
        )
    }

    private fun writeWavHeader(
        raf: RandomAccessFile,
        totalAudioLen: Long,
        sampleRate: Int,
        channels: Int,
        bitsPerSample: Int
    ) {
        val totalDataLen = totalAudioLen + 36
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8

        raf.seek(0)
        val header = ByteArray(44)
        header[0] = 'R'.code.toByte(); header[1] = 'I'.code.toByte(); header[2] = 'F'.code.toByte(); header[3] = 'F'.code.toByte()
        writeIntLE(header, 4, totalDataLen.toInt())
        header[8] = 'W'.code.toByte(); header[9] = 'A'.code.toByte(); header[10] = 'V'.code.toByte(); header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte(); header[13] = 'm'.code.toByte(); header[14] = 't'.code.toByte(); header[15] = ' '.code.toByte()
        writeIntLE(header, 16, 16)
        writeShortLE(header, 20, 1)
        writeShortLE(header, 22, channels.toShort())
        writeIntLE(header, 24, sampleRate)
        writeIntLE(header, 28, byteRate)
        writeShortLE(header, 32, blockAlign.toShort())
        writeShortLE(header, 34, bitsPerSample.toShort())
        header[36] = 'd'.code.toByte(); header[37] = 'a'.code.toByte(); header[38] = 't'.code.toByte(); header[39] = 'a'.code.toByte()
        writeIntLE(header, 40, totalAudioLen.toInt())
        raf.write(header, 0, 44)
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

class LocalAudioPlayer(private val scope: CoroutineScope) {
    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private var mediaPlayer: MediaPlayer? = null
    private var progressJob: Job? = null

    @Volatile
    private var loopRangeMs: LongRange? = null

    fun playOrToggleClip(clipId: Long, filePath: String) {
        playOrToggleInternal(clipId = clipId, segmentId = null, filePath = filePath)
    }

    fun playOrToggleSegment(segmentId: Long, filePath: String) {
        playOrToggleInternal(clipId = null, segmentId = segmentId, filePath = filePath)
    }

    fun prepareAndSeek(
        clipId: Long?,
        segmentId: Long?,
        filePath: String,
        seekMs: Long,
        autoPlay: Boolean = false
    ) {
        val current = _playbackState.value
        val sameTarget = (clipId != null && current.activeClipId == clipId) ||
            (segmentId != null && current.activeSegmentId == segmentId)

        if (sameTarget && mediaPlayer != null) {
            seekTo(seekMs)
            if (autoPlay && !current.isPlaying) {
                mediaPlayer?.start()
                applySpeed(current.playbackSpeed)
                _playbackState.value = _playbackState.value.copy(isPlaying = true)
            }
            return
        }

        stop()
        val file = File(filePath)
        if (!file.exists()) return

        val mp = MediaPlayer()
        runCatching {
            mp.setDataSource(file.absolutePath)
            mp.prepare()
            val dur = mp.duration.toLong().coerceAtLeast(1L)
            val clampedSeek = seekMs.coerceIn(0L, dur)
            mp.seekTo(clampedSeek.toInt())
            mp.setOnCompletionListener {
                val loop = loopRangeMs
                if (loop != null && mediaPlayer != null) {
                    runCatching {
                        mp.seekTo(loop.first.toInt().coerceIn(0, dur.toInt()))
                        mp.start()
                        applySpeed(_playbackState.value.playbackSpeed)
                    }
                } else {
                    _playbackState.value = _playbackState.value.copy(
                        isPlaying = false,
                        positionMs = 0L
                    )
                }
            }
            mediaPlayer = mp
            if (autoPlay) {
                mp.start()
                applySpeed(_playbackState.value.playbackSpeed)
            }
            _playbackState.value = _playbackState.value.copy(
                activeClipId = clipId,
                activeSegmentId = segmentId,
                isPlaying = autoPlay,
                positionMs = clampedSeek,
                durationMs = dur
            )
            startProgressLoop()
        }.onFailure {
            runCatching { mp.release() }
        }
    }

    fun setLoopWindow(rangeMs: LongRange?) {
        loopRangeMs = rangeMs
    }

    private fun playOrToggleInternal(clipId: Long?, segmentId: Long?, filePath: String) {
        val current = _playbackState.value
        val sameTarget = (clipId != null && current.activeClipId == clipId) ||
            (segmentId != null && current.activeSegmentId == segmentId)

        if (sameTarget && mediaPlayer != null) {
            if (current.isPlaying) {
                mediaPlayer?.pause()
                _playbackState.value = current.copy(isPlaying = false)
            } else {
                val loop = loopRangeMs
                if (loop != null && (current.positionMs < loop.first || current.positionMs >= loop.last)) {
                    runCatching { mediaPlayer?.seekTo(loop.first.toInt()) }
                }
                mediaPlayer?.start()
                applySpeed(current.playbackSpeed)
                _playbackState.value = _playbackState.value.copy(isPlaying = true)
            }
            return
        }

        stop()
        val file = File(filePath)
        if (!file.exists()) return

        val mp = MediaPlayer()
        runCatching {
            mp.setDataSource(file.absolutePath)
            mp.prepare()
            val dur = mp.duration.toLong().coerceAtLeast(1L)
            mp.setOnCompletionListener {
                val loop = loopRangeMs
                if (loop != null && mediaPlayer != null) {
                    runCatching {
                        mp.seekTo(loop.first.toInt().coerceIn(0, dur.toInt()))
                        mp.start()
                        applySpeed(_playbackState.value.playbackSpeed)
                    }
                } else {
                    _playbackState.value = _playbackState.value.copy(
                        isPlaying = false,
                        positionMs = 0L
                    )
                }
            }
            mp.start()
            mediaPlayer = mp
            applySpeed(_playbackState.value.playbackSpeed)
            _playbackState.value = _playbackState.value.copy(
                activeClipId = clipId,
                activeSegmentId = segmentId,
                isPlaying = true,
                positionMs = 0L,
                durationMs = dur
            )
            startProgressLoop()
        }.onFailure {
            runCatching { mp.release() }
        }
    }

    fun seekTo(positionMs: Long) {
        val mp = mediaPlayer ?: return
        runCatching {
            val dur = mp.duration.coerceAtLeast(1)
            val clamped = positionMs.toInt().coerceIn(0, dur)
            mp.seekTo(clamped)
            _playbackState.value = _playbackState.value.copy(positionMs = clamped.toLong())
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        _playbackState.value = _playbackState.value.copy(playbackSpeed = speed)
        applySpeed(speed)
    }

    private fun applySpeed(speed: Float) {
        val mp = mediaPlayer ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && mp.isPlaying) {
            runCatching {
                mp.playbackParams = PlaybackParams().setSpeed(speed)
            }
        }
    }

    private fun startProgressLoop() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive && mediaPlayer != null) {
                val mp = mediaPlayer
                if (mp != null && _playbackState.value.isPlaying) {
                    runCatching {
                        val dur = mp.duration.toLong().coerceAtLeast(1L)
                        var pos = mp.currentPosition.toLong()
                        val loop = loopRangeMs
                        if (loop != null && loop.last > loop.first + 80L && pos >= loop.last) {
                            val startTarget = loop.first.toInt().coerceIn(0, dur.toInt())
                            mp.seekTo(startTarget)
                            pos = startTarget.toLong()
                        }
                        _playbackState.value = _playbackState.value.copy(
                            positionMs = pos,
                            durationMs = dur
                        )
                    }
                }
                delay(45)
            }
        }
    }

    fun stop() {
        progressJob?.cancel()
        progressJob = null
        loopRangeMs = null
        runCatching {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        }
        mediaPlayer = null
        _playbackState.value = _playbackState.value.copy(
            activeClipId = null,
            activeSegmentId = null,
            isPlaying = false,
            positionMs = 0L,
            durationMs = 0L
        )
    }
}
