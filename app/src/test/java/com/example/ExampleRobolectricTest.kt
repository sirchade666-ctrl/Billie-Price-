package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AudioClipEntity
import com.example.data.model.EmfAlertSound
import com.example.data.model.EmfReading
import com.example.engine.DigitalAudioRecorder
import com.example.engine.EmfTimelineSample
import com.example.engine.EvpAudioAnalyzer
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.RandomAccessFile
import kotlin.math.PI
import kotlin.math.sin

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import com.example.ui.theme.GeoportEmfTheme
import com.example.ui.theme.LocalNightVisionEnabled
import com.example.ui.theme.NightObsidianBg
import com.example.ui.theme.NightRedPrimary
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.PhosphorCyan
import com.example.ui.theme.nightVisionOverlay
import org.junit.Rule

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `read app name from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Geoport EMF", appName)
    }

    @Test
    fun `emf reading converts microtesla to milligauss accurately`() {
        val reading = EmfReading(
            x = 10f,
            y = 20f,
            z = 15f,
            magnitude = 48.5f,
            baseline = 45.0f,
            delta = 3.5f
        )
        assertEquals(485.0f, reading.milligauss, 0.01f)
        assertEquals(35.0f, reading.deltaMilligauss, 0.01f)
        assertTrue(EmfAlertSound.entries.size >= 4)
    }

    @Test
    fun `evp analyzer flags audio burst concurrent with emf spike and extracts wav segment`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val tempDir = File(context.cacheDir, "test_evp").apply { mkdirs() }
        val segDir = File(tempDir, "segments").apply { mkdirs() }
        val wavFile = File(tempDir, "test_capture.wav")

        val sampleRate = DigitalAudioRecorder.SAMPLE_RATE
        val durationSec = 3
        val totalSamples = sampleRate * durationSec
        val samples = ShortArray(totalSamples)

        // Quiet noise floor across 3 seconds, with a strong 600Hz burst between 1.2s and 1.6s
        for (i in 0 until totalSamples) {
            val t = i.toFloat() / sampleRate
            val floor = if (i % 2 == 0) 200 else -200
            val burst = if (t in 1.2f..1.6f) {
                (sin(2.0 * PI * 600.0 * t) * 14000.0).toInt()
            } else {
                0
            }
            samples[i] = (floor + burst).coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        writeTestWav(wavFile, sampleRate, samples)

        val clip = AudioClipEntity(
            id = 42L,
            sessionId = null,
            fileName = wavFile.name,
            filePath = wavFile.absolutePath,
            durationMs = 3000L,
            createdAtMs = 10000L,
            modeLabel = "Direct EVP Mic"
        )

        val timeline = listOf(
            EmfTimelineSample(offsetMs = 200L, magnitudeUt = 45f, deltaUt = 0.2f),
            EmfTimelineSample(offsetMs = 1350L, magnitudeUt = 64f, deltaUt = 19.0f),
            EmfTimelineSample(offsetMs = 2600L, magnitudeUt = 45.5f, deltaUt = 0.5f)
        )

        val analyzer = EvpAudioAnalyzer()
        val segments = analyzer.analyzeClipAndExtractSegments(
            clip = clip,
            emfTimeline = timeline,
            sessionAnomalies = emptyList(),
            thresholdUt = 6.0f,
            segmentsOutputDir = segDir
        )

        assertTrue("Expected at least 1 flagged EVP+EMF segment", segments.isNotEmpty())
        val first = segments.first()
        assertEquals(19.0f, first.emfDeltaUt, 0.01f)
        assertTrue(File(first.segmentFilePath).exists())
        assertTrue(File(first.segmentFilePath).length() > 44L)
    }

    @Test
    fun `storage location manager detects internal storage and formats sizes`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = com.example.engine.StorageLocationManager(context)
        val status = manager.detectVolumes(
            selectedMode = com.example.engine.StorageTargetMode.AUTO_SD_PREFERRED,
            customTreeUri = null,
            customFolderLabel = null
        )
        assertTrue(status.internalStorage.path.isNotBlank())
        assertEquals("1.5 GB", com.example.engine.formatBytesHuman((1.5 * 1024 * 1024 * 1024).toLong()))
    }

    @Test
    fun `battery monitor parses telemetry broadcasts triggers alerts and tracks session drain`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as android.os.BatteryManager
        val shadowBatteryManager = org.robolectric.Shadows.shadowOf(batteryManager)
        shadowBatteryManager.setIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CURRENT_NOW, -450_000)
        shadowBatteryManager.setIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER, 2_000_000)

        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Unconfined)
        val monitor = com.example.engine.BatteryMonitorEngine(context, scope)
        try {
            monitor.start()
            monitor.setWarningThresholdPct(20)

            val healthyIntent = android.content.Intent(android.content.Intent.ACTION_BATTERY_CHANGED).apply {
                putExtra(android.os.BatteryManager.EXTRA_LEVEL, 60)
                putExtra(android.os.BatteryManager.EXTRA_SCALE, 100)
                putExtra(android.os.BatteryManager.EXTRA_STATUS, android.os.BatteryManager.BATTERY_STATUS_DISCHARGING)
                putExtra(android.os.BatteryManager.EXTRA_PLUGGED, 0)
                putExtra(android.os.BatteryManager.EXTRA_HEALTH, android.os.BatteryManager.BATTERY_HEALTH_GOOD)
                putExtra(android.os.BatteryManager.EXTRA_TEMPERATURE, 315)
                putExtra(android.os.BatteryManager.EXTRA_VOLTAGE, 4000)
            }
            context.sendBroadcast(healthyIntent)
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()

            val s1 = monitor.state.value
            assertEquals(60, s1.levelPct)
            assertEquals(31.5f, s1.temperatureC, 0.01f)
            assertEquals(4.0f, s1.voltageV, 0.01f)
            assertEquals(com.example.data.model.BatteryHealthLabel.GOOD, s1.health)
            assertEquals("Discharging", s1.chargeSource)
            assertEquals(450f, s1.currentMa, 1.0f)
            assertEquals(1.8f, s1.powerWatts, 0.05f)
            assertEquals(false, s1.isLowPowerAlertActive)
            assertEquals(false, s1.isThermalAlertActive)
            assertTrue(s1.powerHistory.isNotEmpty())

            monitor.onInvestigationSessionStarted()

            val lowHotIntent = android.content.Intent(android.content.Intent.ACTION_BATTERY_CHANGED).apply {
                putExtra(android.os.BatteryManager.EXTRA_LEVEL, 15)
                putExtra(android.os.BatteryManager.EXTRA_SCALE, 100)
                putExtra(android.os.BatteryManager.EXTRA_STATUS, android.os.BatteryManager.BATTERY_STATUS_DISCHARGING)
                putExtra(android.os.BatteryManager.EXTRA_PLUGGED, 0)
                putExtra(android.os.BatteryManager.EXTRA_HEALTH, android.os.BatteryManager.BATTERY_HEALTH_OVERHEAT)
                putExtra(android.os.BatteryManager.EXTRA_TEMPERATURE, 452)
                putExtra(android.os.BatteryManager.EXTRA_VOLTAGE, 3600)
            }
            context.sendBroadcast(lowHotIntent)
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()

            val s2 = monitor.state.value
            assertEquals(15, s2.levelPct)
            assertEquals(45, s2.sessionDrainPct)
            assertTrue(s2.isLowPowerAlertActive)
            assertTrue(s2.isThermalAlertActive)
            assertEquals(com.example.data.model.BatteryHealthLabel.OVERHEAT, s2.health)

            monitor.onInvestigationSessionEnded()
            assertEquals(null, monitor.state.value.sessionStartPct)
        } finally {
            monitor.stop()
        }
    }

    @Test
    fun `night vision theme switches palette and provides optical red overlay composition local`() {
        var enabledInTree = false
        var brightnessInTree = 0f
        var primaryColor = PhosphorCyan
        var bgColor = ObsidianBg
        var nightModeState by androidx.compose.runtime.mutableStateOf(false)
        var brightnessState by androidx.compose.runtime.mutableStateOf(0.65f)

        composeTestRule.setContent {
            GeoportEmfTheme(
                nightVision = nightModeState,
                nightVisionBrightness = brightnessState
            ) {
                enabledInTree = LocalNightVisionEnabled.current
                brightnessInTree = com.example.ui.theme.LocalNightVisionBrightness.current
                primaryColor = MaterialTheme.colorScheme.primary
                bgColor = MaterialTheme.colorScheme.background
                androidx.compose.foundation.layout.Box(
                    modifier = androidx.compose.ui.Modifier.nightVisionOverlay(enabledInTree, brightnessInTree)
                )
            }
        }
        composeTestRule.waitForIdle()
        assertEquals(false, enabledInTree)
        assertEquals(0.65f, brightnessInTree, 0.001f)
        assertEquals(PhosphorCyan, primaryColor)
        assertEquals(ObsidianBg, bgColor)

        composeTestRule.runOnUiThread {
            nightModeState = true
            brightnessState = 2.0f
        }
        composeTestRule.waitForIdle()
        assertEquals(true, enabledInTree)
        assertEquals(1.15f, brightnessInTree, 0.001f)
        assertEquals(NightRedPrimary, primaryColor)
        assertEquals(NightObsidianBg, bgColor)

        composeTestRule.runOnUiThread { brightnessState = 0.10f }
        composeTestRule.waitForIdle()
        assertEquals(0.40f, brightnessInTree, 0.001f)
    }

    @Test
    fun `ovilus word bank contains exactly 80000 distinct speakable entries and maps emf readings`() {
        val bank = com.example.engine.OvilusWordBank
        assertEquals(500, bank.coreSingleWords.distinct().size)
        assertEquals(150, bank.adjectives.distinct().size)
        assertEquals(250, bank.nouns.distinct().size)
        assertEquals(120, bank.verbs.distinct().size)
        assertEquals(150, bank.complements.distinct().size)
        assertEquals(100, bank.openers.distinct().size)
        assertEquals(240, bank.targets.distinct().size)
        assertEquals(80_000, bank.totalCount)

        // Spot check all four structural segments plus boundary indices
        val sampleIndices = listOf(0, 499, 500, 12_345, 37_999, 38_000, 55_999, 56_000, 79_999)
        for (idx in sampleIndices) {
            val word = bank.getWordAt(idx)
            assertTrue("Expected non-blank entry at index $idx", word.isNotBlank())
        }

        val reading = EmfReading(x = 12.4f, y = -8.2f, z = 41.0f, magnitude = 43.6f, baseline = 40.0f, delta = 3.6f)
        val (selectedIdx, selectedWord) = bank.selectFromEmf(reading, "Is anyone here?")
        assertTrue(selectedIdx in 0 until 80_000)
        assertTrue(selectedWord.isNotBlank())
        assertTrue(bank.buildPhoneticCue(selectedWord).isNotBlank())

        val defaultBrowse = bank.searchWords("", limit = 10)
        assertEquals(10, defaultBrowse.size)
        assertTrue(defaultBrowse.all { (idx, word) -> idx in 0 until 80_000 && word.isNotBlank() })

        val basementMatches = bank.searchWords("basement", limit = 15)
        assertTrue(basementMatches.isNotEmpty())
        assertTrue(basementMatches.all { (idx, word) ->
            idx in 0 until 80_000 && word.contains("basement", ignoreCase = true)
        })
    }

    @Test
    fun `ovilus engine triggers word log entries voice profiles and builds csv json transcripts`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Unconfined)
        val engine = com.example.engine.OvilusEngine(context, scope)
        try {
            assertEquals(80_000, engine.state.value.wordBankCount)
            assertEquals(true, engine.state.value.autoLogToJournal)
            engine.setAutoLogToJournal(false)
            assertEquals(false, engine.state.value.autoLogToJournal)

            engine.setVoiceProfile(com.example.data.model.OvilusVoiceProfile.DEEP_BARITONE)
            assertEquals(com.example.data.model.OvilusVoiceProfile.DEEP_BARITONE, engine.state.value.voiceProfile)
            assertEquals(0.78f, engine.state.value.pitch, 0.01f)

            engine.askQuestion("Who is with us?")
            assertEquals(2, engine.state.value.logEntries.size)
            assertEquals(false, engine.state.value.logEntries[0].isQuestion)
            assertEquals(true, engine.state.value.logEntries[1].isQuestion)
            assertTrue(engine.state.value.lastBankIndex in 0 until 80_000)

            val triggeredWord = engine.state.value.logEntries[0].text
            val csv = engine.buildTranscriptCsv()
            assertTrue(csv.contains("entry_id,timestamp_iso,type,word_or_prompt"))
            assertTrue(csv.contains("QUESTION"))
            assertTrue(csv.contains("OVILUS_WORD"))
            assertTrue(csv.contains("Who is with us?"))
            assertTrue(csv.contains(triggeredWord))

            val json = engine.buildTranscriptJson()
            assertTrue(json.contains("\"wordBankSize\": 80000"))
            assertTrue(json.contains("\"transcript\": ["))
            assertTrue(json.contains("\"isQuestion\": true"))
            assertTrue(json.contains("Who is with us?"))
            assertTrue(json.contains(triggeredWord))

            engine.speakLexiconWord(42, "attic")
            assertEquals("attic", engine.state.value.lastWord)
            assertEquals(42, engine.state.value.lastBankIndex)
        } finally {
            engine.shutdown()
        }
    }

    @Test
    fun `evp waveform extractor decodes wav envelope detects peak and silence intervals and slices zoom windows`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val tempDir = File(context.cacheDir, "test_waveform").apply { mkdirs() }
        val wavFile = File(tempDir, "evp_wave_sample.wav")

        val sampleRate = DigitalAudioRecorder.SAMPLE_RATE
        val durationSec = 4
        val totalSamples = sampleRate * durationSec
        val samples = ShortArray(totalSamples)

        // Quiet floor (0.0s..1.6s and 2.1s..4.0s) with an intense burst around 1.6s..2.1s
        for (i in 0 until totalSamples) {
            val t = i.toFloat() / sampleRate
            val floor = if (i % 2 == 0) 140 else -140
            val burst = if (t in 1.6f..2.1f) {
                (sin(2.0 * PI * 720.0 * t) * 18000.0).toInt()
            } else {
                0
            }
            samples[i] = (floor + burst).coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        writeTestWav(wavFile, sampleRate, samples)

        val data = com.example.engine.EvpWaveformExtractor.extractFromWavBlocking(
            file = wavFile,
            fallbackDurationMs = 4000L,
            targetBins = 400
        )!!

        assertEquals(400, data.bins.size)
        assertEquals(4000L, data.durationMs)
        assertTrue("Expected detected peak regions", data.peakRegions.isNotEmpty())
        assertTrue("Expected detected silence regions", data.silenceRegions.isNotEmpty())
        assertTrue(data.peakRegions.any { it.startMs <= 2100L && it.endMs >= 1600L })

        val zoomedBins = com.example.engine.EvpWaveformExtractor.sliceVisibleWindow(
            bins = data.bins,
            startFraction = 0.35f,
            endFraction = 0.60f,
            maxDisplayBars = 80
        )
        assertEquals(80, zoomedBins.size)
        assertTrue(zoomedBins.maxOf { it.peakNorm } > 0.45f)
    }

    @Test
    fun `zoomable evp waveform panel zooms updates whisper gain and jumps to peak or silence`() {
        val bins = List(240) { idx ->
            val inPeak = idx in 90..110
            val peak = if (inPeak) 0.85f else 0.02f
            val rms = if (inPeak) 0.52f else 0.01f
            val db = if (inPeak) -2f else -36f
            com.example.engine.WaveformBin(peakNorm = peak, rmsNorm = rms, peakDb = db)
        }
        val waveform = com.example.engine.EvpWaveformData(
            filePath = "/tmp/test_evp.wav",
            sampleRate = 22050,
            durationMs = 6000L,
            bins = bins,
            noiseFloorNorm = 0.015f,
            noiseFloorDb = -36f,
            peakRegions = listOf(
                com.example.engine.WaveformHighlightRegion(2100L, 2800L, "Peak 1", isSilence = false, peakDb = -2f)
            ),
            silenceRegions = listOf(
                com.example.engine.WaveformHighlightRegion(3200L, 5800L, "Quiet 1", isSilence = true, peakDb = -36f)
            )
        )

        var lastSeekMs by mutableStateOf(-1L)

        composeTestRule.setContent {
            GeoportEmfTheme {
                com.example.ui.components.ZoomableEvpWaveformPanel(
                    waveformData = waveform,
                    positionMs = 0L,
                    durationMs = 6000L,
                    isPlaying = false,
                    onSeekMs = { lastSeekMs = it }
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("waveform_zoom_readout").assertTextContains("1.0x Zoom", substring = true)

        composeTestRule.onNodeWithTag("waveform_zoom_preset_8x").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("waveform_zoom_readout").assertTextContains("8.0x Zoom", substring = true)

        composeTestRule.onNodeWithTag("waveform_whisper_gain_chip").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("waveform_zoom_readout").assertTextContains("Gain 2x", substring = true)

        composeTestRule.onNodeWithTag("waveform_jump_quiet_1").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertEquals(3200L, lastSeekMs)
    }

    @Test
    fun `evp noise suppression filter attenuates 60hz hum and static while preserving vocal evp bursts`() {
        val sampleRate = DigitalAudioRecorder.SAMPLE_RATE
        val filter = com.example.engine.EvpNoiseSuppressionFilter(sampleRate)

        // 1. Verify OFF mode passes audio untouched
        filter.configure(com.example.data.model.NoiseSuppressionLevel.OFF, humNotchEnabled = true)
        val rawOff = ShortArray(1024) { idx -> ((idx % 17) * 120 - 960).toShort() }
        val copyOff = rawOff.copyOf()
        val offDb = filter.processBlock(copyOff, copyOff.size)
        assertEquals(0f, offDb, 0.001f)
        assertTrue(rawOff.contentEquals(copyOff))

        // 2. Verify BALANCED + 50/60Hz notch attenuates 60 Hz electrical hum + hiss significantly
        filter.reset()
        filter.configure(com.example.data.model.NoiseSuppressionLevel.BALANCED, humNotchEnabled = true)
        val blockSize = 1024
        var lastHumReductionDb = 0f
        var humRawSq = 0.0
        var humFilteredSq = 0.0
        for (blk in 0 until 20) {
            val buf = ShortArray(blockSize)
            for (i in 0 until blockSize) {
                val sampleIdx = blk * blockSize + i
                val t = sampleIdx.toDouble() / sampleRate
                val hum60 = sin(2.0 * PI * 60.0 * t) * 2600.0
                val hiss = if (i % 3 == 0) 380.0 else -290.0
                buf[i] = (hum60 + hiss).toInt().toShort()
                if (blk >= 12) humRawSq += buf[i].toDouble() * buf[i].toDouble()
            }
            lastHumReductionDb = filter.processBlock(buf, blockSize)
            if (blk >= 12) {
                for (s in buf) humFilteredSq += s.toDouble() * s.toDouble()
            }
        }
        val humRatioDb = 10.0 * kotlin.math.log10((humRawSq / humFilteredSq.coerceAtLeast(1.0)))
        assertTrue("Expected > 8 dB attenuation of 60Hz hum + hiss, got $humRatioDb dB", humRatioDb > 8.0)
        assertTrue("Expected telemetry reduction > 6 dB, got $lastHumReductionDb", lastHumReductionDb > 6f)

        // 3. Verify a clear 750 Hz EVP formant burst well above floor opens the soft knee and stays strong
        val evpBuf = ShortArray(blockSize)
        var evpRawSq = 0.0
        for (i in 0 until blockSize) {
            val t = i.toDouble() / sampleRate
            val voice = sin(2.0 * PI * 750.0 * t) * 11000.0
            evpBuf[i] = voice.toInt().toShort()
            evpRawSq += evpBuf[i].toDouble() * evpBuf[i].toDouble()
        }
        filter.processBlock(evpBuf, blockSize)
        var evpOutSq = 0.0
        for (s in evpBuf) evpOutSq += s.toDouble() * s.toDouble()
        val evpPreservationRatio = kotlin.math.sqrt(evpOutSq / evpRawSq.coerceAtLeast(1.0))
        assertTrue("Expected vocal burst to preserve > 70% amplitude, got $evpPreservationRatio", evpPreservationRatio > 0.70)
    }

    @Test
    fun `recorder screen noise suppression controls update level and hum notch state`() {
        var selectedLevel by mutableStateOf(com.example.data.model.NoiseSuppressionLevel.BALANCED)
        var humNotch by mutableStateOf(true)

        composeTestRule.setContent {
            GeoportEmfTheme {
                com.example.ui.screens.RecorderScreen(
                    recorderState = com.example.data.model.RecorderState(
                        hasMicPermission = true,
                        noiseSuppressionLevel = selectedLevel,
                        mainsHumNotchEnabled = humNotch
                    ),
                    liveWaveform = List(24) { 0.1f },
                    spiritBoxRunning = false,
                    clips = emptyList(),
                    flaggedSegments = emptyList(),
                    analyzingClipId = null,
                    playbackState = com.example.data.model.PlaybackState(),
                    waveformCache = emptyMap(),
                    inspectedClipId = null,
                    inspectedSegmentId = null,
                    storageStatus = com.example.engine.StorageLocationManager(
                        ApplicationProvider.getApplicationContext()
                    ).detectVolumes(com.example.engine.StorageTargetMode.AUTO_SD_PREFERRED, null, null),
                    isTransferring = false,
                    onRefreshStorage = {},
                    onSelectStorageMode = {},
                    onPickCustomFolder = {},
                    onTransferAllFiles = {},
                    onRequestMicPermission = {},
                    onStartRecording = {},
                    onTogglePause = {},
                    onStopRecording = {},
                    onMixSpiritBoxChange = {},
                    onNoiseSuppressionChange = { selectedLevel = it },
                    onHumNotchChange = { humNotch = it },
                    onTagMarker = {},
                    onAnalyzeClip = {},
                    onPlayPauseClip = {},
                    onPlayPauseSegment = {},
                    onInspectClip = {},
                    onInspectSegment = {},
                    onCloseInspector = {},
                    onSeekAndPrepareClip = { _, _ -> },
                    onSeekAndPrepareSegment = { _, _ -> },
                    onLoopRangeChange = {},
                    onLoadWaveform = { _, _ -> },
                    onSeekPlayback = {},
                    onPlaybackSpeedChange = {},
                    onSaveCopyToDevice = {},
                    onSaveSegmentToDevice = {},
                    onShareClip = {},
                    onShareSegment = {},
                    onDeleteClip = {},
                    onDeleteSegment = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("noise_suppression_status").assertTextContains("BALANCED")

        composeTestRule.onNodeWithTag("noise_filter_aggressive").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertEquals(com.example.data.model.NoiseSuppressionLevel.AGGRESSIVE, selectedLevel)
        composeTestRule.onNodeWithTag("noise_suppression_status").assertTextContains("DEEP")

        composeTestRule.onNodeWithTag("hum_notch_switch").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertEquals(false, humNotch)

        composeTestRule.onNodeWithTag("noise_filter_off").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertEquals(com.example.data.model.NoiseSuppressionLevel.OFF, selectedLevel)
        composeTestRule.onNodeWithTag("noise_suppression_status").assertTextContains("BYPASSED")
    }

    @Test
    fun `phonetic analyzer identifies repeated vocal formant patterns as spirit speech in wav recordings`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val tempDir = File(context.cacheDir, "test_phonetic").apply { mkdirs() }
        val wavFile = File(tempDir, "repeated_spirit_speech.wav")

        val sampleRate = DigitalAudioRecorder.SAMPLE_RATE
        val durationSec = 4
        val totalSamples = sampleRate * durationSec
        val samples = ShortArray(totalSamples)

        // Quiet floor with two repeated multi-formant vocal patterns at 0.60..0.95s and 2.40..2.75s
        for (i in 0 until totalSamples) {
            val t = i.toDouble() / sampleRate
            val floor = if (i % 2 == 0) 110.0 else -110.0
            val inFirst = t in 0.60..0.95
            val inSecond = t in 2.40..2.75
            val vocal = if (inFirst || inSecond) {
                sin(2.0 * PI * 380.0 * t) * 4500.0 +
                    sin(2.0 * PI * 820.0 * t) * 6500.0 +
                    sin(2.0 * PI * 1650.0 * t) * 3200.0
            } else {
                0.0
            }
            samples[i] = (floor + vocal).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()
        }
        writeTestWav(wavFile, sampleRate, samples)

        val waveformData = com.example.engine.EvpWaveformExtractor.extractFromWavBlocking(
            file = wavFile,
            fallbackDurationMs = 4000L,
            targetBins = 400
        )!!

        val phonetic = waveformData.phoneticSegments
        assertTrue("Expected >= 2 detected phonetic segments, got ${phonetic.size}", phonetic.size >= 2)
        val repeated = phonetic.filter { it.isRepeatedPattern }
        assertTrue("Expected repeated pattern segments to be flagged", repeated.size >= 2)
        assertTrue(repeated.all { it.patternGroup != null && it.repeatCount >= 2 })
        assertEquals(repeated[0].patternGroup, repeated[1].patternGroup)
    }

    @Test
    fun `zoomable evp waveform panel renders spirit speech chips and seeks when tapped`() {
        val bins = List(200) { idx ->
            val active = idx in 40..55 || idx in 130..145
            com.example.engine.WaveformBin(
                peakNorm = if (active) 0.72f else 0.03f,
                rmsNorm = if (active) 0.45f else 0.015f,
                peakDb = if (active) -3f else -36f
            )
        }
        val speechSegments = listOf(
            com.example.engine.PhoneticSpeechSegment(
                startMs = 800L,
                endMs = 1150L,
                patternGroup = "Pattern A",
                repeatCount = 2,
                phoneticTag = "ah-t/k",
                confidenceScore = 88,
                similarityPct = 94,
                isRepeatedPattern = true
            ),
            com.example.engine.PhoneticSpeechSegment(
                startMs = 2600L,
                endMs = 2950L,
                patternGroup = "Pattern A",
                repeatCount = 2,
                phoneticTag = "ah-t/k",
                confidenceScore = 87,
                similarityPct = 94,
                isRepeatedPattern = true
            )
        )
        val waveform = com.example.engine.EvpWaveformData(
            filePath = "/tmp/phonetic_ui_test.wav",
            sampleRate = 22050,
            durationMs = 4000L,
            bins = bins,
            noiseFloorNorm = 0.015f,
            noiseFloorDb = -36f,
            peakRegions = emptyList(),
            silenceRegions = emptyList(),
            phoneticSegments = speechSegments
        )

        var lastSeekMs by mutableStateOf(-1L)
        composeTestRule.setContent {
            GeoportEmfTheme {
                com.example.ui.components.ZoomableEvpWaveformPanel(
                    waveformData = waveform,
                    positionMs = 0L,
                    durationMs = 4000L,
                    isPlaying = false,
                    onSeekMs = { lastSeekMs = it }
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("phonetic_summary_label")
            .assertTextContains("2 Spirit Speech", substring = true)
            .assertTextContains("2 Repeated Pattern", substring = true)

        composeTestRule.onNodeWithTag("phonetic_jump_chip_1").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertEquals(2600L, lastSeekMs)
    }

    @Test
    fun `clip auto tag engine tags high emf sharp temperature drop and high speech probability`() {
        val phonetic = listOf(
            com.example.engine.PhoneticSpeechSegment(
                startMs = 500L,
                endMs = 950L,
                patternGroup = "Pattern A",
                repeatCount = 2,
                phoneticTag = "ah-s/z",
                confidenceScore = 82,
                similarityPct = 91,
                isRepeatedPattern = true
            )
        )
        val flagged = listOf(
            com.example.data.local.FlaggedAudioSegmentEntity(
                id = 1L,
                clipId = 10L,
                segmentFileName = "seg_1.wav",
                segmentFilePath = "/tmp/seg_1.wav",
                timestampMs = 50_000L,
                startOffsetMs = 400L,
                endOffsetMs = 1100L,
                peakAudioDb = -5f,
                snrDb = 11.2f,
                acousticClass = "Vocal Formant Burst",
                emfMagnitudeUt = 64.5f,
                emfDeltaUt = 18.2f,
                confidenceScore = 85
            )
        )

        val eval = com.example.engine.ClipAutoTagEngine.evaluateTags(
            peakEmfUt = 64.5f,
            peakEmfDeltaUt = 18.2f,
            baselineEmfUt = 45.0f,
            emfThresholdUt = 6.0f,
            tempDropC = -2.3f,
            modeLabel = "Frank's Box + Mic",
            flaggedSegments = flagged,
            phoneticSegments = phonetic
        )

        assertTrue(eval.tags.contains("High EMF"))
        assertTrue(eval.tags.contains("Sharp Temperature Drop"))
        assertTrue(eval.tags.contains("High Speech Probability"))
        assertTrue(eval.tags.contains("Repeated Pattern"))
        assertTrue(eval.tags.contains("EVP+EMF Co-Incidence"))
        assertTrue(eval.tags.contains("Spirit Box Mix"))
        assertTrue(eval.speechProbabilityPct >= 82)

        val parsed = com.example.engine.ClipAutoTagEngine.parseTags(eval.serializedTags)
        assertEquals(eval.tags, parsed)
    }

    @Test
    fun `temperature sensor engine detects sharp drop from rolling peak and resets on calibration`() {
        val engine = com.example.engine.TemperatureSensorEngine(context = null)
        val baseTime = 1_700_000_000_000L

        val r1 = engine.processTemperatureSample(23.0f, baseTime, "Ambient Temp Sensor")
        assertEquals(false, r1.isSharpDrop)

        val r2 = engine.processTemperatureSample(22.8f, baseTime + 5_000L, "Ambient Temp Sensor")
        assertEquals(false, r2.isSharpDrop)

        val r3 = engine.processTemperatureSample(20.9f, baseTime + 12_000L, "Ambient Temp Sensor")
        assertTrue("Expected sharp drop when temp fell by >2°C, delta=${r3.deltaC}", r3.isSharpDrop)
        assertTrue(r3.deltaC <= -1.5f)

        engine.calibrateBaseline()
        assertEquals(false, engine.currentReading.value.isSharpDrop)
        assertEquals(0f, engine.currentReading.value.deltaC, 0.01f)
    }

    @Test
    fun `telemetry trend chart switches time scales toggles series and displays audio correlations`() {
        val now = 1_700_000_600_000L
        val samples = listOf(
            // 10 minutes ago (excluded in 1M and 5M, included in 15M/All)
            com.example.data.local.SensorTrendSampleEntity(
                id = 1L,
                timestampMs = now - 600_000L,
                emfMagnitudeUt = 45.2f,
                emfDeltaUt = 0.2f,
                temperatureC = 22.5f,
                tempDeltaC = 0f,
                tempSource = "Ambient Temp Sensor"
            ),
            // 3 minutes ago (excluded in 1M, included in 5M)
            com.example.data.local.SensorTrendSampleEntity(
                id = 2L,
                timestampMs = now - 180_000L,
                emfMagnitudeUt = 48.0f,
                emfDeltaUt = 3.0f,
                temperatureC = 22.0f,
                tempDeltaC = -0.5f,
                tempSource = "Ambient Temp Sensor"
            ),
            // 30 seconds ago (included in 1M)
            com.example.data.local.SensorTrendSampleEntity(
                id = 3L,
                timestampMs = now - 30_000L,
                emfMagnitudeUt = 62.4f,
                emfDeltaUt = 17.4f,
                temperatureC = 20.1f,
                tempDeltaC = -2.4f,
                tempSource = "Ambient Temp Sensor"
            ),
            // Now (included in 1M)
            com.example.data.local.SensorTrendSampleEntity(
                id = 4L,
                timestampMs = now,
                emfMagnitudeUt = 54.0f,
                emfDeltaUt = 9.0f,
                temperatureC = 20.4f,
                tempDeltaC = -2.1f,
                tempSource = "Ambient Temp Sensor"
            )
        )
        val clips = listOf(
            AudioClipEntity(
                id = 101L,
                fileName = "EVP_cold_spot.wav",
                filePath = "/tmp/EVP_cold_spot.wav",
                durationMs = 4200L,
                createdAtMs = now - 25_000L,
                modeLabel = "Direct EVP Mic",
                peakEmfDuringClip = 62.4f,
                peakEmfDeltaDuringClip = 17.4f,
                tempDropC = -2.4f,
                speechProbabilityPct = 84,
                autoTags = "High EMF,Sharp Temperature Drop,High Speech Probability"
            )
        )

        composeTestRule.setContent {
            GeoportEmfTheme {
                com.example.ui.components.TelemetryTrendChartCard(
                    samples = samples,
                    audioClips = clips,
                    tempSourceLabel = "Ambient Temp Sensor",
                    initialScale = com.example.data.model.TrendTimeScale.ALL
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("telemetry_trend_subtitle")
            .assertTextContains("4 pts", substring = true)
        composeTestRule.onNodeWithTag("trend_toggle_audio")
            .assertTextContains("Audio (1)", substring = true)

        // Switch to 1M scale -> only last 2 samples remain
        composeTestRule.onNodeWithTag("trend_scale_one_min").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("telemetry_trend_subtitle")
            .assertTextContains("2 pts", substring = true)

        // Toggle temperature and EMF series chips cleanly
        composeTestRule.onNodeWithTag("trend_toggle_temp").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("trend_toggle_emf").performScrollTo().performClick()
        composeTestRule.waitForIdle()
    }

    @Test
    fun `recorder screen filters audio clips by auto metadata tags`() {
        val clips = listOf(
            AudioClipEntity(
                id = 1L,
                fileName = "EVP_high_emf_only.wav",
                filePath = "/tmp/c1.wav",
                durationMs = 3000L,
                createdAtMs = 1_700_000_000_000L,
                modeLabel = "Direct EVP Mic",
                peakEmfDuringClip = 63.0f,
                autoTags = "High EMF"
            ),
            AudioClipEntity(
                id = 2L,
                fileName = "EVP_cold_whisper.wav",
                filePath = "/tmp/c2.wav",
                durationMs = 4500L,
                createdAtMs = 1_700_000_050_000L,
                modeLabel = "Direct EVP Mic",
                peakEmfDuringClip = 46.2f,
                tempDropC = -2.2f,
                speechProbabilityPct = 88,
                autoTags = "Sharp Temperature Drop,High Speech Probability"
            )
        )

        composeTestRule.setContent {
            GeoportEmfTheme {
                com.example.ui.screens.RecorderScreen(
                    recorderState = com.example.data.model.RecorderState(hasMicPermission = true),
                    liveWaveform = List(24) { 0.1f },
                    spiritBoxRunning = false,
                    clips = clips,
                    flaggedSegments = emptyList(),
                    analyzingClipId = null,
                    playbackState = com.example.data.model.PlaybackState(),
                    waveformCache = emptyMap(),
                    inspectedClipId = null,
                    inspectedSegmentId = null,
                    storageStatus = com.example.engine.StorageLocationManager(
                        ApplicationProvider.getApplicationContext()
                    ).detectVolumes(com.example.engine.StorageTargetMode.AUTO_SD_PREFERRED, null, null),
                    isTransferring = false,
                    onRefreshStorage = {},
                    onSelectStorageMode = {},
                    onPickCustomFolder = {},
                    onTransferAllFiles = {},
                    onRequestMicPermission = {},
                    onStartRecording = {},
                    onTogglePause = {},
                    onStopRecording = {},
                    onMixSpiritBoxChange = {},
                    onTagMarker = {},
                    onAnalyzeClip = {},
                    onPlayPauseClip = {},
                    onPlayPauseSegment = {},
                    onInspectClip = {},
                    onInspectSegment = {},
                    onCloseInspector = {},
                    onSeekAndPrepareClip = { _, _ -> },
                    onSeekAndPrepareSegment = { _, _ -> },
                    onLoopRangeChange = {},
                    onLoadWaveform = { _, _ -> },
                    onSeekPlayback = {},
                    onPlaybackSpeedChange = {},
                    onSaveCopyToDevice = {},
                    onSaveSegmentToDevice = {},
                    onShareClip = {},
                    onShareSegment = {},
                    onDeleteClip = {},
                    onDeleteSegment = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("recorder_list")
            .performScrollToNode(hasTestTag("clip_auto_tag_filter_bar"))
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("clip_tag_filter_sharp_temp_drop").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("recorder_list")
            .performScrollToNode(hasTestTag("play_clip_2"))
        composeTestRule.onNodeWithTag("play_clip_2").assertExists()
        composeTestRule.onNodeWithTag("play_clip_1").assertDoesNotExist()

        composeTestRule.onNodeWithTag("recorder_list")
            .performScrollToNode(hasTestTag("clip_auto_tag_filter_bar"))
        composeTestRule.onNodeWithTag("clip_tag_filter_all").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("recorder_list")
            .performScrollToNode(hasTestTag("play_clip_1"))
        composeTestRule.onNodeWithTag("play_clip_1").assertExists()
    }

    @Test
    fun `emf sensor engine rejects steady power source wiring and periodic ac mains ripple while passing sudden spikes`() {
        val engine = com.example.engine.EmfSensorEngine(context = null)
        var t = 1_700_000_000_000L

        // 1. Calibrate Earth baseline at 45.0 μT (15 samples)
        repeat(15) {
            engine.processMagneticSample(45.0f, 0f, 0f, t)
            t += 100L
        }

        // 2. Approach steady wall wiring / appliance field (+16 μT constant = 61.0 μT) for 3.0s
        var steadyReading = EmfReading()
        repeat(30) {
            steadyReading = engine.processMagneticSample(61.0f, 0f, 0f, t)
            t += 100L
        }
        assertEquals(true, steadyReading.isPowerSourceFiltered)
        assertEquals("Wiring / Appliance", steadyReading.powerSourceLabel)
        assertTrue(
            "Expected powerSourceOffsetUt > 12 μT, got ${steadyReading.powerSourceOffsetUt}",
            steadyReading.powerSourceOffsetUt > 12f
        )
        assertTrue(
            "Expected steady wiring delta filtered below 3.0 μT, got ${steadyReading.delta}",
            kotlin.math.abs(steadyReading.delta) < 3.0f
        )

        // 3. Walk away from power source back to 45.0 μT — verify no false negative rebound alert
        var walkAwayReading = EmfReading()
        repeat(8) {
            walkAwayReading = engine.processMagneticSample(45.0f, 0f, 0f, t)
            assertTrue(
                "Walking away from power source should not create a false rebound alert, got ${walkAwayReading.delta}",
                kotlin.math.abs(walkAwayReading.delta) < 3.5f
            )
            t += 100L
        }
        assertEquals(0f, walkAwayReading.powerSourceOffsetUt, 0.25f)

        // 4. Sudden transient +18 μT anomaly spike (63.0 μT for 3 samples) should pass through
        var spikeReading = EmfReading()
        repeat(3) {
            spikeReading = engine.processMagneticSample(63.0f, 0f, 0f, t)
            t += 100L
        }
        assertTrue(
            "Expected sudden transient spike > 12 μT to pass through, got ${spikeReading.delta}",
            spikeReading.delta > 12.0f
        )

        // Return briefly to baseline
        repeat(5) {
            engine.processMagneticSample(45.0f, 0f, 0f, t)
            t += 100L
        }

        // 5. Periodic alternating AC mains ripple (45 ± 8 μT) should be detected and suppressed
        var rippleReading = EmfReading()
        for (i in 0 until 16) {
            val mag = if (i % 2 == 0) 53.0f else 37.0f
            rippleReading = engine.processMagneticSample(mag, 0f, 0f, t)
            t += 60L
        }
        assertEquals(true, rippleReading.isPowerSourceFiltered)
        assertEquals("AC Mains Ripple", rippleReading.powerSourceLabel)
        assertTrue(
            "Expected AC mains ripple delta suppressed below 3.0 μT, got ${rippleReading.delta}",
            kotlin.math.abs(rippleReading.delta) < 3.0f
        )

        // 6. Switching to OFF mode passes raw +16 μT reading untouched
        engine.setPowerSourceFilterMode(com.example.data.model.PowerSourceFilterMode.OFF)
        val rawReading = engine.processMagneticSample(61.0f, 0f, 0f, t)
        assertEquals(false, rawReading.isPowerSourceFiltered)
        assertEquals(16.0f, rawReading.delta, 0.1f)
    }

    @Test
    fun `emf radar screen renders power source filter controls and rejected power emf readout`() {
        var selectedMode by mutableStateOf(com.example.data.model.PowerSourceFilterMode.STANDARD)

        composeTestRule.setContent {
            GeoportEmfTheme {
                com.example.ui.screens.EmfRadarScreen(
                    sensorStatus = com.example.data.model.EmfSensorStatus(
                        isHardwareAvailable = true,
                        isListening = true,
                        isCalibrating = false,
                        sensorName = "AK09918 Magnetometer",
                        sensorVendor = "AKM",
                        resolutionUt = 0.15f
                    ),
                    reading = EmfReading(
                        x = 12f,
                        y = 35f,
                        z = 46f,
                        magnitude = 59.2f,
                        baseline = 45.0f,
                        delta = 0.0f,
                        rawDelta = 14.2f,
                        powerSourceOffsetUt = 14.2f,
                        isPowerSourceFiltered = true,
                        powerSourceLabel = "Wiring / Appliance"
                    ),
                    history = emptyList(),
                    activeSession = null,
                    alertConfig = com.example.data.model.EmfAlertConfig(
                        powerSourceFilterMode = selectedMode
                    ),
                    onCalibrateClick = {},
                    onManualMarkClick = {},
                    onThresholdChange = {},
                    onAudibleAlertToggle = {},
                    onSelectAlertSound = {},
                    onPreviewAlertSound = {},
                    onHapticToggle = {},
                    onIgnoreMotionToggle = {},
                    onStartSessionClick = {},
                    onStopSessionClick = {},
                    onPowerFilterModeChange = { selectedMode = it }
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("power_filter_active_badge")
            .assertTextContains("AC/WIRING REJECTED", substring = true)
        composeTestRule.onNodeWithTag("power_filter_offset_readout")
            .assertTextContains("Blocked Wiring / Appliance", substring = true)

        composeTestRule.onNodeWithTag("emf_radar_list")
            .performScrollToNode(hasTestTag("power_source_filter_panel"))
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("power_filter_mode_status")
            .assertTextContains("STANDARD", substring = true)

        composeTestRule.onNodeWithTag("power_filter_strict").performClick()
        composeTestRule.waitForIdle()
        assertEquals(com.example.data.model.PowerSourceFilterMode.STRICT, selectedMode)
        composeTestRule.onNodeWithTag("power_filter_mode_status")
            .assertTextContains("STRICT", substring = true)

        composeTestRule.onNodeWithTag("power_filter_off").performClick()
        composeTestRule.waitForIdle()
        assertEquals(com.example.data.model.PowerSourceFilterMode.OFF, selectedMode)
        composeTestRule.onNodeWithTag("power_filter_mode_status")
            .assertTextContains("OFF", substring = true)
    }

    private fun writeTestWav(file: File, sampleRate: Int, samples: ShortArray) {
        val totalAudioLen = samples.size.toLong() * 2L
        val totalDataLen = totalAudioLen + 36L
        val byteRate = sampleRate * 2
        val header = ByteArray(44)
        header[0] = 'R'.code.toByte(); header[1] = 'I'.code.toByte(); header[2] = 'F'.code.toByte(); header[3] = 'F'.code.toByte()
        header[4] = (totalDataLen and 0xFF).toByte()
        header[5] = ((totalDataLen shr 8) and 0xFF).toByte()
        header[6] = ((totalDataLen shr 16) and 0xFF).toByte()
        header[7] = ((totalDataLen shr 24) and 0xFF).toByte()
        header[8] = 'W'.code.toByte(); header[9] = 'A'.code.toByte(); header[10] = 'V'.code.toByte(); header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte(); header[13] = 'm'.code.toByte(); header[14] = 't'.code.toByte(); header[15] = ' '.code.toByte()
        header[16] = 16; header[20] = 1; header[22] = 1
        header[24] = (sampleRate and 0xFF).toByte()
        header[25] = ((sampleRate shr 8) and 0xFF).toByte()
        header[26] = ((sampleRate shr 16) and 0xFF).toByte()
        header[27] = ((sampleRate shr 24) and 0xFF).toByte()
        header[28] = (byteRate and 0xFF).toByte()
        header[29] = ((byteRate shr 8) and 0xFF).toByte()
        header[30] = ((byteRate shr 16) and 0xFF).toByte()
        header[31] = ((byteRate shr 24) and 0xFF).toByte()
        header[32] = 2; header[34] = 16
        header[36] = 'd'.code.toByte(); header[37] = 'a'.code.toByte(); header[38] = 't'.code.toByte(); header[39] = 'a'.code.toByte()
        header[40] = (totalAudioLen and 0xFF).toByte()
        header[41] = ((totalAudioLen shr 8) and 0xFF).toByte()
        header[42] = ((totalAudioLen shr 16) and 0xFF).toByte()
        header[43] = ((totalAudioLen shr 24) and 0xFF).toByte()

        RandomAccessFile(file, "rw").use { raf ->
            raf.setLength(0L)
            raf.write(header)
            val bytes = ByteArray(samples.size * 2)
            for (i in samples.indices) {
                val s = samples[i].toInt()
                bytes[i * 2] = (s and 0xFF).toByte()
                bytes[i * 2 + 1] = ((s shr 8) and 0xFF).toByte()
            }
            raf.write(bytes)
        }
    }
}
