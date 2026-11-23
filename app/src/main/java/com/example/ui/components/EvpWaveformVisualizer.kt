package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.engine.EvpWaveformData
import com.example.engine.EvpWaveformExtractor
import com.example.engine.WaveformHighlightRegion
import com.example.ui.theme.AnomalyCrimson
import com.example.ui.theme.ElevatedCard
import com.example.ui.theme.FranksAmber
import com.example.ui.theme.GeoportTeal
import com.example.ui.theme.LocalNightVisionEnabled
import com.example.ui.theme.PhosphorCyan
import com.example.ui.theme.SubtleBorder
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VoidBlack
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

fun formatPreciseTimeMs(ms: Long): String {
    val safe = ms.coerceAtLeast(0L)
    val totalSec = safe / 1000L
    val mins = totalSec / 60L
    val secs = totalSec % 60L
    val millis = safe % 1000L
    return String.format(Locale.US, "%02d:%02d.%03d", mins, secs, millis)
}

fun formatShortTimeMs(ms: Long): String {
    val safe = ms.coerceAtLeast(0L)
    val totalSec = safe / 1000L
    val mins = totalSec / 60L
    val secs = totalSec % 60L
    val tenths = (safe % 1000L) / 100L
    return String.format(Locale.US, "%02d:%02d.%d", mins, secs, tenths)
}

