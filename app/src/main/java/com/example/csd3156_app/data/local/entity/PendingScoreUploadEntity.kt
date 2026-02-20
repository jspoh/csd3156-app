package com.example.csd3156_app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pending_score_uploads")
data class PendingScoreUploadEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val challengeDate: String,
    val seed: Long,
    val score: Int,
    val playerName: String,
    val createdAt: Long,
    val attempts: Int
)
