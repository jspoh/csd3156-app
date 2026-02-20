package com.example.csd3156_app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "leaderboard_cache",
    indices = [Index(value = ["challengeDate"])]
)
data class LeaderboardEntryCacheEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val challengeDate: String,
    val playerName: String,
    val score: Int,
    val submittedAt: Long
)
