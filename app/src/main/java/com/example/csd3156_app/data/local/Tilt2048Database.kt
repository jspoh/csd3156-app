package com.example.csd3156_app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.csd3156_app.data.local.dao.DailyChallengeDao
import com.example.csd3156_app.data.local.dao.GameSessionDao
import com.example.csd3156_app.data.local.dao.HighScoreDao
import com.example.csd3156_app.data.local.dao.MoveEventDao
import com.example.csd3156_app.data.local.dao.SettingsDao
import com.example.csd3156_app.data.local.entity.DailySeedCacheEntity
import com.example.csd3156_app.data.local.entity.GameSessionEntity
import com.example.csd3156_app.data.local.entity.HighScoreEntity
import com.example.csd3156_app.data.local.entity.LeaderboardEntryCacheEntity
import com.example.csd3156_app.data.local.entity.MoveEventEntity
import com.example.csd3156_app.data.local.entity.PendingScoreUploadEntity
import com.example.csd3156_app.data.local.entity.SettingsEntity

@Database(
    entities = [
        SettingsEntity::class,
        GameSessionEntity::class,
        HighScoreEntity::class,
        MoveEventEntity::class,
        DailySeedCacheEntity::class,
        LeaderboardEntryCacheEntity::class,
        PendingScoreUploadEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class Tilt2048Database : RoomDatabase() {
    abstract fun settingsDao(): SettingsDao
    abstract fun gameSessionDao(): GameSessionDao
    abstract fun highScoreDao(): HighScoreDao
    abstract fun moveEventDao(): MoveEventDao
    abstract fun dailyChallengeDao(): DailyChallengeDao

    companion object {
        private const val DATABASE_NAME = "tilt2048.db"

        private val migration1To2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    ALTER TABLE game_sessions
                    ADD COLUMN mode TEXT NOT NULL DEFAULT 'CLASSIC'
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    ALTER TABLE game_sessions
                    ADD COLUMN dailyDate TEXT
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    ALTER TABLE game_sessions
                    ADD COLUMN dailySeed INTEGER
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS daily_seed_cache (
                        challengeDate TEXT NOT NULL,
                        seed INTEGER NOT NULL,
                        fetchedAt INTEGER NOT NULL,
                        PRIMARY KEY(challengeDate)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS leaderboard_cache (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        challengeDate TEXT NOT NULL,
                        playerName TEXT NOT NULL,
                        score INTEGER NOT NULL,
                        submittedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS index_leaderboard_cache_challengeDate
                    ON leaderboard_cache(challengeDate)
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS pending_score_uploads (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        challengeDate TEXT NOT NULL,
                        seed INTEGER NOT NULL,
                        score INTEGER NOT NULL,
                        playerName TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        attempts INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        private val migration2To3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    ALTER TABLE settings
                    ADD COLUMN shakeToResetEnabled INTEGER NOT NULL DEFAULT 0
                    """.trimIndent()
                )
            }
        }

        fun create(context: Context): Tilt2048Database {
            return Room.databaseBuilder(
                context,
                Tilt2048Database::class.java,
                DATABASE_NAME
            ).addMigrations(
                migration1To2,
                migration2To3
            )
                .build()
        }
    }
}
