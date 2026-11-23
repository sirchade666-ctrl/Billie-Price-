package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import androidx.core.content.FileProvider
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AudioClipEntity
import com.example.data.local.EmfAnomalyEntity
import com.example.data.local.FlaggedAudioSegmentEntity
import com.example.data.local.InvestigationDatabase
import com.example.data.local.InvestigationSessionEntity
import com.example.data.local.JournalEntryEntity
import com.example.data.local.SensorTrendSampleEntity
import com.example.BuildConfig
import com.example.data.model.AppUpdateUiState
import com.example.data.model.BatteryTelemetryState
import com.example.data.model.EmfAlertBannerState
import com.example.data.model.EmfAlertConfig
import com.example.data.model.EmfAlertSound
import com.example.data.model.EmfReading
import com.example.data.model.EmfSensorStatus
import com.example.data.model.LocationStatus
import com.example.data.model.NoiseSuppressionLevel
import com.example.data.model.OvilusLogEntry
import com.example.data.model.OvilusMode
import com.example.data.model.OvilusState
import com.example.data.model.OvilusVoiceProfile
import com.example.data.model.PlaybackState
import com.example.data.model.PowerSourceFilterMode
import com.example.data.model.RecorderState
import com.example.data.model.SpiritBoxMode
import com.example.data.model.SpiritBoxState
import com.example.data.model.SweepDirection
import com.example.data.model.TemperatureReading
import com.example.data.model.TemperatureSensorStatus
import com.example.data.model.UpdateCheckStatus
import com.example.data.repository.InvestigationRepository
import com.example.engine.AppUpdateEngine
import com.example.engine.BatteryMonitorEngine
import com.example.engine.ClipAutoTagEngine
import com.example.engine.DigitalAudioRecorder
import com.example.engine.EmfAlertAudioPlayer
import com.example.engine.EmfSensorEngine
import com.example.engine.EmfTimelineSample
import com.example.engine.EvpAudioAnalyzer
import com.example.engine.EvpWaveformData
import com.example.engine.EvpWaveformExtractor
import com.example.engine.LocalAudioPlayer
import com.example.engine.LocationTrackerEngine
import com.example.engine.OvilusEngine
import com.example.engine.SpiritBoxDspEngine
import com.example.engine.StorageLocationManager
import com.example.engine.StorageStatus
import com.example.engine.StorageTargetMode
import com.example.engine.TemperatureSensorEngine
import com.example.engine.UpdateCheckOutcome
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

private val Context.settingsDataStore by preferencesDataStore(name = "geoport_settings")

sealed class UiEvent {
    data class ShowMessage(val message: String) : UiEvent()
    data class LaunchShareIntent(val intent: Intent) : UiEvent()
    data class RequestSafSaveText(val suggestedName: String, val mimeType: String, val content: String) : UiEvent()
    data class RequestSafSaveAudio(val suggestedName: String, val sourcePath: String) : UiEvent()
}

class InvestigationViewModel(application: Application) : AndroidViewModel(application) {

    private val db = InvestigationDatabase.getInstance(application)
    private val repository = InvestigationRepository(
        context = application,
        sessionDao = db.sessionDao(),
        anomalyDao = db.anomalyDao(),
        audioClipDao = db.audioClipDao(),
        flaggedSegmentDao = db.flaggedSegmentDao(),
        journalDao = db.journalDao(),
        sensorTrendDao = db.sensorTrendDao()
    )

    private val emfEngine = EmfSensorEngine(application)
    private val temperatureEngine = TemperatureSensorEngine(application)
    private val spiritBoxEngine = SpiritBoxDspEngine(viewModelScope)
    private val recorder = DigitalAudioRecorder(viewModelScope, spiritBoxEngine)
    private val audioPlayer = LocalAudioPlayer(viewModelScope)
    private val alertAudioPlayer = EmfAlertAudioPlayer(viewModelScope)
    private val evpAnalyzer = EvpAudioAnalyzer()
    private val locationTracker = LocationTrackerEngine(application)
    private val storageManagerHelper = StorageLocationManager(application)
    private val batteryMonitor = BatteryMonitorEngine(application, viewModelScope)
    private val ovilusEngine = OvilusEngine(application, viewModelScope)

    private val thresholdKey = floatPreferencesKey("anomaly_threshold_ut")
    private val audibleAlertKey = booleanPreferencesKey("audible_alert_enabled")
    private val alertSoundKey = stringPreferencesKey("alert_sound_name")
    private val hapticKey = booleanPreferencesKey("haptic_alert_enabled")
    private val ignoreMotionAlertKey = booleanPreferencesKey("ignore_motion_alerts")
    private val powerSourceFilterKey = stringPreferencesKey("power_source_filter_mode")

    private val storageModeKey = stringPreferencesKey("storage_target_mode")
    private val customTreeUriKey = stringPreferencesKey("custom_tree_uri")
    private val customFolderLabelKey = stringPreferencesKey("custom_folder_label")

    private val batteryWarnPctKey = intPreferencesKey("battery_warning_pct")
    private val autoProtectRecKey = booleanPreferencesKey("auto_protect_recording")
    private val nightVisionKey = booleanPreferencesKey("night_vision_enabled")
    private val nightVisionBrightnessKey = floatPreferencesKey("night_vision_brightness")
    private val noiseSuppressionKey = stringPreferencesKey("noise_suppression_level")
    private val mainsHumNotchKey = booleanPreferencesKey("mains_hum_notch_enabled")
    private val updateSourceKey = stringPreferencesKey("app_update_source")
    private val autoCheckUpdatesKey = booleanPreferencesKey("app_update_auto_check")
    private val skippedVersionKey = stringPreferencesKey("app_update_skipped_version")

    val nightVisionEnabled: StateFlow<Boolean> = application.settingsDataStore.data
        .map { prefs -> prefs[nightVisionKey] ?: false }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val nightVisionBrightness: StateFlow<Float> = application.settingsDataStore.data
        .map { prefs -> (prefs[nightVisionBrightnessKey] ?: 0.90f).coerceIn(0.40f, 1.15f) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.90f)

    val alertConfig: StateFlow<EmfAlertConfig> = application.settingsDataStore.data
        .map { prefs ->
            val soundName = prefs[alertSoundKey] ?: EmfAlertSound.K2_CHIRP.name
            val sound = runCatching { EmfAlertSound.valueOf(soundName) }.getOrDefault(EmfAlertSound.K2_CHIRP)
            val powerModeName = prefs[powerSourceFilterKey] ?: PowerSourceFilterMode.STANDARD.name
            val powerMode = runCatching { PowerSourceFilterMode.valueOf(powerModeName) }
                .getOrDefault(PowerSourceFilterMode.STANDARD)
            EmfAlertConfig(
                thresholdUt = prefs[thresholdKey] ?: 6.0f,
                audibleAlertEnabled = prefs[audibleAlertKey] ?: true,
                selectedSound = sound,
                hapticAlertEnabled = prefs[hapticKey] ?: true,
                ignoreMotionAlerts = prefs[ignoreMotionAlertKey] ?: true,
                powerSourceFilterMode = powerMode
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), EmfAlertConfig())

    private val _storageStatus = MutableStateFlow(
        storageManagerHelper.detectVolumes(StorageTargetMode.AUTO_SD_PREFERRED, null, null)
    )
    val storageStatus: StateFlow<StorageStatus> = _storageStatus.asStateFlow()

    private val _isTransferring = MutableStateFlow(false)
    val isTransferring: StateFlow<Boolean> = _isTransferring.asStateFlow()

    val sensorStatus: StateFlow<EmfSensorStatus> = emfEngine.sensorStatus
    val currentEmfReading: StateFlow<EmfReading> = emfEngine.currentReading
    val emfHistory: StateFlow<List<EmfReading>> = emfEngine.history
    val temperatureStatus: StateFlow<TemperatureSensorStatus> = temperatureEngine.status
    val temperatureReading: StateFlow<TemperatureReading> = temperatureEngine.currentReading
    val locationStatus: StateFlow<LocationStatus> = locationTracker.status
    val batteryState: StateFlow<BatteryTelemetryState> = batteryMonitor.state

    val spiritBoxState: StateFlow<SpiritBoxState> = spiritBoxEngine.state
    val spiritSpectrum: StateFlow<FloatArray> = spiritBoxEngine.spectrumBars
    val ovilusState: StateFlow<OvilusState> = ovilusEngine.state

