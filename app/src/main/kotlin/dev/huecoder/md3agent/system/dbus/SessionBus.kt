package dev.huecoder.md3agent.system.dbus

import dev.huecoder.md3agent.platform.Log
import org.freedesktop.dbus.connections.impl.DBusConnection
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder

/** The session D-Bus, or nothing when there is none (headless, some containers). Connects on first use. */
class SessionBus private constructor(private val open: () -> DBusConnection?) {
    private val lazyConnection by lazy {
        runCatching(open).onFailure { Log.w(TAG, "No session D-Bus: ${it.message}") }.getOrNull()
    }

    val connection: DBusConnection? get() = lazyConnection

    /** A unique name like ":1.42" as used in portal request paths ("1_42"). */
    val senderToken: String? get() = connection?.uniqueName?.removePrefix(":")?.replace('.', '_')

    companion object {
        private const val TAG = "SessionBus"

        fun connect(): SessionBus = SessionBus {
            if (System.getenv("DBUS_SESSION_BUS_ADDRESS").isNullOrBlank() && System.getenv("XDG_RUNTIME_DIR").isNullOrBlank()) null
            else DBusConnectionBuilder.forSessionBus().withShared(false).build()
        }

        fun unavailable(): SessionBus = SessionBus { null }
    }
}
