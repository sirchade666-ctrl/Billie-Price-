package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.AudioClipEntity
import com.example.data.local.FlaggedAudioSegmentEntity
import com.example.data.model.AudioMetadataTag
import com.example.data.model.NoiseSuppressionLevel
import com.example.data.model.PlaybackState
import com.example.data.model.RecorderState
import com.example.engine.ClipAutoTagEngine
import com.example.engine.EvpWaveformData
import com.example.engine.EvpWaveformExtractor
import com.example.ui.components.ClipAutoTagFlow
import com.example.ui.components.EvpWaveformInspectorDialog
import com.example.ui.components.InstrumentCard
import com.example.ui.components.StatusLed
import com.example.ui.components.TempIceBlue
import com.example.ui.components.ZoomableEvpWaveformPanel
import com.example.ui.components.clipTagColor
import com.example.ui.components.formatDurationMs
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun RecorderScreen(
    recorderState: RecorderState,
    liveWaveform: List<Float>,
    spiritBoxRunning: Boolean,
    clips: List<AudioClipEntity>,
    flaggedSegments: List<FlaggedAudioSegmentEntity>,
    analyzingClipId: Long?,
    playbackState: PlaybackState,
    waveformCache: Map<String, EvpWaveformData>,
    inspectedClipId: Long?,
    inspectedSegmentId: Long?,
    storageStatus: com.example.engine.StorageStatus,
    isTransferring: Boolean,
    onRefreshStorage: () -> Unit,
    onSelectStorageMode: (com.example.engine.StorageTargetMode) -> Unit,
    onPickCustomFolder: () -> Unit,
    onTransferAllFiles: () -> Unit,
    onRequestMicPermission: () -> Unit,
    onStartRecording: () -> Unit,
    onTogglePause: () -> Unit,
    onStopRecording: () -> Unit,
    onMixSpiritBoxChange: (Boolean) -> Unit,
    onNoiseSuppressionChange: (NoiseSuppressionLevel) -> Unit = {},
    onHumNotchChange: (Boolean) -> Unit = {},
    onTagMarker: (String) -> Unit,
    onAnalyzeClip: (AudioClipEntity) -> Unit,
    onPlayPauseClip: (AudioClipEntity) -> Unit,
    onPlayPauseSegment: (FlaggedAudioSegmentEntity) -> Unit,
    onInspectClip: (AudioClipEntity) -> Unit,
    onInspectSegment: (FlaggedAudioSegmentEntity) -> Unit,
    onCloseInspector: () -> Unit,
    onSeekAndPrepareClip: (AudioClipEntity, Long) -> Unit,
    onSeekAndPrepareSegment: (FlaggedAudioSegmentEntity, Long) -> Unit,
    onLoopRangeChange: (LongRange?) -> Unit,
    onLoadWaveform: (String, Long) -> Unit,
    onSeekPlayback: (Long) -> Unit,
    onPlaybackSpeedChange: (Float) -> Unit,
    onSaveCopyToDevice: (AudioClipEntity) -> Unit,
    onSaveSegmentToDevice: (FlaggedAudioSegmentEntity) -> Unit,
    onShareClip: (AudioClipEntity) -> Unit,
    onShareSegment: (FlaggedAudioSegmentEntity) -> Unit,
    onDeleteClip: (AudioClipEntity) -> Unit,
    onDeleteSegment: (FlaggedAudioSegmentEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val inspectedClip = remember(inspectedClipId, clips) {
        inspectedClipId?.let { id -> clips.firstOrNull { it.id == id } }
    }
    val inspectedSegment = remember(inspectedSegmentId, flaggedSegments) {
        inspectedSegmentId?.let { id -> flaggedSegments.firstOrNull { it.id == id } }
    }
    var selectedTagFilter by remember { mutableStateOf<String?>(null) }
    val filteredClips = remember(clips, selectedTagFilter) {
        val activeTag = selectedTagFilter
        if (activeTag == null) {
            clips
        } else {
            clips.filter { clip ->
                ClipAutoTagEngine.parseTags(clip.autoTags)
                    .any { it.equals(activeTag, ignoreCase = true) }
            }
        }
    }
    val tagCounts = remember(clips) {
        val counts = mutableMapOf<String, Int>()
        clips.forEach { clip ->
            ClipAutoTagEngine.parseTags(clip.autoTags).forEach { tag ->
                counts[tag] = (counts[tag] ?: 0) + 1
            }
        }
        counts
    }

    if (inspectedClip != null) {
        val isActive = playbackState.activeClipId == inspectedClip.id
        val clipDuration = if (isActive && playbackState.durationMs > 0L) playbackState.durationMs else inspectedClip.durationMs
        val flaggedForClip = remember(flaggedSegments, inspectedClip.id) {
            EvpWaveformExtractor.buildFlaggedMarkerRegions(flaggedSegments, inspectedClip.id)
        }
        LaunchedEffect(inspectedClip.filePath) {
            onLoadWaveform(inspectedClip.filePath, inspectedClip.durationMs)
        }
        EvpWaveformInspectorDialog(
            title = inspectedClip.fileName,
            subtitle = "${inspectedClip.modeLabel} • Peak ${"%.1f".format(Locale.US, inspectedClip.peakEmfDuringClip)} μT",
            waveformData = waveformCache[inspectedClip.filePath],
            positionMs = if (isActive) playbackState.positionMs else 0L,
            durationMs = clipDuration,
            isPlaying = isActive && playbackState.isPlaying,
            playbackSpeed = playbackState.playbackSpeed,
            flaggedRegions = flaggedForClip,
            onSpeedChange = onPlaybackSpeedChange,
            onSeekMs = { ms -> onSeekAndPrepareClip(inspectedClip, ms) },
            onLoopRangeChange = onLoopRangeChange,
            onTogglePlayPause = { onPlayPauseClip(inspectedClip) },
            onDismiss = onCloseInspector
        )
    } else if (inspectedSegment != null) {
        val isActive = playbackState.activeSegmentId == inspectedSegment.id
        val segDuration = if (isActive && playbackState.durationMs > 0L) playbackState.durationMs else (inspectedSegment.endOffsetMs - inspectedSegment.startOffsetMs).coerceAtLeast(1L)
        LaunchedEffect(inspectedSegment.segmentFilePath) {
            onLoadWaveform(inspectedSegment.segmentFilePath, segDuration)
        }
        EvpWaveformInspectorDialog(
            title = inspectedSegment.acousticClass,
            subtitle = "${formatDurationMs(inspectedSegment.startOffsetMs)} – ${formatDurationMs(inspectedSegment.endOffsetMs)} • SNR +${"%.1f".format(Locale.US, inspectedSegment.snrDb)} dB",
            waveformData = waveformCache[inspectedSegment.segmentFilePath],
            positionMs = if (isActive) playbackState.positionMs else 0L,
            durationMs = segDuration,
            isPlaying = isActive && playbackState.isPlaying,
            playbackSpeed = playbackState.playbackSpeed,
            flaggedRegions = emptyList(),
            onSpeedChange = onPlaybackSpeedChange,
            onSeekMs = { ms -> onSeekAndPrepareSegment(inspectedSegment, ms) },
            onLoopRangeChange = onLoopRangeChange,
            onTogglePlayPause = { onPlayPauseSegment(inspectedSegment) },
            onDismiss = onCloseInspector
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("recorder_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (!recorderState.hasMicPermission) {
            item {
                InstrumentCard(borderColor = FranksAmber.copy(alpha = 0.7f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Mic,
                            contentDescription = null,
                            tint = FranksAmber,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Microphone Access Needed",
                                style = MaterialTheme.typography.titleMedium,
                                color = FranksAmber
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.permission_audio_rationale),
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onRequestMicPermission,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = FranksAmber,
                            contentColor = VoidBlack
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("grant_mic_permission_button")
                    ) {
                        Text(
                            text = stringResource(R.string.grant_mic_permission),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Digital Recorder Deck
        item {
            InstrumentCard(
                borderColor = if (recorderState.isRecording) AnomalyCrimson else SubtleBorder
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusLed(
                            active = recorderState.isRecording && !recorderState.isPaused,
                            activeColor = AnomalyCrimson,
                            pulse = true
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = when {
                                recorderState.isRecording && recorderState.isPaused -> "REC PAUSED"
                                recorderState.isRecording -> "RECORDING WAV + LIVE EVP/EMF SCAN"
                                else -> "DIGITAL EVP RECORDER"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = if (recorderState.isRecording) AnomalyCrimson else TextSecondary
                        )
                    }
                    Text(
                        text = "${"%.0f".format(Locale.US, recorderState.currentDb)} dBFS",
                        style = MaterialTheme.typography.labelMedium,
                        color = PhosphorCyan
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        text = formatDurationMs(recorderState.elapsedMs),
                        style = MaterialTheme.typography.displaySmall,
                        color = if (recorderState.isRecording) AnomalyCrimson else MaterialTheme.colorScheme.onSurface
                    )
                    if (recorderState.isRecording) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = if (recorderState.liveEvpCandidateActive) "EVP+EMF CO-INCIDENCE!" else "CONCURRENT FLAGS",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (recorderState.liveEvpCandidateActive) FranksAmber else TextMuted
                            )
                            Text(
                                text = "${recorderState.concurrentFlagCount} detected",
                                style = MaterialTheme.typography.labelLarge,
                                color = if (recorderState.concurrentFlagCount > 0) GeoportTeal else TextSecondary
                            )
                        }
                    }
                }

                if (recorderState.activeFileName != null) {
                    Text(
                        text = recorderState.activeFileName,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(86.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(VoidBlack)
                        .border(1.dp, SubtleBorder, RoundedCornerShape(10.dp))
                ) {
                    WaveformHistoryCanvas(
                        samples = liveWaveform,
                        active = recorderState.isRecording && !recorderState.isPaused,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp, vertical = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                LinearProgressIndicator(
                    progress = { recorderState.normalizedLevel },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = when {
                        recorderState.normalizedLevel > 0.8f -> AnomalyCrimson
                        recorderState.normalizedLevel > 0.45f -> FranksAmber
                        else -> GeoportTeal
                    },
                    trackColor = ElevatedCard
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Mix Spirit Box Feed",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (spiritBoxRunning) {
                                "Spirit Box active — blending sweep with Mic"
                            } else {
                                "Spirit Box standby — recording Mic only"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted
                        )
                    }
                    Switch(
                        checked = recorderState.mixSpiritBox,
                        onCheckedChange = onMixSpiritBoxChange,
                        enabled = !recorderState.isRecording,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = VoidBlack,
                            checkedTrackColor = PhosphorCyan
                        ),
                        modifier = Modifier.testTag("mix_spirit_box_switch")
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // EVP Noise Suppression & 50/60 Hz Hum Filter
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(ElevatedCard.copy(alpha = 0.6f))
                        .border(1.dp, SubtleBorder, RoundedCornerShape(10.dp))
                        .padding(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "NOISE SUPPRESSION FILTER",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextSecondary
                        )
                        val activeSuppression = recorderState.noiseSuppressionLevel != NoiseSuppressionLevel.OFF
                        Text(
                            text = when {
                                !activeSuppression -> "BYPASSED"
                                recorderState.isRecording -> "-${"%.1f".format(Locale.US, recorderState.noiseReductionDb)} dB cut"
                                else -> recorderState.noiseSuppressionLevel.label.uppercase(Locale.US)
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (activeSuppression) GeoportTeal else TextMuted,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.testTag("noise_suppression_status")
                        )
                    }
                    Text(
                        text = recorderState.noiseSuppressionLevel.subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        NoiseSuppressionLevel.entries.forEach { level ->
                            val selected = recorderState.noiseSuppressionLevel == level
                            FilterChip(
                                selected = selected,
                                onClick = { onNoiseSuppressionChange(level) },
                                label = { Text(level.label, style = MaterialTheme.typography.labelSmall) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = GeoportTeal.copy(alpha = 0.22f),
                                    selectedLabelColor = GeoportTeal
                                ),
                                modifier = Modifier.testTag("noise_filter_${level.name.lowercase()}")
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "50/60 Hz AC Hum Notch",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Cuts wiring buzz & 120 Hz lighting hum",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted
                            )
                        }
                        Switch(
                            checked = recorderState.mainsHumNotchEnabled,
                            onCheckedChange = onHumNotchChange,
                            enabled = recorderState.noiseSuppressionLevel != NoiseSuppressionLevel.OFF,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = VoidBlack,
                                checkedTrackColor = GeoportTeal
                            ),
                            modifier = Modifier.testTag("hum_notch_switch")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (!recorderState.isRecording) {
                    Button(
                        onClick = {
                            if (recorderState.hasMicPermission) onStartRecording()
                            else onRequestMicPermission()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AnomalyCrimson,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("start_recording_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.FiberManualRecord,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Start Local WAV Capture", fontWeight = FontWeight.Bold)
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        FilledTonalButton(
                            onClick = onTogglePause,
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .testTag("pause_recording_button")
                        ) {
                            Icon(
                                imageVector = if (recorderState.isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                                contentDescription = null
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (recorderState.isPaused) "Resume" else "Pause")
                        }
                        Button(
                            onClick = onStopRecording,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AnomalyCrimson,
                                contentColor = Color.White
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .testTag("stop_save_recording_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Stop,
                                contentDescription = null
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Stop & Analyze", fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Tag EVP Timestamp Marker",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("Voice", "Knock", "Cold Spot").forEach { tag ->
                            AssistChip(
                                onClick = { onTagMarker(tag) },
                                label = { Text(tag, style = MaterialTheme.typography.labelMedium) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        // Storage Location (SD Card Auto-Detect & Transfer)
        item {
            com.example.ui.components.StorageTransferCard(
                storageStatus = storageStatus,
                isTransferring = isTransferring,
                onRefreshStorage = onRefreshStorage,
                onSelectMode = onSelectStorageMode,
                onPickCustomFolder = onPickCustomFolder,
                onTransferAll = onTransferAllFiles
            )
        }

        // Flagged Concurrent EVP + EMF Segments Section
        if (flaggedSegments.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        tint = FranksAmber,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "FLAGGED EVP & EMF SEGMENTS (${flaggedSegments.size})",
                            style = MaterialTheme.typography.titleSmall,
                            color = FranksAmber
                        )
                        Text(
                            text = "Extracted audio events concurrent with EMF spikes",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                }
            }

            items(flaggedSegments, key = { "seg_${it.id}" }) { seg ->
                val isCurrentSeg = playbackState.activeSegmentId == seg.id
                val isPlayingSeg = isCurrentSeg && playbackState.isPlaying
                if (isCurrentSeg) {
                    LaunchedEffect(seg.segmentFilePath) {
                        onLoadWaveform(seg.segmentFilePath, (seg.endOffsetMs - seg.startOffsetMs).coerceAtLeast(1L))
                    }
                }
                FlaggedSegmentCard(
                    segment = seg,
                    isCurrent = isCurrentSeg,
                    isPlaying = isPlayingSeg,
                    waveformData = waveformCache[seg.segmentFilePath],
                    positionMs = if (isCurrentSeg) playbackState.positionMs else 0L,
                    durationMs = if (isCurrentSeg && playbackState.durationMs > 0L) playbackState.durationMs else (seg.endOffsetMs - seg.startOffsetMs),
                    onPlayPause = { onPlayPauseSegment(seg) },
                    onInspectWave = { onInspectSegment(seg) },
                    onSeek = onSeekPlayback,
                    onLoopRangeChange = onLoopRangeChange,
                    onSaveAs = { onSaveSegmentToDevice(seg) },
                    onShare = { onShareSegment(seg) },
                    onDelete = { onDeleteSegment(seg) }
                )
            }
        }

        // Local Clips Header + EVP Speed Selector
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (selectedTagFilter == null) {
                                "LOCAL AUDIO CLIPS (${clips.size})"
                            } else {
                                "LOCAL AUDIO CLIPS (${filteredClips.size}/${clips.size})"
                            },
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Auto-tagged by EMF, thermal drop & speech probability",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(0.5f, 1.0f, 1.5f).forEach { speed ->
                            FilterChip(
                                selected = playbackState.playbackSpeed == speed,
                                onClick = { onPlaybackSpeedChange(speed) },
                                label = { Text("${speed}x", style = MaterialTheme.typography.labelSmall) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = PhosphorCyan.copy(alpha = 0.2f),
                                    selectedLabelColor = PhosphorCyan
                                )
                            )
                        }
                    }
                }

                if (clips.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .testTag("clip_auto_tag_filter_bar"),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = selectedTagFilter == null,
                            onClick = { selectedTagFilter = null },
                            label = { Text("All (${clips.size})", style = MaterialTheme.typography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = PhosphorCyan.copy(alpha = 0.22f),
                                selectedLabelColor = PhosphorCyan
                            ),
                            modifier = Modifier.testTag("clip_tag_filter_all")
                        )
                        AudioMetadataTag.entries.forEach { tagEnum ->
                            val count = tagCounts[tagEnum.label] ?: 0
                            val accent = clipTagColor(tagEnum.label)
                            val isSelected = selectedTagFilter == tagEnum.label
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    selectedTagFilter = if (isSelected) null else tagEnum.label
                                },
                                label = {
                                    Text(
                                        text = if (count > 0) "${tagEnum.label} ($count)" else tagEnum.label,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = accent.copy(alpha = 0.22f),
                                    selectedLabelColor = accent
                                ),
                                modifier = Modifier.testTag("clip_tag_filter_${tagEnum.name.lowercase()}")
                            )
                        }
                    }
                }
            }
        }

        if (clips.isEmpty()) {
            item {
                InstrumentCard {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AudioFile,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(38.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "No Audio Clips Saved Yet",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Tap Start Local WAV Capture above to record EVP or Spirit Box audio and automatically tag EMF spikes, thermal drops, and speech probability.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextMuted
                        )
                    }
                }
            }
        } else if (filteredClips.isEmpty()) {
            item {
                InstrumentCard {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "No clips tagged \"$selectedTagFilter\"",
                            style = MaterialTheme.typography.titleSmall,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        FilledTonalButton(
                            onClick = { selectedTagFilter = null },
                            modifier = Modifier.testTag("clear_clip_tag_filter_button")
                        ) {
                            Text("Show All Clips")
                        }
                    }
                }
            }
        } else {
            items(filteredClips, key = { it.id }) { clip ->
                val isCurrentClip = playbackState.activeClipId == clip.id
                val isPlaying = isCurrentClip && playbackState.isPlaying
                val clipFlaggedSegments = remember(flaggedSegments, clip.id) {
                    EvpWaveformExtractor.buildFlaggedMarkerRegions(flaggedSegments, clip.id)
                }
                if (isCurrentClip) {
                    LaunchedEffect(clip.filePath) {
                        onLoadWaveform(clip.filePath, clip.durationMs)
                    }
                }
                AudioClipCard(
                    clip = clip,
                    flaggedCount = clipFlaggedSegments.size,
                    flaggedRegions = clipFlaggedSegments,
                    waveformData = waveformCache[clip.filePath],
                    isAnalyzing = analyzingClipId == clip.id,
                    isCurrentClip = isCurrentClip,
                    isPlaying = isPlaying,
                    positionMs = if (isCurrentClip) playbackState.positionMs else 0L,
                    durationMs = if (isCurrentClip && playbackState.durationMs > 0L) playbackState.durationMs else clip.durationMs,
                    onPlayPause = { onPlayPauseClip(clip) },
                    onInspectWave = { onInspectClip(clip) },
                    onAnalyze = { onAnalyzeClip(clip) },
                    onSeek = onSeekPlayback,
                    onLoopRangeChange = onLoopRangeChange,
                    onSaveCopy = { onSaveCopyToDevice(clip) },
                    onShare = { onShareClip(clip) },
                    onDelete = { onDeleteClip(clip) }
                )
            }
        }
    }
}

@Composable
private fun FlaggedSegmentCard(
    segment: FlaggedAudioSegmentEntity,
    isCurrent: Boolean,
    isPlaying: Boolean,
    waveformData: EvpWaveformData?,
    positionMs: Long,
    durationMs: Long,
    onPlayPause: () -> Unit,
    onInspectWave: () -> Unit,
    onSeek: (Long) -> Unit,
    onLoopRangeChange: (LongRange?) -> Unit,
    onSaveAs: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    InstrumentCard(
        borderColor = if (isCurrent) FranksAmber else FranksAmber.copy(alpha = 0.45f)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onPlayPause,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isPlaying) FranksAmber else ElevatedCard)
                    .testTag("play_segment_${segment.id}")
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = stringResource(R.string.cd_play_pause),
                    tint = if (isPlaying) VoidBlack else FranksAmber
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = segment.acousticClass,
                        style = MaterialTheme.typography.titleSmall,
                        color = FranksAmber
                    )
                    Text(
                        text = "${segment.confidenceScore}% match",
                        style = MaterialTheme.typography.labelSmall,
                        color = GeoportTeal
                    )
                }
                Text(
                    text = "${formatDurationMs(segment.startOffsetMs)} – ${formatDurationMs(segment.endOffsetMs)} • SNR +${"%.1f".format(Locale.US, segment.snrDb)} dB",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
                Text(
                    text = "Concurrent EMF: ${"%.1f".format(Locale.US, segment.emfMagnitudeUt)} μT (Δ ${"%.1f".format(Locale.US, segment.emfDeltaUt)} μT)",
                    style = MaterialTheme.typography.labelSmall,
                    color = AnomalyCrimson
                )
            }
        }

        if (isCurrent) {
            Spacer(modifier = Modifier.height(8.dp))
            ZoomableEvpWaveformPanel(
                waveformData = waveformData,
                positionMs = positionMs,
                durationMs = durationMs,
                isPlaying = isPlaying,
                flaggedRegions = emptyList(),
                compact = true,
                onSeekMs = onSeek,
                onLoopRangeChange = onLoopRangeChange,
                onExpandClick = onInspectWave
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledTonalButton(
                onClick = onInspectWave,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier.testTag("inspect_segment_wave_${segment.id}")
            ) {
                Icon(Icons.Filled.Waves, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Waveform", style = MaterialTheme.typography.labelSmall)
            }
            Spacer(modifier = Modifier.width(6.dp))
            FilledTonalButton(
                onClick = onSaveAs,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Save", style = MaterialTheme.typography.labelSmall)
            }
            Spacer(modifier = Modifier.width(4.dp))
            IconButton(onClick = onShare, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.cd_share_file), tint = FranksAmber)
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Filled.DeleteOutline, contentDescription = stringResource(R.string.cd_delete_item), tint = AnomalyCrimson)
            }
        }
    }
}

