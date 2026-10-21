package dev.huecoder.md3agent.system.files

import dev.huecoder.md3agent.data.settings.FileScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

class FileAccessException(message: String) : IllegalStateException(message)

data class FileEntry(val name: String, val isDirectory: Boolean, val sizeBytes: Long, val modifiedAt: Long)

/**
 * The files the assistant's tools may touch: always its own workspace (plus reading chat attachments and uploads),
 * and with [FileScope.HOME] everything under [home].
 */
class DeviceFiles(
    workspace: File,
    attachments: File,
    uploads: File,
    private val home: File = File(System.getProperty("user.home")),
) {
    val workspace: File by lazy { workspace.apply { mkdirs() }.canonicalFile }
    val attachments: File by lazy { attachments.canonicalFile }
    val uploads: File by lazy { uploads.apply { mkdirs() }.canonicalFile }
    private val homeDir: File by lazy { home.canonicalFile }
    val homePath: String get() = homeDir.path

    fun resolve(raw: String): File {
        val path = raw.trim().ifEmpty { "." }
        val expanded = if (path == "~" || path.startsWith("~/")) homeDir.path + path.removePrefix("~") else path
        val file = File(expanded).let { if (it.isAbsolute) it else File(workspace, expanded) }
        return try {
            file.canonicalFile
        } catch (e: IOException) {
            file.absoluteFile
        }
    }

    suspend fun list(file: File, scope: FileScope): List<FileEntry> = withContext(Dispatchers.IO) {
        requireAllowed(file, scope, write = false)
        val children = file.listFiles()
        when {
            children != null -> children.map { FileEntry(it.name, it.isDirectory, if (it.isDirectory) 0 else it.length(), it.lastModified()) }
            !file.exists() -> throw FileAccessException("${file.path} does not exist")
            !file.isDirectory -> throw FileAccessException("${file.path} is a file, not a folder")
            else -> throw FileAccessException("${file.path} cannot be opened: permission denied")
        }
    }

    suspend fun <T> withLocalCopy(file: File, scope: FileScope, maxBytes: Long, block: suspend (File) -> T): T {
        requireAllowed(file, scope, write = false)
        val readable = withContext(Dispatchers.IO) { file.isFile && file.canRead() }
        if (!readable) throw FileAccessException("${file.path} does not exist or cannot be read")
        if (file.length() > maxBytes) throw FileAccessException("${file.name} is larger than ${maxBytes / BYTES_PER_MB} MB")
        return block(file)
    }

    suspend fun write(file: File, scope: FileScope, bytes: ByteArray, append: Boolean) {
        requireAllowed(file, scope, write = true)
        withContext(Dispatchers.IO) {
            try {
                file.parentFile?.mkdirs()
                if (append) file.appendBytes(bytes) else file.writeBytes(bytes)
            } catch (e: IOException) {
                throw FileAccessException("Cannot write ${file.path}: ${e.message}")
            }
        }
    }

    private fun requireAllowed(file: File, scope: FileScope, write: Boolean) {
        if (file.isWithin(workspace) || (!write && (file.isWithin(attachments) || file.isWithin(uploads)))) return
        if (scope == FileScope.HOME && file.isWithin(homeDir)) return
        val action = if (write) "write" else "read"
        throw FileAccessException("${file.path} is outside the folders the assistant may $action")
    }

    private fun File.isWithin(folder: File): Boolean = path == folder.path || path.startsWith(folder.path + File.separator)

    private companion object {
        const val BYTES_PER_MB = 1024L * 1024
    }
}
