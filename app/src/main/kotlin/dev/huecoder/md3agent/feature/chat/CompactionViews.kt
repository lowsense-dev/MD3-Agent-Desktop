package dev.huecoder.md3agent.feature.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.huecoder.md3agent.ui.text.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Compress
import com.composables.icons.materialsymbols.rounded.Expand_less
import com.composables.icons.materialsymbols.rounded.Expand_more
import com.composables.icons.materialsymbols.rounded.Summarize
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.conversation.ContextUsage
import dev.huecoder.md3agent.ui.text.asString
import kotlin.math.roundToInt

/** Divider where the chat was compacted; the summary the model now sees opens on tap. */
@Composable
fun CompactedDivider(item: ChatItem.Compacted, modifier: Modifier = Modifier) {
    var open by rememberSaveable(item.id) { mutableStateOf(false) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            HorizontalDivider(Modifier.weight(1f))
            AssistChip(
                onClick = { open = !open },
                label = { Text(stringResource(R.string.chat_compacted)) },
                leadingIcon = { Icon(MaterialSymbols.Rounded.Summarize, contentDescription = null, modifier = Modifier.size(AssistChipDefaults.IconSize)) },
                trailingIcon = {
                    Icon(
                        if (open) MaterialSymbols.Rounded.Expand_less else MaterialSymbols.Rounded.Expand_more,
                        contentDescription = stringResource(if (open) R.string.chat_compacted_hide else R.string.chat_compacted_show),
                        modifier = Modifier.size(AssistChipDefaults.IconSize),
                    )
                },
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            HorizontalDivider(Modifier.weight(1f))
        }
        AnimatedVisibility(open) {
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
                SelectionContainer {
                    Text(item.summary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp))
                }
            }
        }
    }
}

/** Codex-style context gauge for the top bar: how full the model's context is, with a way to compact now. */
@Composable
fun ContextMeter(usage: ContextUsage?, compacting: Boolean, canCompact: Boolean, onCompact: () -> Unit) {
    if (usage == null && !compacting) return
    var open by rememberSaveable { mutableStateOf(false) }
    val summary = usage?.let { contextSummary(it) }.orEmpty()
    Box {
        Surface(
            onClick = { open = true },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.padding(horizontal = 4.dp).semantics { contentDescription = summary },
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.heightIn(min = 36.dp).padding(start = 8.dp, end = 10.dp),
            ) {
                val share = usage?.share
                when {
                    compacting -> CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.5.dp)
                    share != null -> CircularProgressIndicator(
                        progress = { share },
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.5.dp,
                        color = if (share >= WARN_SHARE) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    )
                    else -> Icon(MaterialSymbols.Rounded.Summarize, contentDescription = null, modifier = Modifier.size(18.dp))
                }
                usage?.let { Text(shortUsage(it), style = MaterialTheme.typography.labelMedium, maxLines = 1) }
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.chat_context_title), style = MaterialTheme.typography.titleSmall)
                Text(if (compacting) stringResource(R.string.chat_compacting) else summary, style = MaterialTheme.typography.bodyMedium)
                if (usage?.estimated == true && !compacting) {
                    Text(
                        stringResource(R.string.chat_context_estimated),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            DropdownMenuItem(
                text = {
                    Column {
                        Text(stringResource(R.string.chat_context_compact))
                        Text(
                            stringResource(R.string.chat_context_compact_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                leadingIcon = { Icon(MaterialSymbols.Rounded.Compress, contentDescription = null) },
                enabled = canCompact && !compacting,
                onClick = { open = false; onCompact() },
            )
        }
    }
}

/** "8.8k/1M" for the top bar pill; just the used amount when the window is unknown. */
@Composable
private fun shortUsage(usage: ContextUsage): String {
    val used = ReplyMetaFormat.tokens(usage.usedTokens.toLong()).asString()
    return usage.windowTokens?.let { used + "/" + ReplyMetaFormat.tokens(it.toLong()).asString() } ?: used
}

@Composable
private fun contextSummary(usage: ContextUsage): String {
    val used = ReplyMetaFormat.tokens(usage.usedTokens.toLong()).asString()
    val window = usage.windowTokens
    val share = usage.share
    return if (window == null || share == null) {
        stringResource(R.string.chat_context_used_unknown_window, used)
    } else {
        stringResource(R.string.chat_context_used, used, ReplyMetaFormat.tokens(window.toLong()).asString(), (share * PERCENT).roundToInt())
    }
}

private const val WARN_SHARE = 0.8f
private const val PERCENT = 100
