package com.taskscheduling.app.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.taskscheduling.app.domain.model.AssistantIconStyle
import com.taskscheduling.app.domain.model.BotPreferences
import com.taskscheduling.app.domain.model.BotSize
import com.taskscheduling.app.domain.model.BubbleColorPreset
import com.taskscheduling.app.domain.model.ExpandedWindowSize
import com.taskscheduling.app.domain.model.WindowTheme
import com.taskscheduling.app.voice.LanguageMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class BotSettingsRepository(context: Context) {

    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    private val _botPreferences = MutableStateFlow(loadPreferences())
    val botPreferences: StateFlow<BotPreferences> = _botPreferences.asStateFlow()
    val settings: StateFlow<BotPreferences> get() = botPreferences

    fun getPreferences(): BotPreferences = _botPreferences.value

    fun updatePreferences(preferences: BotPreferences) {
        prefs.edit().apply {
            putString(KEY_SIZE, preferences.size.name)
            putFloat(KEY_OPACITY, preferences.windowOpacity)
            putBoolean(KEY_COMPACT, preferences.isCompactMode)
            putBoolean(KEY_SHOW_ICON, preferences.showIcon)
            putBoolean(KEY_SHOW_LABEL, preferences.showLabel)
            putBoolean(KEY_ANIMATIONS, preferences.enableAnimations)
            putString(KEY_ICON_STYLE, preferences.iconStyle.name)
            putString(KEY_BUBBLE_COLOR, preferences.bubbleColor.name)
            putString(KEY_WINDOW_THEME, preferences.windowTheme.name)
            putFloat(KEY_CORNER_RADIUS, preferences.cornerRadiusDp)
            putBoolean(KEY_SHOW_NAME, preferences.showAssistantName)
            putBoolean(KEY_SHOW_STATUS, preferences.showStatusIndicator)
            putBoolean(KEY_EDGE_SNAPPING, preferences.edgeSnapping)
            putString(KEY_EXPANDED_SIZE, preferences.expandedWindowSize.name)

            if (preferences.savedPositionX != null) {
                putInt(KEY_POS_X, preferences.savedPositionX)
            } else {
                remove(KEY_POS_X)
            }
            if (preferences.savedPositionY != null) {
                putInt(KEY_POS_Y, preferences.savedPositionY)
            } else {
                remove(KEY_POS_Y)
            }
            putBoolean(KEY_TAP_TO_LISTEN, preferences.tapToListen)
            putBoolean(KEY_CONTINUOUS_LISTENING, preferences.continuousListening)
            putString(KEY_LANGUAGE_MODE, preferences.languageMode.name)
            putBoolean(KEY_PERSISTENT_FLOATING, preferences.persistentFloatingMode)
            apply()
        }
        _botPreferences.value = preferences
    }

    fun savePosition(x: Int, y: Int) {
        prefs.edit().apply {
            putInt(KEY_POS_X, x)
            putInt(KEY_POS_Y, y)
            apply()
        }
        _botPreferences.value = _botPreferences.value.copy(
            savedPositionX = x,
            savedPositionY = y
        )
    }

    fun resetPosition() {
        prefs.edit().apply {
            remove(KEY_POS_X)
            remove(KEY_POS_Y)
            apply()
        }
        _botPreferences.value = _botPreferences.value.copy(
            savedPositionX = null,
            savedPositionY = null
        )
    }

    fun resetToDefaults() {
        prefs.edit().clear().apply()
        _botPreferences.value = BotPreferences()
    }

    private fun loadPreferences(): BotPreferences {
        val sizeStr = prefs.getString(KEY_SIZE, BotSize.MEDIUM.name) ?: BotSize.MEDIUM.name
        val size = try { BotSize.valueOf(sizeStr) } catch (_: Exception) { BotSize.MEDIUM }

        val langStr = prefs.getString(KEY_LANGUAGE_MODE, LanguageMode.ENGLISH.name) ?: LanguageMode.ENGLISH.name
        val lang = try { LanguageMode.valueOf(langStr) } catch (_: Exception) { LanguageMode.ENGLISH }

        val iconStr = prefs.getString(KEY_ICON_STYLE, AssistantIconStyle.ROBOT.name) ?: AssistantIconStyle.ROBOT.name
        val icon = try { AssistantIconStyle.valueOf(iconStr) } catch (_: Exception) { AssistantIconStyle.ROBOT }

        val colorStr = prefs.getString(KEY_BUBBLE_COLOR, BubbleColorPreset.DEFAULT_DARK.name) ?: BubbleColorPreset.DEFAULT_DARK.name
        val color = try { BubbleColorPreset.valueOf(colorStr) } catch (_: Exception) { BubbleColorPreset.DEFAULT_DARK }

        val themeStr = prefs.getString(KEY_WINDOW_THEME, WindowTheme.DARK.name) ?: WindowTheme.DARK.name
        val theme = try { WindowTheme.valueOf(themeStr) } catch (_: Exception) { WindowTheme.DARK }

        val expSizeStr = prefs.getString(KEY_EXPANDED_SIZE, ExpandedWindowSize.STANDARD.name) ?: ExpandedWindowSize.STANDARD.name
        val expSize = try { ExpandedWindowSize.valueOf(expSizeStr) } catch (_: Exception) { ExpandedWindowSize.STANDARD }

        val posX = if (prefs.contains(KEY_POS_X)) prefs.getInt(KEY_POS_X, 0) else null
        val posY = if (prefs.contains(KEY_POS_Y)) prefs.getInt(KEY_POS_Y, 0) else null

        return BotPreferences(
            botSize = size,
            windowOpacity = prefs.getFloat(KEY_OPACITY, 0.95f),
            isCompactMode = prefs.getBoolean(KEY_COMPACT, false),
            showIcon = prefs.getBoolean(KEY_SHOW_ICON, true),
            showLabel = prefs.getBoolean(KEY_SHOW_LABEL, false),
            enableAnimations = prefs.getBoolean(KEY_ANIMATIONS, true),
            iconStyle = icon,
            bubbleColor = color,
            windowTheme = theme,
            cornerRadiusDp = prefs.getFloat(KEY_CORNER_RADIUS, 20f),
            showAssistantName = prefs.getBoolean(KEY_SHOW_NAME, true),
            showStatusIndicator = prefs.getBoolean(KEY_SHOW_STATUS, true),
            savedPositionX = posX,
            savedPositionY = posY,
            edgeSnapping = prefs.getBoolean(KEY_EDGE_SNAPPING, false),
            expandedWindowSize = expSize,
            tapToListen = prefs.getBoolean(KEY_TAP_TO_LISTEN, true),
            continuousListening = prefs.getBoolean(KEY_CONTINUOUS_LISTENING, false),
            languageMode = lang,
            persistentFloatingMode = prefs.getBoolean(KEY_PERSISTENT_FLOATING, true)
        )
    }

    companion object {
        private const val PREFS_NAME = "bot_settings_preferences"
        private const val KEY_SIZE = "bot_size"
        private const val KEY_OPACITY = "window_opacity"
        private const val KEY_COMPACT = "is_compact_mode"
        private const val KEY_SHOW_ICON = "show_icon"
        private const val KEY_SHOW_LABEL = "show_label"
        private const val KEY_ANIMATIONS = "enable_animations"
        private const val KEY_ICON_STYLE = "icon_style"
        private const val KEY_BUBBLE_COLOR = "bubble_color"
        private const val KEY_WINDOW_THEME = "window_theme"
        private const val KEY_CORNER_RADIUS = "corner_radius"
        private const val KEY_SHOW_NAME = "show_assistant_name"
        private const val KEY_SHOW_STATUS = "show_status_indicator"
        private const val KEY_EDGE_SNAPPING = "edge_snapping"
        private const val KEY_EXPANDED_SIZE = "expanded_window_size"
        private const val KEY_POS_X = "saved_position_x"
        private const val KEY_POS_Y = "saved_position_y"
        private const val KEY_TAP_TO_LISTEN = "tap_to_listen"
        private const val KEY_CONTINUOUS_LISTENING = "continuous_listening"
        private const val KEY_LANGUAGE_MODE = "language_mode"
        private const val KEY_PERSISTENT_FLOATING = "persistent_floating_mode"

        @Volatile
        private var instance: BotSettingsRepository? = null

        fun getInstance(context: Context): BotSettingsRepository {
            return instance ?: synchronized(this) {
                instance ?: BotSettingsRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}
