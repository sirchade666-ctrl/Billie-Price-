package com.example.engine

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.example.data.model.EmfReading
import com.example.data.model.EmfSensorStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.sqrt

class EmfSensorEngine(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val magnetometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    private val accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

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

    private var baselineUt = 45f
    private val calibrationSamples = mutableListOf<Float>()
    private var isCalibrating = true
    private var lastMotionDelta = 0f
    private var lastHistoryEmitMs = 0L

    fun start() {
        if (magnetometer == null) {
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
        sensorManager.unregisterListener(this)
        _sensorStatus.value = _sensorStatus.value.copy(isListening = false)
    }

    fun calibrateBaseline() {
        synchronized(calibrationSamples) {
            calibrationSamples.clear()
            isCalibrating = true
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
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]
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

                val delta = magnitude - baselineUt
                val now = System.currentTimeMillis()
                val reading = EmfReading(
                    timestampMs = now,
                    x = x,
                    y = y,
                    z = z,
                    magnitude = magnitude,
                    baseline = baselineUt,
                    delta = delta,
                    motionDelta = lastMotionDelta,
                    isDeviceMoving = lastMotionDelta > 1.35f
                )
                _currentReading.value = reading

                if (now - lastHistoryEmitMs >= 50L) {
                    lastHistoryEmitMs = now
                    synchronized(historyBuffer) {
                        if (historyBuffer.size >= maxHistoryPoints) {
                            historyBuffer.removeFirst()
                        }
                        historyBuffer.addLast(reading)
                        _history.value = historyBuffer.toList()
                    }
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No-op; sensor accuracy changes handled natively
    }
}
