package dev.huecoder.md3agent.system.secrets

import dev.huecoder.md3agent.system.dbus.SecretCollection
import dev.huecoder.md3agent.system.dbus.SecretItem
import dev.huecoder.md3agent.system.dbus.SecretPrompt
import dev.huecoder.md3agent.system.dbus.SecretService
import dev.huecoder.md3agent.system.dbus.SecretSession
import dev.huecoder.md3agent.system.dbus.Secret
import dev.huecoder.md3agent.system.dbus.SessionBus
import org.freedesktop.dbus.DBusPath
import org.freedesktop.dbus.connections.impl.DBusConnection
import org.freedesktop.dbus.interfaces.Properties
import org.freedesktop.dbus.types.Variant
import java.io.IOException
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/**
 * The settings key kept in the desktop keyring through the Secret Service API. A locked keyring is unlocked through
 * the desktop's own password prompt, the same way browsers do it.
 */
class SecretServiceStore(private val bus: SessionBus, val purpose: String = "settings-key") : SecretStore {
    private val attributes get() = mapOf("application" to APPLICATION, "purpose" to purpose)

    /** A Secret Service with a default collection exists. */
    fun available(): Boolean = runCatching { defaultCollection(bus.connection ?: return false, service() ?: return false) != null }.getOrDefault(false)

    /** Available and its default collection is unlocked, so nothing would prompt the user. */
    fun usable(): Boolean = runCatching {
        val connection = bus.connection ?: return false
        val collection = defaultCollection(connection, service() ?: return false) ?: return false
        !isLocked(connection, collection)
    }.getOrDefault(false)

    override fun loadOrCreateKey(): ByteArray {
        val connection = bus.connection ?: throw IOException("No session D-Bus")
        val service = service() ?: throw IOException("No Secret Service")
        val session = service.OpenSession(ALGORITHM_PLAIN, Variant("")).session
        try {
            findItem(connection, service)?.let { item ->
                val stored = Base64.getDecoder().decode(connection.item(item).GetSecret(session).value.decodeToString())
                if (stored.size == KEY_BYTES) return stored
            }
            val collection = defaultCollection(connection, service) ?: throw IOException("The keyring has no default collection")
            if (isLocked(connection, collection)) unlock(connection, service, listOf(collection))
            val key = ByteArray(KEY_BYTES).also(SecureRandom()::nextBytes)
            val properties = mapOf<String, Variant<*>>(
                "org.freedesktop.Secret.Item.Label" to Variant(LABEL),
                "org.freedesktop.Secret.Item.Attributes" to Variant(attributes, "a{ss}"),
            )
            val secret = Secret(session, ByteArray(0), Base64.getEncoder().encode(key), "text/plain")
            val created = connection.getRemoteObject(SERVICE, collection.path, SecretCollection::class.java).CreateItem(properties, secret, true)
            if (created.prompt.path != NO_PROMPT) prompt(connection, created.prompt)
            return key
        } finally {
            runCatching { connection.getRemoteObject(SERVICE, session.path, SecretSession::class.java).Close() }
        }
    }

    /** Removes the stored key (used by tests to clean up). */
    fun delete() {
        val connection = bus.connection ?: return
        val service = service() ?: return
        findItem(connection, service)?.let { connection.item(it).Delete() }
    }

    private fun findItem(connection: DBusConnection, service: SecretService): DBusPath? {
        val found = service.SearchItems(attributes)
        found.unlocked.firstOrNull()?.let { return it }
        val locked = found.locked.firstOrNull() ?: return null
        unlock(connection, service, listOf(locked))
        return service.SearchItems(attributes).unlocked.firstOrNull()
    }

    private fun unlock(connection: DBusConnection, service: SecretService, objects: List<DBusPath>) {
        val result = service.Unlock(objects)
        if (result.prompt.path != NO_PROMPT) prompt(connection, result.prompt)
    }

    private fun prompt(connection: DBusConnection, path: DBusPath) {
        val done = CompletableFuture<Boolean>()
        val handler = connection.addSigHandler(SecretPrompt.Completed::class.java) { signal -> if (signal.path == path.path) done.complete(signal.dismissed) }
        try {
            connection.getRemoteObject(SERVICE, path.path, SecretPrompt::class.java).Prompt("")
            val dismissed = done.get(PROMPT_TIMEOUT_S, TimeUnit.SECONDS)
            if (dismissed) throw IOException("The keyring prompt was dismissed")
        } finally {
            handler.close()
        }
    }

    private fun defaultCollection(connection: DBusConnection, service: SecretService): DBusPath? =
        service.ReadAlias("default").takeIf { it.path != NO_PROMPT }
            ?: runCatching { connection.secretProperties(SERVICE_PATH).Get<List<DBusPath>>(SERVICE_INTERFACE, "Collections").firstOrNull() }.getOrNull()

    private fun isLocked(connection: DBusConnection, collection: DBusPath): Boolean =
        connection.secretProperties(collection.path).Get<Boolean>(COLLECTION_INTERFACE, "Locked")

    private fun service(): SecretService? = runCatching { bus.connection?.getRemoteObject(SERVICE, SERVICE_PATH, SecretService::class.java) }.getOrNull()

    private fun DBusConnection.item(path: DBusPath) = getRemoteObject(SERVICE, path.path, SecretItem::class.java)

    private fun DBusConnection.secretProperties(path: String) = getRemoteObject(SERVICE, path, Properties::class.java)

    private companion object {
        const val SERVICE = "org.freedesktop.secrets"
        const val SERVICE_PATH = "/org/freedesktop/secrets"
        const val SERVICE_INTERFACE = "org.freedesktop.Secret.Service"
        const val COLLECTION_INTERFACE = "org.freedesktop.Secret.Collection"
        const val ALGORITHM_PLAIN = "plain"
        const val NO_PROMPT = "/"
        const val APPLICATION = "md3-agent"
        const val LABEL = "MD3 Agent settings key"
        const val KEY_BYTES = 32
        const val PROMPT_TIMEOUT_S = 300L
    }
}
