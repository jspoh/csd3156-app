package com.example.csd3156_app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "daily_seed_cache")
data class DailySeedCacheEntity(
    @PrimaryKey
    val challengeDate: String,
    val seed: Long,
    val fetchedAt: Long
)