    val recorderState: StateFlow<RecorderState> = recorder.state
    val liveWaveform: StateFlow<List<Float>> = recorder.liveWaveform
    val playbackState: StateFlow<PlaybackState> = audioPlayer.playbackState

    val sessions: StateFlow<List<InvestigationSessionEntity>> = repository.allSessions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeSession: StateFlow<InvestigationSessionEntity?> = repository.activeSession
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val allAudioClips: StateFlow<List<AudioClipEntity>> = repository.allAudioClips
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allFlaggedSegments: StateFlow<List<FlaggedAudioSegmentEntity>> = repository.allFlaggedSegments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentAnomalies: StateFlow<List<EmfAnomalyEntity>> = repository.recentAnomalies
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val journalEntries: StateFlow<List<JournalEntryEntity>> = repository.allJournalEntries
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val trendSamples: StateFlow<List<SensorTrendSampleEntity>> = repository.recentTrendSamples
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedSessionId = MutableStateFlow<Long?>(null)
    val selectedSessionId: StateFlow<Long?> = _selectedSessionId.asStateFlow()

    private val _alertBannerState = MutableStateFlow(EmfAlertBannerState())
    val alertBannerState: StateFlow<EmfAlertBannerState> = _alertBannerState.asStateFlow()

    private val _analyzingClipId = MutableStateFlow<Long?>(null)
    val analyzingClipId: StateFlow<Long?> = _analyzingClipId.asStateFlow()

    private val _waveformCache = MutableStateFlow<Map<String, EvpWaveformData>>(emptyMap())
    val waveformCache: StateFlow<Map<String, EvpWaveformData>> = _waveformCache.asStateFlow()

    private val _inspectedClipId = MutableStateFlow<Long?>(null)
    val inspectedClipId: StateFlow<Long?> = _inspectedClipId.asStateFlow()

    private val _inspectedSegmentId = MutableStateFlow<Long?>(null)
    val inspectedSegmentId: StateFlow<Long?> = _inspectedSegmentId.asStateFlow()

    private val _hasCameraPermission = MutableStateFlow(false)
    val hasCameraPermission: StateFlow<Boolean> = _hasCameraPermission.asStateFlow()

    private val _appUpdateState = MutableStateFlow(
        AppUpdateUiState(
            currentVersionName = BuildConfig.VERSION_NAME,
            currentVersionCode = BuildConfig.VERSION_CODE
        )
    )
    val appUpdateState: StateFlow<AppUpdateUiState> = _appUpdateState.asStateFlow()

    private var hasRunStartupUpdateCheck = false

    private val _uiEvents = MutableSharedFlow<UiEvent>()
    val uiEvents = _uiEvents.asSharedFlow()

    private var lastAutoLogTimeMs = 0L
    private var lastTrendSampleTimeMs = 0L
    private var trendSampleInsertCount = 0
    private var alertDismissJob: Job? = null
    private val clipEmfTimelines = ConcurrentHashMap<Long, List<EmfTimelineSample>>()
    private var volumeCallback: Any? = null
    private var autoSavedOnCriticalBattery = false

    init {
        emfEngine.start()
        temperatureEngine.start()
        batteryMonitor.start()
        registerStorageVolumeWatcher()

        viewModelScope.launch {
            application.settingsDataStore.data.collect { prefs ->
                val modeName = prefs[storageModeKey] ?: StorageTargetMode.AUTO_SD_PREFERRED.name
                val mode = runCatching { StorageTargetMode.valueOf(modeName) }
                    .getOrDefault(StorageTargetMode.AUTO_SD_PREFERRED)
                val treeUri = prefs[customTreeUriKey]
                val folderLabel = prefs[customFolderLabelKey]
                _storageStatus.value = storageManagerHelper.detectVolumes(mode, treeUri, folderLabel)

                val warnPct = prefs[batteryWarnPctKey] ?: 15
                val autoProtect = prefs[autoProtectRecKey] ?: true
                batteryMonitor.setWarningThresholdPct(warnPct)
                batteryMonitor.setAutoProtectRecording(autoProtect)

                val nsName = prefs[noiseSuppressionKey] ?: NoiseSuppressionLevel.BALANCED.name
                val nsLevel = runCatching { NoiseSuppressionLevel.valueOf(nsName) }
                    .getOrDefault(NoiseSuppressionLevel.BALANCED)
                val humNotch = prefs[mainsHumNotchKey] ?: true
                recorder.setNoiseSuppressionLevel(nsLevel)
                recorder.setMainsHumNotchEnabled(humNotch)

                val savedSource = prefs[updateSourceKey] ?: ""
                val autoCheck = prefs[autoCheckUpdatesKey] ?: true
                val skipped = prefs[skippedVersionKey] ?: ""
                _appUpdateState.value = _appUpdateState.value.copy(
                    updateSource = savedSource,
                    autoCheckOnLaunch = autoCheck,
                    skippedVersion = skipped
                )
                if (!hasRunStartupUpdateCheck) {
                    hasRunStartupUpdateCheck = true
                    if (autoCheck && savedSource.isNotBlank()) {
                        checkForAppUpdates(sourceOverride = savedSource, isManual = false)
                    }
                }
            }
        }

        // Feed real hardware thermal probe fallback when ambient thermometer is absent
        // and auto-save active WAV recording on critical battery
        viewModelScope.launch {
            batteryMonitor.state.collect { bat ->
                temperatureEngine.updateFallbackDeviceTemp(bat.temperatureC)
                if (!bat.isCharging && bat.autoProtectRecording && bat.levelPct <= 5) {
                    if (!autoSavedOnCriticalBattery && recorderState.value.isRecording) {
                        autoSavedOnCriticalBattery = true
                        stopAndSaveAudioRecording()
                        _uiEvents.emit(
                            UiEvent.ShowMessage("Critical battery (${bat.levelPct}%): Auto-saved active WAV recording to prevent loss!")
                        )
                    }
                } else if (bat.levelPct > 8 || bat.isCharging) {
                    autoSavedOnCriticalBattery = false
                }
            }
        }

        viewModelScope.launch {
            temperatureEngine.currentReading.collect { temp ->
                recorder.updateCurrentTemperature(temp.temperatureC, temp.deltaC)
                maybeRecordTrendSample(
                    emf = currentEmfReading.value,
                    temp = temp,
                    forceIfAnomaly = temp.isSharpDrop
                )
            }
        }

        viewModelScope.launch {
            alertConfig.collect { cfg ->
                recorder.updateEmfThreshold(cfg.thresholdUt)
                emfEngine.setPowerSourceFilterMode(cfg.powerSourceFilterMode)
            }
        }

        viewModelScope.launch {
            ovilusEngine.triggeredEvents.collect { entry ->
                val session = activeSession.value
                if (ovilusState.value.autoLogToJournal && session != null && !entry.isQuestion) {
                    val coord = locationStatus.value.currentCoordinate
                    val indexNote = entry.bankIndex?.let { " (Lexicon #${it + 1} / 80,000)" } ?: ""
                    repository.insertJournalEntry(
                        JournalEntryEntity(
                            sessionId = session.id,
                            timestampMs = entry.timestampMs,
                            latitude = coord?.latitude,
                            longitude = coord?.longitude,
                            gpsAccuracyMeters = coord?.accuracyMeters,
                            locationLabel = session.locationName.ifBlank { "Ovilus Auto-Log" },
                            noteText = "Ovilus ITC: \"${entry.text}\"$indexNote [${entry.phoneticCue}]",
                            tags = "Ovilus, Auto-Log, 80K-Bank",
                            emfMagnitudeUt = entry.emfMagnitudeUt,
                            emfDeltaUt = entry.emfDeltaUt,
                            associatedAnomalyId = null,
                            associatedAnomalySummary = "Ovilus Flux: ${"%.1f".format(entry.emfMagnitudeUt)} μT (Δ${"%.1f".format(entry.emfDeltaUt)} μT)",
                            associatedClipId = null,
                            associatedClipName = ""
                        )
                    )
                }
            }
        }

        viewModelScope.launch {
            emfEngine.currentReading.collect { reading ->
                spiritBoxEngine.updateEmfDelta(reading.delta)
                recorder.updateCurrentEmf(reading.magnitude, reading.delta)
                ovilusEngine.onEmfReading(reading)

                val cfg = alertConfig.value
                val absDelta = abs(reading.delta)
                val now = System.currentTimeMillis()
                val thresholdExceeded = absDelta >= cfg.thresholdUt
                val motionSuppressed = cfg.ignoreMotionAlerts && reading.isDeviceMoving

                if (thresholdExceeded && !motionSuppressed) {
                    val severity = when {
                        absDelta >= cfg.thresholdUt * 2.5f -> "CRITICAL"
                        absDelta >= cfg.thresholdUt * 1.6f -> "HIGH"
                        else -> "ELEVATED"
                    }

                    _alertBannerState.value = EmfAlertBannerState(
                        isActive = true,
                        peakDeltaUt = reading.delta,
                        magnitudeUt = reading.magnitude,
                        severity = severity,
                        triggeredAtMs = now
                    )
                    alertDismissJob?.cancel()
                    alertDismissJob = viewModelScope.launch {
                        delay(3200L)
                        _alertBannerState.value = _alertBannerState.value.copy(isActive = false)
                    }

                    if (cfg.audibleAlertEnabled) {
                        alertAudioPlayer.triggerAlert(cfg.selectedSound, forcePreview = false)
                    }
                    if (cfg.hapticAlertEnabled) {
                        triggerHapticPulse()
                    }
                }

                val session = activeSession.value
                if (session != null && thresholdExceeded && now - lastAutoLogTimeMs >= 1600L) {
                    lastAutoLogTimeMs = now
                    val severity = when {
                        absDelta >= cfg.thresholdUt * 2.5f -> "CRITICAL"
                        absDelta >= cfg.thresholdUt * 1.6f -> "HIGH"
                        else -> "ELEVATED"
                    }
                    val note = if (reading.isDeviceMoving) {
                        "Auto-logged EMF fluctuation (motion detected)"
                    } else {
                        "Auto-logged stationary EMF fluctuation"
                    }
                    repository.logEmfAnomaly(
                        sessionId = session.id,
                        magnitudeUt = reading.magnitude,
                        deltaUt = reading.delta,
                        xUt = reading.x,
                        yUt = reading.y,
                        zUt = reading.z,
                        severity = severity,
                        note = note,
                        isMotionArtifact = reading.isDeviceMoving,
                        coordinate = locationStatus.value.currentCoordinate
                    )
                }

                maybeRecordTrendSample(
                    emf = reading,
                    temp = temperatureEngine.currentReading.value,
                    forceIfAnomaly = thresholdExceeded && !motionSuppressed
                )
            }
        }
    }

