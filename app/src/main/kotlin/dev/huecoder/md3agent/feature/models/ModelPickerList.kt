package dev.huecoder.md3agent.feature.models

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import dev.huecoder.md3agent.ui.text.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Add
import com.composables.icons.materialsymbols.rounded.Image
import com.composables.icons.materialsymbols.rounded.Neurology
import com.composables.icons.materialsymbols.rounded.Visibility
import com.composables.icons.materialsymbols.roundedfilled.Check_circle
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.llm.models.ReasoningEffort
import dev.huecoder.md3agent.ui.components.VendorBadge

@Composable
internal fun ModelList(
    rows: List<ModelRow>,
    selectedId: String?,
    onPick: (ModelRow) -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    customId: String? = null,
    onPickCustom: (String) -> Unit = {},
    emptyText: Int = R.string.model_empty,
) {
    if (rows.isEmpty() && customId == null) {
        Box(modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.TopCenter) {
            Text(stringResource(emptyText), color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
        return
    }
    val count = rows.size + if (customId != null) 1 else 0
    LazyColumn(state = listState, modifier = modifier, contentPadding = PickerListPadding, verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
        itemsIndexed(rows, key = { _, row -> row.info.id }) { index, row ->
            ModelRowItem(row, isSelected = row.info.id == selectedId, index = index, count = count, onClick = { onPick(row) })
        }
        if (customId != null) {
            item(key = CUSTOM_ROW_KEY) { CustomModelItem(customId, index = rows.size, count = count, onClick = { onPickCustom(customId) }) }
        }
    }
}

@Composable
private fun CustomModelItem(modelId: String, index: Int, count: Int, onClick: () -> Unit) {
    SegmentedListItem(
        onClick = onClick,
        shapes = ListItemDefaults.segmentedShapes(index, count),
        colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        leadingContent = { Icon(MaterialSymbols.Rounded.Add, contentDescription = null) },
    ) {
        Text(stringResource(R.string.model_use_custom, modelId), style = MaterialTheme.typography.titleMediumEmphasized, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ModelRowItem(row: ModelRow, isSelected: Boolean, index: Int, count: Int, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    SegmentedListItem(
        onClick = onClick,
        selected = isSelected,
        shapes = ListItemDefaults.segmentedShapes(index, count),
        colors = ListItemDefaults.segmentedColors(containerColor = colors.surfaceContainer, selectedContainerColor = colors.secondaryContainer),
        leadingContent = {
            VendorBadge(
                row.family,
                containerColor = if (isSelected) colors.secondary else colors.surfaceContainerHighest,
                contentColor = if (isSelected) colors.onSecondary else colors.onSurface,
            )
        },
        supportingContent = {
            Column {
                val context = row.info.contextWindow?.let { stringResource(R.string.model_context, formatContext(it)) }
                val idLine = listOfNotNull(row.info.id.takeIf { it != row.info.displayName }, context).joinToString(ID_SEPARATOR)
                if (idLine.isNotEmpty()) Text(idLine, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                Capabilities(row)
            }
        },
        trailingContent = if (isSelected) {
            { Icon(MaterialSymbols.RoundedFilled.Check_circle, contentDescription = null, tint = colors.primary) }
        } else {
            null
        },
    ) {
        Text(row.info.displayName, style = MaterialTheme.typography.titleMediumEmphasized, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Capabilities(row: ModelRow) {
    val traits = row.traits
    if (!traits.vision && !traits.reasoning && !traits.generatesImages) return
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
        if (traits.vision) CapabilityMark(MaterialSymbols.Rounded.Visibility, R.string.model_filter_vision)
        if (traits.reasoning) CapabilityMark(MaterialSymbols.Rounded.Neurology, R.string.model_filter_reasoning)
        if (traits.generatesImages) CapabilityMark(MaterialSymbols.Rounded.Image, R.string.model_filter_images)
    }
}

@Composable
private fun CapabilityMark(icon: ImageVector, label: Int) {
    Icon(icon, contentDescription = stringResource(label), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(CAPABILITY_ICON))
}

@Composable
internal fun ReasoningPicker(efforts: List<ReasoningEffort>, selected: ReasoningEffort?, onSelect: (ReasoningEffort?) -> Unit, modifier: Modifier = Modifier) {
    val options = listOf<ReasoningEffort?>(null) + efforts
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.model_reasoning), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween), modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, effort ->
                ToggleButton(
                    checked = effort == selected,
                    onCheckedChange = { onSelect(effort) },
                    shapes = when (index) {
                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(effort.label), maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }
    }
}

val ReasoningEffort?.label: Int
    get() = when (this) {
        null -> R.string.reasoning_default
        ReasoningEffort.NONE -> R.string.reasoning_none
        ReasoningEffort.MINIMAL -> R.string.reasoning_minimal
        ReasoningEffort.LOW -> R.string.reasoning_low
        ReasoningEffort.MEDIUM -> R.string.reasoning_medium
        ReasoningEffort.HIGH -> R.string.reasoning_high
        ReasoningEffort.XHIGH -> R.string.reasoning_xhigh
    }

private fun formatContext(tokens: Int): String = when {
    tokens >= MILLION -> "${(tokens / MILLION.toDouble()).let { if (it % 1.0 == 0.0) it.toInt().toString() else "%.1f".format(it) }}M"
    tokens >= THOUSAND -> "${tokens / THOUSAND}K"
    else -> tokens.toString()
}

private const val THOUSAND = 1_000
private const val ID_SEPARATOR = " · "
private const val MILLION = 1_000_000
private val CAPABILITY_ICON = 16.dp
private const val CUSTOM_ROW_KEY = "custom-model-id"
