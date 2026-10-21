package dev.huecoder.md3agent.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
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
fun AppearanceScreen(onBack: () -> Unit) {
    val viewModel = appViewModel { SettingsViewModel(it.settings) }
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    SettingsScaffold(stringResource(R.string.settings_appearance), onBack) { padding ->
        val current = settings ?: return@SettingsScaffold
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionHeader(stringResource(R.string.appearance_section_chat_list))
            SwipeActionDropdown(R.string.appearance_swipe_right, current.ui.swipeRight) { action -> viewModel.updateInterface { it.copy(swipeRight = action) } }
            SwipeActionDropdown(R.string.appearance_swipe_left, current.ui.swipeLeft) { action -> viewModel.updateInterface { it.copy(swipeLeft = action) } }
            SectionHeader(stringResource(R.string.appearance_section_theme))
            ChoiceRow(
                title = stringResource(R.string.appearance_theme),
                choices = listOf(
                    Choice(dev.huecoder.md3agent.data.settings.ThemeMode.SYSTEM, R.string.appearance_theme_system),
                    Choice(dev.huecoder.md3agent.data.settings.ThemeMode.LIGHT, R.string.appearance_theme_light),
                    Choice(dev.huecoder.md3agent.data.settings.ThemeMode.DARK, R.string.appearance_theme_dark),
                ),
                selected = current.ui.theme,
                onSelect = { mode -> viewModel.updateInterface { it.copy(theme = mode) } },
                hint = stringResource(R.string.appearance_theme_hint),
            )
            SectionHeader(stringResource(R.string.appearance_section_language))
            ChoiceRow(
                title = stringResource(R.string.appearance_language),
                choices = listOf(
                    Choice(dev.huecoder.md3agent.data.settings.AppLanguage.SYSTEM, R.string.appearance_language_system),
                    Choice(dev.huecoder.md3agent.data.settings.AppLanguage.ENGLISH, R.string.appearance_language_english),
                    Choice(dev.huecoder.md3agent.data.settings.AppLanguage.RUSSIAN, R.string.appearance_language_russian),
                ),
                selected = current.ui.language,
                onSelect = { language -> viewModel.updateInterface { it.copy(language = language) } },
                hint = stringResource(R.string.appearance_language_hint),
            )
        }
    }
}

