package dev.huecoder.md3agent.data.settings

import kotlinx.serialization.Serializable

@Serializable
data class InterfaceSettings(
    val haptics: Boolean = true,
    val hapticStrength: Float = DEFAULT_HAPTIC_STRENGTH,
    val swipeRight: SwipeAction = SwipeAction.TRASH,
    val swipeLeft: SwipeAction = SwipeAction.ARCHIVE,
    /** SYSTEM follows the desktop's dark-mode setting when it shares one, else light. */
    val theme: ThemeMode = ThemeMode.SYSTEM,
    /** SYSTEM follows $LANG; otherwise the interface is forced to that language. */
    val language: AppLanguage = AppLanguage.SYSTEM,
) {
    companion object {
        const val DEFAULT_HAPTIC_STRENGTH = 0.85f
        const val MIN_HAPTIC_STRENGTH = 0.2f
    }
}

@Serializable
enum class SwipeAction { NONE, TRASH, ARCHIVE, MOVE }

@Serializable
enum class ThemeMode { SYSTEM, LIGHT, DARK }

@Serializable
enum class AppLanguage(val tag: String?) {
    SYSTEM(null),
    ENGLISH("en"),
    RUSSIAN("ru");

    fun locale(system: java.util.Locale = java.util.Locale.getDefault()): java.util.Locale = tag?.let(java.util.Locale::forLanguageTag) ?: system
}
