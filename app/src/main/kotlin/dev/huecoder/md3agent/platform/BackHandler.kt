package dev.huecoder.md3agent.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * The desktop stand-in for Android's back button: Escape and the mouse's back button go to the innermost enabled
 * [BackHandler], like the system back gesture does on the phone.
 */
class BackDispatcher {
    private val handlers = mutableListOf<Entry>()

    class Entry(var enabled: Boolean, var onBack: () -> Unit)

    fun add(entry: Entry) = synchronized(handlers) { handlers += entry }

    fun remove(entry: Entry) = synchronized(handlers) { handlers -= entry }

    /** True when a handler took the event. */
    fun back(): Boolean {
        val target = synchronized(handlers) { handlers.lastOrNull { it.enabled } } ?: return false
        target.onBack()
        return true
    }
}

val LocalBackDispatcher = staticCompositionLocalOf { BackDispatcher() }

@Composable
fun BackHandler(enabled: Boolean = true, onBack: () -> Unit) {
    val dispatcher = LocalBackDispatcher.current
    val current = rememberUpdatedState(onBack)
    val entry = remember { BackDispatcher.Entry(enabled) { current.value() } }
    SideEffect { entry.enabled = enabled }
    DisposableEffect(dispatcher) {
        dispatcher.add(entry)
        onDispose { dispatcher.remove(entry) }
    }
}
