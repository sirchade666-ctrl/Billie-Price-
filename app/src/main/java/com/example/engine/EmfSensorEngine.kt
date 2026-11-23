package com.example.engine

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.example.data.model.EmfReading
import com.example.data.model.EmfSensorStatus
import com.example.data.model.PowerSourceFilterMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Hardware 3-axis magnetometer & motion sensor engine with built-in
 * Power-Source EMF Rejection to filter out normal household/building electricity:
 *  - Rejects steady/constant magnetic plateaus from wall wiring, outlets, breaker boxes, and appliances.
 *  - Suppresses periodic alternating 50/60 Hz AC mains ripple (aliased hum).
 *  - Preserves sudden, non-periodic transient EMF spikes for alerts, logging, Spirit Box, and Ovilus.
 */
class EmfSensorEngine(context: Context? = null) : SensorEventListener {

    private val sensorManager: SensorManager? =
        context?.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val magnetometer: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    private val accelerometer: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val _sensorStatus = MutableStateFlow(
        EmfSensorStatus(
            isHardwareAvailable = magnetometer != null,
            isListening = false,
            isCalibrating = magnetometer != null,
            sensorName = magnetometer?.name ?: "No Magnetometer",
            sensorVendor = magnetometer?.vendor ?: "",
            resolutionUt = magnetometer?.resolution ?: 0f
        )
    )
    val sensorStatus: StateFlow<EmfSensorStatus> = _sensorStatus.asStateFlow()

    private val _currentReading = MutableStateFlow(EmfReading())
    val currentReading: StateFlow<EmfReading> = _currentReading.asStateFlow()

    private val maxHistoryPoints = 160
    private val historyBuffer = ArrayDeque<EmfReading>(maxHistoryPoints)
    private val _history = MutableStateFlow<List<EmfReading>>(emptyList())
    val history: StateFlow<List<EmfReading>> = _history.asStateFlow()

    private data class TimedMag(val timestampMs: Long, val magnitudeUt: Float)

    private val filterLock = Any()
    private val recentWindow = ArrayDeque<TimedMag>(64)
    private val smoothWindow = ArrayDeque<Float>(8)

    private var baselineUt = 45f
    private val calibrationSamples = mutableListOf<Float>()
    private var isCalibrating = true
    private var lastMotionDelta = 0f
    private var lastHistoryEmitMs = 0L

    @Volatile
    private var filterMode: PowerSourceFilterMode = PowerSourceFilterMode.STANDARD

    private var powerSourceOffsetUt = 0f
    private var steadyHoldStartMs: Long? = null

    fun setPowerSourceFilterMode(mode: PowerSourceFilterMode) {
        synchronized(filterLock) {
            filterMode = mode
            if (mode == PowerSourceFilterMode.OFF) {
                powerSourceOffsetUt = 0f
                steadyHoldStartMs = null
            }
        }
    }

