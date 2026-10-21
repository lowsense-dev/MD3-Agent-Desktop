package dev.huecoder.md3agent.system.instance

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.channels.ServerSocketChannel
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InstanceSocketTest {
    private val path = createTempDirectory("md3-sock").toFile().resolve("md3-agent.sock")

    @Test
    fun secondLaunchForwardsArguments() = runBlocking {
        val received = CompletableDeferred<LaunchCommand>()
        val listener = InstanceSocket(path).listen { received.complete(it) }
        try {
            assertTrue(InstanceSocket(path).tryForward(LaunchCommand.Chat("abc")))
            assertEquals(LaunchCommand.Chat("abc"), withTimeout(5_000) { received.await() })
        } finally {
            listener.close()
        }
    }

    @Test
    fun staleSocketIsReplaced() = runBlocking {
        // A socket file left by a crashed instance: bound once, then the server is gone.
        ServerSocketChannel.open(StandardProtocolFamily.UNIX).apply { bind(UnixDomainSocketAddress.of(path.toPath())) }.close()
        assertTrue(path.exists())
        assertFalse(InstanceSocket(path).tryForward(LaunchCommand.Show))
        val received = CompletableDeferred<LaunchCommand>()
        val listener = InstanceSocket(path).listen { received.complete(it) }
        try {
            assertTrue(InstanceSocket(path).tryForward(LaunchCommand.Overlay))
            assertEquals(LaunchCommand.Overlay, withTimeout(5_000) { received.await() })
        } finally {
            listener.close()
        }
    }

    @Test
    fun noInstanceMeansNothingToForwardTo() {
        assertFalse(InstanceSocket(path).tryForward(LaunchCommand.Show))
    }
}
