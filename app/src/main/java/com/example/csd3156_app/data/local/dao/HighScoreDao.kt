package com.example.csd3156_app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import com.example.csd3156_app.data.local.entity.HighScoreEntity

@Dao
interface HighScoreDao {
    @Insert
    suspend fun insert(entity: HighScoreEntity): Long
}
