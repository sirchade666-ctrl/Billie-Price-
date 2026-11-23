package com.example.data.repository

import android.content.Context
import android.os.Environment
import com.example.data.local.AnomalyDao
import com.example.data.local.AudioClipDao
import com.example.data.local.AudioClipEntity
import com.example.data.local.EmfAnomalyEntity
import com.example.data.local.FlaggedAudioSegmentEntity
import com.example.data.local.FlaggedSegmentDao
import com.example.data.local.InvestigationSessionEntity
import com.example.data.local.JournalDao
import com.example.data.local.JournalEntryEntity
import com.example.data.local.SensorTrendDao
import com.example.data.local.SensorTrendSampleEntity
import com.example.data.local.SessionDao
import com.example.data.model.GeoCoordinate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class InvestigationRepository(
    private val context: Context,
    private val sessionDao: SessionDao,
    private val anomalyDao: AnomalyDao,
    private val audioClipDao: AudioClipDao,
    private val flaggedSegmentDao: FlaggedSegmentDao,
    private val journalDao: JournalDao,
    private val sensorTrendDao: SensorTrendDao
) {
    val allSessions: Flow<List<InvestigationSessionEntity>> = sessionDao.observeAllSessions()
    val activeSession: Flow<InvestigationSessionEntity?> = sessionDao.observeActiveSession()
    val allAudioClips: Flow<List<AudioClipEntity>> = audioClipDao.observeAllClips()
    val recentAnomalies: Flow<List<EmfAnomalyEntity>> = anomalyDao.observeRecentAnomalies()
    val allFlaggedSegments: Flow<List<FlaggedAudioSegmentEntity>> = flaggedSegmentDao.observeAllFlaggedSegments()
    val allJournalEntries: Flow<List<JournalEntryEntity>> = journalDao.observeAllJournalEntries()
    val recentTrendSamples: Flow<List<SensorTrendSampleEntity>> = sensorTrendDao.observeRecentSamples()

    fun observeAnomaliesForSession(sessionId: Long): Flow<List<EmfAnomalyEntity>> =
        anomalyDao.observeAnomaliesForSession(sessionId)

    fun observeClipsForSession(sessionId: Long): Flow<List<AudioClipEntity>> =
        audioClipDao.observeClipsForSession(sessionId)

    fun observeSegmentsForClip(clipId: Long): Flow<List<FlaggedAudioSegmentEntity>> =
        flaggedSegmentDao.observeSegmentsForClip(clipId)

    fun observeJournalEntriesForSession(sessionId: Long): Flow<List<JournalEntryEntity>> =
        journalDao.observeEntriesForSession(sessionId)

    fun observeTrendSamplesForSession(sessionId: Long): Flow<List<SensorTrendSampleEntity>> =
        sensorTrendDao.observeSamplesForSession(sessionId)

    fun getRecordingsDirectory(): File {
        val baseDir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: context.filesDir
        val dir = File(baseDir, "recordings")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getSegmentsDirectory(): File {
        val dir = File(getRecordingsDirectory(), "segments")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    suspend fun startSession(
        title: String,
        locationName: String,
        notes: String,
        baselineUt: Float,
        coordinate: GeoCoordinate?
    ): Long {
        val now = System.currentTimeMillis()
        sessionDao.closeAllActiveSessions(now)
        val session = InvestigationSessionEntity(
            title = title.ifBlank { "Field Investigation" },
            locationName = locationName.ifBlank { "Unspecified Site" },
            notes = notes,
            startTimeMs = now,
            baselineMicroTesla = baselineUt,
            peakMicroTesla = baselineUt,
            peakDeltaMicroTesla = 0f,
            anomalyCount = 0,
            isActive = true,
            latitude = coordinate?.latitude,
            longitude = coordinate?.longitude,
            gpsAccuracyMeters = coordinate?.accuracyMeters
        )
        return sessionDao.insertSession(session)
    }

    suspend fun endActiveSession(sessionId: Long) {
        val existing = sessionDao.getSessionById(sessionId) ?: return
        sessionDao.updateSession(
            existing.copy(
                isActive = false,
                endTimeMs = System.currentTimeMillis()
            )
        )
    }

    suspend fun updateSessionBaseline(sessionId: Long, baselineUt: Float) {
        val existing = sessionDao.getSessionById(sessionId) ?: return
        sessionDao.updateSession(existing.copy(baselineMicroTesla = baselineUt))
    }

    suspend fun deleteSession(sessionId: Long) {
        sessionDao.deleteSessionById(sessionId)
    }

    suspend fun logEmfAnomaly(
        sessionId: Long,
        magnitudeUt: Float,
        deltaUt: Float,
        xUt: Float,
        yUt: Float,
        zUt: Float,
        severity: String,
        note: String,
        isMotionArtifact: Boolean,
        coordinate: GeoCoordinate?
    ) {
        val anomaly = EmfAnomalyEntity(
            sessionId = sessionId,
            timestampMs = System.currentTimeMillis(),
            magnitudeUt = magnitudeUt,
            deltaUt = deltaUt,
            xUt = xUt,
            yUt = yUt,
            zUt = zUt,
            severity = severity,
            note = note,
            isMotionArtifact = isMotionArtifact,
            latitude = coordinate?.latitude,
            longitude = coordinate?.longitude
        )
        anomalyDao.insertAnomaly(anomaly)

        val session = sessionDao.getSessionById(sessionId)
        if (session != null) {
            sessionDao.updateSession(
                session.copy(
                    peakMicroTesla = maxOf(session.peakMicroTesla, magnitudeUt),
                    peakDeltaMicroTesla = maxOf(session.peakDeltaMicroTesla, kotlin.math.abs(deltaUt)),
                    anomalyCount = session.anomalyCount + 1,
                    latitude = session.latitude ?: coordinate?.latitude,
                    longitude = session.longitude ?: coordinate?.longitude,
                    gpsAccuracyMeters = session.gpsAccuracyMeters ?: coordinate?.accuracyMeters
                )
            )
        }
    }

    suspend fun getAnomaliesForSessionOnce(sessionId: Long): List<EmfAnomalyEntity> =
        anomalyDao.getAnomaliesForSessionOnce(sessionId)

    suspend fun saveAudioClip(clip: AudioClipEntity): Long = audioClipDao.insertClip(clip)

    suspend fun markClipAnalyzed(clipId: Long, analyzed: Boolean) =
        audioClipDao.markAnalyzed(clipId, analyzed)

    suspend fun updateClipAutoMetadata(
        clipId: Long,
        autoTags: String,
        speechProbabilityPct: Int,
        analyzed: Boolean = true
    ) = audioClipDao.updateClipAutoMetadata(clipId, autoTags, speechProbabilityPct, analyzed)

    suspend fun insertTrendSample(sample: SensorTrendSampleEntity): Long =
        sensorTrendDao.insertSample(sample)

    suspend fun pruneTrendSamplesOlderThan(cutoffMs: Long) =
        sensorTrendDao.pruneOlderThan(cutoffMs)

    suspend fun replaceSegmentsForClip(clipId: Long, segments: List<FlaggedAudioSegmentEntity>) {
        flaggedSegmentDao.deleteSegmentsForClip(clipId)
        for (seg in segments) {
            flaggedSegmentDao.insertSegment(seg)
        }
        audioClipDao.markAnalyzed(clipId, true)
    }

    suspend fun deleteFlaggedSegment(segment: FlaggedAudioSegmentEntity) {
        withContext(Dispatchers.IO) {
            runCatching {
                val f = File(segment.segmentFilePath)
                if (f.exists()) f.delete()
            }
        }
        flaggedSegmentDao.deleteSegmentById(segment.id)
    }

    suspend fun deleteAudioClip(clip: AudioClipEntity) {
        withContext(Dispatchers.IO) {
            runCatching {
                val f = File(clip.filePath)
                if (f.exists()) f.delete()
            }
        }
        audioClipDao.deleteClipById(clip.id)
    }

    suspend fun insertJournalEntry(entry: JournalEntryEntity): Long = journalDao.insertEntry(entry)

    suspend fun deleteJournalEntry(entryId: Long) = journalDao.deleteEntryById(entryId)

    suspend fun buildJournalCsvReport(sessionId: Long? = null): String = withContext(Dispatchers.IO) {
        val entries = if (sessionId != null) {
            journalDao.getEntriesForSessionOnce(sessionId)
        } else {
            journalDao.getAllEntriesOnce()
        }
        val isoFmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US)

        buildString {
            appendLine("# GEOPORT EMF LOCATION JOURNAL (CSV)")
            appendLine("entry_id,session_id,timestamp_iso,latitude,longitude,gps_accuracy_m,location_label,tags,emf_magnitude_ut,emf_delta_ut,associated_anomaly,associated_clip,note_text")
            for (e in entries) {
                appendLine(
                    listOf(
                        e.id.toString(),
                        e.sessionId?.toString() ?: "",
                        isoFmt.format(Date(e.timestampMs)),
                        e.latitude?.let { "%.6f".format(Locale.US, it) } ?: "",
                        e.longitude?.let { "%.6f".format(Locale.US, it) } ?: "",
                        e.gpsAccuracyMeters?.let { "%.1f".format(Locale.US, it) } ?: "",
                        escapeCsv(e.locationLabel),
                        escapeCsv(e.tags),
                        "%.3f".format(Locale.US, e.emfMagnitudeUt),
                        "%.3f".format(Locale.US, e.emfDeltaUt),
                        escapeCsv(e.associatedAnomalySummary),
                        escapeCsv(e.associatedClipName),
                        escapeCsv(e.noteText)
                    ).joinToString(",")
                )
            }
        }
    }

    suspend fun buildJournalJsonReport(sessionId: Long? = null): String = withContext(Dispatchers.IO) {
        val entries = if (sessionId != null) {
            journalDao.getEntriesForSessionOnce(sessionId)
        } else {
            journalDao.getAllEntriesOnce()
        }
        val isoFmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US)

        buildString {
            appendLine("{")
            appendLine("  \"journalEntries\": [")
            entries.forEachIndexed { idx, e ->
                val comma = if (idx < entries.lastIndex) "," else ""
                appendLine("    {")
                appendLine("      \"id\": ${e.id},")
                appendLine("      \"sessionId\": ${e.sessionId ?: "null"},")
                appendLine("      \"timestampIso\": \"${isoFmt.format(Date(e.timestampMs))}\",")
                appendLine("      \"latitude\": ${e.latitude ?: "null"},")
                appendLine("      \"longitude\": ${e.longitude ?: "null"},")
                appendLine("      \"gpsAccuracyMeters\": ${e.gpsAccuracyMeters ?: "null"},")
                appendLine("      \"locationLabel\": \"${escapeJson(e.locationLabel)}\",")
                appendLine("      \"tags\": \"${escapeJson(e.tags)}\",")
                appendLine("      \"emfMagnitudeUt\": ${"%.3f".format(Locale.US, e.emfMagnitudeUt)},")
                appendLine("      \"emfDeltaUt\": ${"%.3f".format(Locale.US, e.emfDeltaUt)},")
                appendLine("      \"associatedAnomaly\": \"${escapeJson(e.associatedAnomalySummary)}\",")
                appendLine("      \"associatedClip\": \"${escapeJson(e.associatedClipName)}\",")
                appendLine("      \"noteText\": \"${escapeJson(e.noteText)}\"")
                appendLine("    }$comma")
            }
            appendLine("  ]")
            appendLine("}")
        }
    }

    suspend fun buildSessionCsvReport(sessionId: Long): String = withContext(Dispatchers.IO) {
        val session = sessionDao.getSessionById(sessionId)
        val anomalies = anomalyDao.getAnomaliesForSessionOnce(sessionId)
        val clips = audioClipDao.getClipsForSessionOnce(sessionId)
        val segments = flaggedSegmentDao.getSegmentsForSessionOnce(sessionId)
        val journal = journalDao.getEntriesForSessionOnce(sessionId)
        val trendSamples = sensorTrendDao.getSamplesForSessionOnce(sessionId)
        val isoFmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US)

        buildString {
            appendLine("# GEOPORT EMF INVESTIGATION LOG (CSV)")
            if (session != null) {
                appendLine("# Session Title,${escapeCsv(session.title)}")
                appendLine("# Location,${escapeCsv(session.locationName)}")
                if (session.latitude != null && session.longitude != null) {
                    appendLine("# GPS Coordinates,${"%.6f".format(Locale.US, session.latitude)},${"%.6f".format(Locale.US, session.longitude)}")
                }
                appendLine("# Start Time,${isoFmt.format(Date(session.startTimeMs))}")
                appendLine("# End Time,${session.endTimeMs?.let { isoFmt.format(Date(it)) } ?: "ACTIVE"}")
                appendLine("# Baseline (uT),${"%.2f".format(Locale.US, session.baselineMicroTesla)}")
                appendLine("# Peak Magnitude (uT),${"%.2f".format(Locale.US, session.peakMicroTesla)}")
                appendLine("# Peak Delta (uT),${"%.2f".format(Locale.US, session.peakDeltaMicroTesla)}")
                appendLine("# Anomaly Count,${session.anomalyCount}")
                if (session.notes.isNotBlank()) {
                    appendLine("# Notes,${escapeCsv(session.notes)}")
                }
            }
            appendLine()
            appendLine("record_type,timestamp_iso,epoch_ms,latitude,longitude,magnitude_ut,delta_ut,axis_x_ut,axis_y_ut,axis_z_ut,severity_or_class,motion_or_duration_ms,note_or_details")
            for (a in anomalies) {
                appendLine(
                    listOf(
                        "EMF_ANOMALY",
                        isoFmt.format(Date(a.timestampMs)),
                        a.timestampMs.toString(),
                        a.latitude?.let { "%.6f".format(Locale.US, it) } ?: "",
                        a.longitude?.let { "%.6f".format(Locale.US, it) } ?: "",
                        "%.3f".format(Locale.US, a.magnitudeUt),
                        "%.3f".format(Locale.US, a.deltaUt),
                        "%.3f".format(Locale.US, a.xUt),
                        "%.3f".format(Locale.US, a.yUt),
                        "%.3f".format(Locale.US, a.zUt),
                        escapeCsv(a.severity),
                        a.isMotionArtifact.toString(),
                        escapeCsv(a.note)
                    ).joinToString(",")
                )
            }
            for (c in clips) {
                val tagSuffix = if (c.autoTags.isNotBlank()) " | Tags:[${c.autoTags}]" else ""
                val tempSuffix = if (c.tempDropC <= -0.1f) " | TempDrop:${"%.1f".format(Locale.US, c.tempDropC)}C" else ""
                val speechSuffix = if (c.speechProbabilityPct > 0) " | SpeechProb:${c.speechProbabilityPct}%" else ""
                appendLine(
                    listOf(
                        "AUDIO_CLIP",
                        isoFmt.format(Date(c.createdAtMs)),
                        c.createdAtMs.toString(),
                        c.latitude?.let { "%.6f".format(Locale.US, it) } ?: "",
                        c.longitude?.let { "%.6f".format(Locale.US, it) } ?: "",
                        "%.3f".format(Locale.US, c.peakEmfDuringClip),
                        "%.3f".format(Locale.US, c.peakEmfDeltaDuringClip),
                        "",
                        "",
                        "",
                        escapeCsv(c.modeLabel),
                        c.durationMs.toString(),
                        escapeCsv("${c.fileName} | ${c.markersSummary}$tagSuffix$tempSuffix$speechSuffix")
                    ).joinToString(",")
                )
            }
            for (s in segments) {
                appendLine(
                    listOf(
                        "FLAGGED_EVP_SEGMENT",
                        isoFmt.format(Date(s.timestampMs)),
                        s.timestampMs.toString(),
                        "",
                        "",
                        "%.3f".format(Locale.US, s.emfMagnitudeUt),
                        "%.3f".format(Locale.US, s.emfDeltaUt),
                        "",
                        "",
                        "",
                        escapeCsv("${s.acousticClass} (${s.confidenceScore}%)"),
                        (s.endOffsetMs - s.startOffsetMs).toString(),
                        escapeCsv("${s.segmentFileName} | SNR +${"%.1f".format(Locale.US, s.snrDb)}dB | ${s.notes}")
                    ).joinToString(",")
                )
            }
            for (j in journal) {
                appendLine(
                    listOf(
                        "JOURNAL_ENTRY",
                        isoFmt.format(Date(j.timestampMs)),
                        j.timestampMs.toString(),
                        j.latitude?.let { "%.6f".format(Locale.US, it) } ?: "",
                        j.longitude?.let { "%.6f".format(Locale.US, it) } ?: "",
                        "%.3f".format(Locale.US, j.emfMagnitudeUt),
                        "%.3f".format(Locale.US, j.emfDeltaUt),
                        "",
                        "",
                        "",
                        escapeCsv("${j.locationLabel} [${j.tags}]"),
                        "",
                        escapeCsv("${j.noteText} | EMF:${j.associatedAnomalySummary} | Clip:${j.associatedClipName}")
                    ).joinToString(",")
                )
            }
            for (t in trendSamples) {
                appendLine(
                    listOf(
                        "TELEMETRY_SAMPLE",
                        isoFmt.format(Date(t.timestampMs)),
                        t.timestampMs.toString(),
                        "",
                        "",
                        "%.3f".format(Locale.US, t.emfMagnitudeUt),
                        "%.3f".format(Locale.US, t.emfDeltaUt),
                        "%.2f".format(Locale.US, t.temperatureC),
                        "%.2f".format(Locale.US, t.tempDeltaC),
                        "",
                        escapeCsv(t.tempSource),
                        "",
                        escapeCsv("EMF + Temp Trend Sample")
                    ).joinToString(",")
                )
            }
        }
    }

    suspend fun buildSessionJsonReport(sessionId: Long): String = withContext(Dispatchers.IO) {
        val session = sessionDao.getSessionById(sessionId)
        val anomalies = anomalyDao.getAnomaliesForSessionOnce(sessionId)
        val clips = audioClipDao.getClipsForSessionOnce(sessionId)
        val segments = flaggedSegmentDao.getSegmentsForSessionOnce(sessionId)
        val journal = journalDao.getEntriesForSessionOnce(sessionId)
        val trendSamples = sensorTrendDao.getSamplesForSessionOnce(sessionId)
        val isoFmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US)

        buildString {
            appendLine("{")
            if (session != null) {
                appendLine("  \"session\": {")
                appendLine("    \"id\": ${session.id},")
                appendLine("    \"title\": \"${escapeJson(session.title)}\",")
                appendLine("    \"location\": \"${escapeJson(session.locationName)}\",")
                appendLine("    \"latitude\": ${session.latitude ?: "null"},")
                appendLine("    \"longitude\": ${session.longitude ?: "null"},")
                appendLine("    \"gpsAccuracyMeters\": ${session.gpsAccuracyMeters ?: "null"},")
                appendLine("    \"notes\": \"${escapeJson(session.notes)}\",")
                appendLine("    \"startTimeIso\": \"${isoFmt.format(Date(session.startTimeMs))}\",")
                appendLine("    \"endTimeIso\": ${session.endTimeMs?.let { "\"${isoFmt.format(Date(it))}\"" } ?: "null"},")
                appendLine("    \"baselineMicroTesla\": ${"%.3f".format(Locale.US, session.baselineMicroTesla)},")
                appendLine("    \"peakMicroTesla\": ${"%.3f".format(Locale.US, session.peakMicroTesla)},")
                appendLine("    \"peakDeltaMicroTesla\": ${"%.3f".format(Locale.US, session.peakDeltaMicroTesla)},")
                appendLine("    \"anomalyCount\": ${session.anomalyCount}")
                appendLine("  },")
            }
            appendLine("  \"anomalies\": [")
            anomalies.forEachIndexed { idx, a ->
                val comma = if (idx < anomalies.lastIndex) "," else ""
                appendLine("    {")
                appendLine("      \"timestampMs\": ${a.timestampMs},")
                appendLine("      \"timestampIso\": \"${isoFmt.format(Date(a.timestampMs))}\",")
                appendLine("      \"latitude\": ${a.latitude ?: "null"},")
                appendLine("      \"longitude\": ${a.longitude ?: "null"},")
                appendLine("      \"magnitudeUt\": ${"%.3f".format(Locale.US, a.magnitudeUt)},")
                appendLine("      \"deltaUt\": ${"%.3f".format(Locale.US, a.deltaUt)},")
                appendLine("      \"xUt\": ${"%.3f".format(Locale.US, a.xUt)},")
                appendLine("      \"yUt\": ${"%.3f".format(Locale.US, a.yUt)},")
                appendLine("      \"zUt\": ${"%.3f".format(Locale.US, a.zUt)},")
                appendLine("      \"severity\": \"${escapeJson(a.severity)}\",")
                appendLine("      \"isMotionArtifact\": ${a.isMotionArtifact},")
                appendLine("      \"note\": \"${escapeJson(a.note)}\"")
                appendLine("    }$comma")
            }
            appendLine("  ],")
            appendLine("  \"audioClips\": [")
            clips.forEachIndexed { idx, c ->
                val comma = if (idx < clips.lastIndex) "," else ""
                appendLine("    {")
                appendLine("      \"fileName\": \"${escapeJson(c.fileName)}\",")
                appendLine("      \"createdAtIso\": \"${isoFmt.format(Date(c.createdAtMs))}\",")
                appendLine("      \"latitude\": ${c.latitude ?: "null"},")
                appendLine("      \"longitude\": ${c.longitude ?: "null"},")
                appendLine("      \"durationMs\": ${c.durationMs},")
                appendLine("      \"mode\": \"${escapeJson(c.modeLabel)}\",")
                appendLine("      \"peakEmfUt\": ${"%.3f".format(Locale.US, c.peakEmfDuringClip)},")
                appendLine("      \"peakEmfDeltaUt\": ${"%.3f".format(Locale.US, c.peakEmfDeltaDuringClip)},")
                appendLine("      \"minTempC\": ${c.minTempC?.let { "%.2f".format(Locale.US, it) } ?: "null"},")
                appendLine("      \"tempDropC\": ${"%.2f".format(Locale.US, c.tempDropC)},")
                appendLine("      \"speechProbabilityPct\": ${c.speechProbabilityPct},")
                appendLine("      \"autoTags\": \"${escapeJson(c.autoTags)}\",")
                appendLine("      \"fileSizeBytes\": ${c.fileSizeBytes},")
                appendLine("      \"markers\": \"${escapeJson(c.markersSummary)}\"")
                appendLine("    }$comma")
            }
            appendLine("  ],")
            appendLine("  \"flaggedEvpSegments\": [")
            segments.forEachIndexed { idx, s ->
                val comma = if (idx < segments.lastIndex) "," else ""
                appendLine("    {")
                appendLine("      \"segmentFileName\": \"${escapeJson(s.segmentFileName)}\",")
                appendLine("      \"timestampIso\": \"${isoFmt.format(Date(s.timestampMs))}\",")
                appendLine("      \"startOffsetMs\": ${s.startOffsetMs},")
                appendLine("      \"endOffsetMs\": ${s.endOffsetMs},")
                appendLine("      \"acousticClass\": \"${escapeJson(s.acousticClass)}\",")
                appendLine("      \"snrDb\": ${"%.2f".format(Locale.US, s.snrDb)},")
                appendLine("      \"peakAudioDb\": ${"%.2f".format(Locale.US, s.peakAudioDb)},")
                appendLine("      \"emfMagnitudeUt\": ${"%.3f".format(Locale.US, s.emfMagnitudeUt)},")
                appendLine("      \"emfDeltaUt\": ${"%.3f".format(Locale.US, s.emfDeltaUt)},")
                appendLine("      \"confidenceScore\": ${s.confidenceScore},")
                appendLine("      \"notes\": \"${escapeJson(s.notes)}\"")
                appendLine("    }$comma")
            }
            appendLine("  ],")
            appendLine("  \"journalEntries\": [")
            journal.forEachIndexed { idx, j ->
                val comma = if (idx < journal.lastIndex) "," else ""
                appendLine("    {")
                appendLine("      \"timestampIso\": \"${isoFmt.format(Date(j.timestampMs))}\",")
                appendLine("      \"latitude\": ${j.latitude ?: "null"},")
                appendLine("      \"longitude\": ${j.longitude ?: "null"},")
                appendLine("      \"gpsAccuracyMeters\": ${j.gpsAccuracyMeters ?: "null"},")
                appendLine("      \"locationLabel\": \"${escapeJson(j.locationLabel)}\",")
                appendLine("      \"tags\": \"${escapeJson(j.tags)}\",")
                appendLine("      \"emfMagnitudeUt\": ${"%.3f".format(Locale.US, j.emfMagnitudeUt)},")
                appendLine("      \"emfDeltaUt\": ${"%.3f".format(Locale.US, j.emfDeltaUt)},")
                appendLine("      \"associatedAnomaly\": \"${escapeJson(j.associatedAnomalySummary)}\",")
                appendLine("      \"associatedClip\": \"${escapeJson(j.associatedClipName)}\",")
                appendLine("      \"noteText\": \"${escapeJson(j.noteText)}\"")
                appendLine("    }$comma")
            }
            appendLine("  ],")
            appendLine("  \"telemetryTrends\": [")
            trendSamples.forEachIndexed { idx, t ->
                val comma = if (idx < trendSamples.lastIndex) "," else ""
                appendLine("    {")
                appendLine("      \"timestampMs\": ${t.timestampMs},")
                appendLine("      \"timestampIso\": \"${isoFmt.format(Date(t.timestampMs))}\",")
                appendLine("      \"emfMagnitudeUt\": ${"%.3f".format(Locale.US, t.emfMagnitudeUt)},")
                appendLine("      \"emfDeltaUt\": ${"%.3f".format(Locale.US, t.emfDeltaUt)},")
                appendLine("      \"temperatureC\": ${"%.2f".format(Locale.US, t.temperatureC)},")
                appendLine("      \"tempDeltaC\": ${"%.2f".format(Locale.US, t.tempDeltaC)},")
                appendLine("      \"tempSource\": \"${escapeJson(t.tempSource)}\"")
                appendLine("    }$comma")
            }
            appendLine("  ]")
            appendLine("}")
        }
    }

    suspend fun writeTempExportFile(fileName: String, content: String): File = withContext(Dispatchers.IO) {
        val exportDir = File(context.cacheDir, "exports")
        if (!exportDir.exists()) exportDir.mkdirs()
        val file = File(exportDir, fileName)
        file.writeText(content, Charsets.UTF_8)
        file
    }

    private fun escapeCsv(raw: String): String {
        val clean = raw.replace("\"", "\"\"")
        return "\"$clean\""
    }

    private fun escapeJson(raw: String): String {
        return raw
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
    }
}
