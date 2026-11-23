package com.example.engine

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.example.data.model.TemperatureReading
import com.example.data.model.TemperatureSensorStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.ArrayDeque

/**
 * Tracks real temperature readings from Android hardware:
 * 1. Uses Sensor.TYPE_AMBIENT_TEMPERATURE directly when available on the device.
 * 2. Falls back to real device thermal telemetry (BatteryManager.EXTRA_TEMPERATURE)
 *    pushed from BatteryMonitorEngine when an ambient thermometer is absent.
 * Never fabricates or simulates temperature values.
 */
class TemperatureSensorEngine(
    context: Context? = null,
    private val sharpDropThresholdC: Float = -1.5f
) : SensorEventListener {

    private val sensorManager: SensorManager? =
        context?.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val ambientSensor: Sensor? =
        sensorManager?.getDefaultSensor(Sensor.TYPE_AMBIENT_TEMPERATURE)

    private val _status = MutableStateFlow(
        TemperatureSensorStatus(
            hasAmbientThermometer = ambientSensor != null,
            sourceLabel = if (ambientSensor != null) "Ambient Temp Sensor" else "Device Thermal Probe",
            isListening = false
        )
    )
    val status: StateFlow<TemperatureSensorStatus> = _status.asStateFlow()

    private val _currentReading = MutableStateFlow(
        TemperatureReading(
            sourceLabel = _status.value.sourceLabel
        )
    )
    val currentReading: StateFlow<TemperatureReading> = _currentReading.asStateFlow()

    private data class TimedTemp(val timestampMs: Long, val tempC: Float)

    private val recentWindow = ArrayDeque<TimedTemp>()
    private var baselineC: Float? = null
    private var isStarted = false

    fun start() {
        if (isStarted) return
        isStarted = true
        if (ambientSensor != null && sensorManager != null) {
            sensorManager.registerListener(this, ambientSensor, SensorManager.SENSOR_DELAY_NORMAL)
            _status.value = TemperatureSensorStatus(
                hasAmbientThermometer = true,
                sourceLabel = "Ambient Temp Sensor",
                isListening = true
            )
        } else {
            _status.value = TemperatureSensorStatus(
                hasAmbientThermometer = false,
                sourceLabel = "Device Thermal Probe",
                isListening = true
            )
        }
    }

    fun stop() {
        if (!isStarted) return
        isStarted = false
        if (ambientSensor != null) {
            sensorManager?.unregisterListener(this)
        }
        _status.value = _status.value.copy(isListening = false)
    }

    fun calibrateBaseline() {
        val current = _currentReading.value.temperatureC
        baselineC = current
        synchronized(recentWindow) {
            recentWindow.clear()
            recentWindow.addLast(TimedTemp(System.currentTimeMillis(), current))
        }
        _currentReading.value = _currentReading.value.copy(
            baselineC = current,
            deltaC = 0f,
            isSharpDrop = false
        )
    }

    /**
     * Ingests real device thermal probe telemetry from BatteryMonitorEngine
     * only when the device does not expose a dedicated TYPE_AMBIENT_TEMPERATURE sensor.
     */
    fun updateFallbackDeviceTemp(tempC: Float, timestampMs: Long = System.currentTimeMillis()) {
        if (_status.value.hasAmbientThermometer) return
        if (!tempC.isFinite() || tempC < -40f || tempC > 100f) return
        processTemperatureSample(tempC, timestampMs, "Device Thermal Probe")
    }

    /**
     * Processes a real temperature reading (also directly callable from unit tests).
     */
    fun processTemperatureSample(
        tempC: Float,
        timestampMs: Long = System.currentTimeMillis(),
        sourceLabel: String = _status.value.sourceLabel
    ): TemperatureReading {
        if (!tempC.isFinite()) return _currentReading.value

        val prevBaseline = baselineC
        val updatedBaseline = if (prevBaseline == null) {
            tempC
        } else {
            prevBaseline * 0.96f + tempC * 0.04f
        }
        baselineC = updatedBaseline

        var windowMaxTemp = updatedBaseline
        synchronized(recentWindow) {
            recentWindow.addLast(TimedTemp(timestampMs, tempC))
            val cutoff = timestampMs - 60_000L
            while (recentWindow.size > 1 && recentWindow.first.timestampMs < cutoff) {
                recentWindow.removeFirst()
            }
            for (sample in recentWindow) {
                if (sample.tempC > windowMaxTemp) {
                    windowMaxTemp = sample.tempC
                }
            }
        }

        val deltaFromBaseline = tempC - updatedBaseline
        val dropFromRecentPeak = tempC - windowMaxTemp
        val effectiveDelta = if (dropFromRecentPeak < deltaFromBaseline) {
            dropFromRecentPeak
        } else {
            deltaFromBaseline
        }

        val sharpDrop = effectiveDelta <= sharpDropThresholdC
        val reading = TemperatureReading(
            timestampMs = timestampMs,
            temperatureC = tempC,
            baselineC = updatedBaseline,
            deltaC = effectiveDelta,
            isSharpDrop = sharpDrop,
            sourceLabel = sourceLabel
        )
        _currentReading.value = reading
        return reading
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_AMBIENT_TEMPERATURE && event.values.isNotEmpty()) {
            val tempC = event.values[0]
            if (tempC.isFinite() && tempC > -60f && tempC < 120f) {
                processTemperatureSample(tempC, System.currentTimeMillis(), "Ambient Temp Sensor")
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