@Composable
fun ZoomableEvpWaveformPanel(
    waveformData: EvpWaveformData?,
    positionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    flaggedRegions: List<WaveformHighlightRegion> = emptyList(),
    compact: Boolean = true,
    onSeekMs: (Long) -> Unit,
    onLoopRangeChange: (LongRange?) -> Unit = {},
    onTogglePlayPause: (() -> Unit)? = null,
    onExpandClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val nightVision = LocalNightVisionEnabled.current
    val primaryWaveColor = if (nightVision) MaterialTheme.colorScheme.primary else PhosphorCyan
    val silenceTintColor = if (nightVision) MaterialTheme.colorScheme.tertiary else GeoportTeal
    val warnColor = if (nightVision) MaterialTheme.colorScheme.secondary else FranksAmber
    val peakColor = if (nightVision) MaterialTheme.colorScheme.primary else AnomalyCrimson
    val speechHighlightColor = if (nightVision) MaterialTheme.colorScheme.secondary else Color(0xFFC084FC)
    val canvasBg = if (nightVision) MaterialTheme.colorScheme.background else VoidBlack
    val borderCol = if (nightVision) MaterialTheme.colorScheme.outline else SubtleBorder

    val effectiveDurationMs = max(durationMs, waveformData?.durationMs ?: 1L).coerceAtLeast(1L)

    var zoomLevel by remember(waveformData?.filePath) { mutableFloatStateOf(1f) }
    var viewStartFrac by remember(waveformData?.filePath) { mutableFloatStateOf(0f) }
    var verticalGain by remember(waveformData?.filePath) { mutableFloatStateOf(1f) }
    var loopVisibleWindow by remember(waveformData?.filePath) { mutableStateOf(false) }
    var showPhoneticOverlay by remember(waveformData?.filePath) { mutableStateOf(true) }

    val viewSpanFrac = (1f / zoomLevel).coerceIn(0.04f, 1f)
    val clampedStartFrac = viewStartFrac.coerceIn(0f, (1f - viewSpanFrac).coerceAtLeast(0f))
    val viewEndFrac = (clampedStartFrac + viewSpanFrac).coerceAtMost(1f)

    val viewStartMs = (clampedStartFrac * effectiveDurationMs).toLong()
    val viewEndMs = (viewEndFrac * effectiveDurationMs).toLong().coerceAtLeast(viewStartMs + 1L)

    // Keep zoomed viewport following the active playhead smoothly when playing
    LaunchedEffect(positionMs, isPlaying, zoomLevel) {
        if (isPlaying && zoomLevel > 1.05f && !loopVisibleWindow) {
            val posFrac = (positionMs.toFloat() / effectiveDurationMs.toFloat()).coerceIn(0f, 1f)
            if (posFrac < clampedStartFrac || posFrac > viewEndFrac) {
                viewStartFrac = (posFrac - viewSpanFrac * 0.15f).coerceIn(0f, (1f - viewSpanFrac).coerceAtLeast(0f))
            }
        }
    }

    LaunchedEffect(loopVisibleWindow, viewStartMs, viewEndMs) {
        if (loopVisibleWindow) {
            onLoopRangeChange(viewStartMs..viewEndMs)
        } else {
            onLoopRangeChange(null)
        }
    }

    fun applyZoom(newZoom: Float, anchorFrac: Float? = null) {
        val targetZoom = newZoom.coerceIn(1f, 24f)
        val anchor = anchorFrac ?: (clampedStartFrac + viewSpanFrac / 2f)
        val newSpan = 1f / targetZoom
        val relOffset = ((anchor - clampedStartFrac) / viewSpanFrac).coerceIn(0f, 1f)
        val nextStart = (anchor - relOffset * newSpan).coerceIn(0f, (1f - newSpan).coerceAtLeast(0f))
        zoomLevel = targetZoom
        viewStartFrac = nextStart
    }

    fun jumpToRegion(region: WaveformHighlightRegion) {
        val regSpan = (region.endMs - region.startMs).coerceAtLeast(180L)
        val desiredSpanMs = (regSpan * 1.45f).coerceAtLeast(350f)
        val desiredZoom = (effectiveDurationMs.toFloat() / desiredSpanMs).coerceIn(2f, 20f)
        val newSpanFrac = 1f / desiredZoom
        val centerFrac = ((region.startMs + region.endMs) / 2f) / effectiveDurationMs.toFloat()
        zoomLevel = desiredZoom
        viewStartFrac = (centerFrac - newSpanFrac / 2f).coerceIn(0f, (1f - newSpanFrac).coerceAtLeast(0f))
        if (region.isSilence && verticalGain < 2f) {
            verticalGain = 4f
        }
        onSeekMs(region.startMs)
    }

    val visibleBins = remember(waveformData, clampedStartFrac, viewEndFrac, compact) {
        if (waveformData == null) {
            emptyList()
        } else {
            EvpWaveformExtractor.sliceVisibleWindow(
                bins = waveformData.bins,
                startFraction = clampedStartFrac,
                endFraction = viewEndFrac,
                maxDisplayBars = if (compact) 140 else 240
            )
        }
    }

    val playheadBinDb = remember(waveformData, positionMs, effectiveDurationMs) {
        if (waveformData == null || waveformData.bins.isEmpty()) null
        else {
            val frac = (positionMs.toFloat() / effectiveDurationMs.toFloat()).coerceIn(0f, 0.9999f)
            val idx = (frac * waveformData.bins.size).toInt().coerceIn(0, waveformData.bins.lastIndex)
            waveformData.bins[idx].peakDb
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ElevatedCard.copy(alpha = 0.55f))
            .border(1.dp, borderCol, RoundedCornerShape(12.dp))
            .padding(10.dp)
            .testTag(if (compact) "evp_waveform_inline_panel" else "evp_waveform_expanded_panel")
    ) {
        // Header: Readout, Zoom factor, Expand / Play buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.GraphicEq,
                        contentDescription = null,
                        tint = primaryWaveColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = formatPreciseTimeMs(positionMs),
                        style = MaterialTheme.typography.labelLarge,
                        color = primaryWaveColor,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.testTag("waveform_playhead_readout")
                    )
                    Text(
                        text = "/ ${formatShortTimeMs(effectiveDurationMs)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                    if (playheadBinDb != null) {
                        Text(
                            text = "${"%.0f".format(Locale.US, playheadBinDb)} dB",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (playheadBinDb > -14f) warnColor else TextSecondary
                        )
                    }
                }
                Text(
                    text = "Window ${formatShortTimeMs(viewStartMs)}–${formatShortTimeMs(viewEndMs)} • ${"%.1f".format(Locale.US, zoomLevel)}x Zoom • Gain ${verticalGain.toInt()}x",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("waveform_zoom_readout")
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onTogglePlayPause != null && !compact) {
                    IconButton(
                        onClick = onTogglePlayPause,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("waveform_dialog_play_pause")
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = "Play or pause audio",
                            tint = primaryWaveColor
                        )
                    }
                }
                if (onExpandClick != null) {
                    IconButton(
                        onClick = onExpandClick,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("expand_waveform_inspector_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Fullscreen,
                            contentDescription = "Expand waveform inspector",
                            tint = primaryWaveColor
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Main Interactive Waveform Canvas
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (compact) 136.dp else 230.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(canvasBg)
                .border(1.dp, borderCol, RoundedCornerShape(10.dp))
                .pointerInput(waveformData?.filePath, zoomLevel, clampedStartFrac, viewSpanFrac) {
                    detectTapGestures(
                        onTap = { tapOffset ->
                            val fracInView = (tapOffset.x / size.width.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
                            val targetFrac = (clampedStartFrac + fracInView * viewSpanFrac).coerceIn(0f, 1f)
                            onSeekMs((targetFrac * effectiveDurationMs).toLong())
                        },
                        onDoubleTap = { tapOffset ->
                            val fracInView = (tapOffset.x / size.width.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
                            val anchor = (clampedStartFrac + fracInView * viewSpanFrac).coerceIn(0f, 1f)
                            val nextZoom = if (zoomLevel < 2f) 4f else if (zoomLevel < 8f) 10f else 1f
                            applyZoom(nextZoom, anchor)
                        }
                    )
                }
                .then(
                    if (compact) {
                        Modifier.pointerInput(waveformData?.filePath, zoomLevel, clampedStartFrac, viewSpanFrac) {
                            detectHorizontalDragGestures { change, dragAmount ->
                                change.consume()
                                val w = size.width.toFloat().coerceAtLeast(1f)
                                if (zoomLevel > 1.05f) {
                                    val deltaFrac = -(dragAmount / w) * viewSpanFrac
                                    viewStartFrac = (viewStartFrac + deltaFrac)
                                        .coerceIn(0f, (1f - viewSpanFrac).coerceAtLeast(0f))
                                } else {
                                    val fracInView = (change.position.x / w).coerceIn(0f, 1f)
                                    onSeekMs((fracInView * effectiveDurationMs).toLong())
                                }
                            }
                        }
                    } else {
                        Modifier.pointerInput(waveformData?.filePath, zoomLevel, clampedStartFrac, viewSpanFrac) {
                            detectTransformGestures { centroid, pan, zoom, _ ->
                                val w = size.width.toFloat().coerceAtLeast(1f)
                                if (abs(zoom - 1f) > 0.005f) {
                                    val anchor = (clampedStartFrac + (centroid.x / w) * viewSpanFrac).coerceIn(0f, 1f)
                                    applyZoom(zoomLevel * zoom, anchor)
                                }
                                if (abs(pan.x) > 0.5f) {
                                    val currentSpan = 1f / zoomLevel
                                    if (zoomLevel > 1.05f) {
                                        val deltaFrac = -(pan.x / w) * currentSpan
                                        viewStartFrac = (viewStartFrac + deltaFrac)
                                            .coerceIn(0f, (1f - currentSpan).coerceAtLeast(0f))
                                    } else {
                                        val frac = (centroid.x / w).coerceIn(0f, 1f)
                                        onSeekMs((frac * effectiveDurationMs).toLong())
                                    }
                                }
                            }
                        }
                    }
                )
                .testTag("evp_waveform_canvas")
        ) {
            if (waveformData == null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = primaryWaveColor
                        )
                        Text(
                            text = "Decoding WAV envelope...",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextSecondary
                        )
                    }
                }
            } else {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val midY = h / 2f
                    val maxHalfH = (h * 0.44f)
                    val spanMs = (viewEndMs - viewStartMs).coerceAtLeast(1L).toFloat()

                    // 1. Subtle time-grid vertical lines
                    val gridSteps = 6
                    for (g in 1 until gridSteps) {
                        val gx = (g.toFloat() / gridSteps) * w
                        drawLine(
                            color = borderCol.copy(alpha = 0.45f),
                            start = Offset(gx, 0f),
                            end = Offset(gx, h),
                            strokeWidth = 1f
                        )
                    }

                    // 2. Highlight detected silence intervals (teal tint) & flagged EVP+EMF segments (crimson/amber)
                    for (sil in waveformData.silenceRegions) {
                        if (sil.endMs < viewStartMs || sil.startMs > viewEndMs) continue
                        val sx = (((sil.startMs - viewStartMs).coerceAtLeast(0L) / spanMs) * w).coerceIn(0f, w)
                        val ex = (((sil.endMs - viewStartMs).coerceAtMost(spanMs.toLong()) / spanMs) * w).coerceIn(sx + 2f, w)
                        drawRect(
                            color = silenceTintColor.copy(alpha = 0.10f),
                            topLeft = Offset(sx, 0f),
                            size = Size(ex - sx, h)
                        )
                    }

                    for (flag in flaggedRegions) {
                        if (flag.endMs < viewStartMs || flag.startMs > viewEndMs) continue
                        val sx = (((flag.startMs - viewStartMs).coerceAtLeast(0L) / spanMs) * w).coerceIn(0f, w)
                        val ex = (((flag.endMs - viewStartMs).coerceAtMost(spanMs.toLong()) / spanMs) * w).coerceIn(sx + 2f, w)
                        drawRect(
                            color = peakColor.copy(alpha = 0.18f),
                            topLeft = Offset(sx, 0f),
                            size = Size(ex - sx, h)
                        )
                        drawRect(
                            color = peakColor.copy(alpha = 0.85f),
                            topLeft = Offset(sx, 0f),
                            size = Size(ex - sx, 4f)
                        )
                    }

                    // 2b. Highlight potential "spirit speech" & repeated phonetic pattern segments
                    if (showPhoneticOverlay) {
                        for (seg in waveformData.phoneticSegments) {
                            if (seg.endMs < viewStartMs || seg.startMs > viewEndMs) continue
                            val sx = (((seg.startMs - viewStartMs).coerceAtLeast(0L) / spanMs) * w).coerceIn(0f, w)
                            val ex = (((seg.endMs - viewStartMs).coerceAtMost(spanMs.toLong()) / spanMs) * w).coerceIn(sx + 3f, w)
                            val fillAlpha = if (seg.isRepeatedPattern) 0.22f else 0.12f
                            drawRect(
                                color = speechHighlightColor.copy(alpha = fillAlpha),
                                topLeft = Offset(sx, 0f),
                                size = Size(ex - sx, h)
                            )
                            val barH = if (seg.isRepeatedPattern) 5f else 3f
                            drawRect(
                                color = speechHighlightColor.copy(alpha = if (seg.isRepeatedPattern) 0.95f else 0.72f),
                                topLeft = Offset(sx, h - barH),
                                size = Size(ex - sx, barH)
                            )
                            if (seg.isRepeatedPattern) {
                                drawLine(
                                    color = speechHighlightColor.copy(alpha = 0.7f),
                                    start = Offset(sx, 0f),
                                    end = Offset(sx, h),
                                    strokeWidth = 1.4f
                                )
                                drawLine(
                                    color = speechHighlightColor.copy(alpha = 0.7f),
                                    start = Offset(ex, 0f),
                                    end = Offset(ex, h),
                                    strokeWidth = 1.4f
                                )
                            }
                        }
                    }

                    // 3. Center zero axis & dashed noise-floor envelope threshold
                    drawLine(
                        color = borderCol.copy(alpha = 0.85f),
                        start = Offset(0f, midY),
                        end = Offset(w, midY),
                        strokeWidth = 1f
                    )

                    val noiseOffset = (waveformData.noiseFloorNorm * verticalGain).coerceIn(0.02f, 0.92f) * maxHalfH
                    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
                    drawLine(
                        color = silenceTintColor.copy(alpha = 0.48f),
                        start = Offset(0f, midY - noiseOffset),
                        end = Offset(w, midY - noiseOffset),
                        strokeWidth = 1.2f,
                        pathEffect = dashEffect
                    )
                    drawLine(
                        color = silenceTintColor.copy(alpha = 0.48f),
                        start = Offset(0f, midY + noiseOffset),
                        end = Offset(w, midY + noiseOffset),
                        strokeWidth = 1.2f,
                        pathEffect = dashEffect
                    )

                    // 4. Bipolar Peak & RMS Waveform Bars
                    if (visibleBins.isNotEmpty()) {
                        val step = w / visibleBins.size.toFloat()
                        val barW = (step * 0.72f).coerceIn(1.5f, 10f)
                        val highThreshold = max(waveformData.noiseFloorNorm * 3.8f, 0.58f)
                        val midThreshold = max(waveformData.noiseFloorNorm * 2.2f, 0.25f)

                        visibleBins.forEachIndexed { idx, bin ->
                            val x = idx * step + (step - barW) / 2f
                            val scaledPeak = (bin.peakNorm * verticalGain).coerceIn(0.02f, 1f)
                            val scaledRms = (bin.rmsNorm * verticalGain).coerceIn(0.015f, scaledPeak)

                            val peakHalf = scaledPeak * maxHalfH
                            val rmsHalf = scaledRms * maxHalfH

                            val barColor = when {
                                bin.peakNorm >= highThreshold -> peakColor
                                bin.peakNorm >= midThreshold -> warnColor
                                bin.rmsNorm <= waveformData.noiseFloorNorm * 1.35f -> silenceTintColor.copy(alpha = 0.72f)
                                else -> primaryWaveColor
                            }

                            // Outer peak transient bar
                            drawRoundRect(
                                color = barColor.copy(alpha = 0.55f),
                                topLeft = Offset(x, midY - peakHalf),
                                size = Size(barW, peakHalf * 2f),
                                cornerRadius = CornerRadius(2f, 2f)
                            )
                            // Inner denser RMS formant body
                            drawRoundRect(
                                color = barColor,
                                topLeft = Offset(x, midY - rmsHalf),
                                size = Size(barW, (rmsHalf * 2f).coerceAtLeast(2f)),
                                cornerRadius = CornerRadius(2f, 2f)
                            )
                        }
                    }

                    // 5. Playhead Cursor Needle
                    if (positionMs in viewStartMs..viewEndMs) {
                        val px = (((positionMs - viewStartMs) / spanMs) * w).coerceIn(0f, w)
                        drawLine(
                            color = Color.White,
                            start = Offset(px, 0f),
                            end = Offset(px, h),
                            strokeWidth = 2.5f
                        )
                        val cap = Path().apply {
                            moveTo(px - 6f, 0f)
                            lineTo(px + 6f, 0f)
                            lineTo(px, 9f)
                            close()
                        }
                        drawPath(cap, color = Color.White)
                    }
                }
            }
        }

        // Overview Minimap Pan Scrubber (shown whenever waveform is decoded)
        if (waveformData != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(22.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(canvasBg.copy(alpha = 0.9f))
                    .border(1.dp, borderCol.copy(alpha = 0.8f), RoundedCornerShape(6.dp))
                    .pointerInput(waveformData.filePath, zoomLevel, viewSpanFrac) {
                        detectTapGestures { offset ->
                            val targetCenter = (offset.x / size.width.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
                            if (zoomLevel > 1.05f) {
                                viewStartFrac = (targetCenter - viewSpanFrac / 2f)
                                    .coerceIn(0f, (1f - viewSpanFrac).coerceAtLeast(0f))
                            } else {
                                onSeekMs((targetCenter * effectiveDurationMs).toLong())
                            }
                        }
                    }
                    .pointerInput(waveformData.filePath, zoomLevel, viewSpanFrac) {
                        detectHorizontalDragGestures { change, _ ->
                            change.consume()
                            val targetCenter = (change.position.x / size.width.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
                            if (zoomLevel > 1.05f) {
                                viewStartFrac = (targetCenter - viewSpanFrac / 2f)
                                    .coerceIn(0f, (1f - viewSpanFrac).coerceAtLeast(0f))
                            } else {
                                onSeekMs((targetCenter * effectiveDurationMs).toLong())
                            }
                        }
                    }
                    .testTag("waveform_minimap_scrubber")
            ) {
                val miniBins = remember(waveformData) {
                    EvpWaveformExtractor.sliceVisibleWindow(waveformData.bins, 0f, 1f, 96)
                }
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val step = w / miniBins.size.coerceAtLeast(1)
                    miniBins.forEachIndexed { idx, b ->
                        val bh = (b.peakNorm * h * 0.85f).coerceAtLeast(2f)
                        drawRect(
                            color = primaryWaveColor.copy(alpha = 0.45f),
                            topLeft = Offset(idx * step, (h - bh) / 2f),
                            size = Size((step * 0.8f).coerceAtLeast(1f), bh)
                        )
                    }
                    // Highlight zoomed viewport bracket
                    val vx = clampedStartFrac * w
                    val vw = (viewSpanFrac * w).coerceAtLeast(10f)
                    drawRect(
                        color = primaryWaveColor.copy(alpha = 0.20f),
                        topLeft = Offset(vx, 0f),
                        size = Size(vw, h)
                    )
                    drawRect(
                        color = primaryWaveColor,
                        topLeft = Offset(vx, 0f),
                        size = Size(vw, 2f)
                    )
                    drawRect(
                        color = primaryWaveColor,
                        topLeft = Offset(vx, h - 2f),
                        size = Size(vw, 2f)
                    )
                    // Phonetic spirit speech ticks along bottom edge of minimap
                    if (showPhoneticOverlay) {
                        for (seg in waveformData.phoneticSegments) {
                            val sx = ((seg.startMs.toFloat() / effectiveDurationMs.toFloat()).coerceIn(0f, 1f)) * w
                            val ex = ((seg.endMs.toFloat() / effectiveDurationMs.toFloat()).coerceIn(0f, 1f)) * w
                            drawRect(
                                color = speechHighlightColor.copy(alpha = if (seg.isRepeatedPattern) 0.95f else 0.65f),
                                topLeft = Offset(sx, h - 4f),
                                size = Size((ex - sx).coerceAtLeast(3f), 4f)
                            )
                        }
                    }
                    // Playhead tick on minimap
                    val px = ((positionMs.toFloat() / effectiveDurationMs.toFloat()).coerceIn(0f, 1f)) * w
                    drawLine(
                        color = Color.White,
                        start = Offset(px, 0f),
                        end = Offset(px, h),
                        strokeWidth = 2f
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Zoom Controls + Whisper Gain + Loop Visible Window
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { applyZoom(zoomLevel / 1.8f) },
                enabled = zoomLevel > 1.01f,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("waveform_zoom_out_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.ZoomOut,
                    contentDescription = "Zoom out waveform",
                    tint = if (zoomLevel > 1.01f) primaryWaveColor else TextMuted
                )
            }
            IconButton(
                onClick = { applyZoom(zoomLevel * 1.8f) },
                enabled = zoomLevel < 23.9f,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("waveform_zoom_in_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.ZoomIn,
                    contentDescription = "Zoom in waveform",
                    tint = if (zoomLevel < 23.9f) primaryWaveColor else TextMuted
                )
            }

            listOf(1f, 4f, 8f, 16f).forEach { preset ->
                val active = abs(zoomLevel - preset) < 0.35f
                FilterChip(
                    selected = active,
                    onClick = { applyZoom(preset) },
                    label = { Text("${preset.toInt()}x", style = MaterialTheme.typography.labelSmall) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = primaryWaveColor.copy(alpha = 0.22f),
                        selectedLabelColor = primaryWaveColor
                    ),
                    modifier = Modifier.testTag("waveform_zoom_preset_${preset.toInt()}x")
                )
            }

            // Visual amplitude gain for inspecting faint whispers inside silence intervals
            FilterChip(
                selected = verticalGain > 1.1f,
                onClick = {
                    verticalGain = when (verticalGain.toInt()) {
                        1 -> 2f
                        2 -> 4f
                        4 -> 6f
                        else -> 1f
                    }
                },
                label = {
                    Text(
                        text = if (verticalGain <= 1.1f) "Gain 1x" else "Whisper ${verticalGain.toInt()}x",
                        style = MaterialTheme.typography.labelSmall
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = silenceTintColor.copy(alpha = 0.22f),
                    selectedLabelColor = silenceTintColor
                ),
                modifier = Modifier.testTag("waveform_whisper_gain_chip")
            )

            FilterChip(
                selected = loopVisibleWindow,
                onClick = { loopVisibleWindow = !loopVisibleWindow },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Repeat,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                },
                label = { Text("Loop", style = MaterialTheme.typography.labelSmall) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = warnColor.copy(alpha = 0.22f),
                    selectedLabelColor = warnColor
                ),
                modifier = Modifier.testTag("waveform_loop_window_chip")
            )

            FilterChip(
                selected = showPhoneticOverlay,
                onClick = { showPhoneticOverlay = !showPhoneticOverlay },
                label = {
                    val count = waveformData?.phoneticSegments?.size ?: 0
                    Text(
                        text = if (count > 0) "Speech ($count)" else "Speech",
                        style = MaterialTheme.typography.labelSmall
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = speechHighlightColor.copy(alpha = 0.22f),
                    selectedLabelColor = speechHighlightColor
                ),
                modifier = Modifier.testTag("waveform_phonetic_overlay_chip")
            )

            if (zoomLevel > 1.05f || verticalGain > 1.1f) {
                IconButton(
                    onClick = {
                        zoomLevel = 1f
                        viewStartFrac = 0f
                        verticalGain = 1f
                        loopVisibleWindow = false
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("waveform_reset_zoom_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.ZoomOutMap,
                        contentDescription = "Reset zoom and gain",
                        tint = TextSecondary
                    )
                }
            }
        }

        // Potential "Spirit Speech" & Repeated Phonetic Patterns Strip
        val phoneticSegments = waveformData?.phoneticSegments.orEmpty()
        if (phoneticSegments.isNotEmpty()) {
            val repeatedCount = phoneticSegments.count { it.isRepeatedPattern }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (repeatedCount > 0) {
                    "Phonetic Analyzer: ${phoneticSegments.size} Spirit Speech • $repeatedCount Repeated Pattern(s)"
                } else {
                    "Phonetic Analyzer: ${phoneticSegments.size} Potential Spirit Speech Segment(s)"
                },
                style = MaterialTheme.typography.labelSmall,
                color = speechHighlightColor,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.testTag("phonetic_summary_label")
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                phoneticSegments.forEachIndexed { idx, seg ->
                    val prefix = if (seg.patternGroup != null) {
                        "${seg.patternGroup} (x${seg.repeatCount})"
                    } else {
                        "Speech"
                    }
                    FilterChip(
                        selected = positionMs in seg.startMs..seg.endMs,
                        onClick = {
                            jumpToRegion(
                                WaveformHighlightRegion(
                                    startMs = seg.startMs,
                                    endMs = seg.endMs,
                                    label = prefix,
                                    isSilence = false,
                                    peakDb = -10f
                                )
                            )
                        },
                        label = {
                            Text(
                                text = "$prefix • ${formatShortTimeMs(seg.startMs)} [${seg.phoneticTag}] ${seg.confidenceScore}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = speechHighlightColor
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = speechHighlightColor.copy(alpha = 0.24f),
                            selectedLabelColor = speechHighlightColor
                        ),
                        modifier = Modifier.testTag("phonetic_jump_chip_$idx")
                    )
                }
            }
        }

        // Quick-Jump Chips for Flagged Segments, Peaks, and Silence Intervals
        val quickRegions = remember(waveformData, flaggedRegions) {
            buildList {
                addAll(flaggedRegions)
                if (waveformData != null) {
                    addAll(waveformData.peakRegions)
                    addAll(waveformData.silenceRegions)
                }
            }
        }
        if (quickRegions.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Jump & Zoom to Peaks / Silence Intervals:",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                quickRegions.forEachIndexed { idx, reg ->
                    val chipColor = when {
                        reg.label.startsWith("EVP") -> peakColor
                        reg.isSilence -> silenceTintColor
                        else -> warnColor
                    }
                    FilterChip(
                        selected = false,
                        onClick = { jumpToRegion(reg) },
                        label = {
                            Text(
                                text = "${reg.label} • ${formatShortTimeMs(reg.startMs)} (${"%.0f".format(Locale.US, reg.peakDb)}dB)",
                                style = MaterialTheme.typography.labelSmall,
                                color = chipColor
                            )
                        },
                        modifier = Modifier.testTag("waveform_jump_${if (reg.isSilence) "quiet" else "peak"}_$idx")
                    )
                }
            }
        }
    }
}

@Composable
fun EvpWaveformInspectorDialog(
    title: String,
    subtitle: String,
    waveformData: EvpWaveformData?,
    positionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    playbackSpeed: Float,
    flaggedRegions: List<WaveformHighlightRegion>,
    onSpeedChange: (Float) -> Unit,
    onSeekMs: (Long) -> Unit,
    onLoopRangeChange: (LongRange?) -> Unit,
    onTogglePlayPause: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "EVP WAVEFORM ANALYZER",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("close_waveform_inspector_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Close waveform analyzer"
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                ZoomableEvpWaveformPanel(
                    waveformData = waveformData,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    isPlaying = isPlaying,
                    flaggedRegions = flaggedRegions,
                    compact = false,
                    onSeekMs = onSeekMs,
                    onLoopRangeChange = onLoopRangeChange,
                    onTogglePlayPause = onTogglePlayPause,
                    onExpandClick = null
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(0.5f, 1.0f, 1.5f).forEach { speed ->
                            FilterChip(
                                selected = playbackSpeed == speed,
                                onClick = { onSpeedChange(speed) },
                                label = { Text("${speed}x", style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }

                    Button(
                        onClick = onTogglePlayPause,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = VoidBlack
                        ),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isPlaying) "Pause" else "Play", fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Pinch to zoom (1x–24x), drag to pan, tap to scrub playhead",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onDismiss) {
                        Text("Done")
                    }
                }
            }
        }
    }
}
