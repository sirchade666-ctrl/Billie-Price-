package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Query("SELECT * FROM investigation_sessions ORDER BY startTimeMs DESC")
    fun observeAllSessions(): Flow<List<InvestigationSessionEntity>>

    @Query("SELECT * FROM investigation_sessions WHERE isActive = 1 ORDER BY startTimeMs DESC LIMIT 1")
    fun observeActiveSession(): Flow<InvestigationSessionEntity?>

    @Query("SELECT * FROM investigation_sessions WHERE id = :sessionId LIMIT 1")
    suspend fun getSessionById(sessionId: Long): InvestigationSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: InvestigationSessionEntity): Long

    @Update
    suspend fun updateSession(session: InvestigationSessionEntity)

    @Query("UPDATE investigation_sessions SET isActive = 0, endTimeMs = :endTimeMs WHERE isActive = 1")
    suspend fun closeAllActiveSessions(endTimeMs: Long)

    @Query("DELETE FROM investigation_sessions WHERE id = :sessionId")
    suspend fun deleteSessionById(sessionId: Long)
}

@Dao
interface AnomalyDao {
    @Query("SELECT * FROM emf_anomalies WHERE sessionId = :sessionId ORDER BY timestampMs DESC")
    fun observeAnomaliesForSession(sessionId: Long): Flow<List<EmfAnomalyEntity>>

    @Query("SELECT * FROM emf_anomalies WHERE sessionId = :sessionId ORDER BY timestampMs ASC")
    suspend fun getAnomaliesForSessionOnce(sessionId: Long): List<EmfAnomalyEntity>

    @Query("SELECT * FROM emf_anomalies ORDER BY timestampMs DESC LIMIT 30")
    fun observeRecentAnomalies(): Flow<List<EmfAnomalyEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnomaly(anomaly: EmfAnomalyEntity): Long

    @Query("DELETE FROM emf_anomalies WHERE id = :anomalyId")
    suspend fun deleteAnomalyById(anomalyId: Long)
}

@Dao
interface AudioClipDao {
    @Query("SELECT * FROM audio_clips ORDER BY createdAtMs DESC")
    fun observeAllClips(): Flow<List<AudioClipEntity>>

    @Query("SELECT * FROM audio_clips WHERE sessionId = :sessionId ORDER BY createdAtMs DESC")
    fun observeClipsForSession(sessionId: Long): Flow<List<AudioClipEntity>>

    @Query("SELECT * FROM audio_clips WHERE sessionId = :sessionId ORDER BY createdAtMs ASC")
    suspend fun getClipsForSessionOnce(sessionId: Long): List<AudioClipEntity>

    @Query("UPDATE audio_clips SET isAnalyzed = :analyzed WHERE id = :clipId")
    suspend fun markAnalyzed(clipId: Long, analyzed: Boolean)

    @Query(
        "UPDATE audio_clips SET autoTags = :autoTags, speechProbabilityPct = :speechProbabilityPct, isAnalyzed = :analyzed WHERE id = :clipId"
    )
    suspend fun updateClipAutoMetadata(
        clipId: Long,
        autoTags: String,
        speechProbabilityPct: Int,
        analyzed: Boolean
    )

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertClip(clip: AudioClipEntity): Long

    @Query("DELETE FROM audio_clips WHERE id = :clipId")
    suspend fun deleteClipById(clipId: Long)
}

@Dao
interface FlaggedSegmentDao {
    @Query("SELECT * FROM flagged_audio_segments ORDER BY timestampMs DESC")
    fun observeAllFlaggedSegments(): Flow<List<FlaggedAudioSegmentEntity>>

    @Query("SELECT * FROM flagged_audio_segments WHERE clipId = :clipId ORDER BY startOffsetMs ASC")
    fun observeSegmentsForClip(clipId: Long): Flow<List<FlaggedAudioSegmentEntity>>

    @Query("SELECT * FROM flagged_audio_segments WHERE sessionId = :sessionId ORDER BY timestampMs ASC")
    suspend fun getSegmentsForSessionOnce(sessionId: Long): List<FlaggedAudioSegmentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSegment(segment: FlaggedAudioSegmentEntity): Long

    @Query("DELETE FROM flagged_audio_segments WHERE clipId = :clipId")
    suspend fun deleteSegmentsForClip(clipId: Long)

    @Query("DELETE FROM flagged_audio_segments WHERE id = :segmentId")
    suspend fun deleteSegmentById(segmentId: Long)
}

@Dao
interface JournalDao {
    @Query("SELECT * FROM journal_entries ORDER BY timestampMs DESC")
    fun observeAllJournalEntries(): Flow<List<JournalEntryEntity>>

    @Query("SELECT * FROM journal_entries WHERE sessionId = :sessionId ORDER BY timestampMs DESC")
    fun observeEntriesForSession(sessionId: Long): Flow<List<JournalEntryEntity>>

    @Query("SELECT * FROM journal_entries WHERE sessionId = :sessionId ORDER BY timestampMs ASC")
    suspend fun getEntriesForSessionOnce(sessionId: Long): List<JournalEntryEntity>

    @Query("SELECT * FROM journal_entries ORDER BY timestampMs ASC")
    suspend fun getAllEntriesOnce(): List<JournalEntryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: JournalEntryEntity): Long

    @Query("DELETE FROM journal_entries WHERE id = :entryId")
    suspend fun deleteEntryById(entryId: Long)
}

@Dao
interface SensorTrendDao {
    @Query("SELECT * FROM (SELECT * FROM sensor_trend_samples ORDER BY timestampMs DESC LIMIT :limit) ORDER BY timestampMs ASC")
    fun observeRecentSamples(limit: Int = 2400): Flow<List<SensorTrendSampleEntity>>

    @Query("SELECT * FROM sensor_trend_samples WHERE sessionId = :sessionId ORDER BY timestampMs ASC")
    fun observeSamplesForSession(sessionId: Long): Flow<List<SensorTrendSampleEntity>>

    @Query("SELECT * FROM sensor_trend_samples WHERE sessionId = :sessionId ORDER BY timestampMs ASC")
    suspend fun getSamplesForSessionOnce(sessionId: Long): List<SensorTrendSampleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSample(sample: SensorTrendSampleEntity): Long

    @Query("DELETE FROM sensor_trend_samples WHERE timestampMs < :cutoffMs")
    suspend fun pruneOlderThan(cutoffMs: Long)
}
