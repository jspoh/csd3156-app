package com.example.csd3156_app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "high_scores")
data class HighScoreEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val score: Int,
    val achievedAt: Long,
    val mode: String
)
