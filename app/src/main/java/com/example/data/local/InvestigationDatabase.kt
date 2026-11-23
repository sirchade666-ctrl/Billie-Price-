package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        InvestigationSessionEntity::class,
        EmfAnomalyEntity::class,
        AudioClipEntity::class,
        FlaggedAudioSegmentEntity::class,
        JournalEntryEntity::class,
        SensorTrendSampleEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class InvestigationDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun anomalyDao(): AnomalyDao
    abstract fun audioClipDao(): AudioClipDao
    abstract fun flaggedSegmentDao(): FlaggedSegmentDao
    abstract fun journalDao(): JournalDao
    abstract fun sensorTrendDao(): SensorTrendDao

    companion object {
        @Volatile
        private var INSTANCE: InvestigationDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE investigation_sessions ADD COLUMN latitude REAL")
                db.execSQL("ALTER TABLE investigation_sessions ADD COLUMN longitude REAL")
                db.execSQL("ALTER TABLE investigation_sessions ADD COLUMN gpsAccuracyMeters REAL")

                db.execSQL("ALTER TABLE emf_anomalies ADD COLUMN latitude REAL")
                db.execSQL("ALTER TABLE emf_anomalies ADD COLUMN longitude REAL")

                db.execSQL("ALTER TABLE audio_clips ADD COLUMN latitude REAL")
                db.execSQL("ALTER TABLE audio_clips ADD COLUMN longitude REAL")
                db.execSQL("ALTER TABLE audio_clips ADD COLUMN isAnalyzed INTEGER NOT NULL DEFAULT 0")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS flagged_audio_segments (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        clipId INTEGER NOT NULL,
                        sessionId INTEGER,
                        segmentFileName TEXT NOT NULL,
                        segmentFilePath TEXT NOT NULL,
                        timestampMs INTEGER NOT NULL,
                        startOffsetMs INTEGER NOT NULL,
                        endOffsetMs INTEGER NOT NULL,
                        peakAudioDb REAL NOT NULL,
                        snrDb REAL NOT NULL,
                        acousticClass TEXT NOT NULL,
                        emfMagnitudeUt REAL NOT NULL,
                        emfDeltaUt REAL NOT NULL,
                        confidenceScore INTEGER NOT NULL,
                        notes TEXT NOT NULL,
                        FOREIGN KEY(clipId) REFERENCES audio_clips(id) ON DELETE CASCADE,
                        FOREIGN KEY(sessionId) REFERENCES investigation_sessions(id) ON DELETE SET NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_flagged_audio_segments_clipId ON flagged_audio_segments(clipId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_flagged_audio_segments_sessionId ON flagged_audio_segments(sessionId)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS journal_entries (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        sessionId INTEGER,
                        timestampMs INTEGER NOT NULL,
                        latitude REAL,
                        longitude REAL,
                        gpsAccuracyMeters REAL,
                        locationLabel TEXT NOT NULL,
                        noteText TEXT NOT NULL,
                        tags TEXT NOT NULL,
                        emfMagnitudeUt REAL NOT NULL,
                        emfDeltaUt REAL NOT NULL,
                        associatedAnomalyId INTEGER,
                        associatedAnomalySummary TEXT NOT NULL,
                        associatedClipId INTEGER,
                        associatedClipName TEXT NOT NULL,
                        FOREIGN KEY(sessionId) REFERENCES investigation_sessions(id) ON DELETE SET NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_journal_entries_sessionId ON journal_entries(sessionId)")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE audio_clips ADD COLUMN peakEmfDeltaDuringClip REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE audio_clips ADD COLUMN minTempC REAL")
                db.execSQL("ALTER TABLE audio_clips ADD COLUMN tempDropC REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE audio_clips ADD COLUMN speechProbabilityPct INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE audio_clips ADD COLUMN autoTags TEXT NOT NULL DEFAULT ''")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS sensor_trend_samples (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        sessionId INTEGER,
                        timestampMs INTEGER NOT NULL,
                        emfMagnitudeUt REAL NOT NULL,
                        emfDeltaUt REAL NOT NULL,
                        temperatureC REAL NOT NULL,
                        tempDeltaC REAL NOT NULL,
                        tempSource TEXT NOT NULL,
                        FOREIGN KEY(sessionId) REFERENCES investigation_sessions(id) ON DELETE SET NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_sensor_trend_samples_sessionId ON sensor_trend_samples(sessionId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_sensor_trend_samples_timestampMs ON sensor_trend_samples(timestampMs)")
            }
        }

        fun getInstance(context: Context): InvestigationDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    InvestigationDatabase::class.java,
                    "geoport_investigation.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
