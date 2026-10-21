package dev.huecoder.md3agent.feature.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import dev.huecoder.md3agent.ui.text.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Bolt
import com.composables.icons.materialsymbols.rounded.Delete
import com.composables.icons.materialsymbols.rounded.Edit
import com.composables.icons.materialsymbols.rounded.More_vert
import com.composables.icons.materialsymbols.rounded.Palette
import com.composables.icons.materialsymbols.rounded.Smart_toy
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.data.chat.ChatLook
import dev.huecoder.md3agent.feature.home.DeleteChatsDialog
import dev.huecoder.md3agent.feature.home.NameDialog
import dev.huecoder.md3agent.feature.models.label
import dev.huecoder.md3agent.ui.components.BackButton
import dev.huecoder.md3agent.ui.components.ChatBadge
import dev.huecoder.md3agent.ui.components.ModelChip
import dev.huecoder.md3agent.ui.haptics.clickHaptics

private enum class ChatDialog { RENAME, DELETE, LOOK }

@Composable
fun ChatTopBar(
    title: String,
    state: ChatUiState,
    onBack: () -> Unit,
    onPickModel: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    onLookChange: (ChatLook) -> Unit,
    yolo: Boolean = false,
    onYoloChange: (Boolean) -> Unit = {},
    onCompact: () -> Unit = {},
) {
    var dialog by rememberSaveable { mutableStateOf<ChatDialog?>(null) }
    val modelLabel = state.modelLabel()
    TopAppBar(
        title = {
            if (state.isStarted) {
                StartedChatTitle(title, state.look, yolo, onOpenLook = { dialog = ChatDialog.LOOK })
            } else {
                Text(title, style = MaterialTheme.typography.titleMediumEmphasized, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
        navigationIcon = { BackButton(onBack) },
        // Transparent: messages scroll under the bar instead of being cut off by a solid strip.
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, scrolledContainerColor = Color.Transparent),
        actions = {
            if (!state.isStarted) {
                // Mirrors the back button: its arrow sits 20dp from the left edge, the chip ends 20dp from the right.
                ModelChip(family = state.modelFamily, label = modelLabel, onClick = onPickModel, modifier = Modifier.padding(start = 12.dp, end = 16.dp))
            } else {
                ContextMeter(state.context, state.isCompacting, canCompact = !state.isStreaming, onCompact = onCompact)
                ChatMenu(
                    modelSummary = listOfNotNull(modelLabel, state.agentName).joinToString(LABEL_SEPARATOR),
                    onPickModel = onPickModel,
                    onRename = { dialog = ChatDialog.RENAME },
                    onLook = { dialog = ChatDialog.LOOK },
                    onDelete = { dialog = ChatDialog.DELETE },
                    yolo = yolo,
                    onYoloChange = onYoloChange,
                )
            }
        },
    )
    when (dialog) {
        ChatDialog.RENAME -> NameDialog(
            title = stringResource(R.string.home_rename_title),
            label = stringResource(R.string.home_chat_name),
            initial = title,
            confirmLabel = stringResource(R.string.settings_save),
            onConfirm = { onRename(it); dialog = null },
            onDismiss = { dialog = null },
        )
        ChatDialog.LOOK -> ChatLookSheet(state.look, onChange = onLookChange, onDismiss = { dialog = null })
        ChatDialog.DELETE -> DeleteChatsDialog(count = 1, onConfirm = { dialog = null; onDelete() }, onDismiss = { dialog = null })
        null -> Unit
    }
}

@Composable
private fun StartedChatTitle(title: String, look: ChatLook, yolo: Boolean, onOpenLook: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ChatBadge(
            look,
            size = BADGE_SIZE,
            modifier = Modifier.clip(CircleShape).clickable(onClickLabel = stringResource(R.string.chat_look_title), onClick = onOpenLook),
        )
        Text(title, style = MaterialTheme.typography.titleMediumEmphasized, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        if (yolo) Icon(MaterialSymbols.Rounded.Bolt, contentDescription = stringResource(R.string.chat_yolo), tint = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun ChatUiState.modelLabel(): String? = modelName?.let { name ->
    listOfNotNull(name, selection?.reasoningEffort?.let { stringResource(it.label) }).joinToString(LABEL_SEPARATOR)
}

@Composable
private fun ChatMenu(
    modelSummary: String,
    onPickModel: () -> Unit,
    onRename: () -> Unit,
    onLook: () -> Unit,
    onDelete: () -> Unit,
    yolo: Boolean,
    onYoloChange: (Boolean) -> Unit,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, shapes = IconButtonDefaults.shapes()) {
            Icon(MaterialSymbols.Rounded.More_vert, contentDescription = stringResource(R.string.chat_more_options))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.clickHaptics()) {
            DropdownMenuItem(
                text = {
                    Column {
                        Text(stringResource(R.string.chat_model_and_agent))
                        Text(
                            modelSummary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                leadingIcon = { Icon(MaterialSymbols.Rounded.Smart_toy, contentDescription = null) },
                onClick = { open = false; onPickModel() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.home_rename)) },
                leadingIcon = { Icon(MaterialSymbols.Rounded.Edit, contentDescription = null) },
                onClick = { open = false; onRename() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.chat_look_title)) },
                leadingIcon = { Icon(MaterialSymbols.Rounded.Palette, contentDescription = null) },
                onClick = { open = false; onLook() },
            )
            DropdownMenuItem(
                text = {
                    Column {
                        Text(stringResource(R.string.chat_yolo))
                        Text(
                            stringResource(R.string.chat_yolo_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                leadingIcon = { Icon(MaterialSymbols.Rounded.Bolt, contentDescription = null) },
                trailingIcon = { Switch(checked = yolo, onCheckedChange = null) },
                onClick = { onYoloChange(!yolo) },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.home_delete_chat), color = MaterialTheme.colorScheme.error) },
                leadingIcon = { Icon(MaterialSymbols.Rounded.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                onClick = { open = false; onDelete() },
            )
        }
    }
}

private const val LABEL_SEPARATOR = " · "
private val BADGE_SIZE = 40.dp
