package dev.huecoder.md3agent.system.instance

import dev.huecoder.md3agent.platform.Log
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.Channels
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions

/**
 * Keeps the app to one window per user: a second launch (desktop file, hotkey, reminder timer) connects to this Unix
 * socket, hands over what it was asked to do and exits.
 */
class InstanceSocket(private val path: File) {
    fun tryForward(command: LaunchCommand): Boolean = try {
        SocketChannel.open(StandardProtocolFamily.UNIX).use { channel ->
            channel.connect(UnixDomainSocketAddress.of(path.toPath()))
            channel.write(ByteBuffer.wrap((LaunchArgs.encode(command) + "\n").toByteArray()))
        }
        true
    } catch (e: IOException) {
        false
    }

    fun listen(onCommand: (LaunchCommand) -> Unit): Closeable {
        path.parentFile?.let { dir ->
            if (!dir.exists()) Files.createDirectories(dir.toPath(), PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")))
        }
        // Reached only when nobody answered on the socket, so a leftover file is from a crashed instance.
        Files.deleteIfExists(path.toPath())
        val server = ServerSocketChannel.open(StandardProtocolFamily.UNIX).apply { bind(UnixDomainSocketAddress.of(path.toPath())) }
        val thread = Thread({
            while (server.isOpen) {
                val client = try {
                    server.accept()
                } catch (e: IOException) {
                    break
                }
                client.use {
                    runCatching {
                        val line = Channels.newReader(it, Charsets.UTF_8).buffered().readLine().orEmpty()
                        LaunchArgs.decode(line)?.let(onCommand)
                    }.onFailure { e -> Log.w(TAG, "Bad message on the instance socket: ${e.message}") }
                }
            }
        }, "instance-socket").apply {
            isDaemon = true
            start()
        }
        return Closeable {
            server.close()
            thread.join(1_000)
            Files.deleteIfExists(path.toPath())
        }
    }

    private companion object {
        const val TAG = "InstanceSocket"
    }
}
