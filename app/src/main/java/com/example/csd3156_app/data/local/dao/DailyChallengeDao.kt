package com.example.csd3156_app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.csd3156_app.data.local.entity.DailySeedCacheEntity
import com.example.csd3156_app.data.local.entity.LeaderboardEntryCacheEntity
import com.example.csd3156_app.data.local.entity.PendingScoreUploadEntity

@Dao
interface DailyChallengeDao {
    @Query("SELECT * FROM daily_seed_cache WHERE challengeDate = :challengeDate LIMIT 1")
    suspend fun getDailySeed(challengeDate: String): DailySeedCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDailySeed(entity: DailySeedCacheEntity)

    @Query(
        """
        SELECT * FROM leaderboard_cache
        WHERE challengeDate = :challengeDate
        ORDER BY score DESC, submittedAt ASC
        LIMIT :limit
        """
    )
    suspend fun getLeaderboard(challengeDate: String, limit: Int): List<LeaderboardEntryCacheEntity>

    @Query("DELETE FROM leaderboard_cache WHERE challengeDate = :challengeDate")
    suspend fun clearLeaderboard(challengeDate: String)

    @Insert
    suspend fun insertLeaderboard(entries: List<LeaderboardEntryCacheEntity>)

    @Query("SELECT * FROM pending_score_uploads ORDER BY createdAt ASC")
    suspend fun getPendingUploads(): List<PendingScoreUploadEntity>

    @Insert
    suspend fun insertPendingUpload(entity: PendingScoreUploadEntity): Long

    @Query("DELETE FROM pending_score_uploads WHERE id = :id")
    suspend fun deletePendingUpload(id: Long)

    @Query(
        """
        UPDATE pending_score_uploads
        SET attempts = :attempts
        WHERE id = :id
        """
    )
    suspend fun updatePendingAttempts(id: Long, attempts: Int)
}
