package dev.huecoder.md3agent.data.attachments

import dev.huecoder.md3agent.data.files.ViewableFile
import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.net.URLConnection
import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.UUID
import kotlin.math.max

@Serializable
data class Attachment(
    val id: String,
    val name: String,
    val mimeType: String,
    val sizeBytes: Long,
    val forModel: Boolean = true,
) {
    val isImage: Boolean get() = mimeType.startsWith("image/")

    val isText: Boolean get() = TextFormats.isText(mimeType, name)

    companion object {
        private val listSerializer = ListSerializer(serializer())
        private val json = Json { ignoreUnknownKeys = true }

        fun encode(attachments: List<Attachment>): String? = attachments.takeIf { it.isNotEmpty() }?.let { json.encodeToString(listSerializer, it) }

        fun decode(raw: String?): List<Attachment> =
            raw?.let { runCatching { json.decodeFromString(listSerializer, it) }.getOrNull() }.orEmpty()
    }
}

sealed interface AttachmentContent {
    data class Image(val dataUrl: String) : AttachmentContent
    data class Text(val name: String, val text: String) : AttachmentContent
    data class Stored(val name: String, val mimeType: String, val sizeBytes: Long, val path: String) : AttachmentContent
}

class AttachmentTooLargeException(val limitMb: Int) : IOException("Attachment is larger than $limitMb MB")

class AttachmentStore(private val root: File, private val downloads: () -> File) {
    val directory: File get() = root.apply { mkdirs() }

    /** A file the user picked, pasted or dropped. */
    suspend fun import(source: File, name: String = source.name): Attachment = withContext(Dispatchers.IO) {
        if (!source.isFile) throw IOException("Could not open the file")
        if (source.length() > MAX_FILE_BYTES) throw AttachmentTooLargeException((MAX_FILE_BYTES / BYTES_PER_MB).toInt())
        val mimeType = TextFormats.guessMimeType(name)
        if (mimeType.startsWith("image/")) {
            storeImageForModel(name, mimeType) { source.readBytes() }
        } else {
            storeRaw(name, mimeType, forModel = true) { target -> source.inputStream().use { copyCapped(it, target) } }
        }
    }

    suspend fun importLocal(source: File, forModel: Boolean): Attachment = withContext(Dispatchers.IO) {
        val name = source.name
        val mimeType = TextFormats.guessMimeType(name)
        if (forModel && mimeType.startsWith("image/")) {
            storeImageForModel(name, mimeType) { source.readBytes() }
        } else {
            storeRaw(name, mimeType, forModel) { target -> source.inputStream().use { copyCapped(it, target) } }
        }
    }

    suspend fun importBytes(name: String, bytes: ByteArray, forModel: Boolean): Attachment = withContext(Dispatchers.IO) {
        val mimeType = TextFormats.guessMimeType(name)
        if (forModel && mimeType.startsWith("image/")) {
            storeImageForModel(name, mimeType) { bytes }
        } else {
            storeRaw(name, mimeType, forModel) { target -> target.writeBytes(bytes) }
        }
    }

    fun file(attachment: Attachment): File = File(directory, attachment.id)

    fun viewable(attachment: Attachment) = ViewableFile(file(attachment), attachment.name, attachment.mimeType)

    // Every model round re-sends the whole history; encoding each picture once instead of per round saves a lot of IO.
    private val contentCache = SizedLruCache<String, AttachmentContent>(MAX_CACHED_CHARS) { content ->
        when (content) {
            is AttachmentContent.Image -> content.dataUrl.length.toLong()
            is AttachmentContent.Text -> content.text.length.toLong()
            is AttachmentContent.Stored -> 0L
        }
    }

    suspend fun contentFor(attachment: Attachment): AttachmentContent? = withContext(Dispatchers.IO) {
        val file = file(attachment)
        if (!attachment.forModel || !file.exists()) return@withContext null
        val key = "${attachment.id}:${file.length()}:${file.lastModified()}"
        contentCache.get(key)?.let { return@withContext it }
        when {
            attachment.isImage -> AttachmentContent.Image("data:${attachment.mimeType};base64," + Base64.getEncoder().encodeToString(file.readBytes()))
            attachment.isText && file.length() <= MAX_INLINE_TEXT_BYTES -> AttachmentContent.Text(attachment.name, file.readText())
            else -> AttachmentContent.Stored(attachment.name, attachment.mimeType, file.length(), file.absolutePath)
        }.also { contentCache.put(key, it) }
    }

    suspend fun duplicate(attachment: Attachment): Attachment = withContext(Dispatchers.IO) {
        val copy = attachment.copy(id = newId(TextFormats.extension(attachment.id)))
        file(attachment).takeIf { it.exists() }?.copyTo(file(copy))
        copy
    }

    suspend fun delete(attachments: List<Attachment>) = withContext(Dispatchers.IO) {
        attachments.forEach { file(it).delete() }
    }

