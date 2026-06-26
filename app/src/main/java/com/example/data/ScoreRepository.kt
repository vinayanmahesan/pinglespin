package com.example.data

import kotlinx.coroutines.flow.Flow

class ScoreRepository(private val scoreDao: ScoreDao) {
    val topScores: Flow<List<ScoreEntity>> = scoreDao.getTopScores()

    suspend fun insertScore(score: ScoreEntity) {
        scoreDao.insertScore(score)
    }

    suspend fun clearScores() {
        scoreDao.clearAllScores()
    }
}
