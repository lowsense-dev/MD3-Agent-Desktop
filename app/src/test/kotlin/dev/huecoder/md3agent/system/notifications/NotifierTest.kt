package dev.huecoder.md3agent.system.notifications

import dev.huecoder.md3agent.system.dbus.SessionBus
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertNull

class NotifierTest {
    @Test
    fun returnsNullWithoutBus() = runBlocking {
        val notifier = Notifier(SessionBus.unavailable())
        assertNull(notifier.show("Title", "Body", actions = emptyList()) {})
    }
}
