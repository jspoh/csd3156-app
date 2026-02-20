package com.example.csd3156_app.data

import com.example.csd3156_app.game.Direction
import com.example.csd3156_app.game.GameMode

data class PersistedSettings(
    val tiltEnabled: Boolean,
    val sensitivity: Float,
    val calibrationBaselineX: Float,
    val calibrationBaselineY: Float
)

data class PersistedSession(
    val id: Long,
    val board: List<Int>,
    val score: Int,
    val isFinished: Boolean,
    val mode: GameMode,
    val dailyDate: String?,
    val dailySeed: Long?
)

data class LeaderboardEntry(
    val playerName: String,
    val score: Int,
    val submittedAt: Long
)

data class DailyScoreUpload(
    val challengeDate: String,
    val seed: Long,
    val score: Int,
    val playerName: String
)

interface GameRepository : AutoCloseable {
    suspend fun loadSettings(): PersistedSettings

    suspend fun saveSettings(settings: PersistedSettings)

    suspend fun getLatestUnfinishedSession(): PersistedSession?

    suspend fun startSession(
        board: List<Int>,
        score: Int,
        mode: GameMode,
        dailyDate: String? = null,
        dailySeed: Long? = null
    ): Long

    suspend fun saveSessionSnapshot(
        sessionId: Long,
        board: List<Int>,
        score: Int,
        isFinished: Boolean
    )

    suspend fun markSessionFinished(sessionId: Long, score: Int)

    suspend fun addHighScore(score: Int, mode: String)

    suspend fun addMoveEvent(sessionId: Long, direction: Direction, scoreAfter: Int)

    suspend fun getOrFetchDailySeed(challengeDate: String): Long

    suspend fun fetchLeaderboard(challengeDate: String, limit: Int): List<LeaderboardEntry>

    suspend fun queueDailyScoreUpload(upload: DailyScoreUpload)

    suspend fun uploadDailyScore(upload: DailyScoreUpload)

    suspend fun retryPendingDailyUploads()
}
