package com.taskscheduling.app.feature.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.taskscheduling.app.accessibility.TaskSchedulingAccessibilityService
import com.taskscheduling.app.data.repository.BotSettingsRepository
import com.taskscheduling.app.domain.model.AssistantIconStyle
import com.taskscheduling.app.domain.model.BotPreferences
import com.taskscheduling.app.domain.model.BotSize
import com.taskscheduling.app.domain.model.BubbleColorPreset
import com.taskscheduling.app.domain.model.ExpandedWindowSize
import com.taskscheduling.app.domain.model.WindowTheme
import com.taskscheduling.app.feature.diagnostics.VoiceBotDiagnosticTracker
import com.taskscheduling.app.voice.LanguageMode
import com.taskscheduling.app.voice.WhisperCppSpeechRecognitionEngine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class BotSettingsViewModel(
    private val context: Context,
    private val repository: BotSettingsRepository = BotSettingsRepository(context)
) : ViewModel() {

    val preferences: StateFlow<BotPreferences> = repository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), repository.getPreferences())

    private val tracker = VoiceBotDiagnosticTracker.getInstance(context)
    val systemOverview = tracker.systemOverview
    val speechModel = tracker.speechModel

    fun updatePreferences(newPrefs: BotPreferences) {
        viewModelScope.launch {
            repository.updatePreferences(newPrefs)
            applyToActiveOverlay(newPrefs)
        }
    }

    fun setBotSize(size: BotSize) {
        val updated = preferences.value.copy(botSize = size)
        updatePreferences(updated)
    }

    fun setIconStyle(style: AssistantIconStyle) {
        val updated = preferences.value.copy(iconStyle = style)
        updatePreferences(updated)
    }

    fun setBubbleColor(color: BubbleColorPreset) {
        val updated = preferences.value.copy(bubbleColor = color)
        updatePreferences(updated)
    }

    fun setWindowTheme(theme: WindowTheme) {
        val updated = preferences.value.copy(windowTheme = theme)
        updatePreferences(updated)
    }

    fun setExpandedWindowSize(size: ExpandedWindowSize) {
        val updated = preferences.value.copy(expandedWindowSize = size)
        updatePreferences(updated)
    }

    fun setEdgeSnapping(enabled: Boolean) {
        val updated = preferences.value.copy(edgeSnapping = enabled)
        updatePreferences(updated)
    }

    fun setWindowOpacity(opacity: Float) {
        val updated = preferences.value.copy(windowOpacity = opacity.coerceIn(0.3f, 1.0f))
        updatePreferences(updated)
    }

    fun setCompactMode(enabled: Boolean) {
        val updated = preferences.value.copy(isCompactMode = enabled)
        updatePreferences(updated)
    }

    fun setShowIcon(enabled: Boolean) {
        val updated = preferences.value.copy(showIcon = enabled)
        updatePreferences(updated)
    }

    fun setShowLabel(enabled: Boolean) {
        val updated = preferences.value.copy(showLabel = enabled)
        updatePreferences(updated)
    }

    fun setEnableAnimations(enabled: Boolean) {
        val updated = preferences.value.copy(enableAnimations = enabled)
        updatePreferences(updated)
    }

    fun setTapToListen(enabled: Boolean) {
        val updated = preferences.value.copy(tapToListen = enabled)
        updatePreferences(updated)
    }

    fun setContinuousListening(enabled: Boolean) {
        val updated = preferences.value.copy(continuousListening = enabled)
        updatePreferences(updated)
    }

    fun setLanguageMode(mode: LanguageMode) {
        val updated = preferences.value.copy(languageMode = mode)
        updatePreferences(updated)
        // Also apply to Whisper speech engine
        WhisperCppSpeechRecognitionEngine.getInstance(context).languageMode = mode
    }

    fun setPersistentFloatingMode(enabled: Boolean) {
        val updated = preferences.value.copy(persistentFloatingMode = enabled)
        updatePreferences(updated)
        val a11y = TaskSchedulingAccessibilityService.instance
        if (enabled) {
            a11y?.overlayManager?.showFloatingAssistantShortcut()
        }
    }

    fun resetPosition() {
        viewModelScope.launch {
            repository.resetPosition()
            val a11y = TaskSchedulingAccessibilityService.instance
            a11y?.overlayManager?.let { mgr ->
                mgr.hideFloatingAssistantShortcut()
                mgr.showFloatingAssistantShortcut()
            }
        }
    }

    fun resetToDefaults() {
        viewModelScope.launch {
            repository.resetToDefaults()
            val defPrefs = repository.getPreferences()
            applyToActiveOverlay(defPrefs)
        }
    }

    private fun applyToActiveOverlay(prefs: BotPreferences) {
        val a11y = TaskSchedulingAccessibilityService.instance ?: return
        a11y.overlayManager.let { mgr ->
            if (mgr.isFloatingShortcutVisible) {
                // Refresh shortcut with new preferences
                mgr.hideFloatingAssistantShortcut()
                mgr.showFloatingAssistantShortcut()
            }
        }
    }
}
