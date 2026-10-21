package dev.huecoder.md3agent.ui.components

import androidx.compose.ui.platform.Clipboard
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

@Suppress("UnusedReceiverParameter")
suspend fun Clipboard.copyText(text: String) = Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
