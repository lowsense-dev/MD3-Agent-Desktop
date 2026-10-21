package dev.huecoder.md3agent.feature.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.huecoder.md3agent.ui.text.stringResource
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.data.settings.SwipeAction
import dev.huecoder.md3agent.ui.components.DropdownOption
import dev.huecoder.md3agent.ui.components.OptionDropdown

@Composable
fun SwipeActionDropdown(label: Int, selected: SwipeAction, onSelect: (SwipeAction) -> Unit) {
    OptionDropdown(
        label = stringResource(label),
        options = SwipeAction.entries.map { DropdownOption(it, stringResource(it.label)) },
        selected = selected,
        onSelect = onSelect,
        modifier = Modifier.fillMaxWidth(),
    )
}

private val SwipeAction.label: Int
    get() = when (this) {
        SwipeAction.NONE -> R.string.swipe_action_none
        SwipeAction.TRASH -> R.string.swipe_action_trash
        SwipeAction.ARCHIVE -> R.string.swipe_action_archive
        SwipeAction.MOVE -> R.string.swipe_action_move
    }
