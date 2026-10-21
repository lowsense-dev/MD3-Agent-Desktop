@file:Suppress("FunctionName", "unused")

package dev.huecoder.md3agent.system.dbus

import org.freedesktop.dbus.DBusPath
import org.freedesktop.dbus.Struct
import org.freedesktop.dbus.Tuple
import org.freedesktop.dbus.annotations.DBusInterfaceName
import org.freedesktop.dbus.annotations.Position
import org.freedesktop.dbus.interfaces.DBusInterface
import org.freedesktop.dbus.messages.DBusSignal
import org.freedesktop.dbus.types.UInt32
import org.freedesktop.dbus.types.Variant

// Freedesktop interfaces used by the app, as dbus-java remote interfaces. Method names match the D-Bus members.

@DBusInterfaceName("org.freedesktop.Notifications")
interface Notifications : DBusInterface {
    fun Notify(appName: String, replacesId: UInt32, appIcon: String, summary: String, body: String, actions: Array<String>, hints: Map<String, Variant<*>>, expireTimeout: Int): UInt32

    fun CloseNotification(id: UInt32)

    class ActionInvoked(path: String, val id: UInt32, val actionKey: String) : DBusSignal(path, id, actionKey)

    class NotificationClosed(path: String, val id: UInt32, val reason: UInt32) : DBusSignal(path, id, reason)
}

@DBusInterfaceName("org.freedesktop.portal.Request")
interface PortalRequest : DBusInterface {
    fun Close()

    class Response(path: String, val response: UInt32, val results: Map<String, Variant<*>>) : DBusSignal(path, response, results)
}

class FilterRule(@field:Position(0) val type: UInt32, @field:Position(1) val pattern: String) : Struct()

class FileFilter(@field:Position(0) val name: String, @field:Position(1) val rules: List<FilterRule>) : Struct()

@DBusInterfaceName("org.freedesktop.portal.FileChooser")
interface FileChooser : DBusInterface {
    fun OpenFile(parentWindow: String, title: String, options: Map<String, Variant<*>>): DBusPath

    fun SaveFile(parentWindow: String, title: String, options: Map<String, Variant<*>>): DBusPath
}

@DBusInterfaceName("org.freedesktop.portal.Settings")
interface PortalSettings : DBusInterface {
    fun ReadOne(namespace: String, key: String): Variant<*>

    fun Read(namespace: String, key: String): Variant<*>

    class SettingChanged(path: String, val namespace: String, val key: String, val value: Variant<*>) : DBusSignal(path, namespace, key, value)
}

class Shortcut(@field:Position(0) val id: String, @field:Position(1) val properties: Map<String, Variant<*>>) : Struct()

@DBusInterfaceName("org.freedesktop.portal.GlobalShortcuts")
interface GlobalShortcutsPortal : DBusInterface {
    fun CreateSession(options: Map<String, Variant<*>>): DBusPath

    fun BindShortcuts(sessionHandle: DBusPath, shortcuts: List<Shortcut>, parentWindow: String, options: Map<String, Variant<*>>): DBusPath

    class Activated(path: String, val sessionHandle: DBusPath, val shortcutId: String, val timestamp: org.freedesktop.dbus.types.UInt64, val options: Map<String, Variant<*>>) :
        DBusSignal(path, sessionHandle, shortcutId, timestamp, options)
}

// Secret Service (gnome-keyring, KWallet 6, KeePassXC).

class Secret(
    @field:Position(0) val session: DBusPath,
    @field:Position(1) val parameters: ByteArray,
    @field:Position(2) val value: ByteArray,
    @field:Position(3) val contentType: String,
) : Struct()

class OpenSessionResult(@field:Position(0) val output: Variant<*>, @field:Position(1) val session: DBusPath) : Tuple()

class SearchResult(@field:Position(0) val unlocked: List<DBusPath>, @field:Position(1) val locked: List<DBusPath>) : Tuple()

class PathAndPrompt(@field:Position(0) val path: DBusPath, @field:Position(1) val prompt: DBusPath) : Tuple()

@DBusInterfaceName("org.freedesktop.Secret.Service")
interface SecretService : DBusInterface {
    fun OpenSession(algorithm: String, input: Variant<*>): OpenSessionResult

    fun SearchItems(attributes: Map<String, String>): SearchResult

    fun ReadAlias(name: String): DBusPath

    fun Unlock(objects: List<DBusPath>): UnlockResult
}

class UnlockResult(@field:Position(0) val unlocked: List<DBusPath>, @field:Position(1) val prompt: DBusPath) : Tuple()

@DBusInterfaceName("org.freedesktop.Secret.Prompt")
interface SecretPrompt : DBusInterface {
    fun Prompt(windowId: String)

    class Completed(path: String, val dismissed: Boolean, val result: Variant<*>) : DBusSignal(path, dismissed, result)
}

@DBusInterfaceName("org.freedesktop.Secret.Collection")
interface SecretCollection : DBusInterface {
    fun CreateItem(properties: Map<String, Variant<*>>, secret: Secret, replace: Boolean): PathAndPrompt
}

@DBusInterfaceName("org.freedesktop.Secret.Item")
interface SecretItem : DBusInterface {
    fun GetSecret(session: DBusPath): Secret

    fun Delete(): DBusPath
}

@DBusInterfaceName("org.freedesktop.Secret.Session")
interface SecretSession : DBusInterface {
    fun Close()
}
