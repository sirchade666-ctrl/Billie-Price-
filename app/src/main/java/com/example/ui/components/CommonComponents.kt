package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.InvestigationSessionEntity
import com.example.data.model.EmfAlertBannerState
import com.example.ui.theme.AnomalyCrimson
import com.example.ui.theme.AnomalyCrimsonDim
import com.example.ui.theme.ElevatedCard
import com.example.ui.theme.FranksAmber
import com.example.ui.theme.GeoportTeal
import com.example.ui.theme.LocalNightVisionEnabled
import com.example.ui.theme.PanelSurface
import com.example.ui.theme.PhosphorCyan
import com.example.ui.theme.SubtleBorder
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.nightVisionOverlay
import java.util.Locale

@Composable
fun InstrumentCard(
    modifier: Modifier = Modifier,
    borderColor: Color = SubtleBorder,
    containerColor: Color = PanelSurface,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            content = content
        )
    }
}

@Composable
fun StatusLed(
    active: Boolean,
    activeColor: Color,
    pulse: Boolean = false,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "led_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(650),
            repeatMode = RepeatMode.Reverse
        ),
        label = "led_alpha"
    )
    val color by animateColorAsState(
        targetValue = if (active) activeColor else TextMuted.copy(alpha = 0.4f),
        label = "led_color"
    )
    Box(
        modifier = modifier
            .size(10.dp)
            .alpha(if (active && pulse) pulseAlpha else 1f)
            .clip(CircleShape)
            .background(color)
    )
}

@Composable
fun EmfVisualAlertBanner(
    bannerState: EmfAlertBannerState,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "alert_flash")
    val flashAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.55f,
        animationSpec = infiniteRepeatable(
            animation = tween(360),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alert_alpha"
    )
    val alertColor = if (bannerState.severity == "CRITICAL") AnomalyCrimson else FranksAmber

    AnimatedVisibility(
        visible = bannerState.isActive,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .alpha(flashAlpha)
                .clip(RoundedCornerShape(12.dp))
                .background(AnomalyCrimsonDim.copy(alpha = 0.92f))
                .border(1.5.dp, alertColor, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .testTag("emf_visual_alert_banner"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.NotificationsActive,
                    contentDescription = null,
                    tint = alertColor,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "${bannerState.severity} EMF ALERT",
                        style = MaterialTheme.typography.labelLarge,
                        color = alertColor,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${"%.1f".format(Locale.US, bannerState.magnitudeUt)} μT (Δ ${"%.1f".format(Locale.US, bannerState.peakDeltaUt)} μT)",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White
                    )
                }
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Dismiss alert",
                    tint = Color.White
                )
            }
        }
    }
}

