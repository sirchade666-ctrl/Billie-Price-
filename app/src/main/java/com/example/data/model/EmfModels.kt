package com.example.data.model

enum class SpiritBoxMode(val label: String, val subtitle: String) {
    FRANKS_BOX("Frank's Box", "Rapid RF Sweep & Noise Burst"),
    GEOPORT("Geoport ITC", "Harmonic Chamber & Echo Reverb")
}

enum class SweepDirection(val label: String) {
    FORWARD("FWD"),
    REVERSE("REV"),
    RANDOM("RND")
}

enum class EmfAlertSound(val label: String, val description: String) {
    GEIGER_PING("Geiger Ping", "Sharp high-frequency detection click"),
    K2_CHIRP("K-II Chirp", "Ascending dual-tone EMF surge alert"),
    SONAR_PULSE("Sonar Pulse", "Deep tactical harmonic ping"),
    SIREN_PULSE("Critical Sweep", "Urgent modulated warning pulse"),
    SILENT("Visual Only", "Mute audible tone; keep visual & haptic alerts")
}

enum class PowerSourceFilterMode(val label: String, val subtitle: String) {
    OFF("Off", "Raw magnetometer reading including wiring & appliances"),
    STANDARD("Standard", "Rejects steady wall wiring, outlets & 50/60 Hz AC hum"),
    STRICT("Strict", "Aggressive power-source & appliance hum lock-out")
}

data class EmfAlertConfig(
    val thresholdUt: Float = 6.0f,
    val audibleAlertEnabled: Boolean = true,
    val selectedSound: EmfAlertSound = EmfAlertSound.K2_CHIRP,
    val hapticAlertEnabled: Boolean = true,
    val ignoreMotionAlerts: Boolean = true,
    val powerSourceFilterMode: PowerSourceFilterMode = PowerSourceFilterMode.STANDARD
)

data class EmfAlertBannerState(
    val isActive: Boolean = false,
    val peakDeltaUt: Float = 0f,
    val magnitudeUt: Float = 0f,
    val severity: String = "ELEVATED",
    val triggeredAtMs: Long = 0L
)

data class GeoCoordinate(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val altitudeMeters: Double? = null,
    val provider: String = "",
    val updatedAtMs: Long = System.currentTimeMillis()
)

data class LocationStatus(
    val hasPermission: Boolean = false,
    val isTracking: Boolean = false,
    val isProviderAvailable: Boolean = true,
    val currentCoordinate: GeoCoordinate? = null
)

data class EmfReading(
    val timestampMs: Long = System.currentTimeMillis(),
    val x: Float = 0f,
    val y: Float = 0f,
    val z: Float = 0f,
    val magnitude: Float = 0f,
    val baseline: Float = 45f,
    val delta: Float = 0f,
    val motionDelta: Float = 0f,
    val isDeviceMoving: Boolean = false,
    val rawDelta: Float = delta,
    val powerSourceOffsetUt: Float = 0f,
    val isPowerSourceFiltered: Boolean = false,
    val powerSourceLabel: String = ""
) {
    val milligauss: Float get() = magnitude * 10f
    val deltaMilligauss: Float get() = delta * 10f
}

data class EmfSensorStatus(
    val isHardwareAvailable: Boolean = true,
    val isListening: Boolean = false,
    val isCalibrating: Boolean = false,
    val sensorName: String = "Initializing...",
    val sensorVendor: String = "",
    val resolutionUt: Float = 0f
)

data class SpiritBoxState(
    val isRunning: Boolean = false,
    val mode: SpiritBoxMode = SpiritBoxMode.FRANKS_BOX,
    val sweepRateMs: Int = 140,
    val direction: SweepDirection = SweepDirection.FORWARD,
    val currentFrequencyMhz: Float = 87.5f,
    val echoDepth: Float = 0.45f,
    val squelchLevel: Float = 0.25f,
    val gain: Float = 0.72f,
    val emfCoupled: Boolean = true,
    val currentGateOpen: Boolean = true
)

enum class NoiseSuppressionLevel(
    val label: String,
    val subtitle: String,
    val floorGain: Float,
    val thresholdMultiplier: Float,
    val highCutHz: Float
) {
    OFF("Off", "Raw unfiltered microphone capture", 1.0f, 0f, 10500f),
    GENTLE("Gentle", "Whisper-safe light hiss reduction", 0.42f, 1.35f, 7400f),
    BALANCED("Balanced", "Attenuates room static & hum while keeping EVPs", 0.22f, 1.65f, 5800f),
    AGGRESSIVE("Deep", "Maximum background static & buzz suppression", 0.10f, 2.05f, 4600f)
}

data class RecorderState(
    val hasMicPermission: Boolean = false,
    val isRecording: Boolean = false,
    val isPaused: Boolean = false,
    val elapsedMs: Long = 0L,
    val currentDb: Float = -60f,
    val normalizedLevel: Float = 0f,
    val mixSpiritBox: Boolean = true,
    val noiseSuppressionLevel: NoiseSuppressionLevel = NoiseSuppressionLevel.BALANCED,
    val mainsHumNotchEnabled: Boolean = true,
    val noiseReductionDb: Float = 0f,
    val activeFileName: String? = null,
    val pendingMarkers: List<AudioMarker> = emptyList(),
    val concurrentFlagCount: Int = 0,
    val liveEvpCandidateActive: Boolean = false
)

