package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.AudioClipEntity
import com.example.data.local.EmfAnomalyEntity
import com.example.data.local.FlaggedAudioSegmentEntity
import com.example.data.local.InvestigationSessionEntity
import com.example.data.local.SensorTrendSampleEntity
import com.example.data.model.TrendTimeScale
import com.example.ui.components.ActiveSessionBanner
import com.example.ui.components.ClipAutoTagFlow
import com.example.ui.components.InstrumentCard
import com.example.ui.components.TelemetryTrendChartCard
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

@Composable
fun LogsExportScreen(
    sessions: List<InvestigationSessionEntity>,
    activeSession: InvestigationSessionEntity?,
    selectedSessionId: Long?,
    storageStatus: com.example.engine.StorageStatus,
    isTransferring: Boolean,
    onRefreshStorage: () -> Unit,
    onSelectStorageMode: (com.example.engine.StorageTargetMode) -> Unit,
    onPickCustomFolder: () -> Unit,
    onTransferAllFiles: () -> Unit,
    observeAnomalies: (Long) -> Flow<List<EmfAnomalyEntity>>,
    observeClips: (Long) -> Flow<List<AudioClipEntity>>,
    onSelectSession: (Long?) -> Unit,
    onStartSessionClick: () -> Unit,
    onStopSessionClick: () -> Unit,
    onDeleteSession: (Long) -> Unit,
    onExportCsv: (InvestigationSessionEntity) -> Unit,
    onExportJson: (InvestigationSessionEntity) -> Unit,
    onShareReport: (InvestigationSessionEntity) -> Unit,
    observeTrendSamples: (Long) -> Flow<List<SensorTrendSampleEntity>> = { emptyFlow() },
    allFlaggedSegments: List<FlaggedAudioSegmentEntity> = emptyList(),
    onInspectAudioClip: ((AudioClipEntity) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val selectedSession = sessions.firstOrNull { it.id == selectedSessionId }

    if (selectedSession != null) {
        val anomalies by observeAnomalies(selectedSession.id).collectAsState(initial = emptyList())
        val clips by observeClips(selectedSession.id).collectAsState(initial = emptyList())
        val trendSamples by observeTrendSamples(selectedSession.id).collectAsState(initial = emptyList())
        val sessionSegments = allFlaggedSegments.filter { it.sessionId == selectedSession.id }

        SessionDetailInspector(
            session = selectedSession,
            anomalies = anomalies,
            clips = clips,
            trendSamples = trendSamples,
            flaggedSegments = sessionSegments,
            onInspectAudioClip = onInspectAudioClip,
            onBack = { onSelectSession(null) },
            onExportCsv = { onExportCsv(selectedSession) },
            onExportJson = { onExportJson(selectedSession) },
            onShareReport = { onShareReport(selectedSession) },
            modifier = modifier
        )
    } else {
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

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "INVESTIGATION ARCHIVE (${sessions.size})",
                        style = MaterialTheme.typography.titleSmall
                    )
                    FilledTonalButton(
                        onClick = onStartSessionClick,
                        modifier = Modifier.testTag("new_investigation_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("New Log")
                    }
                }
            }

            if (sessions.isEmpty()) {
                item {
                    InstrumentCard {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Filled.FolderOpen,
                                contentDescription = null,
                                tint = TextMuted,
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "No Investigation Logs Yet",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextSecondary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Start an Investigation Log to automatically record EMF fluctuations, baseline metrics, and linked WAV audio clips for CSV/JSON export.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextMuted
                            )
                        }
                    }
                }
            } else {
                items(sessions, key = { it.id }) { session ->
                    SessionArchiveCard(
                        session = session,
                        onOpen = { onSelectSession(session.id) },
                        onExportCsv = { onExportCsv(session) },
                        onExportJson = { onExportJson(session) },
                        onDelete = { onDeleteSession(session.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionArchiveCard(
    session: InvestigationSessionEntity,
    onOpen: () -> Unit,
    onExportCsv: () -> Unit,
    onExportJson: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFmt = SimpleDateFormat("MMM d, yyyy • HH:mm", Locale.getDefault())
    InstrumentCard(
        borderColor = if (session.isActive) GeoportTeal.copy(alpha = 0.7f) else SubtleBorder,
        modifier = Modifier
            .clickable(onClick = onOpen)
            .testTag("session_card_${session.id}")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = session.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${session.locationName} • ${dateFmt.format(Date(session.startTimeMs))}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
                if (session.latitude != null && session.longitude != null) {
                    Text(
                        text = "GPS: ${"%.5f".format(Locale.US, session.latitude)}, ${"%.5f".format(Locale.US, session.longitude)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = GeoportTeal
                    )
                }
            }
            if (session.isActive) {
                Text(
                    text = "LIVE",
                    style = MaterialTheme.typography.labelSmall,
                    color = VoidBlack,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(GeoportTeal)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MetricPill("Anomalies", "${session.anomalyCount}", FranksAmber, Modifier.weight(1f))
            MetricPill("Peak EMF", "${"%.1f".format(Locale.US, session.peakMicroTesla)} μT", PhosphorCyan, Modifier.weight(1f))
            MetricPill("Max Δ", "${"%.1f".format(Locale.US, session.peakDeltaMicroTesla)} μT", AnomalyCrimson, Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(
                    onClick = onExportCsv,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("export_csv_${session.id}")
                ) {
                    Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("CSV", style = MaterialTheme.typography.labelMedium)
                }
                FilledTonalButton(
                    onClick = onExportJson,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("export_json_${session.id}")
                ) {
                    Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("JSON", style = MaterialTheme.typography.labelMedium)
                }
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
private fun SessionDetailInspector(
    session: InvestigationSessionEntity,
    anomalies: List<EmfAnomalyEntity>,
    clips: List<AudioClipEntity>,
    trendSamples: List<SensorTrendSampleEntity>,
    flaggedSegments: List<FlaggedAudioSegmentEntity>,
    onInspectAudioClip: ((AudioClipEntity) -> Unit)?,
    onBack: () -> Unit,
    onExportCsv: () -> Unit,
    onExportJson: () -> Unit,
    onShareReport: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)
    val timeFmt = SimpleDateFormat("HH:mm:ss.S", Locale.getDefault())

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("back_to_sessions_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back to sessions",
                        tint = PhosphorCyan
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = session.title,
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        text = "${session.locationName} • Baseline ${"%.1f".format(Locale.US, session.baselineMicroTesla)} μT",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary
                    )
                    if (session.latitude != null && session.longitude != null) {
                        Text(
                            text = "GPS: ${"%.6f".format(Locale.US, session.latitude)}, ${"%.6f".format(Locale.US, session.longitude)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = GeoportTeal
                        )
                    }
                }
            }
        }

        // Export Bar
        item {
            InstrumentCard {
                Text(
                    text = "POST-INVESTIGATION EXPORT",
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onExportCsv,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PhosphorCyan,
                            contentColor = VoidBlack
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("detail_export_csv_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Download,
                            contentDescription = stringResource(R.string.cd_export_csv),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("CSV", fontWeight = FontWeight.Bold)
                    }
                    FilledTonalButton(
                        onClick = onExportJson,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("detail_export_json_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Download,
                            contentDescription = stringResource(R.string.cd_export_json),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("JSON")
                    }
                    FilledTonalButton(
                        onClick = onShareReport,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("detail_share_report_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Share,
                            contentDescription = stringResource(R.string.cd_share_file),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Share")
                    }
                }
            }
        }

        // Historical EMF & Temperature Correlation Trend Chart
        item {
            TelemetryTrendChartCard(
                samples = trendSamples,
                audioClips = clips,
                flaggedSegments = flaggedSegments,
                tempSourceLabel = trendSamples.lastOrNull()?.tempSource?.ifBlank { "Session Telemetry" } ?: "Session Telemetry",
                initialScale = TrendTimeScale.ALL,
                title = "SESSION EMF & TEMPERATURE CORRELATION",
                onInspectAudioClip = onInspectAudioClip
            )
        }

        // Anomaly Timeline Chart
        item {
            InstrumentCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Assessment,
                        contentDescription = null,
                        tint = PhosphorCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "ANOMALY DELTA TIMELINE (${anomalies.size} EVENTS)",
                        style = MaterialTheme.typography.titleSmall
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(VoidBlack)
                        .border(1.dp, SubtleBorder, RoundedCornerShape(10.dp))
                ) {
                    if (anomalies.isEmpty()) {
                        Text(
                            text = "No EMF spikes recorded in this session.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextMuted,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    } else {
                        AnomalyTimelineCanvas(
                            anomalies = anomalies.reversed(),
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp)
                        )
                    }
                }
            }
        }

        // Linked Audio Clips
        if (clips.isNotEmpty()) {
            item {
                Text(
                    text = "LINKED AUDIO CLIPS (${clips.size})",
                    style = MaterialTheme.typography.titleSmall
                )
            }
            items(clips, key = { "clip_${it.id}" }) { c ->
                InstrumentCard {
                    Text(c.fileName, style = MaterialTheme.typography.titleSmall, color = PhosphorCyan)
                    Text(
                        text = "${c.modeLabel} • ${formatDurationMs(c.durationMs)} • Peak ${"%.1f".format(Locale.US, c.peakEmfDuringClip)} μT",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                    if (c.autoTags.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        ClipAutoTagFlow(rawTags = c.autoTags)
                    }
                    if (c.markersSummary.isNotBlank() && c.markersSummary != "No markers") {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Markers: ${c.markersSummary}",
                            style = MaterialTheme.typography.labelSmall,
                            color = FranksAmber
                        )
                    }
                }
            }
        }

        // Anomaly Event Log Table
        item {
            Text(
                text = "EMF EVENT LOG",
                style = MaterialTheme.typography.titleSmall
            )
        }

        if (anomalies.isEmpty()) {
            item {
                InstrumentCard {
                    Text(
                        text = "All readings remained within ambient baseline during this session.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted
                    )
                }
            }
        } else {
            items(anomalies, key = { it.id }) { a ->
                val sevColor = when (a.severity) {
                    "CRITICAL" -> AnomalyCrimson
                    "HIGH" -> FranksAmber
                    "MANUAL" -> GeoportTeal
                    else -> PhosphorCyan
                }
                InstrumentCard(borderColor = sevColor.copy(alpha = 0.45f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${a.severity} • ${timeFmt.format(Date(a.timestampMs))}",
                            style = MaterialTheme.typography.labelMedium,
                            color = sevColor
                        )
                        if (a.isMotionArtifact) {
                            Text(
                                text = "MOTION ARTIFACT",
                                style = MaterialTheme.typography.labelSmall,
                                color = FranksAmber
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${"%.2f".format(Locale.US, a.magnitudeUt)} μT  (Δ ${"%.2f".format(Locale.US, a.deltaUt)} μT)",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "X:${"%.1f".format(Locale.US, a.xUt)}  Y:${"%.1f".format(Locale.US, a.yUt)}  Z:${"%.1f".format(Locale.US, a.zUt)}  |  ${a.note}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricPill(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(ElevatedCard)
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = TextMuted)
        Text(text = value, style = MaterialTheme.typography.labelLarge, color = color)
    }
}

@Composable
private fun AnomalyTimelineCanvas(
    anomalies: List<EmfAnomalyEntity>,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val maxDelta = anomalies.maxOfOrNull { abs(it.deltaUt) }?.coerceAtLeast(10f) ?: 10f
        if (anomalies.size == 1) {
            val y = size.height - (abs(anomalies[0].deltaUt) / maxDelta) * size.height * 0.85f
            drawCircle(PhosphorCyan, radius = 8f, center = Offset(size.width / 2f, y))
            return@Canvas
        }

        val path = Path()
        val stepX = size.width / (anomalies.size - 1).coerceAtLeast(1)
        anomalies.forEachIndexed { idx, a ->
            val x = idx * stepX
            val y = size.height - (abs(a.deltaUt) / maxDelta) * size.height * 0.85f
            if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path = path,
            color = PhosphorCyan,
            style = Stroke(width = 3f, cap = StrokeCap.Round)
        )
        anomalies.forEachIndexed { idx, a ->
            val x = idx * stepX
            val y = size.height - (abs(a.deltaUt) / maxDelta) * size.height * 0.85f
            drawCircle(
                color = if (a.severity == "CRITICAL") AnomalyCrimson else FranksAmber,
                radius = 5f,
                center = Offset(x, y)
            )
        }
    }
}
