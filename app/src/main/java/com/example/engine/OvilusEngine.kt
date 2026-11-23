package com.example.engine

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import com.example.data.model.EmfReading
import com.example.data.model.OvilusLogEntry
import com.example.data.model.OvilusMode
import com.example.data.model.OvilusState
import com.example.data.model.OvilusVoiceOption
import com.example.data.model.OvilusVoiceProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs

class OvilusEngine(
    private val context: Context,
    private val scope: CoroutineScope
) : TextToSpeech.OnInitListener {

    private val _state = MutableStateFlow(
        OvilusState(wordBankCount = OvilusWordBank.totalCount)
    )
    val state: StateFlow<OvilusState> = _state.asStateFlow()

    private val _triggeredEvents = MutableSharedFlow<OvilusLogEntry>(extraBufferCapacity = 32)
    val triggeredEvents = _triggeredEvents.asSharedFlow()

    private var tts: TextToSpeech? = null
    private var discoveredVoices: Map<String, Voice> = emptyMap()
    private var lastTriggerTimeMs = 0L
    private var lastReading = EmfReading()
    private val entryIdGen = AtomicLong(1L)

    init {
        runCatching {
            tts = TextToSpeech(context.applicationContext, this)
        }
    }

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            _state.value = _state.value.copy(isTtsReady = false)
            return
        }
        val engine = tts ?: return
        runCatching {
            engine.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            engine.language = Locale.US
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    _state.value = _state.value.copy(isSpeaking = true)
                }

                override fun onDone(utteranceId: String?) {
                    _state.value = _state.value.copy(isSpeaking = false)
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    _state.value = _state.value.copy(isSpeaking = false)
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    _state.value = _state.value.copy(isSpeaking = false)
                }
            })

            val allVoices = engine.voices.orEmpty()
                .filter { it.locale?.language == "en" }
                .ifEmpty { engine.voices.orEmpty().toList() }

            val sortedVoices = allVoices.sortedWith(
                compareByDescending<Voice> { !it.isNetworkConnectionRequired }
                    .thenByDescending { it.quality }
                    .thenBy { it.locale?.country ?: "" }
                    .thenBy { it.name }
            ).take(8)

            discoveredVoices = sortedVoices.associateBy { it.name }
            val voiceOptions = sortedVoices.mapIndexed { idx, voice ->
                val qualityText = when {
                    voice.quality >= Voice.QUALITY_VERY_HIGH -> "HD"
                    voice.quality >= Voice.QUALITY_HIGH -> "High"
                    else -> "Clear"
                }
                val region = voice.locale?.country?.takeIf { it.isNotBlank() } ?: "EN"
                val offlineTag = if (!voice.isNetworkConnectionRequired) "Local" else "Net"
                OvilusVoiceOption(
                    id = voice.name,
                    displayName = "Voice ${idx + 1} ($region)",
                    localeLabel = "${voice.locale?.displayLanguage ?: "English"} • $offlineTag",
                    qualityLabel = qualityText
                )
            }

            val defaultVoice = engine.defaultVoice?.name?.takeIf { discoveredVoices.containsKey(it) }
                ?: voiceOptions.firstOrNull()?.id
            if (defaultVoice != null) {
                discoveredVoices[defaultVoice]?.let { engine.voice = it }
            }
            applyVoiceParams(engine, _state.value.pitch, _state.value.speechRate)

            _state.value = _state.value.copy(
                isTtsReady = true,
                availableVoices = voiceOptions,
                selectedVoiceId = defaultVoice
            )
        }
    }

    fun toggleActive() {
        val next = !_state.value.isActive
        _state.value = _state.value.copy(isActive = next)
        if (!next) {
            runCatching { tts?.stop() }
            _state.value = _state.value.copy(isSpeaking = false)
        } else {
            lastTriggerTimeMs = 0L
        }
    }

    fun stop() {
        _state.value = _state.value.copy(isActive = false, isSpeaking = false)
        runCatching { tts?.stop() }
    }

    fun setMode(mode: OvilusMode) {
        _state.value = _state.value.copy(mode = mode)
    }

    fun setVoiceProfile(profile: OvilusVoiceProfile) {
        _state.value = _state.value.copy(
            voiceProfile = profile,
            pitch = profile.pitch,
            speechRate = profile.speechRate
        )
        tts?.let { applyVoiceParams(it, profile.pitch, profile.speechRate) }
    }

    fun selectDeviceVoice(voiceId: String) {
        val voice = discoveredVoices[voiceId] ?: return
        runCatching { tts?.voice = voice }
        _state.value = _state.value.copy(selectedVoiceId = voiceId)
    }

    fun setPitch(pitch: Float) {
        val clamped = pitch.coerceIn(0.6f, 1.5f)
        _state.value = _state.value.copy(pitch = clamped)
        tts?.let { applyVoiceParams(it, clamped, _state.value.speechRate) }
    }

    fun setSpeechRate(rate: Float) {
        val clamped = rate.coerceIn(0.55f, 1.35f)
        _state.value = _state.value.copy(speechRate = clamped)
        tts?.let { applyVoiceParams(it, _state.value.pitch, clamped) }
    }

    fun setSensitivityUt(ut: Float) {
        _state.value = _state.value.copy(sensitivityUt = ut.coerceIn(1.5f, 20.0f))
    }

    fun setCooldownSeconds(sec: Int) {
        _state.value = _state.value.copy(cooldownSeconds = sec.coerceIn(2, 20))
    }

    fun setAutoLogToJournal(enabled: Boolean) {
        _state.value = _state.value.copy(autoLogToJournal = enabled)
    }

    fun speakLexiconWord(index: Int, word: String) {
        val phonetic = OvilusWordBank.buildPhoneticCue(word)
        _state.value = _state.value.copy(
            lastWord = word,
            lastPhonetic = phonetic,
            lastBankIndex = index
        )
        speakText(word, "ovilus_lex_${System.nanoTime()}")
    }

    fun onEmfReading(reading: EmfReading) {
        lastReading = reading
        val cur = _state.value
        if (!cur.isActive) return

        val now = System.currentTimeMillis()
        val cooldownMs = cur.cooldownSeconds * 1000L
        if (now - lastTriggerTimeMs < cooldownMs) return
        if (cur.isSpeaking) return

        val deltaAbs = abs(reading.delta)
        val ambientPulse = now - lastTriggerTimeMs >= cooldownMs * 3L && lastTriggerTimeMs > 0L
        if (deltaAbs >= cur.sensitivityUt || (lastTriggerTimeMs == 0L && deltaAbs >= 1.2f) || ambientPulse) {
            triggerWordFromReading(reading, questionSeed = "")
        }
    }

    fun triggerManualScan() {
        triggerWordFromReading(lastReading, questionSeed = "manual_${System.nanoTime()}")
    }

    fun askQuestion(questionText: String) {
        val trimmed = questionText.trim()
        if (trimmed.isEmpty()) return
        val qEntry = OvilusLogEntry(
            id = entryIdGen.getAndIncrement(),
            timestampMs = System.currentTimeMillis(),
            text = trimmed,
            bankIndex = null,
            phoneticCue = "",
            emfMagnitudeUt = lastReading.magnitude,
            emfDeltaUt = lastReading.delta,
            isQuestion = true
        )
        _state.value = _state.value.copy(
            logEntries = (listOf(qEntry) + _state.value.logEntries).take(120)
        )
        triggerWordFromReading(lastReading, questionSeed = trimmed)
    }

    fun testClearVoice() {
        speakText("Ovilus voice clarity check. Eighty thousand word bank ready.", "ovilus_test")
    }

    fun replayLogEntry(entry: OvilusLogEntry) {
        if (entry.isQuestion) return
        speakEntry(entry.text, entry.phoneticCue)
    }

    fun clearLog() {
        _state.value = _state.value.copy(
            logEntries = emptyList(),
            lastWord = "Log Cleared",
            lastPhonetic = "",
            lastBankIndex = null
        )
    }

    private fun triggerWordFromReading(reading: EmfReading, questionSeed: String) {
        lastTriggerTimeMs = System.currentTimeMillis()
        val (idx, word) = OvilusWordBank.selectFromEmf(reading, questionSeed)
        val phonetic = OvilusWordBank.buildPhoneticCue(word)

        val logEntry = OvilusLogEntry(
            id = entryIdGen.getAndIncrement(),
            timestampMs = lastTriggerTimeMs,
            text = word,
            bankIndex = idx,
            phoneticCue = phonetic,
            emfMagnitudeUt = reading.magnitude,
            emfDeltaUt = reading.delta,
            isQuestion = false
        )

        _state.value = _state.value.copy(
            lastWord = word,
            lastPhonetic = phonetic,
            lastBankIndex = idx,
            logEntries = (listOf(logEntry) + _state.value.logEntries).take(120)
        )
        _triggeredEvents.tryEmit(logEntry)

        speakEntry(word, phonetic)
    }

    fun buildTranscriptCsv(): String {
        val isoFmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US)
        val entries = _state.value.logEntries.asReversed()
        return buildString {
            appendLine("# GEOPORT EMF OVILUS ITC TRANSCRIPT (80,000 WORD BANK)")
            appendLine("entry_id,timestamp_iso,type,word_or_prompt,lexicon_index,phonetic_cue,emf_magnitude_ut,emf_delta_ut")
            for (e in entries) {
                val type = if (e.isQuestion) "QUESTION" else "OVILUS_WORD"
                val safeText = "\"${e.text.replace("\"", "\"\"")}\""
                appendLine(
                    listOf(
                        e.id.toString(),
                        isoFmt.format(Date(e.timestampMs)),
                        type,
                        safeText,
                        e.bankIndex?.plus(1)?.toString() ?: "",
                        e.phoneticCue,
                        "%.3f".format(Locale.US, e.emfMagnitudeUt),
                        "%.3f".format(Locale.US, e.emfDeltaUt)
                    ).joinToString(",")
                )
            }
        }
    }

    fun buildTranscriptJson(): String {
        val isoFmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US)
        val entries = _state.value.logEntries.asReversed()
        return buildString {
            appendLine("{")
            appendLine("  \"wordBankSize\": ${OvilusWordBank.totalCount},")
            appendLine("  \"mode\": \"${_state.value.mode.name}\",")
            appendLine("  \"voiceProfile\": \"${_state.value.voiceProfile.name}\",")
            appendLine("  \"transcript\": [")
            entries.forEachIndexed { idx, e ->
                val comma = if (idx < entries.lastIndex) "," else ""
                val safe = e.text.replace("\\", "\\\\").replace("\"", "\\\"")
                appendLine("    {")
                appendLine("      \"id\": ${e.id},")
                appendLine("      \"timestampIso\": \"${isoFmt.format(Date(e.timestampMs))}\",")
                appendLine("      \"isQuestion\": ${e.isQuestion},")
                appendLine("      \"text\": \"$safe\",")
                appendLine("      \"lexiconIndex\": ${e.bankIndex?.plus(1) ?: "null"},")
                appendLine("      \"phoneticCue\": \"${e.phoneticCue}\",")
                appendLine("      \"emfMagnitudeUt\": ${"%.3f".format(Locale.US, e.emfMagnitudeUt)},")
                appendLine("      \"emfDeltaUt\": ${"%.3f".format(Locale.US, e.emfDeltaUt)}")
                appendLine("    }$comma")
            }
            appendLine("  ]")
            appendLine("}")
        }
    }

    private fun speakEntry(word: String, phonetic: String) {
        val textToSpeak = when (_state.value.mode) {
            OvilusMode.DICTIONARY -> word
            OvilusMode.PHONETIC -> "$phonetic. $word"
        }
        speakText(textToSpeak, "ovilus_${System.nanoTime()}")
    }

    private fun speakText(text: String, utteranceId: String) {
        val engine = tts ?: return
        if (!_state.value.isTtsReady) return
        scope.launch(Dispatchers.Main.immediate) {
            runCatching {
                applyVoiceParams(engine, _state.value.pitch, _state.value.speechRate)
                engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
            }
        }
    }

    private fun applyVoiceParams(engine: TextToSpeech, pitch: Float, rate: Float) {
        runCatching {
            engine.setPitch(pitch)
            engine.setSpeechRate(rate)
        }
    }

    fun shutdown() {
        stop()
        runCatching {
            tts?.shutdown()
            tts = null
        }
    }
}
