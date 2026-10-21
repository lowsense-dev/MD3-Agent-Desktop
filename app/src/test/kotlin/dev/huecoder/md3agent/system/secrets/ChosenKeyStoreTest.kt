package dev.huecoder.md3agent.system.secrets

import dev.huecoder.md3agent.system.dbus.SessionBus
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class ChosenKeyStoreTest {
    private val config = createTempDirectory("md3-cfg").toFile()
    private var keyringAsked = 0
    private val store = ChosenKeyStore(config) {
        keyringAsked++
        SecretServiceStore(SessionBus.unavailable())
    }

    @Test
    fun fallsBackToFileAndRemembersIt() {
        val key = store.loadOrCreateKey()
        assertEquals("file", config.resolve("key-source").readText())
        assertContentEquals(key, store.loadOrCreateKey())
        assertEquals(1, keyringAsked)
    }

    @Test
    fun existingFileKeyWinsWithoutAskingTheKeyring() {
        val key = FileKeyStore(config.resolve("key")).loadOrCreateKey()
        assertContentEquals(key, store.loadOrCreateKey())
        assertEquals(0, keyringAsked)
    }
}
