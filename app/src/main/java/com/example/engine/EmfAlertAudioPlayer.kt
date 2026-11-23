package com.example.engine

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.example.data.model.EmfAlertSound
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

class EmfAlertAudioPlayer(
    private val scope: CoroutineScope
) {
    companion object {
        private const val SAMPLE_RATE = 22050
    }

    private var playJob: Job? = null
    private var lastPlayedMs = 0L

    fun triggerAlert(sound: EmfAlertSound, forcePreview: Boolean = false) {
        if (sound == EmfAlertSound.SILENT) return
        val now = System.currentTimeMillis()
        if (!forcePreview && now - lastPlayedMs < 1300L) return
        lastPlayedMs = now

        if (playJob?.isActive == true && !forcePreview) return
        playJob?.cancel()

        playJob = scope.launch(Dispatchers.Default) {
            val samples = synthesizeTone(sound)
            val track = runCatching {
                AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
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
                    .setBufferSizeInBytes(samples.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
            }.getOrNull() ?: return@launch

            try {
                track.write(samples, 0, samples.size)
                track.play()
                val durationMs = (samples.size * 1000L) / SAMPLE_RATE
                kotlinx.coroutines.delay(durationMs + 60L)
            } finally {
                runCatching {
                    track.stop()
                    track.release()
                }
            }
        }
    }

    private fun synthesizeTone(sound: EmfAlertSound): ShortArray {
        val durationSec = when (sound) {
            EmfAlertSound.GEIGER_PING -> 0.14f
            EmfAlertSound.K2_CHIRP -> 0.24f
            EmfAlertSound.SONAR_PULSE -> 0.36f
            EmfAlertSound.SIREN_PULSE -> 0.42f
            EmfAlertSound.SILENT -> 0.05f
        }
        val count = (SAMPLE_RATE * durationSec).toInt()
        val out = ShortArray(count)
        var phase = 0.0

        for (i in 0 until count) {
            val t = i.toFloat() / SAMPLE_RATE
            val progress = i.toFloat() / count.toFloat()

            val sample = when (sound) {
                EmfAlertSound.GEIGER_PING -> {
                    // Sharp double-click pip
                    val env = if (progress < 0.35f) exp(-progress * 18f) else exp(-(progress - 0.4f) * 20f) * 0.7f
                    val freq = 2450.0
                    phase += 2.0 * PI * freq / SAMPLE_RATE
                    sin(phase).toFloat() * env * 0.85f
                }
                EmfAlertSound.K2_CHIRP -> {
                    // Classic ascending dual-step electronic chirp
                    val freq = if (progress < 0.45f) 1180.0 else 1760.0
                    val env = sin(progress * PI).toFloat()
                    phase += 2.0 * PI * freq / SAMPLE_RATE
                    sin(phase).toFloat() * env * 0.78f
                }
                EmfAlertSound.SONAR_PULSE -> {
                    // Resonant tactical sonar ping
                    val env = (1f - progress) * (1f - progress)
                    val freq = 690.0 - progress * 80.0
                    phase += 2.0 * PI * freq / SAMPLE_RATE
                    (sin(phase) * 0.75 + sin(phase * 2.0) * 0.25).toFloat() * env * 0.8f
                }
                EmfAlertSound.SIREN_PULSE -> {
                    // Modulated two-pulse alert
                    val mod = sin(progress * PI * 4.0)
                    val freq = 960.0 + mod * 380.0
                    val env = sin(progress * PI).toFloat()
                    phase += 2.0 * PI * freq / SAMPLE_RATE
                    sin(phase).toFloat() * env * 0.82f
                }
                EmfAlertSound.SILENT -> 0f
            }

            out[i] = (sample.coerceIn(-0.98f, 0.98f) * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }
}
