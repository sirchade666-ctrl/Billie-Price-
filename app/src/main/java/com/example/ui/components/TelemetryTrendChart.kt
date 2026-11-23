package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.AudioClipEntity
import com.example.data.local.FlaggedAudioSegmentEntity
import com.example.data.local.SensorTrendSampleEntity
import com.example.data.model.AudioMetadataTag
import com.example.data.model.TrendTimeScale
import com.example.engine.ClipAutoTagEngine
import com.example.ui.theme.AnomalyCrimson
import com.example.ui.theme.ElevatedCard
import com.example.ui.theme.FranksAmber
import com.example.ui.theme.GeoportTeal
import com.example.ui.theme.PhosphorCyan
import com.example.ui.theme.SubtleBorder
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VoidBlack
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

private data class CorrelatedAudioMarker(
    val timestampMs: Long,
    val title: String,
    val subtitle: String,
    val tags: List<String>,
    val accentColor: Color,
    val clip: AudioClipEntity?
)

/**
 * Interactive dual-axis historical trend line chart for EMF (μT) and Temperature (°C)
 * with selectable time scales (1M, 5M, 15M, 1H, All), toggleable series, touch crosshair
 * inspection tooltip, and correlated audio clip / EVP event pins along the timeline.
 */
@Composable
fun TelemetryTrendChartCard(
    samples: List<SensorTrendSampleEntity>,
    audioClips: List<AudioClipEntity>,
    flaggedSegments: List<FlaggedAudioSegmentEntity> = emptyList(),
    tempSourceLabel: String = "Device Thermal Probe",
    initialScale: TrendTimeScale = TrendTimeScale.FIVE_MIN,
    title: String = "EMF & TEMPERATURE TREND",
    onInspectAudioClip: ((AudioClipEntity) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var selectedScale by remember(initialScale) { mutableStateOf(initialScale) }
    var showEmfSeries by remember { mutableStateOf(true) }
    var showTempSeries by remember { mutableStateOf(true) }
    var showAudioEvents by remember { mutableStateOf(true) }
    var scrubFraction by remember { mutableStateOf<Float?>(null) }

    val sortedSamples = remember(samples) { samples.sortedBy { it.timestampMs } }
    val referenceEndMs = remember(sortedSamples, audioClips) {
        maxOf(
            sortedSamples.lastOrNull()?.timestampMs ?: 0L,
            audioClips.maxOfOrNull { it.createdAtMs } ?: 0L
        )
    }

    val filteredSamples = remember(sortedSamples, selectedScale, referenceEndMs) {
        val window = selectedScale.windowMs
        if (window == null || sortedSamples.isEmpty()) {
            sortedSamples
        } else {
            val cutoff = referenceEndMs - window
            sortedSamples.filter { it.timestampMs >= cutoff }
        }
    }

    val windowRange = remember(filteredSamples, selectedScale, referenceEndMs) {
        if (filteredSamples.isEmpty()) {
            0L to 1L
        } else {
            val start = filteredSamples.first().timestampMs
            val end = filteredSamples.last().timestampMs.coerceAtLeast(start + 1000L)
            start to end
        }
    }

    val correlatedMarkers = remember(audioClips, flaggedSegments, windowRange, filteredSamples) {
        if (filteredSamples.isEmpty()) {
            emptyList()
        } else {
            val (minT, maxT) = windowRange
            val padMs = ((maxT - minT) * 0.05).toLong().coerceAtLeast(5_000L)
            val clipMarkers = audioClips
                .filter { it.createdAtMs in (minT - padMs)..(maxT + padMs) }
                .map { clip ->
                    val parsedTags = ClipAutoTagEngine.parseTags(clip.autoTags)
                    val color = when {
                        parsedTags.contains(AudioMetadataTag.SHARP_TEMP_DROP.label) -> TempIceBlue
                        parsedTags.contains(AudioMetadataTag.HIGH_SPEECH_PROB.label) -> SpeechViolet
                        parsedTags.contains(AudioMetadataTag.HIGH_EMF.label) -> AnomalyCrimson
                        else -> GeoportTeal
                    }
                    CorrelatedAudioMarker(
                        timestampMs = clip.createdAtMs.coerceIn(minT, maxT),
                        title = clip.fileName,
                        subtitle = "${clip.modeLabel} • Peak ${"%.1f".format(Locale.US, clip.peakEmfDuringClip)} μT",
                        tags = parsedTags,
                        accentColor = color,
                        clip = clip
                    )
                }

            val segMarkers = flaggedSegments
                .filter { it.timestampMs in (minT - padMs)..(maxT + padMs) }
                .map { seg ->
                    val parentClip = audioClips.firstOrNull { it.id == seg.clipId }
                    CorrelatedAudioMarker(
                        timestampMs = seg.timestampMs.coerceIn(minT, maxT),
                        title = seg.acousticClass,
                        subtitle = "EVP+EMF • SNR +${"%.1f".format(Locale.US, seg.snrDb)} dB • Δ${"%.1f".format(Locale.US, seg.emfDeltaUt)} μT",
                        tags = listOf(AudioMetadataTag.EVP_EMF_COINCIDENCE.label),
                        accentColor = FranksAmber,
                        clip = parentClip
                    )
                }

            (clipMarkers + segMarkers).sortedBy { it.timestampMs }
        }
    }

    val emfMin = remember(filteredSamples) {
        (filteredSamples.minOfOrNull { it.emfMagnitudeUt } ?: 0f).let { (it - 2f).coerceAtLeast(0f) }
    }
    val emfMax = remember(filteredSamples, emfMin) {
        (filteredSamples.maxOfOrNull { it.emfMagnitudeUt } ?: 60f).let { maxOf(it + 2f, emfMin + 5f) }
    }
    val tempMin = remember(filteredSamples) {
        (filteredSamples.minOfOrNull { it.temperatureC } ?: 18f) - 1.0f
    }
    val tempMax = remember(filteredSamples, tempMin) {
        (filteredSamples.maxOfOrNull { it.temperatureC } ?: 28f).let { maxOf(it + 1.0f, tempMin + 2.0f) }
    }

    val inspectedSample = remember(scrubFraction, filteredSamples, windowRange) {
        val frac = scrubFraction ?: return@remember null
        if (filteredSamples.isEmpty()) return@remember null
        val targetMs = windowRange.first + ((windowRange.second - windowRange.first) * frac).toLong()
        filteredSamples.minByOrNull { abs(it.timestampMs - targetMs) }
    }

    val inspectedMarker = remember(scrubFraction, correlatedMarkers, windowRange, showAudioEvents) {
        val frac = scrubFraction ?: return@remember null
        if (!showAudioEvents || correlatedMarkers.isEmpty()) return@remember null
        val span = (windowRange.second - windowRange.first).coerceAtLeast(1L)
        val targetMs = windowRange.first + (span * frac).toLong()
        val toleranceMs = (span * 0.08).toLong().coerceAtLeast(4_000L)
        correlatedMarkers
            .minByOrNull { abs(it.timestampMs - targetMs) }
            ?.takeIf { abs(it.timestampMs - targetMs) <= toleranceMs }
    }

    val timeFmt = remember(selectedScale) {
        val pattern = if (selectedScale == TrendTimeScale.ONE_HOUR || selectedScale == TrendTimeScale.ALL) {
            "HH:mm"
        } else {
            "HH:mm:ss"
        }
        SimpleDateFormat(pattern, Locale.getDefault())
    }
    val detailTimeFmt = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val chartCd = stringResource(R.string.cd_telemetry_trend_chart)

    InstrumentCard(modifier = modifier.testTag("telemetry_trend_chart_card")) {
        // Header + Temp Source
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ShowChart,
                    contentDescription = null,
                    tint = PhosphorCyan,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "$tempSourceLabel • ${filteredSamples.size} pts • ${correlatedMarkers.size} audio event(s)",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("telemetry_trend_subtitle")
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Time Scale Toggle Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "SCALE",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted
            )
            TrendTimeScale.entries.forEach { scale ->
                val isSelected = selectedScale == scale
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        selectedScale = scale
                        scrubFraction = null
                    },
                    label = {
                        Text(
                            text = scale.label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = PhosphorCyan.copy(alpha = 0.22f),
                        selectedLabelColor = PhosphorCyan
                    ),
                    modifier = Modifier.testTag("trend_scale_${scale.name.lowercase()}")
                )
            }
        }

        // Interactive Series Legend Toggles
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = showEmfSeries,
                onClick = { showEmfSeries = !showEmfSeries },
                leadingIcon = {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(PhosphorCyan)
                    )
                },
                label = { Text("EMF (μT)", style = MaterialTheme.typography.labelSmall) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = PhosphorCyan.copy(alpha = 0.16f),
                    selectedLabelColor = PhosphorCyan
                ),
                modifier = Modifier.testTag("trend_toggle_emf")
            )
            FilterChip(
                selected = showTempSeries,
                onClick = { showTempSeries = !showTempSeries },
                leadingIcon = {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(FranksAmber)
                    )
                },
                label = { Text("Temp (°C)", style = MaterialTheme.typography.labelSmall) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = FranksAmber.copy(alpha = 0.16f),
                    selectedLabelColor = FranksAmber
                ),
                modifier = Modifier.testTag("trend_toggle_temp")
            )
            FilterChip(
                selected = showAudioEvents,
                onClick = { showAudioEvents = !showAudioEvents },
                leadingIcon = {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(SpeechViolet)
                    )
                },
                label = {
                    Text(
                        "Audio (${correlatedMarkers.size})",
                        style = MaterialTheme.typography.labelSmall
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = SpeechViolet.copy(alpha = 0.16f),
                    selectedLabelColor = SpeechViolet
                ),
                modifier = Modifier.testTag("trend_toggle_audio")
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Chart Canvas + Dual Y-Axis Labels
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(210.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(VoidBlack)
                .border(1.dp, SubtleBorder, RoundedCornerShape(12.dp))
                .semantics { contentDescription = chartCd }
                .testTag("telemetry_trend_canvas")
        ) {
            if (filteredSamples.isEmpty()) {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Collecting EMF & Temperature History...",
                        style = MaterialTheme.typography.labelLarge,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Real-time magnetometer and thermal samples appear here every 2 seconds and correlate automatically with audio recordings.",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                }
            } else {
                // Dual-axis Canvas with tap & drag crosshair scrubbing
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 42.dp, end = 42.dp, top = 16.dp, bottom = 24.dp)
                        .pointerInput(filteredSamples, windowRange) {
                            detectTapGestures { offset ->
                                val w = size.width.toFloat().coerceAtLeast(1f)
                                scrubFraction = (offset.x / w).coerceIn(0f, 1f)
                            }
                        }
                        .pointerInput(filteredSamples, windowRange) {
                            detectHorizontalDragGestures(
                                onDragEnd = { },
                                onHorizontalDrag = { change, _ ->
                                    val w = size.width.toFloat().coerceAtLeast(1f)
                                    scrubFraction = (change.position.x / w).coerceIn(0f, 1f)
                                }
                            )
                        }
                ) {
                    val w = size.width
                    val h = size.height
                    val (minT, maxT) = windowRange
                    val spanT = (maxT - minT).coerceAtLeast(1L).toFloat()
                    val emfSpan = (emfMax - emfMin).coerceAtLeast(1f)
                    val tempSpan = (tempMax - tempMin).coerceAtLeast(0.5f)

                    // Horizontal dashed gridlines
                    val gridColor = Color(0xFF1B2734)
                    val dash = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
                    for (i in 0..4) {
                        val y = h * (i / 4f)
                        drawLine(
                            color = gridColor,
                            start = Offset(0f, y),
                            end = Offset(w, y),
                            strokeWidth = 1f,
                            pathEffect = if (i in 1..3) dash else null
                        )
                    }

                    // Correlated audio event vertical markers (behind curves)
                    if (showAudioEvents) {
                        correlatedMarkers.forEach { marker ->
                            val x = (((marker.timestampMs - minT) / spanT).coerceIn(0f, 1f)) * w
                            drawLine(
                                color = marker.accentColor.copy(alpha = 0.55f),
                                start = Offset(x, 0f),
                                end = Offset(x, h),
                                strokeWidth = 2f,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 5f))
                            )
                            drawCircle(
                                color = marker.accentColor,
                                radius = 6f,
                                center = Offset(x, 6f)
                            )
                            drawCircle(
                                color = marker.accentColor.copy(alpha = 0.25f),
                                radius = 11f,
                                center = Offset(x, 6f)
                            )
                        }
                    }

                    // Plot EMF series (Left Y-axis)
                    if (showEmfSeries) {
                        if (filteredSamples.size == 1) {
                            val pt = filteredSamples[0]
                            val y = h - ((pt.emfMagnitudeUt - emfMin) / emfSpan).coerceIn(0f, 1f) * h
                            drawCircle(PhosphorCyan, radius = 6f, center = Offset(w / 2f, y))
                        } else {
                            val linePath = Path()
                            val fillPath = Path()
                            filteredSamples.forEachIndexed { idx, pt ->
                                val x = (((pt.timestampMs - minT) / spanT).coerceIn(0f, 1f)) * w
                                val y = h - ((pt.emfMagnitudeUt - emfMin) / emfSpan).coerceIn(0f, 1f) * h
                                if (idx == 0) {
                                    linePath.moveTo(x, y)
                                    fillPath.moveTo(x, h)
                                    fillPath.lineTo(x, y)
                                } else {
                                    linePath.lineTo(x, y)
                                    fillPath.lineTo(x, y)
                                }
                                if (idx == filteredSamples.lastIndex) {
                                    fillPath.lineTo(x, h)
                                    fillPath.close()
                                }
                            }
                            drawPath(
                                path = fillPath,
                                brush = Brush.verticalGradient(
                                    colors = listOf(PhosphorCyan.copy(alpha = 0.20f), Color.Transparent)
                                )
                            )
                            drawPath(
                                path = linePath,
                                color = PhosphorCyan,
                                style = Stroke(width = 3f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                            )
                        }
                    }

                    // Plot Temperature series (Right Y-axis)
                    if (showTempSeries) {
                        if (filteredSamples.size == 1) {
                            val pt = filteredSamples[0]
                            val y = h - ((pt.temperatureC - tempMin) / tempSpan).coerceIn(0f, 1f) * h
                            drawCircle(FranksAmber, radius = 6f, center = Offset(w / 2f, y))
                        } else {
                            val tempPath = Path()
                            val tempFill = Path()
                            filteredSamples.forEachIndexed { idx, pt ->
                                val x = (((pt.timestampMs - minT) / spanT).coerceIn(0f, 1f)) * w
                                val y = h - ((pt.temperatureC - tempMin) / tempSpan).coerceIn(0f, 1f) * h
                                if (idx == 0) {
                                    tempPath.moveTo(x, y)
                                    tempFill.moveTo(x, h)
                                    tempFill.lineTo(x, y)
                                } else {
                                    tempPath.lineTo(x, y)
                                    tempFill.lineTo(x, y)
                                }
                                if (idx == filteredSamples.lastIndex) {
                                    tempFill.lineTo(x, h)
                                    tempFill.close()
                                }
                            }
                            drawPath(
                                path = tempFill,
                                brush = Brush.verticalGradient(
                                    colors = listOf(FranksAmber.copy(alpha = 0.14f), Color.Transparent)
                                )
                            )
                            drawPath(
                                path = tempPath,
                                color = FranksAmber,
                                style = Stroke(width = 2.8f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                            )

                            // Mark sharp temperature drops with ice-blue points
                            filteredSamples.forEach { pt ->
                                if (pt.tempDeltaC <= -1.5f) {
                                    val x = (((pt.timestampMs - minT) / spanT).coerceIn(0f, 1f)) * w
                                    val y = h - ((pt.temperatureC - tempMin) / tempSpan).coerceIn(0f, 1f) * h
                                    drawCircle(TempIceBlue, radius = 5f, center = Offset(x, y))
                                }
                            }
                        }
                    }

                    // Active Crosshair Scrub Indicator
                    val activeSample = inspectedSample
                    if (activeSample != null) {
                        val cx = (((activeSample.timestampMs - minT) / spanT).coerceIn(0f, 1f)) * w
                        drawLine(
                            color = Color.White.copy(alpha = 0.7f),
                            start = Offset(cx, 0f),
                            end = Offset(cx, h),
                            strokeWidth = 1.6f
                        )
                        if (showEmfSeries) {
                            val ey = h - ((activeSample.emfMagnitudeUt - emfMin) / emfSpan).coerceIn(0f, 1f) * h
                            drawCircle(PhosphorCyan, radius = 6.5f, center = Offset(cx, ey))
                            drawCircle(Color.White, radius = 2.8f, center = Offset(cx, ey))
                        }
                        if (showTempSeries) {
                            val ty = h - ((activeSample.temperatureC - tempMin) / tempSpan).coerceIn(0f, 1f) * h
                            drawCircle(FranksAmber, radius = 6.5f, center = Offset(cx, ty))
                            drawCircle(Color.White, radius = 2.8f, center = Offset(cx, ty))
                        }
                    }
                }

                // Left Y-Axis Labels (EMF μT)
                if (showEmfSeries) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 4.dp, top = 12.dp, bottom = 22.dp)
                            .height(176.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${"%.0f".format(Locale.US, emfMax)}μT",
                            style = MaterialTheme.typography.labelSmall,
                            color = PhosphorCyan
                        )
                        Text(
                            text = "${"%.0f".format(Locale.US, (emfMax + emfMin) / 2f)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = PhosphorCyan.copy(alpha = 0.7f)
                        )
                        Text(
                            text = "${"%.0f".format(Locale.US, emfMin)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = PhosphorCyan.copy(alpha = 0.7f)
                        )
                    }
                }

                // Right Y-Axis Labels (Temp °C)
                if (showTempSeries) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 4.dp, top = 12.dp, bottom = 22.dp)
                            .height(176.dp),
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${"%.1f".format(Locale.US, tempMax)}°",
                            style = MaterialTheme.typography.labelSmall,
                            color = FranksAmber
                        )
                        Text(
                            text = "${"%.1f".format(Locale.US, (tempMax + tempMin) / 2f)}°",
                            style = MaterialTheme.typography.labelSmall,
                            color = FranksAmber.copy(alpha = 0.7f)
                        )
                        Text(
                            text = "${"%.1f".format(Locale.US, tempMin)}°",
                            style = MaterialTheme.typography.labelSmall,
                            color = FranksAmber.copy(alpha = 0.7f)
                        )
                    }
                }

                // Bottom X-Axis Time Ticks
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(start = 42.dp, end = 42.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val midT = (windowRange.first + windowRange.second) / 2L
                    Text(
                        text = timeFmt.format(Date(windowRange.first)),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                    Text(
                        text = timeFmt.format(Date(midT)),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                    Text(
                        text = timeFmt.format(Date(windowRange.second)),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                }
            }
        }

        // Crosshair Inspection Tooltip Box
        if (inspectedSample != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(ElevatedCard)
                    .border(1.dp, PhosphorCyan.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                    .padding(10.dp)
                    .testTag("trend_tooltip_panel")
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "INSPECTED @ ${detailTimeFmt.format(Date(inspectedSample.timestampMs))}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Tap chart to scrub",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val emfSign = if (inspectedSample.emfDeltaUt >= 0f) "+" else ""
                    Text(
                        text = "EMF: ${"%.2f".format(Locale.US, inspectedSample.emfMagnitudeUt)} μT ($emfSign${"%.1f".format(Locale.US, inspectedSample.emfDeltaUt)})",
                        style = MaterialTheme.typography.labelMedium,
                        color = PhosphorCyan,
                        fontWeight = FontWeight.SemiBold
                    )
                    val tSign = if (inspectedSample.tempDeltaC >= 0f) "+" else ""
                    Text(
                        text = "Temp: ${"%.1f".format(Locale.US, inspectedSample.temperatureC)}°C ($tSign${"%.1f".format(Locale.US, inspectedSample.tempDeltaC)}°)",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (inspectedSample.tempDeltaC <= -1.5f) TempIceBlue else FranksAmber,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                if (inspectedMarker != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AudioFile,
                                contentDescription = null,
                                tint = inspectedMarker.accentColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(
                                    text = inspectedMarker.title,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = inspectedMarker.accentColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = inspectedMarker.subtitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        if (inspectedMarker.clip != null && onInspectAudioClip != null) {
                            Spacer(modifier = Modifier.width(8.dp))
                            FilledTonalButton(
                                onClick = { onInspectAudioClip(inspectedMarker.clip) },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.testTag("trend_inspect_audio_button")
                            ) {
                                Icon(Icons.Filled.Waves, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Inspect", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    if (inspectedMarker.tags.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        ClipAutoTagFlow(rawTags = inspectedMarker.tags.joinToString(","))
                    }
                }
            }
        }

        // Window Summary Strip
        if (filteredSamples.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            val peakEmf = filteredSamples.maxOf { it.emfMagnitudeUt }
            val minTemp = filteredSamples.minOf { it.temperatureC }
            val maxTemp = filteredSamples.maxOf { it.temperatureC }
            val maxDrop = filteredSamples.minOf { it.tempDeltaC }.coerceAtMost(0f)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TrendStatPill(
                    label = "Peak EMF",
                    value = "${"%.1f".format(Locale.US, peakEmf)} μT",
                    color = PhosphorCyan,
                    modifier = Modifier.weight(1f)
                )
                TrendStatPill(
                    label = "Temp Span",
                    value = "${"%.1f".format(Locale.US, minTemp)}–${"%.1f".format(Locale.US, maxTemp)}°C",
                    color = FranksAmber,
                    modifier = Modifier.weight(1f)
                )
                TrendStatPill(
                    label = "Max Drop",
                    value = "${"%.1f".format(Locale.US, maxDrop)}°C",
                    color = if (maxDrop <= -1.5f) TempIceBlue else GeoportTeal,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun TrendStatPill(
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
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = color,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}
