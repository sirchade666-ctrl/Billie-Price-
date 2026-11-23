package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.SensorsOff
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.filled.DeviceThermostat
import com.example.R
import com.example.data.local.AudioClipEntity
import com.example.data.local.FlaggedAudioSegmentEntity
import com.example.data.local.InvestigationSessionEntity
import com.example.data.local.SensorTrendSampleEntity
import com.example.data.model.EmfAlertConfig
import com.example.data.model.EmfAlertSound
import com.example.data.model.EmfReading
import com.example.data.model.EmfSensorStatus
import com.example.data.model.TemperatureReading
import com.example.data.model.TrendTimeScale
import com.example.ui.components.ActiveSessionBanner
import com.example.ui.components.InstrumentCard
import com.example.ui.components.StatusLed
import com.example.ui.components.TelemetryTrendChartCard
import com.example.ui.components.TempIceBlue
import com.example.ui.theme.AnomalyCrimson
import com.example.ui.theme.ElevatedCard
import com.example.ui.theme.FranksAmber
import com.example.ui.theme.GeoportTeal
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.PhosphorCyan
import com.example.ui.theme.SubtleBorder
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VoidBlack
import java.util.Locale
import kotlin.math.abs
import kotlin.math.min

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EmfRadarScreen(
    sensorStatus: EmfSensorStatus,
    reading: EmfReading,
    history: List<EmfReading>,
    activeSession: InvestigationSessionEntity?,
    alertConfig: EmfAlertConfig,
    onCalibrateClick: () -> Unit,
    onManualMarkClick: () -> Unit,
    onThresholdChange: (Float) -> Unit,
    onAudibleAlertToggle: (Boolean) -> Unit,
    onSelectAlertSound: (EmfAlertSound) -> Unit,
    onPreviewAlertSound: () -> Unit,
    onHapticToggle: (Boolean) -> Unit,
    onIgnoreMotionToggle: (Boolean) -> Unit,
    onStartSessionClick: () -> Unit,
    onStopSessionClick: () -> Unit,
    temperatureReading: TemperatureReading = TemperatureReading(),
    trendSamples: List<SensorTrendSampleEntity> = emptyList(),
    audioClips: List<AudioClipEntity> = emptyList(),
    flaggedSegments: List<FlaggedAudioSegmentEntity> = emptyList(),
    onInspectAudioClip: ((AudioClipEntity) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val thresholdUt = alertConfig.thresholdUt
    val absDelta = abs(reading.delta)
    val isAlert = absDelta >= thresholdUt
    val isCritical = absDelta >= thresholdUt * 2.2f
    val statusColor = when {
        !sensorStatus.isHardwareAvailable -> TextMuted
        isCritical -> AnomalyCrimson
        isAlert -> FranksAmber
        else -> PhosphorCyan
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            ActiveSessionBanner(
                activeSession = activeSession,
                onStartSessionClick = onStartSessionClick,
                onStopSessionClick = onStopSessionClick
            )
        }

        if (!sensorStatus.isHardwareAvailable) {
            item {
                InstrumentCard(borderColor = FranksAmber.copy(alpha = 0.7f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.SensorsOff,
                            contentDescription = null,
                            tint = FranksAmber,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.no_magnetometer_title),
                                style = MaterialTheme.typography.titleMedium,
                                color = FranksAmber
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.no_magnetometer_desc),
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }
        }

        // Primary EMF Telemetry Digital Readout
        item {
            InstrumentCard(
                borderColor = if (isAlert) statusColor else SubtleBorder
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusLed(
                            active = sensorStatus.isListening,
                            activeColor = statusColor,
                            pulse = isAlert
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (sensorStatus.isCalibrating) {
                                "CALIBRATING BASELINE..."
                            } else if (isCritical) {
                                "CRITICAL EMF SURGE"
                            } else if (isAlert) {
                                "ALERT THRESHOLD EXCEEDED"
                            } else {
                                "MAGNETOMETER ACTIVE"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = statusColor
                        )
                    }
                    if (reading.isDeviceMoving) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(FranksAmber.copy(alpha = 0.16f))
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Warning,
                                contentDescription = null,
                                tint = FranksAmber,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "MOTION",
                                style = MaterialTheme.typography.labelSmall,
                                color = FranksAmber
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column {
                        Text(
                            text = "${"%.2f".format(Locale.US, reading.magnitude)} μT",
                            style = MaterialTheme.typography.displaySmall,
                            color = statusColor
                        )
                        Text(
                            text = "${"%.1f".format(Locale.US, reading.milligauss)} mG  •  Baseline ${"%.1f".format(Locale.US, reading.baseline)} μT",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextSecondary
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "DELTA (Δ)",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted
                        )
                        val sign = if (reading.delta >= 0) "+" else ""
                        Text(
                            text = "$sign${"%.2f".format(Locale.US, reading.delta)} μT",
                            style = MaterialTheme.typography.headlineSmall,
                            color = statusColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AxisBadge(label = "Bx", value = reading.x, modifier = Modifier.weight(1f))
                    AxisBadge(label = "By", value = reading.y, modifier = Modifier.weight(1f))
                    AxisBadge(label = "Bz", value = reading.z, modifier = Modifier.weight(1f))
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Live Temperature Telemetry Strip
                val tempAccent = if (temperatureReading.isSharpDrop) TempIceBlue else FranksAmber
                val tempSign = if (temperatureReading.deltaC >= 0f) "+" else ""
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(ElevatedCard)
                        .border(
                            1.dp,
                            if (temperatureReading.isSharpDrop) TempIceBlue.copy(alpha = 0.65f) else SubtleBorder,
                            RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                        .testTag("live_temperature_strip"),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.DeviceThermostat,
                            contentDescription = null,
                            tint = tempAccent,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text(
                                text = temperatureReading.sourceLabel.uppercase(Locale.US),
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted
                            )
                            Text(
                                text = "${"%.1f".format(Locale.US, temperatureReading.temperatureC)} °C",
                                style = MaterialTheme.typography.labelLarge,
                                color = tempAccent,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = if (temperatureReading.isSharpDrop) "SHARP DROP" else "THERMAL Δ",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (temperatureReading.isSharpDrop) TempIceBlue else TextMuted,
                            fontWeight = if (temperatureReading.isSharpDrop) FontWeight.Bold else FontWeight.Normal
                        )
                        Text(
                            text = "$tempSign${"%.1f".format(Locale.US, temperatureReading.deltaC)} °C",
                            style = MaterialTheme.typography.labelLarge,
                            color = tempAccent
                        )
                    }
                }
            }
        }

        // Spatial Flux Radar Map
        item {
            val cdRadar = stringResource(R.string.cd_emf_radar_canvas)
            InstrumentCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "SPATIAL EMF FLUX MAP",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "3-Axis Vector Trail",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1.45f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(VoidBlack)
                        .border(1.dp, SubtleBorder, RoundedCornerShape(12.dp))
                        .semantics { contentDescription = cdRadar }
                ) {
                    EmfSpatialRadarCanvas(
                        history = history,
                        current = reading,
                        thresholdUt = thresholdUt,
                        accentColor = statusColor,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        // Oscilloscope Waveform Monitor
        item {
            val cdWave = stringResource(R.string.cd_emf_waveform)
            InstrumentCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "REAL-TIME FLUCTUATION WAVEFORM",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "±${"%.1f".format(Locale.US, thresholdUt)} μT Trigger",
                        style = MaterialTheme.typography.labelSmall,
                        color = FranksAmber
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(155.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(VoidBlack)
                        .border(1.dp, SubtleBorder, RoundedCornerShape(12.dp))
                        .semantics { contentDescription = cdWave }
                ) {
                    EmfOscilloscopeCanvas(
                        history = history,
                        thresholdUt = thresholdUt,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        // Historical Dual-Axis EMF & Temperature Trend Chart with Audio Correlation
        item {
            TelemetryTrendChartCard(
                samples = trendSamples,
                audioClips = audioClips,
                flaggedSegments = flaggedSegments,
                tempSourceLabel = temperatureReading.sourceLabel,
                initialScale = TrendTimeScale.FIVE_MIN,
                onInspectAudioClip = onInspectAudioClip
            )
        }

        // Calibration & Alert System Customization
        item {
            InstrumentCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Tune,
                        contentDescription = null,
                        tint = PhosphorCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "CALIBRATION & ALERT SYSTEM",
                        style = MaterialTheme.typography.titleSmall
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FilledTonalButton(
                        onClick = onCalibrateClick,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("calibrate_emf_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Sensors,
                            contentDescription = stringResource(R.string.cd_calibrate),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Zero Baseline")
                    }
                    Button(
                        onClick = onManualMarkClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = FranksAmber,
                            contentColor = VoidBlack
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("mark_emf_anomaly_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.BookmarkAdd,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Mark Spike", fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Alert Threshold Sensitivity",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    Text(
                        text = "±${"%.1f".format(Locale.US, thresholdUt)} μT",
                        style = MaterialTheme.typography.labelLarge,
                        color = PhosphorCyan
                    )
                }
                Slider(
                    value = thresholdUt,
                    onValueChange = onThresholdChange,
                    valueRange = 1.0f..30f,
                    colors = SliderDefaults.colors(
                        thumbColor = PhosphorCyan,
                        activeTrackColor = PhosphorCyan
                    ),
                    modifier = Modifier.testTag("emf_threshold_slider")
                )

                // Quick Sensitivity Presets
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        "High (2.5μT)" to 2.5f,
                        "Std (6.0μT)" to 6.0f,
                        "Surge (15μT)" to 15.0f
                    ).forEach { (label, valUt) ->
                        val selected = abs(thresholdUt - valUt) < 0.6f
                        FilterChip(
                            selected = selected,
                            onClick = { onThresholdChange(valUt) },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = PhosphorCyan.copy(alpha = 0.2f),
                                selectedLabelColor = PhosphorCyan
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Audible Alert Switch + Preview
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.VolumeUp,
                            contentDescription = null,
                            tint = FranksAmber,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Audible EMF Alert Tone",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Switch(
                        checked = alertConfig.audibleAlertEnabled,
                        onCheckedChange = onAudibleAlertToggle,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = VoidBlack,
                            checkedTrackColor = FranksAmber
                        ),
                        modifier = Modifier.testTag("audible_alert_switch")
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Alert Sound Profile",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary
                    )
                    FilledTonalButton(
                        onClick = onPreviewAlertSound,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.testTag("preview_alert_sound_button")
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Preview", style = MaterialTheme.typography.labelSmall)
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    EmfAlertSound.entries.forEach { sound ->
                        FilterChip(
                            selected = alertConfig.selectedSound == sound,
                            onClick = { onSelectAlertSound(sound) },
                            label = { Text(sound.label, style = MaterialTheme.typography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = FranksAmber.copy(alpha = 0.22f),
                                selectedLabelColor = FranksAmber
                            ),
                            modifier = Modifier.testTag("alert_sound_${sound.name.lowercase()}")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.NotificationsActive,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Suppress Alerts While Moving",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Switch(
                        checked = alertConfig.ignoreMotionAlerts,
                        onCheckedChange = onIgnoreMotionToggle,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = VoidBlack,
                            checkedTrackColor = PhosphorCyan
                        ),
                        modifier = Modifier.testTag("ignore_motion_alerts_switch")
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Vibration,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Haptic Spike Pulse",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Switch(
                        checked = alertConfig.hapticAlertEnabled,
                        onCheckedChange = onHapticToggle,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = VoidBlack,
                            checkedTrackColor = GeoportTeal
                        ),
                        modifier = Modifier.testTag("haptic_alert_switch")
                    )
                }

                if (sensorStatus.isHardwareAvailable) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Sensor: ${sensorStatus.sensorName} (${sensorStatus.sensorVendor}) • Res ${"%.3f".format(Locale.US, sensorStatus.resolutionUt)} μT",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                }
            }
        }
    }
}

@Composable
private fun AxisBadge(
    label: String,
    value: Float,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(ElevatedCard)
            .border(1.dp, SubtleBorder, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted
        )
        Text(
            text = "${"%.1f".format(Locale.US, value)} μT",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun EmfSpatialRadarCanvas(
    history: List<EmfReading>,
    current: EmfReading,
    thresholdUt: Float,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val maxRadius = min(size.width, size.height) * 0.43f

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(accentColor.copy(alpha = 0.14f), Color.Transparent),
                center = Offset(cx, cy),
                radius = maxRadius * 1.15f
            ),
            radius = maxRadius * 1.15f,
            center = Offset(cx, cy)
        )

        val gridColor = Color(0xFF1F2B38)
        listOf(0.33f, 0.66f, 1.0f).forEach { fraction ->
            drawCircle(
                color = gridColor,
                radius = maxRadius * fraction,
                center = Offset(cx, cy),
                style = Stroke(width = 1.2f)
            )
        }

        drawLine(gridColor, Offset(cx - maxRadius, cy), Offset(cx + maxRadius, cy), strokeWidth = 1f)
        drawLine(gridColor, Offset(cx, cy - maxRadius), Offset(cx, cy + maxRadius), strokeWidth = 1f)

        val scaleRange = (thresholdUt * 4f).coerceAtLeast(30f)
        val refX = history.firstOrNull()?.x ?: current.x
        val refY = history.firstOrNull()?.y ?: current.y

        if (history.size >= 2) {
            val trailPath = Path()
            history.forEachIndexed { idx, pt ->
                val dx = ((pt.x - refX) / scaleRange).coerceIn(-1f, 1f) * maxRadius
                val dy = ((pt.y - refY) / scaleRange).coerceIn(-1f, 1f) * maxRadius
                val px = cx + dx
                val py = cy - dy
                if (idx == 0) trailPath.moveTo(px, py) else trailPath.lineTo(px, py)
            }
            drawPath(
                path = trailPath,
                color = PhosphorCyan.copy(alpha = 0.55f),
                style = Stroke(width = 2.4f, cap = StrokeCap.Round)
            )

            history.takeLast(24).forEachIndexed { i, pt ->
                val alpha = (i + 1) / 24f
                val dx = ((pt.x - refX) / scaleRange).coerceIn(-1f, 1f) * maxRadius
                val dy = ((pt.y - refY) / scaleRange).coerceIn(-1f, 1f) * maxRadius
                val nodeColor = if (abs(pt.delta) >= thresholdUt) AnomalyCrimson else GeoportTeal
                drawCircle(
                    color = nodeColor.copy(alpha = alpha * 0.8f),
                    radius = if (abs(pt.delta) >= thresholdUt) 5.5f else 3f,
                    center = Offset(cx + dx, cy - dy)
                )
            }
        }

        val curDx = ((current.x - refX) / scaleRange).coerceIn(-1f, 1f) * maxRadius
        val curDy = ((current.y - refY) / scaleRange).coerceIn(-1f, 1f) * maxRadius
        val tip = Offset(cx + curDx, cy - curDy)
        drawLine(
            color = accentColor,
            start = Offset(cx, cy),
            end = tip,
            strokeWidth = 3.2f,
            cap = StrokeCap.Round
        )
        drawCircle(color = accentColor, radius = 7f, center = tip)
        drawCircle(color = accentColor.copy(alpha = 0.25f), radius = 16f, center = tip)
    }
}

@Composable
private fun EmfOscilloscopeCanvas(
    history: List<EmfReading>,
    thresholdUt: Float,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val midY = h / 2f

        val gridColor = Color(0xFF18222D)
        for (i in 1..3) {
            val y = h * (i / 4f)
            drawLine(gridColor, Offset(0f, y), Offset(w, y), strokeWidth = 1f)
        }
        for (i in 1..5) {
            val x = w * (i / 6f)
            drawLine(gridColor, Offset(x, 0f), Offset(x, h), strokeWidth = 1f)
        }

        drawLine(
            color = TextMuted.copy(alpha = 0.45f),
            start = Offset(0f, midY),
            end = Offset(w, midY),
            strokeWidth = 1.2f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
        )

        val maxDisplayDelta = (thresholdUt * 2.5f).coerceAtLeast(15f)
        val thresholdOffset = (thresholdUt / maxDisplayDelta) * (h * 0.44f)

        drawLine(
            color = FranksAmber.copy(alpha = 0.55f),
            start = Offset(0f, midY - thresholdOffset),
            end = Offset(w, midY - thresholdOffset),
            strokeWidth = 1.2f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
        )
        drawLine(
            color = FranksAmber.copy(alpha = 0.55f),
            start = Offset(0f, midY + thresholdOffset),
            end = Offset(w, midY + thresholdOffset),
            strokeWidth = 1.2f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
        )

        if (history.size < 2) return@Canvas

        val wavePath = Path()
        val fillPath = Path()
        val stepX = w / (history.size - 1).coerceAtLeast(1)

        history.forEachIndexed { idx, pt ->
            val x = idx * stepX
            val normalizedDelta = (pt.delta / maxDisplayDelta).coerceIn(-1f, 1f)
            val y = midY - normalizedDelta * (h * 0.44f)
            if (idx == 0) {
                wavePath.moveTo(x, y)
                fillPath.moveTo(x, midY)
                fillPath.lineTo(x, y)
            } else {
                wavePath.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
            if (idx == history.lastIndex) {
                fillPath.lineTo(x, midY)
                fillPath.close()
            }
        }

        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(
                    PhosphorCyan.copy(alpha = 0.25f),
                    Color.Transparent,
                    AnomalyCrimson.copy(alpha = 0.2f)
                )
            )
        )
        drawPath(
            path = wavePath,
            color = PhosphorCyan,
            style = Stroke(width = 3f, cap = StrokeCap.Round)
        )

        history.forEachIndexed { idx, pt ->
            if (abs(pt.delta) >= thresholdUt) {
                val x = idx * stepX
                val normalizedDelta = (pt.delta / maxDisplayDelta).coerceIn(-1f, 1f)
                val y = midY - normalizedDelta * (h * 0.44f)
                drawCircle(
                    color = AnomalyCrimson,
                    radius = 4.5f,
                    center = Offset(x, y)
                )
            }
        }
    }
}
