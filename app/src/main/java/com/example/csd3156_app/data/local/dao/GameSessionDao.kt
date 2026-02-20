package com.example.csd3156_app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.csd3156_app.data.local.entity.GameSessionEntity

@Dao
interface GameSessionDao {
    @Query(
        """
        SELECT * FROM game_sessions
        WHERE isFinished = 0
        ORDER BY lastUpdatedAt DESC
        LIMIT 1
        """
    )
    suspend fun getLatestUnfinishedSession(): GameSessionEntity?

    @Insert
    suspend fun insert(entity: GameSessionEntity): Long

    @Query(
        """
        UPDATE game_sessions
        SET boardState = :boardState,
            score = :score,
            lastUpdatedAt = :lastUpdatedAt,
            isFinished = :isFinished
        WHERE id = :sessionId
        """
    )
    suspend fun updateSnapshot(
        sessionId: Long,
        boardState: String,
        score: Int,
        lastUpdatedAt: Long,
        isFinished: Boolean
    )

    @Query(
        """
        UPDATE game_sessions
        SET score = :score,
            lastUpdatedAt = :lastUpdatedAt,
            isFinished = 1
        WHERE id = :sessionId
        """
    )
    suspend fun markFinished(
        sessionId: Long,
        score: Int,
        lastUpdatedAt: Long
    )
}
