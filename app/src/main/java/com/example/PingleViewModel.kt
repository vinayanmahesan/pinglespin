package com.example

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.PingleDatabase
import com.example.data.ScoreEntity
import com.example.data.ScoreRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class Screen {
    HOME, PLAY, HIGH_SCORES, OPTIONS, MANUAL
}

class PingleViewModel(application: Application) : AndroidViewModel(application) {
    private val database = PingleDatabase.getDatabase(application)
    private val repository = ScoreRepository(database.scoreDao())
    private val prefs = application.getSharedPreferences("pingle_prefs", Context.MODE_PRIVATE)

    val currentScreen = MutableStateFlow(Screen.HOME)

    val topScores: StateFlow<List<ScoreEntity>> = repository.topScores
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val pingleSpeed = MutableStateFlow(prefs.getFloat("pingle_speed", 1.0f))
    val pingleFriction = MutableStateFlow(prefs.getFloat("pingle_friction", 15.0f))
    val isManualUnlocked = MutableStateFlow(prefs.getBoolean("manual_unlocked", false))
    val totalSpinDuration = MutableStateFlow(prefs.getLong("total_spin_duration", 0L))
    val pingleTint = MutableStateFlow(prefs.getString("pingle_tint", "none") ?: "none")
    val pingleCustomColor = MutableStateFlow(prefs.getInt("pingle_custom_color", 0xFF00FFCC.toInt()))
    val pingleFoldAngleThreshold = MutableStateFlow(prefs.getFloat("pingle_fold_angle_threshold", 120.0f))

    fun setScreen(screen: Screen) {
        currentScreen.value = screen
    }

    fun setPingleFoldAngleThreshold(angle: Float) {
        val rounded = Math.round(angle).toFloat().coerceIn(0f, 180f)
        pingleFoldAngleThreshold.value = rounded
        prefs.edit().putFloat("pingle_fold_angle_threshold", rounded).apply()
    }

    fun setPingleSpeed(speed: Float) {
        val rounded = Math.round(speed * 10f) / 10f
        pingleSpeed.value = rounded.coerceIn(0.5f, 10.0f)
        prefs.edit().putFloat("pingle_speed", rounded).apply()
    }

    fun setPingleFriction(friction: Float) {
        val rounded = Math.round(friction).toFloat().coerceIn(0f, 100f)
        pingleFriction.value = rounded
        prefs.edit().putFloat("pingle_friction", rounded).apply()
    }

    fun unlockManual() {
        isManualUnlocked.value = true
        prefs.edit().putBoolean("manual_unlocked", true).apply()
    }

    fun unlockEverything() {
        isManualUnlocked.value = true
        prefs.edit().putBoolean("manual_unlocked", true).apply()
        val targetDuration = 18000000L // 5 hours in ms
        if (totalSpinDuration.value < targetDuration) {
            totalSpinDuration.value = targetDuration
            prefs.edit().putLong("total_spin_duration", targetDuration).apply()
        }
    }

    fun adjustSpeed(increase: Boolean) {
        val current = pingleSpeed.value
        val step = 0.5f
        val newVal = if (increase) {
            (current + step).coerceAtMost(10.0f)
        } else {
            (current - step).coerceAtLeast(0.5f)
        }
        val rounded = Math.round(newVal * 10f) / 10f
        pingleSpeed.value = rounded
        prefs.edit().putFloat("pingle_speed", rounded).apply()
    }

    fun saveScore(durationMs: Long, isManual: Boolean = false) {
        if (durationMs < 100L) return // don't save ultra-tiny accidental spinnigs
        viewModelScope.launch {
            repository.insertScore(ScoreEntity(durationMs = durationMs, isManual = isManual))
            val currentTotal = prefs.getLong("total_spin_duration", 0L)
            val newTotal = currentTotal + durationMs
            prefs.edit().putLong("total_spin_duration", newTotal).apply()
            totalSpinDuration.value = newTotal
        }
    }

    fun setPingleTint(tintId: String) {
        pingleTint.value = tintId
        prefs.edit().putString("pingle_tint", tintId).apply()
    }

    fun setPingleCustomColor(colorInt: Int) {
        pingleCustomColor.value = colorInt
        prefs.edit().putInt("pingle_custom_color", colorInt).apply()
    }

    fun addDebugSpinDuration(ms: Long) {
        val currentTotal = prefs.getLong("total_spin_duration", 0L)
        val newTotal = currentTotal + ms
        prefs.edit().putLong("total_spin_duration", newTotal).apply()
        totalSpinDuration.value = newTotal
    }

    fun clearAllScores() {
        viewModelScope.launch {
            repository.clearScores()
            prefs.edit().putLong("total_spin_duration", 0L).apply()
            totalSpinDuration.value = 0L
        }
    }
}
