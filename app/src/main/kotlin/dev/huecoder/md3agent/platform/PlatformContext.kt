package dev.huecoder.md3agent.platform

import androidx.compose.runtime.staticCompositionLocalOf
import dev.huecoder.md3agent.AppContainer
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.data.files.ViewableFile
import dev.huecoder.md3agent.ui.text.Strings
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.io.File

/**
 * What screens asked Android's Context for, done the desktop way: strings, short notices (shown as a snackbar),
 * opening files and links with the user's default apps, and "share", which puts the files on the clipboard.
 */
class PlatformContext(val container: AppContainer, private val notice: (String) -> Unit) {
    fun getString(id: Int, vararg args: Any): String = Strings.get(id, *args)

    fun toast(id: Int) = notice(Strings.get(id))

    fun toast(text: String) = notice(text)

    fun openFile(target: ViewableFile) {
        if (!target.file.exists() || !open(target.file.path)) toast(Strings.get(R.string.chat_attachment_no_viewer, target.name))
    }

    fun openUrl(url: String) {
        if (!open(url)) toast(url)
    }

    /** No share sheet on the desktop: the files go to the clipboard, ready to paste into a file manager, chat or mail. */
    fun shareFiles(targets: List<ViewableFile>) {
        if (targets.isEmpty()) return
        runCatching {
            Toolkit.getDefaultToolkit().systemClipboard.setContents(FileListSelection(targets.map { it.file }), null)
        }.onSuccess { toast(Strings.plural(R.plurals.files_copied_to_clipboard, targets.size, targets.size)) }
            .onFailure { toast(R.string.files_error_share) }
    }

    private fun open(target: String): Boolean = runCatching {
        ProcessBuilder("xdg-open", target).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start()
        true
    }.getOrElse {
        runCatching { java.awt.Desktop.getDesktop().browse(if (target.contains("://")) java.net.URI(target) else File(target).toURI()) }.isSuccess
    }

    private class FileListSelection(private val files: List<File>) : Transferable {
        private val uriList = DataFlavor("text/uri-list;class=java.lang.String")
        private val gnome = DataFlavor("x-special/gnome-copied-files;class=java.lang.String")
        private val uris get() = files.joinToString("\r\n") { it.toURI().toASCIIString().replaceFirst("file:/", "file:///") }

        override fun getTransferDataFlavors() = arrayOf(DataFlavor.javaFileListFlavor, uriList, gnome, DataFlavor.stringFlavor)

        override fun isDataFlavorSupported(flavor: DataFlavor) = transferDataFlavors.any { it.equals(flavor) }

        override fun getTransferData(flavor: DataFlavor): Any = when {
            flavor.equals(DataFlavor.javaFileListFlavor) -> files
            flavor.equals(uriList) -> uris
            flavor.equals(gnome) -> "copy\n" + uris.replace("\r\n", "\n")
            flavor.equals(DataFlavor.stringFlavor) -> files.joinToString("\n") { it.path }
            else -> throw UnsupportedFlavorException(flavor)
        }
    }
}

val LocalPlatformContext = staticCompositionLocalOf<PlatformContext> { error("PlatformContext is not provided") }
