package com.taskscheduling.app.domain.model

import com.taskscheduling.app.voice.LanguageMode

/**
 * Robot icon size options.
 */
enum class BotSize(val displayName: String, val sizeDp: Float) {
    SMALL("Small", 40f),
    MEDIUM("Medium", 52f),
    LARGE("Large", 64f)
}

/**
 * Visual assistant icon styles.
 */
enum class AssistantIconStyle(val displayName: String, val emoji: String) {
    ROBOT("Robot", "🤖"),
    ORB("Orb", "🔮"),
    SPARKLE("Sparkle", "✨"),
    GEMINI("Gemini", "✦"),
    AI_CORE("Core", "💎")
}

/**
 * Preset color themes for floating bubble and window accents.
 */
enum class BubbleColorPreset(val displayName: String, val hexColor: String, val strokeColor: String) {
    DEFAULT_DARK("Slate Dark", "#0F172A", "#06B6D4"),
    INDIGO("Deep Indigo", "#1E1B4B", "#6366F1"),
    EMERALD("Emerald", "#064E3B", "#10B981"),
    CORAL("Warm Coral", "#431407", "#F97316"),
    ROYAL_VIOLET("Royal Violet", "#3B0764", "#A855F7")
}

/**
 * Window theme modes.
 */
enum class WindowTheme(val displayName: String) {
    SYSTEM("System"),
    DARK("Dark"),
    LIGHT("Light")
}

/**
 * Expanded chat window size presets.
 */
enum class ExpandedWindowSize(val displayName: String, val widthDp: Float) {
    COMPACT("Compact", 320f),
    STANDARD("Standard", 380f),
    LARGE("Large", 440f)
}

/**
 * User preferences and customization settings for the Floating Assistant Bot.
 */
data class BotPreferences(
    // Appearance
    val botSize: BotSize = BotSize.MEDIUM,
    val windowOpacity: Float = 0.95f,
    val isCompactMode: Boolean = false,
    val showIcon: Boolean = true,
    val showLabel: Boolean = false,
    val enableAnimations: Boolean = true,
    val iconStyle: AssistantIconStyle = AssistantIconStyle.ROBOT,
    val bubbleColor: BubbleColorPreset = BubbleColorPreset.DEFAULT_DARK,
    val windowTheme: WindowTheme = WindowTheme.DARK,
    val cornerRadiusDp: Float = 20f,
    val showAssistantName: Boolean = true,
    val showStatusIndicator: Boolean = true,

    // Position & Movement
    val savedPositionX: Int? = null,
    val savedPositionY: Int? = null,
    val edgeSnapping: Boolean = false,
    val expandedWindowSize: ExpandedWindowSize = ExpandedWindowSize.STANDARD,

    // Interaction & Voice
    val tapToListen: Boolean = true,
    val continuousListening: Boolean = false,
    val languageMode: LanguageMode = LanguageMode.ENGLISH,

    // Android System Floating Mode
    val persistentFloatingMode: Boolean = true
) {
    val size: BotSize get() = botSize
}
