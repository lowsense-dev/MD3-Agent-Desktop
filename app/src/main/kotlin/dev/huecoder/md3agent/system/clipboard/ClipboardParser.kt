package dev.huecoder.md3agent.system.clipboard

import java.io.File
import java.net.URI

/** Reads the file lists other programs put on the clipboard (text/uri-list, GNOME's copied-files). */
object ClipboardParser {
    fun uriList(text: String): List<File> = text.lineSequence()
        .map { it.trim() }
        .filter { it.startsWith("file:") }
        .mapNotNull { line ->
            runCatching {
                val uri = URI(line)
                // file://localhost/x and file:///x are the same local file; any other host is not local.
                if (!uri.host.isNullOrEmpty() && uri.host != "localhost") return@runCatching null
                uri.path?.takeIf { it.startsWith("/") }?.let(::File)
            }.getOrNull()
        }
        .toList()
}
