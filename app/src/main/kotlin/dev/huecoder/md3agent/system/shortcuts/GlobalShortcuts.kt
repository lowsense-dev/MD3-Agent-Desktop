package dev.huecoder.md3agent.system.shortcuts

import dev.huecoder.md3agent.platform.Log
import dev.huecoder.md3agent.system.dbus.GlobalShortcutsPortal
import dev.huecoder.md3agent.system.dbus.PortalRequest
import dev.huecoder.md3agent.system.dbus.SessionBus
import dev.huecoder.md3agent.system.dbus.Shortcut
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.freedesktop.dbus.DBusPath
import org.freedesktop.dbus.types.Variant
import java.util.UUID

/**
 * A system-wide shortcut for the quick-question window through the GlobalShortcuts portal (GNOME 48+, KDE, Hyprland
 * and others). Desktops without it get the `md3-agent --overlay` command to bind themselves.
 */
class GlobalShortcuts(private val bus: SessionBus) {
    @Volatile
    var bound: Boolean = false
        private set

    suspend fun bind(onOverlay: () -> Unit): Boolean = withContext(Dispatchers.IO) {
        val connection = bus.connection ?: return@withContext false
        runCatching {
            val portal = connection.getRemoteObject(PORTAL, PORTAL_PATH, GlobalShortcutsPortal::class.java)
            val created = request { token -> portal.CreateSession(mapOf("handle_token" to Variant(token), "session_handle_token" to Variant(token))) } ?: return@runCatching false
            val session = (created["session_handle"]?.value as? String)?.let(::DBusPath) ?: return@runCatching false
            connection.addSigHandler(GlobalShortcutsPortal.Activated::class.java) { if (it.shortcutId == OVERLAY_ID) onOverlay() }
            val shortcut = Shortcut(OVERLAY_ID, mapOf("description" to Variant("MD3 Agent: quick question"), "preferred_trigger" to Variant("CTRL+ALT+space")))
            request { token -> portal.BindShortcuts(session, listOf(shortcut), "", mapOf("handle_token" to Variant(token))) } ?: return@runCatching false
            true
        }.onFailure { Log.i(TAG, "No global shortcut portal: ${it.message}") }.getOrDefault(false).also { bound = it }
    }

    private suspend fun request(call: (String) -> DBusPath): Map<String, Variant<*>>? {
        val connection = bus.connection ?: return null
        val sender = bus.senderToken ?: return null
        val token = "md3" + UUID.randomUUID().toString().replace("-", "")
        val path = "$PORTAL_PATH/request/$sender/$token"
        val reply = CompletableDeferred<PortalRequest.Response>()
        connection.addSigHandler(PortalRequest.Response::class.java) { if (it.path == path) reply.complete(it) }.use {
            call(token)
            // The desktop may ask the user to confirm the shortcut; give them time.
            val response = withTimeoutOrNull(REPLY_TIMEOUT_MS) { reply.await() } ?: return null
            return if (response.response.toInt() == 0) response.results else null
        }
    }

    private companion object {
        const val TAG = "GlobalShortcuts"
        const val PORTAL = "org.freedesktop.portal.Desktop"
        const val PORTAL_PATH = "/org/freedesktop/portal/desktop"
        const val OVERLAY_ID = "overlay"
        const val REPLY_TIMEOUT_MS = 120_000L
    }
}
