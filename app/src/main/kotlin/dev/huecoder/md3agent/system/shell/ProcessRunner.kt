package dev.huecoder.md3agent.system.shell

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.concurrent.TimeUnit

data class ShellResult(val exitCode: Int?, val stdout: String, val stderr: String, val timedOut: Boolean = false)

object ProcessRunner {
    const val MAX_CAPTURE_BYTES = 256 * 1024
    private const val DRAIN_WAIT_MS = 2_000L
    private const val KILL_WAIT_MS = 2_000L
    private const val TRUNCATED = "\n[output truncated]"

    fun run(
        command: List<String>,
        timeoutMs: Long,
        workdir: File? = null,
        environment: Map<String, String> = emptyMap(),
        maxOutputBytes: Int = MAX_CAPTURE_BYTES,
    ): ShellResult {
        val process = ProcessBuilder(command)
            .apply {
                workdir?.let(::directory)
                environment().putAll(environment)
            }
            .start()
        process.outputStream.close()
        val stdout = StreamCollector(process.inputStream, maxOutputBytes).apply { start() }
        val stderr = StreamCollector(process.errorStream, maxOutputBytes).apply { start() }
        val finished = try {
            process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (e: InterruptedException) {
            killTree(process)
            throw e
        }
        if (!finished) killTree(process)
        stdout.join(DRAIN_WAIT_MS)
        stderr.join(DRAIN_WAIT_MS)
        return ShellResult(
            exitCode = if (finished) process.exitValue() else null,
            stdout = stdout.text(),
            stderr = stderr.text(),
            timedOut = !finished,
        )
    }

    /** Children first: once the parent is gone its children are re-parented and no longer reachable from it. */
    private fun killTree(process: Process) {
        val handle = process.toHandle()
        val descendants = handle.descendants().toList()
        descendants.forEach { it.destroy() }
        handle.destroy()
        handle.onExit().runCatching { get(KILL_WAIT_MS, TimeUnit.MILLISECONDS) }
        (descendants + handle).filter { it.isAlive }.forEach { it.destroyForcibly() }
    }

    private class StreamCollector(private val stream: InputStream, private val limit: Int) : Thread() {
        private val captured = ByteArrayOutputStream()
        private var truncated = false

        init {
            isDaemon = true
        }

        override fun run() {
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            runCatching {
                stream.use {
                    while (true) {
                        val read = it.read(buffer)
                        if (read < 0) break
                        synchronized(captured) {
                            val room = limit - captured.size()
                            if (room > 0) captured.write(buffer, 0, minOf(read, room))
                            if (read > room) truncated = true
                        }
                    }
                }
            }
        }

        fun text(): String = synchronized(captured) { captured.toString(Charsets.UTF_8.name()) + if (truncated) TRUNCATED else "" }
    }
}
