package com.example.csd3156_app.data.network

data class DailySeedResponse(
    val date: String,
    val seed: Long
)

data class LeaderboardEntryDto(
    val playerName: String,
    val score: Int,
    val submittedAt: Long
)

interface DailyLeaderboardApi {
    suspend fun fetchDailySeed(date: String): DailySeedResponse

    suspend fun fetchLeaderboard(date: String, limit: Int): List<LeaderboardEntryDto>

    suspend fun submitDailyScore(
        date: String,
        seed: Long,
        score: Int,
        playerName: String
    )
}
