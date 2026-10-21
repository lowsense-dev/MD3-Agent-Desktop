package dev.huecoder.md3agent.system.appearance

import androidx.compose.ui.graphics.Color
import dev.huecoder.md3agent.system.dbus.PortalSettings
import dev.huecoder.md3agent.system.dbus.SessionBus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.freedesktop.dbus.Struct
import org.freedesktop.dbus.types.UInt32
import org.freedesktop.dbus.types.Variant

/** Dark mode and accent colour from the desktop's settings portal; null means the desktop does not say. */
class SystemAppearance(private val bus: SessionBus) {
    private val settings: PortalSettings? by lazy {
        runCatching { bus.connection?.getRemoteObject(PORTAL, PORTAL_PATH, PortalSettings::class.java) }.getOrNull()
    }

    private val _darkMode = MutableStateFlow<Boolean?>(null)
    val darkMode: StateFlow<Boolean?> = _darkMode.asStateFlow()

    private val _accent = MutableStateFlow<Color?>(null)
    val accent: StateFlow<Color?> = _accent.asStateFlow()

    /** Reads the current values once and follows changes. */
    fun start() {
        _darkMode.value = readDarkMode()
        _accent.value = readAccent()
        runCatching {
            bus.connection?.addSigHandler(PortalSettings.SettingChanged::class.java) { signal ->
                if (signal.namespace != NAMESPACE) return@addSigHandler
                when (signal.key) {
                    COLOR_SCHEME -> _darkMode.value = (unwrap(signal.value) as? UInt32)?.toInt()?.let(::darkFromColorScheme)
                    ACCENT -> _accent.value = accentFrom(unwrap(signal.value))
                }
            }
        }
    }

    fun readDarkMode(): Boolean? = (read(COLOR_SCHEME) as? UInt32)?.toInt()?.let(::darkFromColorScheme)

    fun readAccent(): Color? = accentFrom(read(ACCENT))

    private fun read(key: String): Any? {
        val portal = settings ?: return null
        // ReadOne is Settings v2; older portals only have Read, which wraps the value in one more variant.
        return runCatching { unwrap(portal.ReadOne(NAMESPACE, key)) }.recoverCatching { unwrap(portal.Read(NAMESPACE, key)) }.getOrNull()
    }

    private fun unwrap(value: Any?): Any? = if (value is Variant<*>) unwrap(value.value) else value

    private fun accentFrom(value: Any?): Color? {
        val parts = when (value) {
            is Struct -> value.parameters.toList()
            is Array<*> -> value.toList()
            is List<*> -> value
            else -> return null
        }.mapNotNull { (it as? Number)?.toFloat() }
        if (parts.size != 3 || parts.any { it !in 0f..1f }) return null
        return Color(parts[0], parts[1], parts[2])
    }

    companion object {
        private const val PORTAL = "org.freedesktop.portal.Desktop"
        private const val PORTAL_PATH = "/org/freedesktop/portal/desktop"
        private const val NAMESPACE = "org.freedesktop.appearance"
        private const val COLOR_SCHEME = "color-scheme"
        private const val ACCENT = "accent-color"

        /** color-scheme: 0 no preference, 1 prefer dark, 2 prefer light. */
        fun darkFromColorScheme(value: Int): Boolean? = when (value) {
            1 -> true
            2 -> false
            else -> null
        }
    }
}
