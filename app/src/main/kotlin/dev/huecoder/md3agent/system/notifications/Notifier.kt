package dev.huecoder.md3agent.system.notifications

import dev.huecoder.md3agent.platform.Log
import dev.huecoder.md3agent.system.dbus.Notifications
import dev.huecoder.md3agent.system.dbus.SessionBus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.freedesktop.dbus.types.UInt32
import org.freedesktop.dbus.types.Variant
import java.util.concurrent.ConcurrentHashMap

/** Desktop notifications through org.freedesktop.Notifications; null from [show] means there is no notification server. */
class Notifier(private val bus: SessionBus) {
    private val handlers = ConcurrentHashMap<Int, (String) -> Unit>()

    @Volatile
    private var listening = false

    private val service: Notifications? by lazy {
        runCatching { bus.connection?.getRemoteObject(SERVICE, PATH, Notifications::class.java) }.getOrNull()
    }

    suspend fun show(title: String, body: String, actions: List<Pair<String, String>>, onAction: (String) -> Unit): Int? = withContext(Dispatchers.IO) {
        val connection = bus.connection ?: return@withContext null
        val remote = service ?: return@withContext null
        try {
            listen(connection)
            val flat = actions.flatMap { (key, label) -> listOf(key, label) }.toTypedArray()
            val hints = mapOf<String, Variant<*>>("desktop-entry" to Variant(DESKTOP_ENTRY), "urgency" to Variant(URGENCY_NORMAL.toByte()))
            val id = remote.Notify(APP_NAME, UInt32(0), ICON, title, body, flat, hints, -1).toInt()
            if (actions.isNotEmpty()) handlers[id] = onAction
            id
        } catch (e: Exception) {
            Log.w(TAG, "Notification failed: ${e.message}")
            null
        }
    }

    fun close(id: Int) {
        handlers.remove(id)
        runCatching { service?.CloseNotification(UInt32(id.toLong())) }
    }

    private fun listen(connection: org.freedesktop.dbus.connections.impl.DBusConnection) {
        if (listening) return
        synchronized(this) {
            if (listening) return
            connection.addSigHandler(Notifications.ActionInvoked::class.java) { signal ->
                handlers.remove(signal.id.toInt())?.invoke(signal.actionKey)
            }
            connection.addSigHandler(Notifications.NotificationClosed::class.java) { signal -> handlers.remove(signal.id.toInt()) }
            listening = true
        }
    }

    private companion object {
        const val TAG = "Notifier"
        const val SERVICE = "org.freedesktop.Notifications"
        const val PATH = "/org/freedesktop/Notifications"
        const val APP_NAME = "MD3 Agent"
        const val ICON = "md3-agent"
        const val DESKTOP_ENTRY = "md3-agent"
        const val URGENCY_NORMAL = 1
    }
}
