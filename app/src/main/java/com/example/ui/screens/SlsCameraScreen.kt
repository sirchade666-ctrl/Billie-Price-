package com.example.ui.screens

import android.view.ViewGroup
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R
import com.example.data.local.InvestigationSessionEntity
import com.example.data.model.EmfReading
import com.example.data.model.SlsFrameTelemetry
import com.example.data.model.SlsJointType
import com.example.data.model.SlsSensitivity
import com.example.data.model.SlsSkeletonTopology
import com.example.data.model.SlsVisualMode
import com.example.data.model.TemperatureReading
import com.example.engine.SlsPoseAnalyzer
import com.example.ui.components.ActiveSessionBanner
import com.example.ui.components.InstrumentCard
import com.example.ui.components.StatusLed
import com.example.ui.theme.AnomalyCrimson
import com.example.ui.theme.FranksAmber
import com.example.ui.theme.GeoportTeal
import com.example.ui.theme.PhosphorCyan
import com.example.ui.theme.SubtleBorder
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VoidBlack
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.hypot

private val SlsClassicGreen = Color(0xFF39FF14)
private val SlsThermalOrange = Color(0xFFFF6D00)

@Composable
fun SlsCameraScreen(
    hasCameraPermission: Boolean,
    emfReading: EmfReading,
    temperatureReading: TemperatureReading,
    activeSession: InvestigationSessionEntity?,
    onRequestCameraPermission: () -> Unit,
    onStartSessionClick: () -> Unit,
    onStopSessionClick: () -> Unit,
    onLogSlsSighting: (jointCount: Int, avgConfidencePct: Int, visualModeLabel: String, isLowLight: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var visualMode by remember { mutableStateOf(SlsVisualMode.CLASSIC_GREEN) }
    var sensitivity by remember { mutableStateOf(SlsSensitivity.STANDARD) }
    var showIrDotGrid by remember { mutableStateOf(true) }
    var showTargetBox by remember { mutableStateOf(true) }
    var smoothSkeletonEnabled by remember { mutableStateOf(true) }
    var useFrontCamera by remember { mutableStateOf(false) }
    var torchEnabled by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var telemetry by remember { mutableStateOf(SlsFrameTelemetry()) }
    var sightingsLogged by remember { mutableStateOf(0) }

    val activeAccent = when (visualMode) {
        SlsVisualMode.CLASSIC_GREEN -> SlsClassicGreen
        SlsVisualMode.PHOSPHOR_CYAN -> PhosphorCyan
        SlsVisualMode.THERMAL_DEPTH -> SlsThermalOrange
        SlsVisualMode.RAW_OPTICAL -> GeoportTeal
    }

    val skeleton = telemetry.skeleton
    val isFigureDetected = skeleton.isDetected
    val isPartialForm = skeleton.isPartialForm
    val confPct = (skeleton.avgConfidence * 100f).toInt().coerceIn(0, 100)
    val lockStatusColor = when {
        !hasCameraPermission -> TextMuted
        isFigureDetected -> activeAccent
        isPartialForm -> FranksAmber
        else -> PhosphorCyan
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("sls_camera_list"),
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

        // Main SLS Viewfinder & Real-Time ML Kit Pose Skeletal Tracker
        item {
            InstrumentCard(
                borderColor = if (isFigureDetected || isPartialForm) lockStatusColor else SubtleBorder
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusLed(
                            active = hasCameraPermission,
                            activeColor = lockStatusColor,
                            pulse = isFigureDetected || isPartialForm
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = when {
                                !hasCameraPermission -> "SLS CAMERA STANDBY"
                                isFigureDetected -> "FIGURE LOCKED (${skeleton.jointCount} JOINTS)"
                                isPartialForm -> "PARTIAL FORM (${skeleton.jointCount} JOINTS)"
                                else -> "ML KIT SLS SCANNING..."
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = lockStatusColor,
                            modifier = Modifier.testTag("sls_status_label")
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (telemetry.isLowLight && hasCameraPermission) {
                            Text(
                                text = "LOW LIGHT",
                                style = MaterialTheme.typography.labelSmall,
                                color = FranksAmber,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(FranksAmber.copy(alpha = 0.16f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        if (hasCameraPermission && telemetry.fps > 0.5f) {
                            Text(
                                text = "${telemetry.fps.toInt()} FPS",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (!hasCameraPermission) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(0.85f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(VoidBlack)
                            .border(1.dp, SubtleBorder, RoundedCornerShape(14.dp))
                            .padding(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AccessibilityNew,
                                contentDescription = null,
                                tint = SlsClassicGreen,
                                modifier = Modifier.size(52.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "SLS Camera Mode (ML Kit Pose)",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = stringResource(R.string.permission_camera_rationale),
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = onRequestCameraPermission,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = SlsClassicGreen,
                                    contentColor = VoidBlack
                                ),
                                modifier = Modifier.testTag("grant_camera_permission_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Videocam,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.grant_camera_permission),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                } else {
                    val cdFeed = stringResource(R.string.cd_sls_camera_feed)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(0.82f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(VoidBlack)
                            .border(
                                width = if (isFigureDetected || isPartialForm) 2.dp else 1.dp,
                                color = if (isFigureDetected || isPartialForm) lockStatusColor else SubtleBorder,
                                shape = RoundedCornerShape(14.dp)
                            )
                            .semantics { contentDescription = cdFeed }
                            .testTag("sls_camera_viewport")
                    ) {
                        SlsCameraPreviewHost(
                            useFrontCamera = useFrontCamera,
                            torchEnabled = torchEnabled && !useFrontCamera,
                            sensitivity = sensitivity,
                            smoothingEnabled = smoothSkeletonEnabled,
                            onTelemetry = { telemetry = it },
                            onCameraError = { cameraError = it },
                            modifier = Modifier.fillMaxSize()
                        )

                        // Visual filter tint over live optical preview
                        val tintBrush = when (visualMode) {
                            SlsVisualMode.CLASSIC_GREEN -> Brush.verticalGradient(
                                listOf(
                                    Color(0x38052A08),
                                    Color(0x28041A06),
                                    Color(0x45021404)
                                )
                            )
                            SlsVisualMode.PHOSPHOR_CYAN -> Brush.verticalGradient(
                                listOf(
                                    Color(0x34002430),
                                    Color(0x24001420),
                                    Color(0x42001D29)
                                )
                            )
                            SlsVisualMode.THERMAL_DEPTH -> Brush.verticalGradient(
                                listOf(
                                    Color(0x38280736),
                                    Color(0x2A360B06),
                                    Color(0x421D0429)
                                )
                            )
                            SlsVisualMode.RAW_OPTICAL -> null
                        }
                        if (tintBrush != null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(tintBrush)
                            )
                        }

                        // Pixel-aligned FILL_CENTER SLS Structured-Light + ML Kit Skeletal Wireframe
                        SlsSkeletonOverlayCanvas(
                            telemetry = telemetry,
                            accentColor = if (isPartialForm && !isFigureDetected) FranksAmber else activeAccent,
                            showIrDotGrid = showIrDotGrid,
                            showTargetBox = showTargetBox,
                            modifier = Modifier.fillMaxSize()
                        )

                        // Top-left live EMF & Temp HUD pill
                        Column(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(VoidBlack.copy(alpha = 0.74f))
                                    .padding(horizontal = 8.dp, vertical = 5.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val emfColor = if (abs(emfReading.delta) >= 6f) FranksAmber else PhosphorCyan
                                Text(
                                    text = "EMF Δ ${if (emfReading.delta >= 0) "+" else ""}${"%.1f".format(Locale.US, emfReading.delta)} μT",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = emfColor,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${"%.1f".format(Locale.US, temperatureReading.temperatureC)}°C",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (temperatureReading.isSharpDrop) AnomalyCrimson else TextSecondary
                                )
                            }
                            Text(
                                text = "ML KIT POSE • STREAM",
                                style = MaterialTheme.typography.labelSmall,
                                color = activeAccent,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(VoidBlack.copy(alpha = 0.68f))
                                    .padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }

                        // Top-right camera controls (Torch & Flip Camera)
                        Row(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (!useFrontCamera) {
                                IconButton(
                                    onClick = { torchEnabled = !torchEnabled },
                                    colors = IconButtonDefaults.iconButtonColors(
                                        containerColor = VoidBlack.copy(alpha = 0.72f),
                                        contentColor = if (torchEnabled) FranksAmber else TextSecondary
                                    ),
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("sls_torch_toggle")
                                ) {
                                    Icon(
                                        imageVector = if (torchEnabled) Icons.Filled.FlashlightOn else Icons.Filled.FlashlightOff,
                                        contentDescription = if (torchEnabled) "Turn off flashlight" else "Turn on flashlight"
                                    )
                                }
                            }
                            IconButton(
                                onClick = {
                                    useFrontCamera = !useFrontCamera
                                    if (useFrontCamera) torchEnabled = false
                                },
                                colors = IconButtonDefaults.iconButtonColors(
                                    containerColor = VoidBlack.copy(alpha = 0.72f),
                                    contentColor = activeAccent
                                ),
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("sls_flip_camera_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Cameraswitch,
                                    contentDescription = "Switch front and rear camera"
                                )
                            }
                        }

                        // Bottom telemetry HUD strip inside viewport
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color.Transparent, VoidBlack.copy(alpha = 0.86f))
                                    )
                                )
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = when {
                                        isFigureDetected -> "SKELETAL LOCK • ${skeleton.jointCount} JOINTS • $confPct% CONF"
                                        isPartialForm -> "PARTIAL FORM DETECTED • ${skeleton.jointCount} JOINTS • $confPct%"
                                        else -> "NO HUMAN-LIKE FORM IN FRAME"
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (isFigureDetected || isPartialForm) lockStatusColor else TextSecondary,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${visualMode.label} • Motion Zones: ${telemetry.motionCells.size} • Logged: $sightingsLogged",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextMuted
                                )
                            }
                            if (emfReading.isPowerSourceFiltered) {
                                Text(
                                    text = "AC FILTER",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = GeoportTeal,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        if (cameraError != null) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .padding(16.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(VoidBlack.copy(alpha = 0.88f))
                                    .padding(12.dp)
                            ) {
                                Text(
                                    text = cameraError ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = FranksAmber
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Action row: Log SLS Sighting + Toggle IR Grid
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                sightingsLogged++
                                onLogSlsSighting(
                                    skeleton.jointCount,
                                    confPct,
                                    visualMode.label,
                                    telemetry.isLowLight
                                )
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = activeAccent,
                                contentColor = VoidBlack
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("log_sls_sighting_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.BookmarkAdd,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (skeleton.hasAnyForm) "Log Figure Sighting" else "Log SLS Snapshot",
                                fontWeight = FontWeight.Bold
                            )
                        }
                        FilledTonalButton(
                            onClick = { showIrDotGrid = !showIrDotGrid },
                            modifier = Modifier.testTag("sls_ir_grid_toggle_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.GridOn,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (showIrDotGrid) "Grid On" else "Grid Off")
                        }
                    }
                }
            }
        }

        // Real-Time ML Kit Pose Skeletal Region Telemetry Card
        if (hasCameraPermission) {
            item {
                InstrumentCard(
                    modifier = Modifier.testTag("sls_landmark_telemetry_card")
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.AccessibilityNew,
                                contentDescription = null,
                                tint = activeAccent,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "ML KIT SKELETAL LANDMARKS",
                                style = MaterialTheme.typography.titleSmall
                            )
                        }
                        Text(
                            text = "${skeleton.jointCount} / 27 ACTIVE",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (skeleton.hasAnyForm) activeAccent else TextMuted,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    val headConf = skeleton.regionConfidence(SlsSkeletonTopology.headRegion)
                    val torsoConf = skeleton.regionConfidence(SlsSkeletonTopology.torsoRegion)
                    val armsConf = skeleton.regionConfidence(SlsSkeletonTopology.armsRegion)
                    val legsConf = skeleton.regionConfidence(SlsSkeletonTopology.legsRegion)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        SlsRegionMeter("Head", headConf, activeAccent, Modifier.weight(1f))
                        SlsRegionMeter("Torso", torsoConf, activeAccent, Modifier.weight(1f))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        SlsRegionMeter("Arms / Hands", armsConf, activeAccent, Modifier.weight(1f))
                        SlsRegionMeter("Legs / Feet", legsConf, activeAccent, Modifier.weight(1f))
                    }
                }
            }
        }

        // SLS Visual Filter & Tracking Controls
        item {
            InstrumentCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Visibility,
                        contentDescription = null,
                        tint = activeAccent,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "SLS OPTICS & TRACKING TUNING",
                        style = MaterialTheme.typography.titleSmall
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Visual Filter Overlay",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SlsVisualMode.entries.forEach { mode ->
                        val selected = visualMode == mode
                        FilterChip(
                            selected = selected,
                            onClick = { visualMode = mode },
                            label = {
                                Text(
                                    text = mode.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = activeAccent.copy(alpha = 0.22f),
                                selectedLabelColor = activeAccent
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("sls_mode_${mode.name.lowercase()}")
                        )
                    }
                }
                Text(
                    text = visualMode.subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Tune,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Joint Tracking Confidence Threshold",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SlsSensitivity.entries.forEach { level ->
                        val selected = sensitivity == level
                        FilterChip(
                            selected = selected,
                            onClick = { sensitivity = level },
                            label = { Text(level.label, style = MaterialTheme.typography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = PhosphorCyan.copy(alpha = 0.2f),
                                selectedLabelColor = PhosphorCyan
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("sls_sensitivity_${level.name.lowercase()}")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Anti-Jitter Skeleton Smoothing",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Switch(
                        checked = smoothSkeletonEnabled,
                        onCheckedChange = { smoothSkeletonEnabled = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = VoidBlack,
                            checkedTrackColor = PhosphorCyan
                        ),
                        modifier = Modifier.testTag("sls_smoothing_switch")
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Figure Bounding Brackets",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Switch(
                        checked = showTargetBox,
                        onCheckedChange = { showTargetBox = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = VoidBlack,
                            checkedTrackColor = activeAccent
                        ),
                        modifier = Modifier.testTag("sls_target_box_switch")
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Powered by on-device ML Kit Pose Detection (27 skeletal landmarks with aspect-matched PreviewView projection) + Y-plane optical motion grid.",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
            }
        }
    }
}

@Composable
private fun SlsRegionMeter(
    label: String,
    confidence: Float,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val pct = (confidence * 100f).toInt().coerceIn(0, 100)
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
            Text(
                text = if (pct > 0) "$pct%" else "—",
                style = MaterialTheme.typography.labelSmall,
                color = if (pct > 0) accentColor else TextMuted,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { confidence.coerceIn(0f, 1f) },
            color = accentColor,
            trackColor = SubtleBorder,
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
        )
    }
}

@Composable
private fun SlsCameraPreviewHost(
    useFrontCamera: Boolean,
    torchEnabled: Boolean,
    sensitivity: SlsSensitivity,
    smoothingEnabled: Boolean,
    onTelemetry: (SlsFrameTelemetry) -> Unit,
    onCameraError: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentSensitivity by rememberUpdatedState(sensitivity)
    val currentSmoothing by rememberUpdatedState(smoothingEnabled)
    val currentFront by rememberUpdatedState(useFrontCamera)
    val currentOnTelemetry by rememberUpdatedState(onTelemetry)

    var boundCamera by remember { mutableStateOf<Camera?>(null) }
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
    }

    DisposableEffect(boundCamera, torchEnabled) {
        runCatching {
            boundCamera?.cameraControl?.enableTorch(torchEnabled)
        }
        onDispose { }
    }

    DisposableEffect(lifecycleOwner, useFrontCamera) {
        val analysisExecutor = Executors.newSingleThreadExecutor()
        val analyzer = SlsPoseAnalyzer(
            isFrontCamera = { currentFront },
            sensitivityProvider = { currentSensitivity },
            smoothingEnabledProvider = { currentSmoothing },
            onTelemetryUpdate = { frame ->
                ContextCompat.getMainExecutor(context).execute {
                    currentOnTelemetry(frame)
                }
            }
        )

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        var providerRef: ProcessCameraProvider? = null
        val listener = Runnable {
            try {
                val cameraProvider = cameraProviderFuture.get()
                providerRef = cameraProvider
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }
                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also {
                        it.setAnalyzer(analysisExecutor, analyzer)
                    }

                val selector = if (useFrontCamera) {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                } else {
                    CameraSelector.DEFAULT_BACK_CAMERA
                }

                cameraProvider.unbindAll()
                val camera = cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    selector,
                    preview,
                    imageAnalysis
                )
                boundCamera = camera
                camera.cameraControl.enableTorch(torchEnabled && !useFrontCamera)
                onCameraError(null)
            } catch (e: Exception) {
                onCameraError("Camera unavailable on this device (${e.localizedMessage ?: "error"}). Try switching camera lens.")
            }
        }
        cameraProviderFuture.addListener(listener, ContextCompat.getMainExecutor(context))

        onDispose {
            runCatching { boundCamera?.cameraControl?.enableTorch(false) }
            runCatching { providerRef?.unbindAll() }
            boundCamera = null
            analyzer.close()
            analysisExecutor.shutdown()
        }
    }

    AndroidView(
        factory = { previewView },
        modifier = modifier
    )
}

@Composable
private fun SlsSkeletonOverlayCanvas(
    telemetry: SlsFrameTelemetry,
    accentColor: Color,
    showIrDotGrid: Boolean,
    showTargetBox: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "sls_scan_sweep")
    val sweepProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sls_sweep_y"
    )

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val srcW = telemetry.sourceWidth
        val srcH = telemetry.sourceHeight

        fun project(nx: Float, ny: Float): Offset {
            val (px, py) = SlsPoseAnalyzer.mapNormalizedToFillCenterViewport(
                normX = nx,
                normY = ny,
                sourceWidth = srcW,
                sourceHeight = srcH,
                viewWidth = w,
                viewHeight = h
            )
            return Offset(px, py)
        }

        // 1. Structured-Light Infrared Dot Matrix + Real Motion Hotspots
        if (showIrDotGrid) {
            val cols = 12
            val rows = 16
            for (r in 0 until rows) {
                val py = (r + 0.5f) / rows * h
                for (c in 0 until cols) {
                    val px = (c + 0.5f) / cols * w
                    drawCircle(
                        color = accentColor.copy(alpha = 0.24f),
                        radius = 1.8.dp.toPx(),
                        center = Offset(px, py)
                    )
                }
            }

            // Highlight live Y-plane motion disturbance cells projected onto camera viewport
            for (cell in telemetry.motionCells) {
                val pt = project(cell.normX, cell.normY)
                if (pt.x in 0f..w && pt.y in 0f..h) {
                    drawCircle(
                        color = accentColor.copy(alpha = (0.35f + cell.intensity * 0.45f).coerceIn(0f, 0.85f)),
                        radius = (3.5f + cell.intensity * 3.5f).dp.toPx(),
                        center = pt
                    )
                }
            }

            // Animated horizontal LIDAR scan sweep line
            val sweepY = sweepProgress * h
            drawLine(
                brush = Brush.horizontalGradient(
                    listOf(Color.Transparent, accentColor.copy(alpha = 0.55f), Color.Transparent)
                ),
                start = Offset(0f, sweepY),
                end = Offset(w, sweepY),
                strokeWidth = 2.dp.toPx()
            )
        }

        // 2. Real-Time ML Kit Skeletal Wireframe Overlay
        val skeleton = telemetry.skeleton
        if (skeleton.hasAnyForm) {
            val alpha = skeleton.persistenceAlpha.coerceIn(0.15f, 1f)
            val lineColor = accentColor.copy(alpha = alpha)
            val glowColor = accentColor.copy(alpha = 0.34f * alpha)
            val joints = skeleton.joints

            // Optional bounding target brackets around locked figure
            if (showTargetBox) {
                val topLeft = project(skeleton.minX, skeleton.minY)
                val bottomRight = project(skeleton.maxX, skeleton.maxY)
                val pad = 16.dp.toPx()
                val left = (topLeft.x - pad).coerceIn(8.dp.toPx(), w - 24.dp.toPx())
                val top = (topLeft.y - pad).coerceIn(8.dp.toPx(), h - 24.dp.toPx())
                val right = (bottomRight.x + pad).coerceIn(left + 20.dp.toPx(), w - 8.dp.toPx())
                val bottom = (bottomRight.y + pad).coerceIn(top + 20.dp.toPx(), h - 8.dp.toPx())
                val corner = 18.dp.toPx()
                val bracketStroke = 2.2.dp.toPx()

                drawLine(lineColor, Offset(left, top), Offset(left + corner, top), bracketStroke)
                drawLine(lineColor, Offset(left, top), Offset(left, top + corner), bracketStroke)
                drawLine(lineColor, Offset(right, top), Offset(right - corner, top), bracketStroke)
                drawLine(lineColor, Offset(right, top), Offset(right, top + corner), bracketStroke)
                drawLine(lineColor, Offset(left, bottom), Offset(left + corner, bottom), bracketStroke)
                drawLine(lineColor, Offset(left, bottom), Offset(left, bottom - corner), bracketStroke)
                drawLine(lineColor, Offset(right, bottom), Offset(right - corner, bottom), bracketStroke)
                drawLine(lineColor, Offset(right, bottom), Offset(right, bottom - corner), bracketStroke)
            }

            // Spine & neck connectors for authentic SLS stick-figure look
            val leftShoulder = joints[SlsJointType.LEFT_SHOULDER]
            val rightShoulder = joints[SlsJointType.RIGHT_SHOULDER]
            val leftHip = joints[SlsJointType.LEFT_HIP]
            val rightHip = joints[SlsJointType.RIGHT_HIP]
            val nose = joints[SlsJointType.NOSE]

            val midShoulder = if (leftShoulder != null && rightShoulder != null) {
                project(
                    (leftShoulder.normX + rightShoulder.normX) * 0.5f,
                    (leftShoulder.normY + rightShoulder.normY) * 0.5f
                )
            } else null

            val midHip = if (leftHip != null && rightHip != null) {
                project(
                    (leftHip.normX + rightHip.normX) * 0.5f,
                    (leftHip.normY + rightHip.normY) * 0.5f
                )
            } else null

            if (midShoulder != null && midHip != null) {
                drawLine(
                    color = glowColor,
                    start = midShoulder,
                    end = midHip,
                    strokeWidth = 7.dp.toPx(),
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = lineColor,
                    start = midShoulder,
                    end = midHip,
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            // Head circle & neck connector
            if (nose != null) {
                val headCenter = project(nose.normX, nose.normY)
                val headRadius = if (leftShoulder != null && rightShoulder != null) {
                    val pLs = project(leftShoulder.normX, leftShoulder.normY)
                    val pRs = project(rightShoulder.normX, rightShoulder.normY)
                    val shoulderWidth = hypot(pLs.x - pRs.x, pLs.y - pRs.y)
                    (shoulderWidth * 0.26f).coerceIn(14.dp.toPx(), 44.dp.toPx())
                } else {
                    20.dp.toPx()
                }
                if (midShoulder != null) {
                    drawLine(
                        color = lineColor,
                        start = headCenter,
                        end = midShoulder,
                        strokeWidth = 3.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
                drawCircle(
                    color = accentColor.copy(alpha = 0.22f * alpha),
                    radius = headRadius + 3.dp.toPx(),
                    center = headCenter
                )
                drawCircle(
                    color = lineColor,
                    radius = headRadius,
                    center = headCenter,
                    style = Stroke(width = 3.dp.toPx())
                )
            }

            // Draw all SLS bone connections (arms, hands, torso, legs, feet)
            for ((startType, endType) in SlsSkeletonTopology.boneConnections) {
                val startJoint = joints[startType] ?: continue
                val endJoint = joints[endType] ?: continue
                val p1 = project(startJoint.normX, startJoint.normY)
                val p2 = project(endJoint.normX, endJoint.normY)
                val isExtremity = startType.name.contains("WRIST") ||
                    startType.name.contains("ANKLE") ||
                    startType.name.contains("HEEL")

                drawLine(
                    color = glowColor,
                    start = p1,
                    end = p2,
                    strokeWidth = if (isExtremity) 5.dp.toPx() else 8.dp.toPx(),
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = lineColor,
                    start = p1,
                    end = p2,
                    strokeWidth = if (isExtremity) 2.2.dp.toPx() else 3.2.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            // Draw glowing joint nodes
            for ((type, joint) in joints) {
                if (type == SlsJointType.LEFT_EYE || type == SlsJointType.RIGHT_EYE ||
                    type == SlsJointType.LEFT_EAR || type == SlsJointType.RIGHT_EAR
                ) continue
                val pt = project(joint.normX, joint.normY)
                val isFineJoint = type.name.contains("INDEX") ||
                    type.name.contains("PINKY") ||
                    type.name.contains("THUMB") ||
                    type.name.contains("HEEL") ||
                    type.name.contains("FOOT")
                val innerRadius = if (isFineJoint) 2.8.dp.toPx() else 4.5.dp.toPx()
                val outerRadius = if (isFineJoint) 4.4.dp.toPx() else 6.5.dp.toPx()
                drawCircle(
                    color = Color.White.copy(alpha = alpha),
                    radius = innerRadius,
                    center = pt
                )
                drawCircle(
                    color = lineColor,
                    radius = outerRadius,
                    center = pt,
                    style = Stroke(width = 1.8.dp.toPx())
                )
            }
        }
    }
}
