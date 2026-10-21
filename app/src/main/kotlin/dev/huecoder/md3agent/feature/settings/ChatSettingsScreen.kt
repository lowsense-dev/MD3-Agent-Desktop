package dev.huecoder.md3agent.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import dev.huecoder.md3agent.ui.text.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.ui.components.LabeledSwitch
import dev.huecoder.md3agent.ui.components.SectionHeader
import dev.huecoder.md3agent.ui.components.SettingsScaffold
import dev.huecoder.md3agent.ui.components.appViewModel

@Composable
fun ChatSettingsScreen(onBack: () -> Unit) {
    val viewModel = appViewModel { SettingsViewModel(it.settings) }
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    SettingsScaffold(stringResource(R.string.settings_chat), onBack) { padding ->
        val current = settings ?: return@SettingsScaffold
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionHeader(stringResource(R.string.chat_settings_section_replies))
            LabeledSwitch(
                title = stringResource(R.string.chat_settings_show_tokens),
                summary = stringResource(R.string.chat_settings_show_tokens_summary),
                checked = current.chat.showTokenUsage,
                onCheckedChange = { value -> viewModel.updateChat { it.copy(showTokenUsage = value) } },
            )
            LabeledSwitch(
                title = stringResource(R.string.chat_settings_auto_title),
                summary = stringResource(R.string.chat_settings_auto_title_summary),
                checked = current.chat.autoTitle,
                onCheckedChange = { value -> viewModel.updateChat { it.copy(autoTitle = value) } },
            )
            SectionHeader(stringResource(R.string.chat_settings_section_context))
            LabeledSwitch(
                title = stringResource(R.string.chat_settings_auto_compact),
                summary = stringResource(R.string.chat_settings_auto_compact_summary),
                checked = current.chat.autoCompact,
                onCheckedChange = { value -> viewModel.updateChat { it.copy(autoCompact = value) } },
            )
            if (current.chat.autoCompact) {
                PercentSlider(R.string.chat_settings_compact_at, current.chat.compactAt, COMPACT_AT_RANGE) { value ->
                    viewModel.updateChat { it.copy(compactAt = value) }
                }
            }
            SectionHeader(stringResource(R.string.chat_settings_section_typing))
            TypingSettingsCard(current.chat.typing, onChange = { style -> viewModel.updateChat { it.copy(typing = style) } })
        }
    }
}

private val COMPACT_AT_RANGE = 0.5f..0.95f


@Composable
internal fun PercentSlider(title: Int, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    val haptics = dev.huecoder.md3agent.ui.haptics.rememberHaptics()
    var dragged by remember(value) { androidx.compose.runtime.mutableFloatStateOf(value) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row {
            Text(stringResource(title), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(
                stringResource(R.string.phone_use_percent, kotlin.math.round(dragged * PERCENT).toInt()),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        androidx.compose.material3.Slider(
            value = dragged,
            onValueChange = { next ->
                if (kotlin.math.round(next * PERCENT).toInt() != kotlin.math.round(dragged * PERCENT).toInt()) haptics.preview(next)
                dragged = next
            },
            onValueChangeFinished = { onChange(dragged) },
            valueRange = range,
        )
    }
}

private const val PERCENT = 100f
