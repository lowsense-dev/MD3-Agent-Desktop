package dev.huecoder.md3agent.system.clipboard

import dev.huecoder.md3agent.system.environment.Programs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.image.RenderedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO

sealed interface ClipboardContent {
    data class Text(val text: String) : ClipboardContent
    data class Files(val files: List<File>) : ClipboardContent
    class Image(val bytes: ByteArray, val mimeType: String) : ClipboardContent
}

/**
 * What is on the clipboard: files, a picture or text. AWT goes first; it reaches Wayland only through XWayland and often
 * sees just text there, so wl-paste (Wayland) or xclip (X11) fill in files and pictures when they are installed.
 */
class ClipboardReader(
    private val awt: () -> ClipboardContent? = ::readAwt,
    private val wayland: Boolean = System.getenv("WAYLAND_DISPLAY") != null,
    private val run: (List<String>) -> ByteArray? = ::capture,
) {
    suspend fun read(): ClipboardContent? = withContext(Dispatchers.IO) {
        val fromAwt = runCatching(awt).getOrNull()
        if (fromAwt is ClipboardContent.Files || fromAwt is ClipboardContent.Image) return@withContext fromAwt
        fromTools() ?: fromAwt
    }

    private fun fromTools(): ClipboardContent? {
        val types = run(listTypes())?.decodeToString()?.lines()?.map { it.trim() }?.filter { it.isNotEmpty() } ?: return null
        FILE_TYPES.firstOrNull { it in types }?.let { type ->
            val files = run(readType(type))?.decodeToString()?.let(ClipboardParser::uriList).orEmpty()
            if (files.isNotEmpty()) return ClipboardContent.Files(files)
        }
        val image = types.firstOrNull { it == PNG } ?: types.firstOrNull { it.startsWith("image/") } ?: return null
        val bytes = run(readType(image))?.takeIf { it.isNotEmpty() } ?: return null
        return ClipboardContent.Image(bytes, image)
    }

    private fun listTypes(): List<String> = if (wayland) listOf("wl-paste", "--list-types") else listOf("xclip", "-selection", "clipboard", "-t", "TARGETS", "-o")

    private fun readType(type: String): List<String> = if (wayland) listOf("wl-paste", "--no-newline", "--type", type) else listOf("xclip", "-selection", "clipboard", "-t", type, "-o")

    companion object {
        private const val PNG = "image/png"
        private val FILE_TYPES = listOf("text/uri-list", "x-special/gnome-copied-files")
        private const val TIMEOUT_S = 3L
        private const val MAX_BYTES = 64L * 1024 * 1024

        private fun capture(command: List<String>): ByteArray? {
            if (Programs.find(command.first()) == null) return null
            return runCatching {
                val process = ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD).start()
                process.outputStream.close()
                val output = ByteArrayOutputStream()
                val pump = Thread {
                    process.inputStream.use { input ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0 || output.size() > MAX_BYTES) break
                            output.write(buffer, 0, n)
                        }
                    }
                }.apply { isDaemon = true; start() }
                if (!process.waitFor(TIMEOUT_S, TimeUnit.SECONDS)) process.destroyForcibly()
                pump.join(1_000)
                output.toByteArray().takeIf { process.exitValue() == 0 }
            }.getOrNull()
        }

        private fun readAwt(): ClipboardContent? {
            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
            if (clipboard.isDataFlavorAvailable(DataFlavor.javaFileListFlavor)) {
                @Suppress("UNCHECKED_CAST")
                val files = clipboard.getData(DataFlavor.javaFileListFlavor) as List<File>
                if (files.isNotEmpty()) return ClipboardContent.Files(files)
            }
            if (clipboard.isDataFlavorAvailable(DataFlavor.imageFlavor)) {
                val image = clipboard.getData(DataFlavor.imageFlavor) as? RenderedImage
                if (image != null) {
                    val png = ByteArrayOutputStream().also { ImageIO.write(image, "png", it) }.toByteArray()
                    return ClipboardContent.Image(png, PNG)
                }
            }
            if (clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)) {
                val text = clipboard.getData(DataFlavor.stringFlavor) as String
                val files = ClipboardParser.uriList(text)
                return if (files.isNotEmpty() && text.lines().all { it.isBlank() || it.startsWith("file:") || it.startsWith("#") }) ClipboardContent.Files(files) else ClipboardContent.Text(text)
            }
            return null
        }
    }
}
