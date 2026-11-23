package com.example.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "investigation_sessions")
data class InvestigationSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val locationName: String,
    val notes: String = "",
    val startTimeMs: Long = System.currentTimeMillis(),
    val endTimeMs: Long? = null,
    val baselineMicroTesla: Float = 45f,
    val peakMicroTesla: Float = 0f,
    val peakDeltaMicroTesla: Float = 0f,
    val anomalyCount: Int = 0,
    val isActive: Boolean = true,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val gpsAccuracyMeters: Float? = null
)

@Entity(
    tableName = "emf_anomalies",
    foreignKeys = [
        ForeignKey(
            entity = InvestigationSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["sessionId"])]
)
data class EmfAnomalyEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val sessionId: Long,
    val timestampMs: Long = System.currentTimeMillis(),
    val magnitudeUt: Float,
    val deltaUt: Float,
    val xUt: Float,
    val yUt: Float,
    val zUt: Float,
    val severity: String,
    val note: String = "",
    val isMotionArtifact: Boolean = false,
    val latitude: Double? = null,
    val longitude: Double? = null
)

@Entity(
    tableName = "audio_clips",
    foreignKeys = [
        ForeignKey(
            entity = InvestigationSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index(value = ["sessionId"])]
)
data class AudioClipEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val sessionId: Long? = null,
    val fileName: String,
    val filePath: String,
    val durationMs: Long,
    val createdAtMs: Long = System.currentTimeMillis(),
    val modeLabel: String,
    val peakEmfDuringClip: Float = 0f,
    val markersSummary: String = "",
    val fileSizeBytes: Long = 0L,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val isAnalyzed: Boolean = false,
    val peakEmfDeltaDuringClip: Float = 0f,
    val minTempC: Float? = null,
    val tempDropC: Float = 0f,
    val speechProbabilityPct: Int = 0,
    val autoTags: String = ""
)

@Entity(
    tableName = "flagged_audio_segments",
    foreignKeys = [
        ForeignKey(
            entity = AudioClipEntity::class,
            parentColumns = ["id"],
            childColumns = ["clipId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = InvestigationSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index(value = ["clipId"]), Index(value = ["sessionId"])]
)
data class FlaggedAudioSegmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val clipId: Long,
    val sessionId: Long? = null,
    val segmentFileName: String,
    val segmentFilePath: String,
    val timestampMs: Long,
    val startOffsetMs: Long,
    val endOffsetMs: Long,
    val peakAudioDb: Float,
    val snrDb: Float,
    val acousticClass: String,
    val emfMagnitudeUt: Float,
    val emfDeltaUt: Float,
    val confidenceScore: Int,
    val notes: String = ""
)

@Entity(
    tableName = "journal_entries",
    foreignKeys = [
        ForeignKey(
            entity = InvestigationSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index(value = ["sessionId"])]
)
data class JournalEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val sessionId: Long? = null,
    val timestampMs: Long = System.currentTimeMillis(),
    val latitude: Double? = null,
    val longitude: Double? = null,
    val gpsAccuracyMeters: Float? = null,
    val locationLabel: String,
    val noteText: String,
    val tags: String = "",
    val emfMagnitudeUt: Float,
    val emfDeltaUt: Float,
    val associatedAnomalyId: Long? = null,
    val associatedAnomalySummary: String = "",
    val associatedClipId: Long? = null,
    val associatedClipName: String = ""
)

@Entity(
    tableName = "sensor_trend_samples",
    foreignKeys = [
        ForeignKey(
            entity = InvestigationSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index(value = ["sessionId"]), Index(value = ["timestampMs"])]
)
data class SensorTrendSampleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val sessionId: Long? = null,
    val timestampMs: Long = System.currentTimeMillis(),
    val emfMagnitudeUt: Float,
    val emfDeltaUt: Float,
    val temperatureC: Float,
    val tempDeltaC: Float,
    val tempSource: String = ""
)