    fun start() {
        if (magnetometer == null || sensorManager == null) {
            _sensorStatus.value = _sensorStatus.value.copy(
                isHardwareAvailable = false,
                isListening = false,
                isCalibrating = false
            )
            return
        }
        sensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_UI)
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
        _sensorStatus.value = _sensorStatus.value.copy(isListening = true)
    }

    fun stop() {
        sensorManager?.unregisterListener(this)
        _sensorStatus.value = _sensorStatus.value.copy(isListening = false)
    }

    fun calibrateBaseline() {
        synchronized(calibrationSamples) {
            calibrationSamples.clear()
            isCalibrating = true
        }
        synchronized(filterLock) {
            recentWindow.clear()
            smoothWindow.clear()
            powerSourceOffsetUt = 0f
            steadyHoldStartMs = null
        }
        _sensorStatus.value = _sensorStatus.value.copy(isCalibrating = true)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                val ax = event.values[0]
                val ay = event.values[1]
                val az = event.values[2]
                val accelMag = sqrt(ax * ax + ay * ay + az * az)
                lastMotionDelta = abs(accelMag - SensorManager.GRAVITY_EARTH)
            }

            Sensor.TYPE_MAGNETIC_FIELD -> {
                processMagneticSample(
                    x = event.values[0],
                    y = event.values[1],
                    z = event.values[2],
                    timestampMs = System.currentTimeMillis()
                )
            }
        }
    }

    /**
     * Processes a real 3-axis magnetometer sample through calibration and
     * power-source EMF rejection. Public for deterministic unit verification.
     */
    fun processMagneticSample(
        x: Float,
        y: Float,
        z: Float,
        timestampMs: Long = System.currentTimeMillis()
    ): EmfReading {
        val magnitude = sqrt(x * x + y * y + z * z)

        synchronized(calibrationSamples) {
            if (isCalibrating) {
                calibrationSamples.add(magnitude)
                if (calibrationSamples.size >= 15) {
                    baselineUt = calibrationSamples.average().toFloat()
                    isCalibrating = false
                    _sensorStatus.value = _sensorStatus.value.copy(isCalibrating = false)
                } else {
                    baselineUt = calibrationSamples.average().toFloat()
                }
            }
        }

        val rawDelta = magnitude - baselineUt
        val activeMode = filterMode

        var filteredDelta = rawDelta
        var activeOffset = 0f
        var isFiltered = false
        var sourceLabel = ""

        if (activeMode != PowerSourceFilterMode.OFF) {
            synchronized(filterLock) {
                val smoothSize = if (activeMode == PowerSourceFilterMode.STRICT) 5 else 3
                while (smoothWindow.size >= smoothSize) {
                    smoothWindow.removeFirst()
                }
                smoothWindow.addLast(magnitude)
                val smoothedMag = smoothWindow.average().toFloat()

                // Keep a 2.5-second rolling history for steady wiring & AC ripple analysis
                recentWindow.addLast(TimedMag(timestampMs, magnitude))
                val windowHorizonMs = 2_500L
                while (recentWindow.size > 6 && timestampMs - recentWindow.first().timestampMs > windowHorizonMs) {
                    recentWindow.removeFirst()
                }

                // 1. Detect periodic alternating AC mains hum / transformer ripple
                var isAcRipple = false
                var acLocalMean = smoothedMag
                if (recentWindow.size >= 8) {
                    val sampleList = recentWindow.takeLast(14)
                    val localMean = sampleList.map { it.magnitudeUt }.average().toFloat()
                    acLocalMean = localMean
                    var signFlips = 0
                    var prevSign = 0
                    var sumAbsDev = 0f
                    var maxDev = 0f
                    for (pt in sampleList) {
                        val dev = pt.magnitudeUt - localMean
                        val absDev = abs(dev)
                        sumAbsDev += absDev
                        if (absDev > maxDev) maxDev = absDev
                        val sign = when {
                            dev > 0.35f -> 1
                            dev < -0.35f -> -1
                            else -> 0
                        }
                        if (sign != 0) {
                            if (prevSign != 0 && sign != prevSign) {
                                signFlips++
                            }
                            prevSign = sign
                        }
                    }
                    val avgDev = sumAbsDev / sampleList.size
                    val flipRatio = signFlips.toFloat() / (sampleList.size - 1).coerceAtLeast(1)
                    val requiredFlipRatio = if (activeMode == PowerSourceFilterMode.STRICT) 0.48f else 0.55f
                    if (flipRatio >= requiredFlipRatio && avgDev >= 1.2f && maxDev <= avgDev * 2.4f) {
                        isAcRipple = true
                    }
                }

                // 2. Detect steady constant wiring / power-strip / appliance plateau on recent tail
                val stdLimitUt = if (activeMode == PowerSourceFilterMode.STRICT) 3.4f else 2.2f
                val holdRequiredMs = if (activeMode == PowerSourceFilterMode.STRICT) 500L else 1_000L

                val tailSamples = recentWindow.takeLast(8)
                val tailMean = tailSamples.map { it.magnitudeUt }.average().toFloat()
                val tailVariance = tailSamples.map {
                    val d = it.magnitudeUt - tailMean
                    d * d
                }.average().toFloat()
                val tailStdDev = sqrt(tailVariance)
                val effectiveOffsetFromBase = tailMean - baselineUt
                val smoothedDeltaFromBase = smoothedMag - baselineUt

                // Rebound-free release when moving away from a power source back toward Earth baseline
                if (abs(powerSourceOffsetUt) > 0.25f) {
                    val returningTowardBaseline =
                        (powerSourceOffsetUt > 0f && smoothedDeltaFromBase < powerSourceOffsetUt) ||
                            (powerSourceOffsetUt < 0f && smoothedDeltaFromBase > powerSourceOffsetUt)
                    if (returningTowardBaseline) {
                        powerSourceOffsetUt = if (
                            (powerSourceOffsetUt > 0f && smoothedDeltaFromBase <= 0f) ||
                            (powerSourceOffsetUt < 0f && smoothedDeltaFromBase >= 0f)
                        ) {
                            0f
                        } else {
                            smoothedDeltaFromBase
                        }
                        if (abs(powerSourceOffsetUt) < 0.25f) {
                            powerSourceOffsetUt = 0f
                            steadyHoldStartMs = null
                        }
                    }
                }

                if (isAcRipple) {
                    // Track the DC mean of the AC hum source and cancel out its oscillation
                    val targetOffset = acLocalMean - baselineUt
                    powerSourceOffsetUt = powerSourceOffsetUt * 0.50f + targetOffset * 0.50f
                    filteredDelta = (smoothedMag - (baselineUt + powerSourceOffsetUt)) * 0.25f
                    isFiltered = true
                    sourceLabel = "AC Mains Ripple"
                } else if (tailSamples.size >= 6 && tailStdDev <= stdLimitUt && abs(effectiveOffsetFromBase) >= 1.5f) {
                    // Field is elevated but steady (typical of wall wiring, outlets, chargers, appliances)
                    val started = steadyHoldStartMs ?: timestampMs.also { steadyHoldStartMs = it }
                    val heldMs = timestampMs - started
                    if (heldMs >= holdRequiredMs) {
                        val adaptRate = if (activeMode == PowerSourceFilterMode.STRICT) 0.42f else 0.30f
                        powerSourceOffsetUt += (effectiveOffsetFromBase - powerSourceOffsetUt) * adaptRate
                        isFiltered = true
                        sourceLabel = "Wiring / Appliance"
                    }
                    filteredDelta = smoothedMag - (baselineUt + powerSourceOffsetUt)
                } else {
                    // Sudden non-steady fluctuation or back near Earth baseline
                    if (abs(rawDelta) < 1.5f) {
                        steadyHoldStartMs = null
                    } else if (tailStdDev > stdLimitUt * 1.4f) {
                        // Genuine abrupt fluctuation — do not adapt it away!
                        steadyHoldStartMs = null
                    }
                    filteredDelta = smoothedMag - (baselineUt + powerSourceOffsetUt)
                    if (abs(powerSourceOffsetUt) >= 1.5f) {
                        isFiltered = true
                        sourceLabel = "Wiring / Appliance"
                    }
                }

                // 3. Mild micro-hum deadband in Strict mode for electrically noisy rooms
                val deadbandUt = if (activeMode == PowerSourceFilterMode.STRICT) 1.6f else 0.6f
                if (abs(filteredDelta) < deadbandUt) {
                    filteredDelta = 0f
                }

                activeOffset = powerSourceOffsetUt
            }
        }

        val reading = EmfReading(
            timestampMs = timestampMs,
            x = x,
            y = y,
            z = z,
            magnitude = magnitude,
            baseline = baselineUt,
            delta = filteredDelta,
            motionDelta = lastMotionDelta,
            isDeviceMoving = lastMotionDelta > 1.35f,
            rawDelta = rawDelta,
            powerSourceOffsetUt = activeOffset,
            isPowerSourceFiltered = isFiltered,
            powerSourceLabel = sourceLabel
        )
        _currentReading.value = reading

        if (timestampMs - lastHistoryEmitMs >= 50L) {
            lastHistoryEmitMs = timestampMs
            synchronized(historyBuffer) {
                if (historyBuffer.size >= maxHistoryPoints) {
                    historyBuffer.removeFirst()
                }
                historyBuffer.addLast(reading)
                _history.value = historyBuffer.toList()
            }
        }
        return reading
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No-op; sensor accuracy changes handled natively
    }
}
