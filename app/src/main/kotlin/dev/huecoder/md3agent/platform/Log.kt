package dev.huecoder.md3agent.platform

import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Same calls as android.util.Log; lines go to stderr and to a small rotating file under the XDG state dir. */
object Log {
    private const val MAX_BYTES = 1_000_000L
    private val stamp = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")

    @Volatile
    var file: File? = null

    fun d(tag: String, message: String, error: Throwable? = null) = write("D", tag, message, error)
    fun i(tag: String, message: String, error: Throwable? = null) = write("I", tag, message, error)
    fun w(tag: String, message: String, error: Throwable? = null) = write("W", tag, message, error)
    fun w(tag: String, error: Throwable) = write("W", tag, error.message.orEmpty(), error)
    fun e(tag: String, message: String, error: Throwable? = null) = write("E", tag, message, error)

    @Synchronized
    private fun write(level: String, tag: String, message: String, error: Throwable?) {
        val line = buildString {
            append(LocalDateTime.now().format(stamp)).append(' ').append(level).append('/').append(tag).append(": ").append(message)
            if (error != null) append('\n').append(error.stackTraceToString())
        }
        System.err.println(line)
        val target = file ?: return
        runCatching {
            if (target.length() > MAX_BYTES) target.renameTo(File(target.path + ".1"))
            target.appendText(line + "\n")
        }
    }
}
