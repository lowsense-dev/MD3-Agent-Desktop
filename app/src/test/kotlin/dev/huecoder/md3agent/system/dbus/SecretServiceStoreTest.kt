package dev.huecoder.md3agent.system.dbus

import dev.huecoder.md3agent.system.secrets.SecretServiceStore
import org.junit.Assume.assumeTrue
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/** Talks to the real Secret Service when one runs unlocked; the item it creates is deleted afterwards. */
class SecretServiceStoreTest {
    @Test
    fun createsKeyOnceAndReadsItBack() {
        val bus = SessionBus.connect()
        val store = SecretServiceStore(bus, purpose = "md3-agent-test-${System.nanoTime()}")
        assumeTrue("no unlocked Secret Service on this session bus", store.usable())
        try {
            val first = store.loadOrCreateKey()
            assertEquals(32, first.size)
            assertContentEquals(first, store.loadOrCreateKey())
            assertContentEquals(first, SecretServiceStore(bus, purpose = store.purpose).loadOrCreateKey())
        } finally {
            store.delete()
        }
    }

    @Test
    fun unusableWithoutBus() {
        assertEquals(false, SecretServiceStore(SessionBus.unavailable()).usable())
    }
}
