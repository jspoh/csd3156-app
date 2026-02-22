package com.example.csd3156_app.ui.game

import com.example.csd3156_app.data.LeaderboardEntry
import com.example.csd3156_app.game.GameMode
import com.example.csd3156_app.game.TileMovement

data class Tilt2048UiState(
    val board: List<Int> = List(16) { 0 },
    val score: Int = 0,
    val gridSize: Int = 4,
    val hasWon: Boolean = false,
    val isGameOver: Boolean = false,
    val shakeToResetEnabled: Boolean = true,
    val mergedIndices: Set<Int> = emptySet(),
    val tileMovements: List<TileMovement> = emptyList(),
    val moveKey: Int = 0,
    val tiltControlsEnabled: Boolean = false,
    val tiltSensitivity: Float = 1f,
    val tiltDebugDirection: String = "NEUTRAL",
    val tiltDebugMagnitude: Float = 0f,
    val tiltSensorLabel: String = "Unavailable",
    val tiltSensorAvailable: Boolean = false,
    val mode: GameMode = GameMode.CLASSIC,
    val dailyDate: String = "",
    val dailySeed: Long? = null,
    val leaderboard: List<LeaderboardEntry> = emptyList(),
    val leaderboardLoading: Boolean = false,
    val networkMessage: String = "",
    val isSubmittingDailyScore: Boolean = false
)
