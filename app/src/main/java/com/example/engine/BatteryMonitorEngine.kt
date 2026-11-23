package com.example.engine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.example.data.model.BatteryHealthLabel
import com.example.data.model.BatteryTelemetryState
import com.example.data.model.PowerSample
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

class BatteryMonitorEngine(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager

    private val _state = MutableStateFlow(BatteryTelemetryState())
    val state: StateFlow<BatteryTelemetryState> = _state.asStateFlow()

    private val historyBuffer = ArrayDeque<PowerSample>(60)
    private var pollJob: Job? = null
    private var receiverRegistered = false

    private var sessionStartTimeMs: Long? = null
    private var sessionStartLevelPct: Int? = null
    private var powerSumWatts = 0f
    private var powerSampleCount = 0

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (intent != null) {
                parseBatteryIntent(intent)
            }
        }
    }

    fun start() {
        if (!receiverRegistered) {
            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val sticky = context.registerReceiver(batteryReceiver, filter)
            receiverRegistered = true
            if (sticky != null) {
                parseBatteryIntent(sticky)
            }
        }

        pollJob?.cancel()
        pollJob = scope.launch(Dispatchers.Default) {
            while (isActive) {
                sampleCurrentDraw()
                delay(2500L)
            }
        }
    }

    fun stop() {
        pollJob?.cancel()
        pollJob = null
        if (receiverRegistered) {
            runCatching { context.unregisterReceiver(batteryReceiver) }
            receiverRegistered = false
        }
    }

    fun setWarningThresholdPct(pct: Int) {
        val clamped = pct.coerceIn(5, 35)
        _state.value = _state.value.copy(warningThresholdPct = clamped)
        recomputeAlerts()
    }

    fun setAutoProtectRecording(enabled: Boolean) {
        _state.value = _state.value.copy(autoProtectRecording = enabled)
    }

    fun onInvestigationSessionStarted() {
        sessionStartTimeMs = System.currentTimeMillis()
        sessionStartLevelPct = _state.value.levelPct
        powerSumWatts = 0f
        powerSampleCount = 0
        _state.value = _state.value.copy(
            sessionStartPct = _state.value.levelPct,
            sessionDrainPct = 0,
            peakPowerWatts = _state.value.powerWatts,
            avgPowerWatts = _state.value.powerWatts,
            drainRatePctPerHour = 0f
        )
    }

    fun onInvestigationSessionEnded() {
        sessionStartTimeMs = null
        sessionStartLevelPct = null
        _state.value = _state.value.copy(sessionStartPct = null)
    }

    private fun parseBatteryIntent(intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
        val pct = if (level >= 0) ((level * 100f) / scale).toInt().coerceIn(0, 100) else _state.value.levelPct

        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL

        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
        val chargeSource = when {
            plugged and BatteryManager.BATTERY_PLUGGED_AC != 0 -> "AC Power"
            plugged and BatteryManager.BATTERY_PLUGGED_USB != 0 -> "USB"
            plugged and BatteryManager.BATTERY_PLUGGED_WIRELESS != 0 -> "Wireless"
            isCharging -> "Charging"
            else -> "Discharging"
        }

        val healthInt = intent.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN)
        val health = when (healthInt) {
            BatteryManager.BATTERY_HEALTH_GOOD -> BatteryHealthLabel.GOOD
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> BatteryHealthLabel.OVERHEAT
            BatteryManager.BATTERY_HEALTH_COLD -> BatteryHealthLabel.COLD
            BatteryManager.BATTERY_HEALTH_DEAD -> BatteryHealthLabel.DEAD
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> BatteryHealthLabel.OVER_VOLTAGE
            else -> BatteryHealthLabel.UNKNOWN
        }

        val tempTenths = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 250)
        val tempC = tempTenths / 10f

        val voltageMv = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 3850)
        val voltageV = if (voltageMv > 100) voltageMv / 1000f else 3.85f

        _state.value = _state.value.copy(
            levelPct = pct,
            isCharging = isCharging,
            chargeSource = chargeSource,
            health = health,
            temperatureC = tempC,
            voltageV = voltageV
        )
        sampleCurrentDraw()
    }

    private fun sampleCurrentDraw() {
        val curRaw = runCatching {
            batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        }.getOrDefault(0)

        val avgRaw = runCatching {
            batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE)
        }.getOrDefault(0)

        val chosenRaw = if (curRaw != Int.MIN_VALUE && curRaw != 0) curRaw else if (avgRaw != Int.MIN_VALUE) avgRaw else 0

        // Android reports current in microamperes on standard HALs, or occasionally milliamperes on older/virtual HALs
        val absRaw = abs(chosenRaw).toFloat()
        val currentMa = when {
            absRaw == 0f -> 0f
            absRaw > 10000f -> absRaw / 1000f // microamperes -> milliamperes
            else -> absRaw
        }

        val chargeCounterRaw = runCatching {
            batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
        }.getOrDefault(Int.MIN_VALUE)
        val remainingMah = if (chargeCounterRaw != Int.MIN_VALUE && chargeCounterRaw > 0) {
            chargeCounterRaw / 1000f
        } else {
            null
        }

        val curState = _state.value
        val watts = (curState.voltageV * (currentMa / 1000f)).coerceAtLeast(0f)

        powerSumWatts += watts
        powerSampleCount++
        val avgWatts = if (powerSampleCount > 0) powerSumWatts / powerSampleCount else watts
        val peakWatts = maxOf(curState.peakPowerWatts, watts)

        val now = System.currentTimeMillis()
        synchronized(historyBuffer) {
            if (historyBuffer.size >= 60) historyBuffer.removeFirst()
            historyBuffer.addLast(
                PowerSample(
                    timestampMs = now,
                    levelPct = curState.levelPct,
                    powerWatts = watts,
                    currentMa = currentMa
                )
            )
        }

        val startPct = sessionStartLevelPct
        val startTime = sessionStartTimeMs
        val drainPct = if (startPct != null) (startPct - curState.levelPct).coerceAtLeast(0) else 0
        val elapsedHours = if (startTime != null) ((now - startTime) / 3_600_000f).coerceAtLeast(0.01f) else 0f
        val drainRate = if (startTime != null && drainPct > 0) drainPct / elapsedHours else 0f

        val estMinutes = when {
            curState.isCharging -> null
            drainRate > 0.5f -> ((curState.levelPct / drainRate) * 60f).toInt().coerceIn(1, 2400)
            remainingMah != null && currentMa > 25f -> ((remainingMah / currentMa) * 60f).toInt().coerceIn(1, 2400)
            else -> null
        }

        _state.value = curState.copy(
            currentMa = currentMa,
            powerWatts = watts,
            avgPowerWatts = avgWatts,
            peakPowerWatts = peakWatts,
            remainingCapacityMah = remainingMah,
            sessionDrainPct = drainPct,
            drainRatePctPerHour = drainRate,
            estimatedMinutesRemaining = estMinutes,
            powerHistory = synchronized(historyBuffer) { historyBuffer.toList() }
        )
        recomputeAlerts()
    }

    private fun recomputeAlerts() {
        val s = _state.value
        val lowAlert = !s.isCharging && s.levelPct <= s.warningThresholdPct
        val thermalAlert = s.health == BatteryHealthLabel.OVERHEAT || s.temperatureC >= 44.0f
        _state.value = s.copy(
            isLowPowerAlertActive = lowAlert,
            isThermalAlertActive = thermalAlert
        )
    }
}
