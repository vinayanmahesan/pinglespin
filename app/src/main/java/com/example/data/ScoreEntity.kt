package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pingle_scores")
data class ScoreEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val durationMs: Long,
    val timestamp: Long = System.currentTimeMillis(),
    val isManual: Boolean = false
)
