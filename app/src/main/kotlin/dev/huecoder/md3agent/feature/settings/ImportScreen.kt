package dev.huecoder.md3agent.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.huecoder.md3agent.LocalAppContainer
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.data.transfer.ImportItem
import dev.huecoder.md3agent.data.transfer.ImportPreview
import dev.huecoder.md3agent.data.transfer.Importer
import dev.huecoder.md3agent.data.transfer.UnsupportedTransferFormat
import dev.huecoder.md3agent.data.transfer.WrongTransferPassword
import dev.huecoder.md3agent.platform.LocalPlatformContext
import dev.huecoder.md3agent.ui.components.SectionHeader
import dev.huecoder.md3agent.ui.components.SettingsScaffold
import dev.huecoder.md3agent.ui.text.stringResource
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException

/** Brings providers, agents, MCP servers, memory and chats over from a file the phone app exported. */
@Composable
fun ImportScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val context = LocalPlatformContext.current
    val scope = rememberCoroutineScope()
    val importer = remember(container) { Importer(container) }
    var file by remember { mutableStateOf<File?>(null) }
    var password by remember { mutableStateOf("") }
    var preview by remember { mutableStateOf<ImportPreview?>(null) }
    var replace by remember { mutableStateOf(emptySet<String>()) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val pickTitle = stringResource(R.string.import_choose_file)
    val wrongPassword = stringResource(R.string.import_error_password)
    val newerFormat = stringResource(R.string.import_error_format)
    val badFile = stringResource(R.string.import_error_file)
    val done = stringResource(R.string.import_done)

    SettingsScaffold(stringResource(R.string.settings_import), onBack = onBack) { padding ->
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(padding).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.import_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FilledTonalButton(onClick = {
                scope.launch {
                    container.filePicker.open(pickTitle, multiple = false, imagesOnly = false).firstOrNull()?.let {
                        file = it
                        preview = null
                    }
                }
            }) { Text(file?.name ?: stringResource(R.string.import_choose_file)) }
            OutlinedTextField(
                value = password,
                onValueChange = { password = it; error = null },
                label = { Text(stringResource(R.string.import_password)) },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                enabled = file != null && password.isNotEmpty() && !busy,
                onClick = {
                    val target = file ?: return@Button
                    busy = true
                    scope.launch {
                        try {
                            preview = importer.preview(target.readBytes(), password.toCharArray())
                            replace = emptySet()
                            error = null
                        } catch (e: WrongTransferPassword) {
                            error = wrongPassword
                        } catch (e: UnsupportedTransferFormat) {
                            error = newerFormat
                        } catch (e: IOException) {
                            error = badFile
                        } finally {
                            busy = false
                        }
                    }
                },
            ) { Text(stringResource(R.string.import_read)) }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            preview?.let { found ->
                SectionHeader(stringResource(R.string.import_new_items, found.items.count { !it.conflict }))
                if (found.conflicts.isNotEmpty()) {
                    Text(stringResource(R.string.import_conflicts), style = MaterialTheme.typography.bodyMedium)
                    found.conflicts.forEach { item ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = item.key in replace, onCheckedChange = { on -> replace = if (on) replace + item.key else replace - item.key })
                            Column {
                                Text(kindLabel(item), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                if (item.key != Importer.SETTINGS_KEY) Text(item.label, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }
                Button(enabled = !busy, onClick = {
                    busy = true
                    scope.launch {
                        try {
                            importer.apply(found, replace)
                            context.toast(done)
                            onBack()
                        } finally {
                            busy = false
                        }
                    }
                }) { Text(stringResource(R.string.import_apply)) }
            }
        }
    }
}

@Composable
private fun kindLabel(item: ImportItem): String = stringResource(
    when (item.key.substringBefore(':')) {
        "provider" -> R.string.import_kind_provider
        "agent" -> R.string.import_kind_agent
        "mcp" -> R.string.import_kind_mcp
        "memory" -> R.string.import_kind_memory
        "chat" -> R.string.import_kind_chat
        else -> R.string.import_kind_settings
    },
)
