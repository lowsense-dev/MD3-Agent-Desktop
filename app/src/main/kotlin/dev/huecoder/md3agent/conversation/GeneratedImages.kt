package dev.huecoder.md3agent.conversation

import java.util.Base64
import dev.huecoder.md3agent.platform.Log
import dev.huecoder.md3agent.data.attachments.Attachment
import dev.huecoder.md3agent.data.attachments.AttachmentStore
import dev.huecoder.md3agent.net.HttpClientProvider
import dev.huecoder.md3agent.net.executeCancellable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import java.io.IOException

class GeneratedImages(private val attachments: AttachmentStore, private val httpClients: HttpClientProvider) {
    suspend fun store(urls: List<String>): List<Attachment> = urls.distinct().mapIndexedNotNull { index, url ->
        try {
            val (bytes, extension) = fetch(url)
            attachments.importBytes("image-${index + 1}.$extension", bytes, forModel = true)
        } catch (e: IOException) {
            Log.w(TAG, "Generated image could not be saved", e)
            null
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Generated image is not valid base64", e)
            null
        } catch (e: TimeoutCancellationException) {
            Log.w(TAG, "Generated image took too long to download", e)
            null
        }
    }

    private suspend fun fetch(url: String): Pair<ByteArray, String> = withContext(Dispatchers.IO) {
        if (url.startsWith(DATA_PREFIX)) {
            val header = url.substringBefore(',')
            val mime = header.removePrefix(DATA_PREFIX).substringBefore(';')
            Base64.getMimeDecoder().decode(url.substringAfter(',')) to extensionOf(mime)
        } else {
            val link = url.toHttpUrlOrNull() ?: throw IOException("Not an http(s) image link")
            // Bounded, and cancellable: this also runs while a cancelled reply is being saved.
            withTimeout(DOWNLOAD_TIMEOUT_MS) {
                httpClients.current().newCall(Request.Builder().url(link).build()).executeCancellable { response ->
                    if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                    val mime = response.header("Content-Type").orEmpty().substringBefore(';')
                    val source = response.body.source()
                    if (source.request(MAX_IMAGE_BYTES + 1)) throw IOException("Generated image is larger than ${MAX_IMAGE_BYTES / BYTES_PER_MB} MB")
                    source.buffer.readByteArray() to extensionOf(mime)
                }
            }
        }
    }

    private fun extensionOf(mime: String) = when (mime.lowercase()) {
        "image/jpeg", "image/jpg" -> "jpg"
        "image/webp" -> "webp"
        "image/gif" -> "gif"
        else -> "png"
    }

    private companion object {
        const val TAG = "GeneratedImages"
        const val DATA_PREFIX = "data:"
        const val DOWNLOAD_TIMEOUT_MS = 20_000L
        const val MAX_IMAGE_BYTES = 25L * 1024 * 1024
        const val BYTES_PER_MB = 1024 * 1024
    }
}
