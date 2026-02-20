package com.example.csd3156_app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "game_sessions")
data class GameSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val boardState: String,
    val score: Int,
    val startedAt: Long,
    val lastUpdatedAt: Long,
    val isFinished: Boolean,
    val mode: String,
    val dailyDate: String?,
    val dailySeed: Long?
)