@Composable
private fun AudioClipCard(
    clip: AudioClipEntity,
    flaggedCount: Int,
    flaggedRegions: List<com.example.engine.WaveformHighlightRegion>,
    waveformData: EvpWaveformData?,
    isAnalyzing: Boolean,
    isCurrentClip: Boolean,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    onPlayPause: () -> Unit,
    onInspectWave: () -> Unit,
    onAnalyze: () -> Unit,
    onSeek: (Long) -> Unit,
    onLoopRangeChange: (LongRange?) -> Unit,
    onSaveCopy: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFmt = SimpleDateFormat("MMM d, HH:mm:ss", Locale.getDefault())
    val kb = (clip.fileSizeBytes / 1024L).coerceAtLeast(1L)

    InstrumentCard(
        borderColor = if (isCurrentClip) PhosphorCyan else SubtleBorder
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onPlayPause,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isPlaying) PhosphorCyan else ElevatedCard)
                    .testTag("play_clip_${clip.id}")
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = stringResource(R.string.cd_play_pause),
                    tint = if (isPlaying) VoidBlack else PhosphorCyan
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = clip.fileName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${clip.modeLabel} • ${dateFmt.format(Date(clip.createdAtMs))}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
                Text(
                    text = "${formatDurationMs(clip.durationMs)} • ${kb} KB • Peak ${"%.1f".format(Locale.US, clip.peakEmfDuringClip)} μT",
                    style = MaterialTheme.typography.labelSmall,
                    color = GeoportTeal
                )
            }
        }

        if (clip.autoTags.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            ClipAutoTagFlow(rawTags = clip.autoTags)
        }

        val hasExtraTelemetry = clip.tempDropC <= -0.5f || clip.speechProbabilityPct > 0
        if (hasExtraTelemetry) {
            Spacer(modifier = Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (clip.tempDropC <= -0.5f) {
                    Text(
                        text = "Temp Δ ${"%.1f".format(Locale.US, clip.tempDropC)}°C",
                        style = MaterialTheme.typography.labelSmall,
                        color = TempIceBlue
                    )
                }
                if (clip.speechProbabilityPct > 0) {
                    Text(
                        text = "Speech Prob ${clip.speechProbabilityPct}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFC084FC)
                    )
                }
            }
        }

        if (clip.markersSummary.isNotBlank() && clip.markersSummary != "No markers") {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Markers: ${clip.markersSummary}",
                style = MaterialTheme.typography.labelSmall,
                color = FranksAmber
            )
        }

        if (flaggedCount > 0) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "$flaggedCount concurrent EVP+EMF segment(s) extracted above",
                style = MaterialTheme.typography.labelSmall,
                color = FranksAmber
            )
        }

        val phoneticHits = waveformData?.phoneticSegments.orEmpty()
        if (phoneticHits.isNotEmpty()) {
            val repeated = phoneticHits.count { it.isRepeatedPattern }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (repeated > 0) {
                    "Phonetic: ${phoneticHits.size} spirit speech region(s) • $repeated repeated pattern(s)"
                } else {
                    "Phonetic: ${phoneticHits.size} potential spirit speech region(s)"
                },
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFFC084FC)
            )
        }

        if (isCurrentClip) {
            Spacer(modifier = Modifier.height(8.dp))
            ZoomableEvpWaveformPanel(
                waveformData = waveformData,
                positionMs = positionMs,
                durationMs = durationMs,
                isPlaying = isPlaying,
                flaggedRegions = flaggedRegions,
                compact = true,
                onSeekMs = onSeek,
                onLoopRangeChange = onLoopRangeChange,
                onExpandClick = onInspectWave
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledTonalButton(
                onClick = onInspectWave,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                modifier = Modifier.testTag("inspect_clip_wave_${clip.id}")
            ) {
                Icon(Icons.Filled.Waves, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Waveform", style = MaterialTheme.typography.labelSmall)
            }

            FilledTonalButton(
                onClick = onAnalyze,
                enabled = !isAnalyzing,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                modifier = Modifier.testTag("analyze_clip_${clip.id}")
            ) {
                if (isAnalyzing) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Filled.GraphicEq, contentDescription = null, modifier = Modifier.size(16.dp))
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (isAnalyzing) "Scan..." else "EVP Scan",
                    style = MaterialTheme.typography.labelSmall
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            IconButton(
                onClick = onSaveCopy,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("export_clip_${clip.id}")
            ) {
                Icon(
                    imageVector = Icons.Filled.Download,
                    contentDescription = "Save copy to device",
                    tint = GeoportTeal
                )
            }
            IconButton(
                onClick = onShare,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Share,
                    contentDescription = stringResource(R.string.cd_share_file),
                    tint = PhosphorCyan
                )
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.DeleteOutline,
                    contentDescription = stringResource(R.string.cd_delete_item),
                    tint = AnomalyCrimson
                )
            }
        }
    }
}

@Composable
private fun WaveformHistoryCanvas(
    samples: List<Float>,
    active: Boolean,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val count = samples.size.coerceAtLeast(1)
        val barWidth = (size.width / count) * 0.68f
        val step = size.width / count

        samples.forEachIndexed { idx, amp ->
            val h = (size.height * amp.coerceIn(0.05f, 1f))
            val x = idx * step + (step - barWidth) / 2f
            val y = (size.height - h) / 2f
            val barColor = when {
                !active -> TextMuted.copy(alpha = 0.35f)
                amp > 0.78f -> AnomalyCrimson
                amp > 0.45f -> FranksAmber
                else -> PhosphorCyan
            }
            drawRoundRect(
                color = barColor,
                topLeft = Offset(x, y),
                size = Size(barWidth, h),
                cornerRadius = CornerRadius(3f, 3f)
            )
        }
    }
}
