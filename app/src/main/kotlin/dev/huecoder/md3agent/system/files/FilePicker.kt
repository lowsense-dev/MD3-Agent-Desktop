package dev.huecoder.md3agent.system.files

import dev.huecoder.md3agent.platform.Log
import dev.huecoder.md3agent.system.clipboard.ClipboardParser
import dev.huecoder.md3agent.system.dbus.FileChooser
import dev.huecoder.md3agent.system.dbus.FileFilter
import dev.huecoder.md3agent.system.dbus.FilterRule
import dev.huecoder.md3agent.system.dbus.PortalRequest
import dev.huecoder.md3agent.system.dbus.SessionBus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.freedesktop.dbus.types.UInt32
import org.freedesktop.dbus.types.Variant
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.util.UUID

/** The desktop's own file dialog through xdg-desktop-portal; a plain AWT dialog when there is no portal. */
class FilePicker(private val bus: SessionBus) {
    suspend fun open(title: String, multiple: Boolean, imagesOnly: Boolean): List<File> {
        portal("OpenFile") { chooser, token -> chooser.OpenFile("", title, openOptions(token, multiple, imagesOnly)) }
            ?.let { return it }
        return awtDialog(title, FileDialog.LOAD, null, multiple)
    }

    suspend fun save(title: String, suggestedName: String): File? {
        portal("SaveFile") { chooser, token -> chooser.SaveFile("", title, mapOf("handle_token" to Variant(token), "current_name" to Variant(suggestedName))) }
            ?.let { return it.firstOrNull() }
        return awtDialog(title, FileDialog.SAVE, suggestedName, multiple = false).firstOrNull()
    }

    /** Null when the portal is missing or failed, so the caller can fall back; empty when the user cancelled. */
    private suspend fun portal(method: String, call: (FileChooser, String) -> Unit): List<File>? = withContext(Dispatchers.IO) {
        val connection = bus.connection ?: return@withContext null
        val sender = bus.senderToken ?: return@withContext null
        val token = "md3" + UUID.randomUUID().toString().replace("-", "")
        val path = requestPath(sender, token)
        val reply = CompletableDeferred<PortalRequest.Response>()
        try {
            // Subscribe before calling, or a fast reply could arrive before anyone listens.
            connection.addSigHandler(PortalRequest.Response::class.java) { if (it.path == path) reply.complete(it) }.use {
                val chooser = connection.getRemoteObject(PORTAL, PORTAL_PATH, FileChooser::class.java)
                call(chooser, token)
                val response = reply.await()
                if (response.response.toInt() != 0) return@withContext emptyList()
                @Suppress("UNCHECKED_CAST")
                val uris = (response.results["uris"]?.value as? List<String>) ?: (response.results["uris"]?.value as? Array<String>)?.toList().orEmpty()
                filesFrom(uris)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Portal $method failed, using the built-in dialog: ${e.message}")
            null
        }
    }

    private suspend fun awtDialog(title: String, mode: Int, name: String?, multiple: Boolean): List<File> = withContext(Dispatchers.Main) {
        val dialog = FileDialog(null as Frame?, title, mode).apply {
            isMultipleMode = multiple
            name?.let { file = it }
            isVisible = true
        }
        dialog.files.orEmpty().toList()
    }

    companion object {
        private const val TAG = "FilePicker"
        private const val PORTAL = "org.freedesktop.portal.Desktop"
        private const val PORTAL_PATH = "/org/freedesktop/portal/desktop"
        private const val MIME_RULE = 1L

        fun requestPath(sender: String, token: String): String = "$PORTAL_PATH/request/$sender/$token"

        fun openOptions(token: String, multiple: Boolean, imagesOnly: Boolean): Map<String, Variant<*>> = buildMap {
            put("handle_token", Variant(token))
            put("multiple", Variant(multiple))
            if (imagesOnly) put("filters", Variant(listOf(FileFilter("Images", listOf(FilterRule(UInt32(MIME_RULE), "image/*")))), "a(sa(us))"))
        }

        fun filesFrom(uris: List<String>): List<File> = ClipboardParser.uriList(uris.joinToString("\n"))
    }
}