@Composable
fun ActiveSessionBanner(
    activeSession: InvestigationSessionEntity?,
    onStartSessionClick: () -> Unit,
    onStopSessionClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    InstrumentCard(
        modifier = modifier,
        borderColor = if (activeSession != null) GeoportTeal.copy(alpha = 0.6f) else SubtleBorder,
        containerColor = ElevatedCard
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusLed(
                    active = activeSession != null,
                    activeColor = GeoportTeal,
                    pulse = true
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = if (activeSession != null) "ACTIVE LOGGING SESSION" else "STANDBY MONITOR",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (activeSession != null) GeoportTeal else TextSecondary
                    )
                    Text(
                        text = activeSession?.let { "${it.title} • ${it.locationName}" }
                            ?: "Start a session to record EMF spikes, GPS & clips",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            if (activeSession != null) {
                FilledTonalButton(
                    onClick = onStopSessionClick,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = AnomalyCrimson.copy(alpha = 0.2f),
                        contentColor = AnomalyCrimson
                    ),
                    modifier = Modifier.testTag("stop_session_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Stop,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("End", style = MaterialTheme.typography.labelLarge)
                }
            } else {
                Button(
                    onClick = onStartSessionClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PhosphorCyan,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier.testTag("start_session_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Log", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun NewSessionDialog(
    onDismiss: () -> Unit,
    onConfirm: (title: String, location: String, notes: String) -> Unit
) {
    var title by remember { mutableStateOf("Night Investigation") }
    var location by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    val nightVision = LocalNightVisionEnabled.current

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                text = "Start Investigation Log",
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.nightVisionOverlay(nightVision)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Session Title") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("session_title_input")
                )
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text("Site / Room Location") },
                    placeholder = { Text("e.g., Basement Boiler Room") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("session_location_input")
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Pre-Investigation Notes") },
                    maxLines = 3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("session_notes_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(title.trim(), location.trim(), notes.trim()) },
                modifier = Modifier.testTag("confirm_start_session_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.FiberManualRecord,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Start Logging")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

fun formatDurationMs(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val mins = totalSec / 60L
    val secs = totalSec % 60L
    val tenths = (ms % 1000L) / 100L
    return "%02d:%02d.%d".format(mins, secs, tenths)
}

@Composable
fun StorageTransferCard(
    storageStatus: com.example.engine.StorageStatus,
    isTransferring: Boolean,
    onRefreshStorage: () -> Unit,
    onSelectMode: (com.example.engine.StorageTargetMode) -> Unit,
    onPickCustomFolder: () -> Unit,
    onTransferAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sdCard = storageStatus.removableSdCard
    InstrumentCard(
        modifier = modifier,
        borderColor = if (sdCard != null) GeoportTeal.copy(alpha = 0.55f) else SubtleBorder
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusLed(
                        active = sdCard != null,
                        activeColor = GeoportTeal
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (sdCard != null) {
                            "SD CARD DETECTED"
                        } else {
                            "INTERNAL STORAGE MODE"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = if (sdCard != null) GeoportTeal else TextSecondary
                    )
                }
                Text(
                    text = if (sdCard != null) {
                        "${sdCard.label} • ${com.example.engine.formatBytesHuman(sdCard.freeBytes)} free"
                    } else {
                        "No removable SD card detected (${com.example.engine.formatBytesHuman(storageStatus.internalStorage.freeBytes)} internal free)"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            TextButton(
                onClick = onRefreshStorage,
                modifier = Modifier.testTag("rescan_sd_button")
            ) {
                Text("Rescan", style = MaterialTheme.typography.labelMedium, color = PhosphorCyan)
            }
        }

        Spacer(modifier = Modifier.size(10.dp))

        Text(
            text = "Save Location",
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary
        )
        Spacer(modifier = Modifier.size(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            com.example.engine.StorageTargetMode.entries.forEach { mode ->
                val selected = storageStatus.selectedMode == mode
                androidx.compose.material3.FilterChip(
                    selected = selected,
                    onClick = {
                        if (mode == com.example.engine.StorageTargetMode.CUSTOM_FOLDER && storageStatus.customTreeUri == null) {
                            onPickCustomFolder()
                        } else {
                            onSelectMode(mode)
                        }
                    },
                    label = {
                        Text(
                            text = mode.label,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1
                        )
                    },
                    colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                        selectedContainerColor = PhosphorCyan.copy(alpha = 0.2f),
                        selectedLabelColor = PhosphorCyan
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("storage_mode_${mode.name.lowercase()}")
                )
            }
        }

        Spacer(modifier = Modifier.size(6.dp))

        Text(
            text = "Active Destination: ${storageStatus.activeDestinationLabel}",
            style = MaterialTheme.typography.labelSmall,
            color = PhosphorCyan
        )

        Spacer(modifier = Modifier.size(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilledTonalButton(
                onClick = onPickCustomFolder,
                modifier = Modifier
                    .weight(1f)
                    .testTag("choose_transfer_folder_button")
            ) {
                Text("Choose Folder", style = MaterialTheme.typography.labelMedium)
            }
            Button(
                onClick = onTransferAll,
                enabled = !isTransferring,
                colors = ButtonDefaults.buttonColors(
                    containerColor = GeoportTeal,
                    contentColor = Color(0xFF090D10)
                ),
                modifier = Modifier
                    .weight(1f)
                    .testTag("transfer_all_files_button")
            ) {
                Text(
                    text = if (isTransferring) "Transferring..." else "Transfer All",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun BatteryWarningBanner(
    batteryState: com.example.data.model.BatteryTelemetryState,
    onOpenMonitor: () -> Unit,
    onEnableLowPower: () -> Unit,
    modifier: Modifier = Modifier
) {
    val showBanner = batteryState.isLowPowerAlertActive || batteryState.isThermalAlertActive
    AnimatedVisibility(
        visible = showBanner,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier
    ) {
        val warnColor = if (batteryState.levelPct <= 8 || batteryState.isThermalAlertActive) AnomalyCrimson else FranksAmber
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(warnColor.copy(alpha = 0.16f))
                .border(1.dp, warnColor, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .testTag("battery_warning_banner"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (batteryState.isThermalAlertActive) {
                        "THERMAL WARNING (${"%.1f".format(Locale.US, batteryState.temperatureC)}°C)"
                    } else {
                        "LOW BATTERY (${batteryState.levelPct}%) — SHUTDOWN RISK"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = warnColor,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = batteryState.estimatedMinutesRemaining?.let { "~${it} min left • Tap Save Power to conserve" }
                        ?: "Connect power or enable Low-Power Mode",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            TextButton(onClick = onOpenMonitor) {
                Text("Details", style = MaterialTheme.typography.labelSmall, color = PhosphorCyan)
            }
            FilledTonalButton(
                onClick = onEnableLowPower,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = warnColor.copy(alpha = 0.25f),
                    contentColor = warnColor
                ),
                modifier = Modifier.testTag("low_power_mode_button")
            ) {
                Text("Save Power", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@androidx.compose.material3.ExperimentalMaterial3Api
@Composable
fun BatteryPowerMonitorSheet(
    batteryState: com.example.data.model.BatteryTelemetryState,
    nightVisionEnabled: Boolean = false,
    nightVisionBrightness: Float = 0.90f,
    onNightVisionChange: (Boolean) -> Unit = {},
    onNightVisionBrightnessChange: (Float) -> Unit = {},
    onThresholdChange: (Int) -> Unit,
    onAutoProtectChange: (Boolean) -> Unit,
    onEnableLowPower: () -> Unit,
    onDismiss: () -> Unit
) {
    val nightVision = LocalNightVisionEnabled.current || nightVisionEnabled
    val levelColor = when {
        batteryState.isCharging -> GeoportTeal
        batteryState.levelPct <= batteryState.warningThresholdPct -> AnomalyCrimson
        batteryState.levelPct <= 30 -> FranksAmber
        else -> PhosphorCyan
    }

    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .nightVisionOverlay(nightVision)
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "BATTERY HEALTH & POWER MONITOR",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${batteryState.chargeSource} • Health: ${batteryState.health.label}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (batteryState.health == com.example.data.model.BatteryHealthLabel.GOOD) GeoportTeal else FranksAmber
                    )
                }
                Text(
                    text = "${batteryState.levelPct}%",
                    style = MaterialTheme.typography.headlineMedium,
                    color = levelColor,
                    fontWeight = FontWeight.Bold
                )
            }

            // Key Power & Health Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PowerMetricTile("Power Draw", "${"%.2f".format(Locale.US, batteryState.powerWatts)} W", PhosphorCyan, Modifier.weight(1f))
                PowerMetricTile("Current", "${"%.0f".format(Locale.US, batteryState.currentMa)} mA", FranksAmber, Modifier.weight(1f))
                PowerMetricTile("Temp", "${"%.1f".format(Locale.US, batteryState.temperatureC)} °C", if (batteryState.isThermalAlertActive) AnomalyCrimson else GeoportTeal, Modifier.weight(1f))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PowerMetricTile("Voltage", "${"%.2f".format(Locale.US, batteryState.voltageV)} V", TextSecondary, Modifier.weight(1f))
                PowerMetricTile(
                    label = "Session Drain",
                    value = if (batteryState.sessionStartPct != null) "${batteryState.sessionDrainPct}%" else "Standby",
                    color = if (batteryState.sessionDrainPct > 10) FranksAmber else PhosphorCyan,
                    modifier = Modifier.weight(1f)
                )
                PowerMetricTile(
                    label = "Est. Left",
                    value = batteryState.estimatedMinutesRemaining?.let {
                        if (it >= 60) "${it / 60}h ${it % 60}m" else "${it}m"
                    } ?: if (batteryState.isCharging) "Charging" else "—",
                    color = GeoportTeal,
                    modifier = Modifier.weight(1f)
                )
            }

            // Power Consumption Sparkline
            if (batteryState.powerHistory.size >= 2) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF090D10))
                        .border(1.dp, SubtleBorder, RoundedCornerShape(10.dp))
                        .padding(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("POWER DRAW HISTORY (W)", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                        Text(
                            "Avg ${"%.2f".format(Locale.US, batteryState.avgPowerWatts)}W • Peak ${"%.2f".format(Locale.US, batteryState.peakPowerWatts)}W",
                            style = MaterialTheme.typography.labelSmall,
                            color = PhosphorCyan
                        )
                    }
                    androidx.compose.foundation.Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .size(width = 300.dp, height = 64.dp)
                    ) {
                        val pts = batteryState.powerHistory
                        val maxW = pts.maxOfOrNull { it.powerWatts }?.coerceAtLeast(1.5f) ?: 1.5f
                        val path = androidx.compose.ui.graphics.Path()
                        val stepX = size.width / (pts.size - 1).coerceAtLeast(1)
                        pts.forEachIndexed { idx, s ->
                            val x = idx * stepX
                            val y = size.height - (s.powerWatts / maxW).coerceIn(0f, 1f) * (size.height * 0.88f)
                            if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
                        }
                        drawPath(
                            path = path,
                            color = PhosphorCyan,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(
                                width = 3f,
                                cap = androidx.compose.ui.graphics.StrokeCap.Round
                            )
                        )
                    }
                }
            }

            // Threshold Slider
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Low-Battery Alert Threshold", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                    Text("${batteryState.warningThresholdPct}%", style = MaterialTheme.typography.labelLarge, color = FranksAmber)
                }
                androidx.compose.material3.Slider(
                    value = batteryState.warningThresholdPct.toFloat(),
                    onValueChange = { onThresholdChange(it.toInt()) },
                    valueRange = 5f..35f,
                    modifier = Modifier.testTag("battery_threshold_slider")
                )
            }

            // Auto-Save Recording Protection
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Auto-Save Audio Before Shutdown", style = MaterialTheme.typography.bodyMedium)
                    Text("Safely finalizes WAV files at ≤5% battery", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                }
                androidx.compose.material3.Switch(
                    checked = batteryState.autoProtectRecording,
                    onCheckedChange = onAutoProtectChange,
                    modifier = Modifier.testTag("auto_protect_recording_switch")
                )
            }

            // Night Vision Red Overlay Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Night Vision Red Overlay", style = MaterialTheme.typography.bodyMedium)
                    Text("Preserves dark-adapted vision & reduces screen glare", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                }
                androidx.compose.material3.Switch(
                    checked = nightVisionEnabled,
                    onCheckedChange = onNightVisionChange,
                    modifier = Modifier.testTag("sheet_night_vision_switch")
                )
            }

            if (nightVisionEnabled) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Night Vision Visibility", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                        Text("${(nightVisionBrightness * 100).toInt()}%", style = MaterialTheme.typography.labelLarge, color = AnomalyCrimson)
                    }
                    androidx.compose.material3.Slider(
                        value = nightVisionBrightness,
                        onValueChange = onNightVisionBrightnessChange,
                        valueRange = 0.40f..1.15f,
                        modifier = Modifier.testTag("night_vision_brightness_slider")
                    )
                }
            }

            Button(
                onClick = {
                    onEnableLowPower()
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = FranksAmber,
                    contentColor = Color(0xFF090D10)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("sheet_low_power_button")
            ) {
                Text("Enable Low-Power Mode (Keep EMF Active)", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun PowerMetricTile(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(ElevatedCard)
            .border(1.dp, SubtleBorder, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 8.dp)
    ) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = TextMuted)
        Text(text = value, style = MaterialTheme.typography.labelLarge, color = color, fontWeight = FontWeight.Bold)
    }
}

