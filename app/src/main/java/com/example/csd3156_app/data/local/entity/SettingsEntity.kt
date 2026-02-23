package com.example.csd3156_app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "settings")
data class SettingsEntity(
    @PrimaryKey
    val id: Long = SETTINGS_SINGLETON_ID,
    val tiltEnabled: Boolean,
    val shakeToResetEnabled: Boolean,
    val sensitivity: Float,
    val calibrationBaseline: String,
    val updatedAt: Long
) {
    companion object {
        const val SETTINGS_SINGLETON_ID: Long = 1L
    }
}