    private suspend fun maybeRecordTrendSample(
        emf: EmfReading,
        temp: TemperatureReading,
        forceIfAnomaly: Boolean
    ) {
        val now = System.currentTimeMillis()
        val minIntervalMs = if (forceIfAnomaly) 600L else 2000L
        if (now - lastTrendSampleTimeMs < minIntervalMs) return
        lastTrendSampleTimeMs = now

        val sample = SensorTrendSampleEntity(
            sessionId = activeSession.value?.id,
            timestampMs = now,
            emfMagnitudeUt = emf.magnitude,
            emfDeltaUt = emf.delta,
            temperatureC = temp.temperatureC,
            tempDeltaC = temp.deltaC,
            tempSource = temp.sourceLabel
        )
        runCatching {
            repository.insertTrendSample(sample)
            trendSampleInsertCount += 1
            if (trendSampleInsertCount % 120 == 0) {
                repository.pruneTrendSamplesOlderThan(now - 24L * 60L * 60L * 1000L)
            }
        }
    }

    private fun registerStorageVolumeWatcher() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val sm = getApplication<Application>().getSystemService(Context.STORAGE_SERVICE) as StorageManager
            val cb = object : StorageManager.StorageVolumeCallback() {
                override fun onStateChanged(volume: StorageVolume) {
                    refreshStorageVolumes(notifyIfSdFound = true)
                }
            }
            runCatching {
                sm.registerStorageVolumeCallback(getApplication<Application>().mainExecutor, cb)
                volumeCallback = cb
            }
        }
    }

    fun refreshStorageVolumes(notifyIfSdFound: Boolean = false) {
        val current = _storageStatus.value
        val updated = storageManagerHelper.detectVolumes(
            selectedMode = current.selectedMode,
            customTreeUri = current.customTreeUri,
            customFolderLabel = current.customFolderLabel
        )
        _storageStatus.value = updated
        if (notifyIfSdFound && updated.removableSdCard != null) {
            viewModelScope.launch {
                _uiEvents.emit(UiEvent.ShowMessage("Removable SD Card detected: ${updated.removableSdCard.label}"))
            }
        }
    }

    fun setStorageTargetMode(mode: StorageTargetMode) {
        viewModelScope.launch {
            getApplication<Application>().settingsDataStore.edit { prefs ->
                prefs[storageModeKey] = mode.name
            }
            val cur = _storageStatus.value
            _storageStatus.value = storageManagerHelper.detectVolumes(mode, cur.customTreeUri, cur.customFolderLabel)
        }
    }

    fun setCustomTransferFolder(treeUri: Uri) {
        viewModelScope.launch {
            val folderLabel = storageManagerHelper.persistTreePermission(treeUri)
            getApplication<Application>().settingsDataStore.edit { prefs ->
                prefs[storageModeKey] = StorageTargetMode.CUSTOM_FOLDER.name
                prefs[customTreeUriKey] = treeUri.toString()
                prefs[customFolderLabelKey] = folderLabel
            }
            _storageStatus.value = storageManagerHelper.detectVolumes(
                StorageTargetMode.CUSTOM_FOLDER,
                treeUri.toString(),
                folderLabel
            )
            _uiEvents.emit(UiEvent.ShowMessage("Save location set to \"$folderLabel\"."))
        }
    }

    fun transferAllFilesToSaveLocation() {
        if (_isTransferring.value) return
        viewModelScope.launch {
            _isTransferring.value = true
            try {
                val status = _storageStatus.value
                val clips = allAudioClips.value
                val segments = allFlaggedSegments.value
                val sessionList = sessions.value
                val journalList = journalEntries.value

                var copiedCount = 0

                if (status.selectedMode == StorageTargetMode.CUSTOM_FOLDER && !status.customTreeUri.isNullOrBlank()) {
                    val treeUri = Uri.parse(status.customTreeUri)
                    for (clip in clips) {
                        if (storageManagerHelper.copyFileToSafFolder(treeUri, File(clip.filePath), "audio/wav")) {
                            copiedCount++
                        }
                    }
                    for (seg in segments) {
                        if (storageManagerHelper.copyFileToSafFolder(treeUri, File(seg.segmentFilePath), "audio/wav")) {
                            copiedCount++
                        }
                    }
                    for (session in sessionList) {
                        val safe = session.title.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(24)
                        val csv = repository.buildSessionCsvReport(session.id)
                        val json = repository.buildSessionJsonReport(session.id)
                        if (storageManagerHelper.writeTextToSafFolder(treeUri, "geoport_${safe}_${session.id}.csv", "text/csv", csv)) copiedCount++
                        if (storageManagerHelper.writeTextToSafFolder(treeUri, "geoport_${safe}_${session.id}.json", "application/json", json)) copiedCount++
                    }
                    if (journalList.isNotEmpty()) {
                        val jCsv = repository.buildJournalCsvReport()
                        val jJson = repository.buildJournalJsonReport()
                        if (storageManagerHelper.writeTextToSafFolder(treeUri, "geoport_journal.csv", "text/csv", jCsv)) copiedCount++
                        if (storageManagerHelper.writeTextToSafFolder(treeUri, "geoport_journal.json", "application/json", jJson)) copiedCount++
                    }
                    if (ovilusState.value.logEntries.isNotEmpty()) {
                        val oCsv = ovilusEngine.buildTranscriptCsv()
                        val oJson = ovilusEngine.buildTranscriptJson()
                        if (storageManagerHelper.writeTextToSafFolder(treeUri, "geoport_ovilus_transcript.csv", "text/csv", oCsv)) copiedCount++
                        if (storageManagerHelper.writeTextToSafFolder(treeUri, "geoport_ovilus_transcript.json", "application/json", oJson)) copiedCount++
                    }
                    _uiEvents.emit(
                        UiEvent.ShowMessage("Transferred $copiedCount file(s) to \"${status.activeDestinationLabel}\".")
                    )
                } else {
                    val destDir = storageManagerHelper.resolveTransferDirectoryOnSdOrInternal(status)
                    for (clip in clips) {
                        if (storageManagerHelper.copyFileToDir(File(clip.filePath), destDir)) copiedCount++
                    }
                    for (seg in segments) {
                        if (storageManagerHelper.copyFileToDir(File(seg.segmentFilePath), destDir)) copiedCount++
                    }
                    withContext(Dispatchers.IO) {
                        for (session in sessionList) {
                            val safe = session.title.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(24)
                            File(destDir, "geoport_${safe}_${session.id}.csv").writeText(
                                repository.buildSessionCsvReport(session.id)
                            )
                            File(destDir, "geoport_${safe}_${session.id}.json").writeText(
                                repository.buildSessionJsonReport(session.id)
                            )
                            copiedCount += 2
                        }
                        if (journalList.isNotEmpty()) {
                            File(destDir, "geoport_journal.csv").writeText(repository.buildJournalCsvReport())
                            File(destDir, "geoport_journal.json").writeText(repository.buildJournalJsonReport())
                            copiedCount += 2
                        }
                        if (ovilusState.value.logEntries.isNotEmpty()) {
                            File(destDir, "geoport_ovilus_transcript.csv").writeText(ovilusEngine.buildTranscriptCsv())
                            File(destDir, "geoport_ovilus_transcript.json").writeText(ovilusEngine.buildTranscriptJson())
                            copiedCount += 2
                        }
                    }
                    _uiEvents.emit(
                        UiEvent.ShowMessage("Transferred $copiedCount file(s) to ${status.activeDestinationLabel}/GeoportTransfers.")
                    )
                }
            } finally {
                _isTransferring.value = false
            }
        }
    }

    fun observeAnomaliesForSession(sessionId: Long): Flow<List<EmfAnomalyEntity>> =
        repository.observeAnomaliesForSession(sessionId)

    fun observeClipsForSession(sessionId: Long): Flow<List<AudioClipEntity>> =
        repository.observeClipsForSession(sessionId)

    fun observeSegmentsForClip(clipId: Long): Flow<List<FlaggedAudioSegmentEntity>> =
        repository.observeSegmentsForClip(clipId)

    fun observeTrendSamplesForSession(sessionId: Long): Flow<List<SensorTrendSampleEntity>> =
        repository.observeTrendSamplesForSession(sessionId)

    fun selectSessionForInspection(sessionId: Long?) {
        _selectedSessionId.value = sessionId
    }

    // Alert Configuration
    fun setAnomalyThreshold(value: Float) {
        viewModelScope.launch {
            getApplication<Application>().settingsDataStore.edit { prefs ->
                prefs[thresholdKey] = value.coerceIn(1.0f, 30f)
            }
        }
    }

    fun setAudibleAlertEnabled(enabled: Boolean) {
        viewModelScope.launch {
            getApplication<Application>().settingsDataStore.edit { prefs ->
                prefs[audibleAlertKey] = enabled
            }
        }
    }

    fun setAlertSound(sound: EmfAlertSound) {
        viewModelScope.launch {
            getApplication<Application>().settingsDataStore.edit { prefs ->
                prefs[alertSoundKey] = sound.name
                prefs[audibleAlertKey] = sound != EmfAlertSound.SILENT
            }
            if (sound != EmfAlertSound.SILENT) {
                alertAudioPlayer.triggerAlert(sound, forcePreview = true)
            }
        }
    }

    fun previewSelectedAlertSound() {
        alertAudioPlayer.triggerAlert(alertConfig.value.selectedSound, forcePreview = true)
    }

    fun setHapticAlertEnabled(enabled: Boolean) {
        viewModelScope.launch {
            getApplication<Application>().settingsDataStore.edit { prefs ->
                prefs[hapticKey] = enabled
            }
        }
    }

    fun setIgnoreMotionAlerts(ignore: Boolean) {
        viewModelScope.launch {
            getApplication<Application>().settingsDataStore.edit { prefs ->
                prefs[ignoreMotionAlertKey] = ignore
            }
        }
    }

    fun setPowerSourceFilterMode(mode: PowerSourceFilterMode) {
        emfEngine.setPowerSourceFilterMode(mode)
        viewModelScope.launch {
            getApplication<Application>().settingsDataStore.edit { prefs ->
                prefs[powerSourceFilterKey] = mode.name
            }
            val msg = when (mode) {
                PowerSourceFilterMode.OFF -> "Power-source EMF filter disabled (showing raw EMF)."
                PowerSourceFilterMode.STANDARD -> "Power-source EMF filter set to Standard (rejecting steady wiring & AC hum)."
                PowerSourceFilterMode.STRICT -> "Power-source EMF filter set to Strict (aggressive wiring & appliance rejection)."
            }
            _uiEvents.emit(UiEvent.ShowMessage(msg))
        }
    }

    fun dismissVisualAlert() {
        alertDismissJob?.cancel()
        _alertBannerState.value = _alertBannerState.value.copy(isActive = false)
    }

    // Location Permission & Tracking
    fun updateLocationPermission(granted: Boolean) {
        locationTracker.updatePermission(granted)
    }

    fun calibrateEmfBaseline() {
        emfEngine.calibrateBaseline()
        temperatureEngine.calibrateBaseline()
        viewModelScope.launch {
            val session = activeSession.value
            if (session != null) {
                repository.updateSessionBaseline(session.id, currentEmfReading.value.magnitude)
            }
            _uiEvents.emit(UiEvent.ShowMessage("Calibrating ambient EMF & temperature baseline..."))
        }
    }

    fun setBatteryWarningThreshold(pct: Int) {
        viewModelScope.launch {
            getApplication<Application>().settingsDataStore.edit { prefs ->
                prefs[batteryWarnPctKey] = pct.coerceIn(5, 35)
            }
        }
    }

    fun setAutoProtectRecording(enabled: Boolean) {
        viewModelScope.launch {
            getApplication<Application>().settingsDataStore.edit { prefs ->
                prefs[autoProtectRecKey] = enabled
            }
        }
    }

    fun setNightVisionEnabled(enabled: Boolean) {
        viewModelScope.launch {
            getApplication<Application>().settingsDataStore.edit { prefs ->
                prefs[nightVisionKey] = enabled
            }
        }
    }

    fun setNightVisionBrightness(brightness: Float) {
        viewModelScope.launch {
            getApplication<Application>().settingsDataStore.edit { prefs ->
                prefs[nightVisionBrightnessKey] = brightness.coerceIn(0.40f, 1.15f)
            }
        }
    }

    fun toggleNightVision() {
        val next = !nightVisionEnabled.value
        setNightVisionEnabled(next)
        viewModelScope.launch {
            _uiEvents.emit(
                UiEvent.ShowMessage(
                    if (next) "Night Vision red overlay enabled" else "Night Vision mode disabled"
                )
            )
        }
    }

    fun enableLowPowerMode() {
        viewModelScope.launch {
            if (spiritBoxState.value.isRunning) {
                spiritBoxEngine.stop()
            }
            if (ovilusState.value.isActive) {
                ovilusEngine.stop()
            }
            locationTracker.stopTracking()
            _uiEvents.emit(
                UiEvent.ShowMessage("Low-Power Mode enabled: Spirit Box, Ovilus & continuous GPS paused; EMF logging remains active.")
            )
        }
    }

    fun startInvestigationSession(title: String, location: String, notes: String) {
        viewModelScope.launch {
            if (locationStatus.value.hasPermission) {
                locationTracker.startTracking()
            }
            val baseline = currentEmfReading.value.magnitude.takeIf { it > 0f } ?: 45f
            val newId = repository.startSession(
                title = title,
                locationName = location,
                notes = notes,
                baselineUt = baseline,
                coordinate = locationStatus.value.currentCoordinate
            )
            batteryMonitor.onInvestigationSessionStarted()
            _selectedSessionId.value = newId
            _uiEvents.emit(UiEvent.ShowMessage("Investigation session started."))
        }
    }

    fun endActiveInvestigationSession() {
        val current = activeSession.value ?: return
        viewModelScope.launch {
            repository.endActiveSession(current.id)
            batteryMonitor.onInvestigationSessionEnded()
            _uiEvents.emit(UiEvent.ShowMessage("Investigation session saved to archive."))
        }
    }

    fun deleteSession(sessionId: Long) {
        viewModelScope.launch {
            if (_selectedSessionId.value == sessionId) {
                _selectedSessionId.value = null
            }
            repository.deleteSession(sessionId)
            _uiEvents.emit(UiEvent.ShowMessage("Investigation session deleted."))
        }
    }

    fun logManualAnomaly(note: String = "Manual Investigator Marker") {
        viewModelScope.launch {
            var session = activeSession.value
            if (session == null) {
                val baseline = currentEmfReading.value.baseline
                val id = repository.startSession(
                    title = "Quick Field Log",
                    locationName = "Field Site",
                    notes = "Auto-created from manual EMF marker",
                    baselineUt = baseline,
                    coordinate = locationStatus.value.currentCoordinate
                )
                session = repository.allSessions.first().firstOrNull { it.id == id }
            }
            val reading = currentEmfReading.value
            if (session != null) {
                repository.logEmfAnomaly(
                    sessionId = session.id,
                    magnitudeUt = reading.magnitude,
                    deltaUt = reading.delta,
                    xUt = reading.x,
                    yUt = reading.y,
                    zUt = reading.z,
                    severity = "MANUAL",
                    note = note,
                    isMotionArtifact = reading.isDeviceMoving,
                    coordinate = locationStatus.value.currentCoordinate
                )
                _uiEvents.emit(UiEvent.ShowMessage("EMF marker logged (${"%.1f".format(reading.magnitude)} μT)."))
            }
        }
    }

    // Spirit Box Controls
    fun toggleSpiritBox() = spiritBoxEngine.toggleRunning()
    fun setSpiritBoxMode(mode: SpiritBoxMode) = spiritBoxEngine.setMode(mode)
    fun setSweepRateMs(ms: Int) = spiritBoxEngine.setSweepRateMs(ms)
    fun setSweepDirection(direction: SweepDirection) = spiritBoxEngine.setDirection(direction)
    fun setEchoDepth(depth: Float) = spiritBoxEngine.setEchoDepth(depth)
    fun setSquelch(squelch: Float) = spiritBoxEngine.setSquelch(squelch)
    fun setGain(gain: Float) = spiritBoxEngine.setGain(gain)
    fun setEmfCoupled(enabled: Boolean) = spiritBoxEngine.setEmfCoupled(enabled)

    // Ovilus ITC & 80K Word Bank Controls
    fun toggleOvilus() = ovilusEngine.toggleActive()
    fun triggerOvilusManualScan() = ovilusEngine.triggerManualScan()
    fun setOvilusMode(mode: OvilusMode) = ovilusEngine.setMode(mode)
    fun setOvilusVoiceProfile(profile: OvilusVoiceProfile) = ovilusEngine.setVoiceProfile(profile)
    fun selectOvilusDeviceVoice(voiceId: String) = ovilusEngine.selectDeviceVoice(voiceId)
    fun setOvilusPitch(pitch: Float) = ovilusEngine.setPitch(pitch)
    fun setOvilusSpeechRate(rate: Float) = ovilusEngine.setSpeechRate(rate)
    fun setOvilusSensitivity(ut: Float) = ovilusEngine.setSensitivityUt(ut)
    fun setOvilusCooldown(sec: Int) = ovilusEngine.setCooldownSeconds(sec)
    fun setOvilusAutoLog(enabled: Boolean) = ovilusEngine.setAutoLogToJournal(enabled)
    fun testOvilusVoice() = ovilusEngine.testClearVoice()
    fun speakOvilusLexiconWord(index: Int, word: String) = ovilusEngine.speakLexiconWord(index, word)
    fun askOvilusQuestion(question: String) = ovilusEngine.askQuestion(question)
    fun replayOvilusEntry(entry: OvilusLogEntry) = ovilusEngine.replayLogEntry(entry)
    fun clearOvilusLog() = ovilusEngine.clearLog()

    fun exportOvilusCsv() {
        viewModelScope.launch {
            val csv = ovilusEngine.buildTranscriptCsv()
            val name = "geoport_ovilus_${System.currentTimeMillis() / 1000}.csv"
            saveTextToActiveOrSaf(name, "text/csv", csv)
        }
    }

    fun exportOvilusJson() {
        viewModelScope.launch {
            val json = ovilusEngine.buildTranscriptJson()
            val name = "geoport_ovilus_${System.currentTimeMillis() / 1000}.json"
            saveTextToActiveOrSaf(name, "application/json", json)
        }
    }

    fun shareOvilusTranscript() {
        viewModelScope.launch {
            val csv = ovilusEngine.buildTranscriptCsv()
            val file = repository.writeTempExportFile("geoport_ovilus_transcript.csv", csv)
            val ctx = getApplication<Application>()
            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_SUBJECT, "Geoport EMF Ovilus Transcript (80K Word Bank)")
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            _uiEvents.emit(UiEvent.LaunchShareIntent(Intent.createChooser(intent, "Share Ovilus Transcript")))
        }
    }

    fun pinOvilusEntryToJournal(entry: OvilusLogEntry) {
        viewModelScope.launch {
            val coord = locationStatus.value.currentCoordinate
            val session = activeSession.value
            val indexNote = entry.bankIndex?.let { " (Lexicon #${it + 1} / 80,000)" } ?: ""
            val jEntry = JournalEntryEntity(
                sessionId = session?.id,
                timestampMs = entry.timestampMs,
                latitude = coord?.latitude,
                longitude = coord?.longitude,
                gpsAccuracyMeters = coord?.accuracyMeters,
                locationLabel = session?.locationName ?: "Ovilus ITC Log",
                noteText = "Ovilus Word: \"${entry.text}\"$indexNote [${entry.phoneticCue}]",
                tags = "Ovilus, ITC, 80K-Bank",
                emfMagnitudeUt = entry.emfMagnitudeUt,
                emfDeltaUt = entry.emfDeltaUt,
                associatedAnomalyId = null,
                associatedAnomalySummary = "Ovilus Flux: ${"%.1f".format(entry.emfMagnitudeUt)} μT (Δ${"%.1f".format(entry.emfDeltaUt)} μT)",
                associatedClipId = null,
                associatedClipName = ""
            )
            repository.insertJournalEntry(jEntry)
            _uiEvents.emit(UiEvent.ShowMessage("Pinned \"${entry.text}\" to Investigation Journal."))
        }
    }

    // Recorder & EVP/EMF Concurrent Analysis
    fun updateMicPermission(granted: Boolean) {
        recorder.updatePermission(granted)
    }

    fun setMixSpiritBox(mix: Boolean) = recorder.setMixSpiritBox(mix)

    fun setNoiseSuppressionLevel(level: NoiseSuppressionLevel) {
        recorder.setNoiseSuppressionLevel(level)
        viewModelScope.launch {
            getApplication<Application>().settingsDataStore.edit { prefs ->
                prefs[noiseSuppressionKey] = level.name
            }
        }
    }

    fun setMainsHumNotchEnabled(enabled: Boolean) {
        recorder.setMainsHumNotchEnabled(enabled)
        viewModelScope.launch {
            getApplication<Application>().settingsDataStore.edit { prefs ->
                prefs[mainsHumNotchKey] = enabled
            }
        }
    }

    fun startAudioRecording() {
        val dir = storageManagerHelper.resolveRecordingDirectory(_storageStatus.value)
        recorder.startRecording(dir) { err ->
            viewModelScope.launch {
                _uiEvents.emit(UiEvent.ShowMessage(err))
            }
        }
    }

    fun togglePauseRecording() = recorder.togglePause()

    fun stopAndSaveAudioRecording() {
        viewModelScope.launch {
            val result = recorder.stopRecording()
            if (result == null) {
                _uiEvents.emit(UiEvent.ShowMessage("Recording discarded (too short or empty)."))
                return@launch
            }
            val session = activeSession.value
            val coord = locationStatus.value.currentCoordinate
            val initialAutoEval = ClipAutoTagEngine.evaluateTags(
                peakEmfUt = result.peakEmfUt,
                peakEmfDeltaUt = result.peakEmfDeltaUt,
                baselineEmfUt = currentEmfReading.value.baseline,
                emfThresholdUt = alertConfig.value.thresholdUt,
                tempDropC = result.tempDropC,
                modeLabel = result.modeLabel
            )
            val clip = AudioClipEntity(
                sessionId = session?.id,
                fileName = result.file.name,
                filePath = result.file.absolutePath,
                durationMs = result.durationMs,
                createdAtMs = result.createdAtMs + result.durationMs,
                modeLabel = result.modeLabel,
                peakEmfDuringClip = result.peakEmfUt,
                markersSummary = result.markersSummary,
                fileSizeBytes = result.file.length(),
                latitude = coord?.latitude,
                longitude = coord?.longitude,
                isAnalyzed = false,
                peakEmfDeltaDuringClip = result.peakEmfDeltaUt,
                minTempC = result.minTempC,
                tempDropC = result.tempDropC,
                speechProbabilityPct = initialAutoEval.speechProbabilityPct,
                autoTags = initialAutoEval.serializedTags
            )
            val savedId = repository.saveAudioClip(clip)
            val savedClip = clip.copy(id = savedId)
            clipEmfTimelines[savedId] = result.emfTimeline

            // Mirror to custom SAF folder if selected
            val st = _storageStatus.value
            if (st.selectedMode == StorageTargetMode.CUSTOM_FOLDER && !st.customTreeUri.isNullOrBlank()) {
                storageManagerHelper.copyFileToSafFolder(Uri.parse(st.customTreeUri), result.file, "audio/wav")
            }

            // Automatically run concurrent audio + EMF EVP segment scan & auto-tag metadata
            val (segments, tagEval) = runClipAnalysisInternal(savedClip, result.emfTimeline)
            if (st.selectedMode == StorageTargetMode.CUSTOM_FOLDER && !st.customTreeUri.isNullOrBlank()) {
                for (seg in segments) {
                    storageManagerHelper.copyFileToSafFolder(
                        Uri.parse(st.customTreeUri),
                        File(seg.segmentFilePath),
                        "audio/wav"
                    )
                }
            }

            val destLabel = st.activeDestinationLabel
            val tagSummary = if (tagEval.tags.isNotEmpty()) " • Tags: ${tagEval.tags.joinToString(", ")}" else ""
            val msg = if (segments.isNotEmpty()) {
                "Saved ${result.file.name} ($destLabel) • Flagged ${segments.size} segment(s)$tagSummary"
            } else {
                "Saved ${result.file.name} to $destLabel$tagSummary"
            }
            _uiEvents.emit(UiEvent.ShowMessage(msg))
        }
    }

    fun analyzeAudioClip(clip: AudioClipEntity) {
        viewModelScope.launch {
            val timeline = clipEmfTimelines[clip.id].orEmpty()
            val (segments, tagEval) = runClipAnalysisInternal(clip, timeline)
            val tagText = if (tagEval.tags.isNotEmpty()) " Tags: ${tagEval.tags.joinToString(", ")}." else ""
            val msg = if (segments.isNotEmpty()) {
                "Flagged ${segments.size} concurrent audio+EMF segment(s) in ${clip.fileName}.$tagText"
            } else if (tagEval.tags.isNotEmpty()) {
                "Auto-tagged ${clip.fileName}:$tagText"
            } else {
                "Analysis complete: no unusual sound, speech, or EMF triggers detected."
            }
            _uiEvents.emit(UiEvent.ShowMessage(msg))
        }
    }

    private suspend fun runClipAnalysisInternal(
        clip: AudioClipEntity,
        timeline: List<EmfTimelineSample>
    ): Pair<List<FlaggedAudioSegmentEntity>, com.example.engine.AutoTagEvaluation> {
        _analyzingClipId.value = clip.id
        return try {
            val sessionAnomalies = clip.sessionId?.let {
                repository.getAnomaliesForSessionOnce(it)
            }.orEmpty()
            val segDir = storageManagerHelper.resolveSegmentsDirectory(_storageStatus.value)
            val segments = evpAnalyzer.analyzeClipAndExtractSegments(
                clip = clip,
                emfTimeline = timeline,
                sessionAnomalies = sessionAnomalies,
                thresholdUt = alertConfig.value.thresholdUt,
                segmentsOutputDir = segDir
            )
            repository.replaceSegmentsForClip(clip.id, segments)

            // Run phonetic waveform extraction to compute speech probability & repeated patterns
            val waveform = _waveformCache.value[clip.filePath]
                ?: EvpWaveformExtractor.extractFromWav(File(clip.filePath), clip.durationMs)?.also { extracted ->
                    _waveformCache.value = _waveformCache.value + (clip.filePath to extracted)
                }

            val timelineMaxDelta = timeline.maxOfOrNull { abs(it.deltaUt) } ?: 0f
            val eval = ClipAutoTagEngine.evaluateTags(
                peakEmfUt = clip.peakEmfDuringClip,
                peakEmfDeltaUt = maxOf(clip.peakEmfDeltaDuringClip, timelineMaxDelta),
                baselineEmfUt = currentEmfReading.value.baseline,
                emfThresholdUt = alertConfig.value.thresholdUt,
                tempDropC = clip.tempDropC,
                modeLabel = clip.modeLabel,
                flaggedSegments = segments,
                phoneticSegments = waveform?.phoneticSegments.orEmpty()
            )
            repository.updateClipAutoMetadata(
                clipId = clip.id,
                autoTags = eval.serializedTags,
                speechProbabilityPct = eval.speechProbabilityPct,
                analyzed = true
            )
            segments to eval
        } finally {
            _analyzingClipId.value = null
        }
    }

    fun tagRecordingMarker(label: String) {
        recorder.addMarker(label, currentEmfReading.value.delta)
        viewModelScope.launch {
            _uiEvents.emit(UiEvent.ShowMessage("Tagged \"$label\" at current timestamp."))
        }
    }

    fun loadWaveformForFile(filePath: String, fallbackDurationMs: Long = 0L) {
        if (_waveformCache.value.containsKey(filePath)) return
        viewModelScope.launch {
            val extracted = EvpWaveformExtractor.extractFromWav(File(filePath), fallbackDurationMs)
            if (extracted != null) {
                _waveformCache.value = _waveformCache.value + (filePath to extracted)
            }
        }
    }

    fun inspectAudioClip(clip: AudioClipEntity) {
        _inspectedSegmentId.value = null
        _inspectedClipId.value = clip.id
        loadWaveformForFile(clip.filePath, clip.durationMs)
    }

    fun inspectFlaggedSegment(segment: FlaggedAudioSegmentEntity) {
        _inspectedClipId.value = null
        _inspectedSegmentId.value = segment.id
        loadWaveformForFile(segment.segmentFilePath, (segment.endOffsetMs - segment.startOffsetMs).coerceAtLeast(1L))
    }

    fun closeWaveformInspector() {
        _inspectedClipId.value = null
        _inspectedSegmentId.value = null
        audioPlayer.setLoopWindow(null)
    }

    fun toggleClipPlayback(clip: AudioClipEntity) {
        loadWaveformForFile(clip.filePath, clip.durationMs)
        audioPlayer.playOrToggleClip(clip.id, clip.filePath)
    }

    fun toggleSegmentPlayback(segment: FlaggedAudioSegmentEntity) {
        loadWaveformForFile(segment.segmentFilePath, (segment.endOffsetMs - segment.startOffsetMs).coerceAtLeast(1L))
        audioPlayer.playOrToggleSegment(segment.id, segment.segmentFilePath)
    }

    fun seekAndPrepareClip(clip: AudioClipEntity, seekMs: Long, autoPlay: Boolean = false) {
        loadWaveformForFile(clip.filePath, clip.durationMs)
        audioPlayer.prepareAndSeek(clip.id, null, clip.filePath, seekMs, autoPlay)
    }

    fun seekAndPrepareSegment(segment: FlaggedAudioSegmentEntity, seekMs: Long, autoPlay: Boolean = false) {
        loadWaveformForFile(segment.segmentFilePath, (segment.endOffsetMs - segment.startOffsetMs).coerceAtLeast(1L))
        audioPlayer.prepareAndSeek(null, segment.id, segment.segmentFilePath, seekMs, autoPlay)
    }

    fun seekPlayback(posMs: Long) = audioPlayer.seekTo(posMs)

    fun setPlaybackLoopWindow(rangeMs: LongRange?) = audioPlayer.setLoopWindow(rangeMs)

    fun setPlaybackSpeed(speed: Float) = audioPlayer.setPlaybackSpeed(speed)

    fun deleteAudioClip(clip: AudioClipEntity) {
        viewModelScope.launch {
            if (playbackState.value.activeClipId == clip.id) {
                audioPlayer.stop()
            }
            repository.deleteAudioClip(clip)
            _uiEvents.emit(UiEvent.ShowMessage("Deleted ${clip.fileName}"))
        }
    }

    fun deleteFlaggedSegment(segment: FlaggedAudioSegmentEntity) {
        viewModelScope.launch {
            if (playbackState.value.activeSegmentId == segment.id) {
                audioPlayer.stop()
            }
            repository.deleteFlaggedSegment(segment)
            _uiEvents.emit(UiEvent.ShowMessage("Removed flagged segment ${segment.segmentFileName}"))
        }
    }

    // Location-Based Journal Actions
    fun addJournalEntry(
        locationLabel: String,
        noteText: String,
        tags: String,
        associatedAnomaly: EmfAnomalyEntity?,
        associatedClip: AudioClipEntity?
    ) {
        viewModelScope.launch {
            val reading = currentEmfReading.value
            val coord = locationStatus.value.currentCoordinate
            val session = activeSession.value
            val anomalySummary = associatedAnomaly?.let {
                "${it.severity}: ${"%.1f".format(it.magnitudeUt)} μT (Δ${"%.1f".format(it.deltaUt)} μT)"
            } ?: "Live: ${"%.1f".format(reading.magnitude)} μT (Δ${"%.1f".format(reading.delta)} μT)"

            val entry = JournalEntryEntity(
                sessionId = session?.id,
                timestampMs = System.currentTimeMillis(),
                latitude = coord?.latitude,
                longitude = coord?.longitude,
                gpsAccuracyMeters = coord?.accuracyMeters,
                locationLabel = locationLabel.ifBlank { session?.locationName ?: "Field Spot" },
                noteText = noteText.trim(),
                tags = tags,
                emfMagnitudeUt = associatedAnomaly?.magnitudeUt ?: reading.magnitude,
                emfDeltaUt = associatedAnomaly?.deltaUt ?: reading.delta,
                associatedAnomalyId = associatedAnomaly?.id,
                associatedAnomalySummary = anomalySummary,
                associatedClipId = associatedClip?.id,
                associatedClipName = associatedClip?.fileName ?: ""
            )
            repository.insertJournalEntry(entry)
            _uiEvents.emit(UiEvent.ShowMessage("Journal entry saved with GPS & sensor snapshot."))
        }
    }

    fun deleteJournalEntry(entryId: Long) {
        viewModelScope.launch {
            repository.deleteJournalEntry(entryId)
            _uiEvents.emit(UiEvent.ShowMessage("Journal entry deleted."))
        }
    }

    fun exportJournalCsv() {
        viewModelScope.launch {
            val csv = repository.buildJournalCsvReport()
            val name = "geoport_journal_${System.currentTimeMillis() / 1000}.csv"
            saveTextToActiveOrSaf(name, "text/csv", csv)
        }
    }

    fun exportJournalJson() {
        viewModelScope.launch {
            val json = repository.buildJournalJsonReport()
            val name = "geoport_journal_${System.currentTimeMillis() / 1000}.json"
            saveTextToActiveOrSaf(name, "application/json", json)
        }
    }

    fun shareJournalReport() {
        viewModelScope.launch {
            val csv = repository.buildJournalCsvReport()
            val file = repository.writeTempExportFile("geoport_journal_share.csv", csv)
            val ctx = getApplication<Application>()
            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_SUBJECT, "Geoport EMF Location Journal")
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            _uiEvents.emit(UiEvent.LaunchShareIntent(Intent.createChooser(intent, "Share Investigation Journal")))
        }
    }

    // Export & Share Flows for Sessions & Audio
    fun exportSessionCsv(session: InvestigationSessionEntity) {
        viewModelScope.launch {
            val csv = repository.buildSessionCsvReport(session.id)
            val safeTitle = session.title.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(24)
            saveTextToActiveOrSaf("geoport_${safeTitle}_${session.id}.csv", "text/csv", csv)
        }
    }

    fun exportSessionJson(session: InvestigationSessionEntity) {
        viewModelScope.launch {
            val json = repository.buildSessionJsonReport(session.id)
            val safeTitle = session.title.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(24)
            saveTextToActiveOrSaf("geoport_${safeTitle}_${session.id}.json", "application/json", json)
        }
    }

    private suspend fun saveTextToActiveOrSaf(fileName: String, mimeType: String, content: String) {
        val st = _storageStatus.value
        if (st.selectedMode == StorageTargetMode.CUSTOM_FOLDER && !st.customTreeUri.isNullOrBlank()) {
            val ok = storageManagerHelper.writeTextToSafFolder(
                Uri.parse(st.customTreeUri),
                fileName,
                mimeType,
                content
            )
            if (ok) {
                _uiEvents.emit(UiEvent.ShowMessage("Saved $fileName to \"${st.activeDestinationLabel}\"."))
                return
            }
        }
        _uiEvents.emit(
            UiEvent.RequestSafSaveText(
                suggestedName = fileName,
                mimeType = mimeType,
                content = content
            )
        )
    }

    fun shareSessionReport(session: InvestigationSessionEntity) {
        viewModelScope.launch {
            val csv = repository.buildSessionCsvReport(session.id)
            val safeTitle = session.title.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(24)
            val file = repository.writeTempExportFile("geoport_${safeTitle}_${session.id}.csv", csv)
            val ctx = getApplication<Application>()
            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_SUBJECT, "Geoport EMF Log: ${session.title}")
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            _uiEvents.emit(UiEvent.LaunchShareIntent(Intent.createChooser(intent, "Share Investigation Log")))
        }
    }

    fun requestSaveClipToDevice(clip: AudioClipEntity) {
        viewModelScope.launch {
            saveAudioToActiveOrSaf(clip.fileName, clip.filePath)
        }
    }

    fun requestSaveSegmentToDevice(segment: FlaggedAudioSegmentEntity) {
        viewModelScope.launch {
            saveAudioToActiveOrSaf(segment.segmentFileName, segment.segmentFilePath)
        }
    }

    private suspend fun saveAudioToActiveOrSaf(fileName: String, sourcePath: String) {
        val st = _storageStatus.value
        if (st.selectedMode == StorageTargetMode.CUSTOM_FOLDER && !st.customTreeUri.isNullOrBlank()) {
            val ok = storageManagerHelper.copyFileToSafFolder(
                Uri.parse(st.customTreeUri),
                File(sourcePath),
                "audio/wav"
            )
            if (ok) {
                _uiEvents.emit(UiEvent.ShowMessage("Saved $fileName to \"${st.activeDestinationLabel}\"."))
                return
            }
        }
        _uiEvents.emit(
            UiEvent.RequestSafSaveAudio(
                suggestedName = fileName,
                sourcePath = sourcePath
            )
        )
    }

    fun shareAudioClip(clip: AudioClipEntity) {
        shareWavFile(clip.filePath, clip.fileName)
    }

    fun shareFlaggedSegment(segment: FlaggedAudioSegmentEntity) {
        shareWavFile(segment.segmentFilePath, segment.segmentFileName)
    }

    private fun shareWavFile(filePath: String, fileName: String) {
        viewModelScope.launch {
            val file = File(filePath)
            if (!file.exists()) {
                _uiEvents.emit(UiEvent.ShowMessage("Audio file not found on device."))
                return@launch
            }
            val ctx = getApplication<Application>()
            val uri = runCatching {
                FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
            }.getOrElse {
                // If stored on secondary removable SD card, stage a temporary copy in cache/exports for FileProvider
                val staged = withContext(Dispatchers.IO) {
                    val exportDir = File(ctx.cacheDir, "exports").apply { mkdirs() }
                    val copyFile = File(exportDir, fileName)
                    file.inputStream().use { input -> copyFile.outputStream().use { output -> input.copyTo(output) } }
                    copyFile
                }
                FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", staged)
            }

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "audio/wav"
                putExtra(Intent.EXTRA_SUBJECT, "Geoport Audio Capture: $fileName")
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            _uiEvents.emit(UiEvent.LaunchShareIntent(Intent.createChooser(intent, "Share Audio Clip")))
        }
    }

    fun writeTextToSafUri(uri: Uri, content: String) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    getApplication<Application>().contentResolver.openOutputStream(uri)?.use { os ->
                        os.write(content.toByteArray(Charsets.UTF_8))
                    }
                    true
                }.getOrDefault(false)
            }
            _uiEvents.emit(
                UiEvent.ShowMessage(
                    if (ok) "Exported file to local device storage." else "Failed to write export file."
                )
            )
        }
    }

    fun copyAudioToSafUri(uri: Uri, sourcePath: String) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val src = File(sourcePath)
                    if (!src.exists()) return@runCatching false
                    getApplication<Application>().contentResolver.openOutputStream(uri)?.use { os ->
                        src.inputStream().use { input -> input.copyTo(os) }
                    }
                    true
                }.getOrDefault(false)
            }
            _uiEvents.emit(
                UiEvent.ShowMessage(
                    if (ok) "Audio clip exported to selected folder." else "Failed to export audio clip."
                )
            )
        }
    }

    // ---- SLS Camera Mode ----

    fun updateCameraPermission(granted: Boolean) {
        _hasCameraPermission.value = granted
    }

    fun logSlsSighting(
        jointCount: Int,
        avgConfidencePct: Int,
        visualModeLabel: String,
        isLowLight: Boolean
    ) {
        viewModelScope.launch {
            val emf = currentEmfReading.value
            val temp = temperatureReading.value
            val coord = locationStatus.value.currentCoordinate
            val session = activeSession.value
            val now = System.currentTimeMillis()
            val header = if (jointCount >= 4) {
                "SLS Figure Sighting ($jointCount joints • $avgConfidencePct% conf)"
            } else {
                "SLS Camera Snapshot ($visualModeLabel)"
            }
            val tempText = "${"%.1f".format(Locale.US, temp.temperatureC)}°C (Δ ${"%+.1f".format(Locale.US, temp.deltaC)}°C)"
            val noteBody = buildString {
                append(header)
                append(" | Optics: $visualModeLabel")
                if (isLowLight) append(" [Low-Light]")
                append(" | EMF: ${"%.1f".format(Locale.US, emf.magnitude)} μT (Δ ${"%+.1f".format(Locale.US, emf.delta)} μT)")
                append(" | Temp: $tempText")
            }

            repository.insertJournalEntry(
                JournalEntryEntity(
                    sessionId = session?.id,
                    timestampMs = now,
                    latitude = coord?.latitude,
                    longitude = coord?.longitude,
                    gpsAccuracyMeters = coord?.accuracyMeters,
                    locationLabel = session?.locationName?.ifBlank { "SLS Camera Sighting" } ?: "SLS Camera Sighting",
                    noteText = noteBody,
                    tags = if (jointCount >= 4) "SLS Camera, Figure Locked" else "SLS Camera, Snapshot",
                    emfMagnitudeUt = emf.magnitude,
                    emfDeltaUt = emf.delta,
                    associatedAnomalyId = null,
                    associatedAnomalySummary = header
                )
            )

            if (session != null && jointCount >= 4) {
                repository.logEmfAnomaly(
                    sessionId = session.id,
                    magnitudeUt = emf.magnitude,
                    deltaUt = emf.delta,
                    xUt = emf.x,
                    yUt = emf.y,
                    zUt = emf.z,
                    severity = "SLS_FIGURE",
                    note = "SLS locked $jointCount joints ($avgConfidencePct% conf, $visualModeLabel)",
                    isMotionArtifact = emf.isDeviceMoving,
                    coordinate = coord
                )
            }

            triggerHapticPulse()
            _uiEvents.emit(UiEvent.ShowMessage("SLS sighting logged to Investigation Journal."))
        }
    }

    // ---- Creator Program-Compliant App Updates ----

    fun openUpdateSettings() {
        _appUpdateState.value = _appUpdateState.value.copy(showUpdateSettingsDialog = true)
    }

    fun dismissUpdateSettings() {
        _appUpdateState.value = _appUpdateState.value.copy(showUpdateSettingsDialog = false)
    }

    fun dismissUpdatePopup() {
        _appUpdateState.value = _appUpdateState.value.copy(showUpdatePopup = false)
    }

    fun skipUpdateVersion(versionName: String) {
        viewModelScope.launch {
            getApplication<Application>().settingsDataStore.edit { prefs ->
                prefs[skippedVersionKey] = versionName
            }
            _appUpdateState.value = _appUpdateState.value.copy(
                skippedVersion = versionName,
                showUpdatePopup = false
            )
            _uiEvents.emit(UiEvent.ShowMessage("Skipped reminder for v$versionName."))
        }
    }

    fun setAutoCheckUpdates(enabled: Boolean) {
        viewModelScope.launch {
            getApplication<Application>().settingsDataStore.edit { prefs ->
                prefs[autoCheckUpdatesKey] = enabled
            }
            _appUpdateState.value = _appUpdateState.value.copy(autoCheckOnLaunch = enabled)
        }
    }

    fun checkForAppUpdates(sourceOverride: String? = null, isManual: Boolean = true) {
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            val source = (sourceOverride ?: _appUpdateState.value.updateSource).trim()
            if (sourceOverride != null) {
                ctx.settingsDataStore.edit { prefs ->
                    prefs[updateSourceKey] = source
                }
            }
            _appUpdateState.value = _appUpdateState.value.copy(
                updateSource = source,
                status = UpdateCheckStatus.CHECKING,
                statusMessage = "Checking for updates..."
            )

            val outcome = AppUpdateEngine.checkForUpdates(
                currentVersionName = BuildConfig.VERSION_NAME,
                updateSource = source,
                packageName = ctx.packageName
            )
            val now = System.currentTimeMillis()
            val skipped = _appUpdateState.value.skippedVersion

            when (outcome) {
                is UpdateCheckOutcome.UpdateAvailable -> {
                    val shouldPopup = isManual || outcome.release.versionName != skipped
                    _appUpdateState.value = _appUpdateState.value.copy(
                        status = UpdateCheckStatus.UPDATE_AVAILABLE,
                        availableRelease = outcome.release,
                        showUpdatePopup = shouldPopup,
                        showUpdateSettingsDialog = if (shouldPopup) false else _appUpdateState.value.showUpdateSettingsDialog,
                        statusMessage = "New version v${outcome.release.versionName} is available!",
                        lastCheckedMs = now
                    )
                }
                is UpdateCheckOutcome.UpToDate -> {
                    _appUpdateState.value = _appUpdateState.value.copy(
                        status = UpdateCheckStatus.UP_TO_DATE,
                        availableRelease = null,
                        showUpdatePopup = false,
                        statusMessage = "You are up to date (v${BuildConfig.VERSION_NAME} • Latest: v${outcome.latestVersion}).",
                        lastCheckedMs = now
                    )
                }
                is UpdateCheckOutcome.NeedsSourceConfig -> {
                    _appUpdateState.value = _appUpdateState.value.copy(
                        status = UpdateCheckStatus.NEEDS_SOURCE_CONFIG,
                        statusMessage = "Enter your GitHub repository (e.g. username/geoport-emf) above, or tap Open Releases / Play Store Page.",
                        lastCheckedMs = now
                    )
                }
                is UpdateCheckOutcome.Error -> {
                    _appUpdateState.value = _appUpdateState.value.copy(
                        status = UpdateCheckStatus.ERROR,
                        statusMessage = outcome.message,
                        lastCheckedMs = now
                    )
                }
            }
        }
    }

    fun openExternalUpdatePage(urlOrSource: String) {
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            val resolvedUrl = AppUpdateEngine.resolveExternalPageUrl(urlOrSource, ctx.packageName)
            val viewIntent = Intent(Intent.ACTION_VIEW, Uri.parse(resolvedUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            _appUpdateState.value = _appUpdateState.value.copy(
                showUpdatePopup = false,
                showUpdateSettingsDialog = false
            )
            _uiEvents.emit(UiEvent.LaunchShareIntent(viewIntent))
        }
    }

    private fun triggerHapticPulse() {
        val ctx = getApplication<Application>()
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator.vibrate(
                    VibrationEffect.createOneShot(45, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                val v = ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v.vibrate(VibrationEffect.createOneShot(45, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    v.vibrate(45)
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        emfEngine.stop()
        temperatureEngine.stop()
        batteryMonitor.stop()
        spiritBoxEngine.stop()
        ovilusEngine.shutdown()
        audioPlayer.stop()
        locationTracker.stopTracking()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val cb = volumeCallback as? StorageManager.StorageVolumeCallback
            if (cb != null) {
                val sm = getApplication<Application>().getSystemService(Context.STORAGE_SERVICE) as StorageManager
                runCatching { sm.unregisterStorageVolumeCallback(cb) }
            }
        }
    }
}