    /** Copies [target] into Downloads/MD3 Agent, never overwriting: a clash gets " (2)", " (3)"… */
    suspend fun exportToDownloads(target: ViewableFile): File = withContext(Dispatchers.IO) {
        val folder = File(downloads(), DOWNLOADS_FOLDER).apply { mkdirs() }
        val base = target.name.substringBeforeLast('.')
        val extension = target.name.substringAfterLast('.', "").let { if (it.isEmpty()) "" else ".$it" }
        val destination = generateSequence(1) { it + 1 }
            .map { n -> File(folder, if (n == 1) target.name else "$base ($n)$extension") }
            .first { !it.exists() }
        target.file.copyTo(destination)
    }

    /** Pictures are re-encoded as JPEG at most 1600 px on the long side, like on the phone; formats ImageIO can't read are kept as they are. */
    private fun storeImageForModel(name: String, mimeType: String, bytes: () -> ByteArray): Attachment {
        val raw = bytes()
        val decoded = runCatching { ImageIO.read(raw.inputStream()) }.getOrNull()
            ?: return storeRaw(name, mimeType, forModel = true) { it.writeBytes(raw) }
        val longest = max(decoded.width, decoded.height)
        val scale = if (longest > MAX_IMAGE_SIDE_PX) MAX_IMAGE_SIDE_PX.toFloat() / longest else 1f
        val width = (decoded.width * scale).toInt().coerceAtLeast(1)
        val height = (decoded.height * scale).toInt().coerceAtLeast(1)
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        image.createGraphics().apply {
            setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            color = Color.WHITE
            fillRect(0, 0, width, height)
            drawImage(decoded, 0, 0, width, height, null)
            dispose()
        }
        val id = newId(JPEG_EXTENSION)
        val target = File(directory, id)
        val writer = ImageIO.getImageWritersByFormatName("jpeg").next()
        try {
            ImageIO.createImageOutputStream(target).use { output ->
                writer.output = output
                val params = writer.defaultWriteParam.apply {
                    compressionMode = ImageWriteParam.MODE_EXPLICIT
                    compressionQuality = JPEG_QUALITY / 100f
                }
                writer.write(null, IIOImage(image, null, null), params)
            }
        } finally {
            writer.dispose()
        }
        return Attachment(id, name, STORED_IMAGE_MIME, target.length())
    }

    private fun storeRaw(name: String, mimeType: String, forModel: Boolean, write: (File) -> Unit): Attachment {
        val target = File(directory, newId(TextFormats.extension(name)))
        try {
            write(target)
        } catch (e: IOException) {
            target.delete()
            throw e
        }
        return Attachment(target.name, name, mimeType, target.length(), forModel)
    }

    private fun copyCapped(input: InputStream, target: File) {
        target.outputStream().use { output ->
            val chunk = ByteArray(COPY_BUFFER_BYTES)
            var total = 0L
            while (true) {
                val read = input.read(chunk)
                if (read == -1) break
                total += read
                if (total > MAX_FILE_BYTES) throw AttachmentTooLargeException((MAX_FILE_BYTES / BYTES_PER_MB).toInt())
                output.write(chunk, 0, read)
            }
        }
    }

    private fun newId(extension: String): String {
        val base = UUID.randomUUID().toString()
        return if (extension.matches(SAFE_EXTENSION)) "$base.$extension" else base
    }

    private companion object {
        const val DOWNLOADS_FOLDER = "MD3 Agent"
        const val STORED_IMAGE_MIME = "image/jpeg"
        const val JPEG_EXTENSION = "jpg"
        const val MAX_IMAGE_SIDE_PX = 1600
        const val JPEG_QUALITY = 85
        const val COPY_BUFFER_BYTES = 64 * 1024
        const val BYTES_PER_MB = 1024L * 1024
        const val MAX_FILE_BYTES = 64 * BYTES_PER_MB
        const val MAX_INLINE_TEXT_BYTES = 256 * 1024L
        const val MAX_CACHED_CHARS = 32L * 1024 * 1024
        val SAFE_EXTENSION = Regex("[a-z0-9]{1,8}")
    }
}

object TextFormats {
    private val textMimeTypes = setOf(
        "application/json", "application/xml", "application/javascript", "application/x-yaml",
        "application/yaml", "application/toml", "application/x-sh", "application/sql", "application/csv",
    )
    private val textExtensions = setOf(
        "txt", "md", "markdown", "csv", "tsv", "json", "xml", "yaml", "yml", "toml", "ini", "conf", "cfg", "log",
        "kt", "kts", "java", "py", "js", "ts", "tsx", "jsx", "go", "rs", "c", "h", "cpp", "hpp", "cs", "swift",
        "rb", "php", "sh", "bash", "zsh", "sql", "html", "htm", "css", "scss", "gradle", "properties", "env",
    )

    fun isText(mimeType: String, name: String): Boolean =
        mimeType.startsWith("text/") || mimeType in textMimeTypes || extension(name) in textExtensions

    fun guessMimeType(name: String): String {
        val extension = extension(name)
        return when {
            extension in textExtensions -> "text/plain"
            extension.isEmpty() -> GENERIC_MIME
            else -> runCatching { Files.probeContentType(Path.of("file.$extension")) }.getOrNull()
                ?: URLConnection.guessContentTypeFromName("file.$extension")
                ?: GENERIC_MIME
        }
    }

    fun extension(name: String) = name.substringAfterLast('.', missingDelimiterValue = "").lowercase()

    private const val GENERIC_MIME = "application/octet-stream"
}
