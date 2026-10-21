package dev.huecoder.md3agent.platform

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

enum class AppShortcut { SEARCH, SETTINGS }

/** Keyboard shortcuts the window catches and hands to whichever screen can act on them. */
object AppEvents {
    private val _shortcuts = MutableSharedFlow<AppShortcut>(extraBufferCapacity = 4)
    val shortcuts: SharedFlow<AppShortcut> = _shortcuts.asSharedFlow()

    fun send(shortcut: AppShortcut) {
        _shortcuts.tryEmit(shortcut)
    }
}