data class AudioMarker(
    val offsetMs: Long,
    val label: String,
    val emfDeltaUt: Float
)

data class PlaybackState(
    val activeClipId: Long? = null,
    val activeSegmentId: Long? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val playbackSpeed: Float = 1.0f
)

enum class BatteryHealthLabel(val label: String) {
    GOOD("Good"),
    OVERHEAT("Overheating"),
    COLD("Too Cold"),
    DEAD("Degraded / Dead"),
    OVER_VOLTAGE("Over Voltage"),
    UNKNOWN("Unknown")
}

data class PowerSample(
    val timestampMs: Long,
    val levelPct: Int,
    val powerWatts: Float,
    val currentMa: Float
)

data class BatteryTelemetryState(
    val levelPct: Int = 100,
    val isCharging: Boolean = false,
    val chargeSource: String = "Battery",
    val health: BatteryHealthLabel = BatteryHealthLabel.GOOD,
    val temperatureC: Float = 25.0f,
    val voltageV: Float = 3.85f,
    val currentMa: Float = 0f,
    val powerWatts: Float = 0f,
    val avgPowerWatts: Float = 0f,
    val peakPowerWatts: Float = 0f,
    val remainingCapacityMah: Float? = null,
    val sessionStartPct: Int? = null,
    val sessionDrainPct: Int = 0,
    val drainRatePctPerHour: Float = 0f,
    val estimatedMinutesRemaining: Int? = null,
    val warningThresholdPct: Int = 15,
    val autoProtectRecording: Boolean = true,
    val isLowPowerAlertActive: Boolean = false,
    val isThermalAlertActive: Boolean = false,
    val powerHistory: List<PowerSample> = emptyList()
)

enum class OvilusMode(val label: String, val subtitle: String) {
    DICTIONARY("Dictionary 80K", "Maps EMF vector flux directly to the 80,000-word bank"),
    PHONETIC("Phonetic + Word", "Speaks syllabic formant cue followed by 80K lexicon match")
}

enum class OvilusVoiceProfile(
    val label: String,
    val description: String,
    val pitch: Float,
    val speechRate: Float
) {
    CLEAR_NATURAL("Clear Natural", "Balanced crystal-clear articulation", 1.0f, 0.94f),
    DEEP_BARITONE("Deep Voice", "Low resonant baritone clarity", 0.78f, 0.88f),
    CRISP_HIGH("Crisp Bright", "Elevated bright vocal presence", 1.15f, 0.96f),
    SLOW_DELIBERATE("Slow Clear", "Measured unhurried enunciation", 0.96f, 0.76f)
}

data class OvilusVoiceOption(
    val id: String,
    val displayName: String,
    val localeLabel: String,
    val qualityLabel: String
)

data class OvilusLogEntry(
    val id: Long,
    val timestampMs: Long,
    val text: String,
    val bankIndex: Int?,
    val phoneticCue: String,
    val emfMagnitudeUt: Float,
    val emfDeltaUt: Float,
    val isQuestion: Boolean = false
)

data class OvilusState(
    val isActive: Boolean = false,
    val isTtsReady: Boolean = false,
    val isSpeaking: Boolean = false,
    val mode: OvilusMode = OvilusMode.DICTIONARY,
    val voiceProfile: OvilusVoiceProfile = OvilusVoiceProfile.CLEAR_NATURAL,
    val availableVoices: List<OvilusVoiceOption> = emptyList(),
    val selectedVoiceId: String? = null,
    val pitch: Float = 1.0f,
    val speechRate: Float = 0.94f,
    val sensitivityUt: Float = 4.5f,
    val cooldownSeconds: Int = 5,
    val autoLogToJournal: Boolean = true,
    val wordBankCount: Int = 80_000,
    val lastWord: String = "Awaiting EMF Flux",
    val lastPhonetic: String = "",
    val lastBankIndex: Int? = null,
    val logEntries: List<OvilusLogEntry> = emptyList()
)

data class TemperatureReading(
    val timestampMs: Long = System.currentTimeMillis(),
    val temperatureC: Float = 22.0f,
    val baselineC: Float = 22.0f,
    val deltaC: Float = 0f,
    val isSharpDrop: Boolean = false,
    val sourceLabel: String = "Device Thermal Probe"
)

data class TemperatureSensorStatus(
    val hasAmbientThermometer: Boolean = false,
    val sourceLabel: String = "Device Thermal Probe",
    val isListening: Boolean = false
)

enum class TrendTimeScale(val label: String, val windowMs: Long?) {
    ONE_MIN("1M", 60_000L),
    FIVE_MIN("5M", 5 * 60_000L),
    FIFTEEN_MIN("15M", 15 * 60_000L),
    ONE_HOUR("1H", 60 * 60_000L),
    ALL("All", null)
}

enum class AudioMetadataTag(val label: String) {
    HIGH_EMF("High EMF"),
    SHARP_TEMP_DROP("Sharp Temperature Drop"),
    HIGH_SPEECH_PROB("High Speech Probability"),
    REPEATED_PATTERN("Repeated Pattern"),
    EVP_EMF_COINCIDENCE("EVP+EMF Co-Incidence"),
    SPIRIT_BOX_MIX("Spirit Box Mix");

    companion object {
        fun fromLabel(label: String): AudioMetadataTag? =
            entries.firstOrNull { it.label.equals(label.trim(), ignoreCase = true) }
    }
}
