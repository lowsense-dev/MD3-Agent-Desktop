package dev.huecoder.md3agent.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.Modifier
import dev.huecoder.md3agent.ui.text.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Delete
import com.composables.icons.materialsymbols.rounded.Visibility
import com.composables.icons.materialsymbols.rounded.Visibility_off
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.data.settings.ApiFormat
import dev.huecoder.md3agent.ui.components.SectionHeader
import dev.huecoder.md3agent.ui.components.FormField
import dev.huecoder.md3agent.ui.components.SaveAction
import dev.huecoder.md3agent.ui.components.SettingsScaffold
import dev.huecoder.md3agent.ui.components.appViewModel
import dev.huecoder.md3agent.ui.text.asString

@Composable
fun ProviderEditorScreen(providerId: String?, onDone: () -> Unit) {
    val viewModel = appViewModel(key = "provider-$providerId") { ProviderEditorViewModel(providerId, it.settings, it.modelCatalog) }
    val form by viewModel.form.collectAsStateWithLifecycle()
    val isDirty by viewModel.isDirty.collectAsStateWithLifecycle()

    SettingsScaffold(
        title = stringResource(if (form?.isNew == false) R.string.provider_title else R.string.provider_title_new),
        onBack = onDone,
        save = SaveAction(isDirty, form?.canSave == true) { viewModel.save(onDone) },
        actions = {
            if (form?.isNew == false) {
                IconButton(onClick = { viewModel.delete(onDone) }, shapes = IconButtonDefaults.shapes()) {
                    Icon(MaterialSymbols.Rounded.Delete, contentDescription = stringResource(R.string.provider_delete))
                }
            }
        },
    ) { padding ->
        val current = form ?: return@SettingsScaffold
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (current.isNew) {
                ProviderPresetRow(current.preset) { preset -> viewModel.edit { it.withPreset(preset) } }
            }
            ChoiceRow(
                title = stringResource(R.string.provider_format),
                choices = apiFormatChoices(ApiFormat.CHAT),
                selected = current.format,
                onSelect = { format -> viewModel.edit { it.withFormat(format) } },
                hint = stringResource(R.string.provider_format_hint),
            )
            FormField(
                value = current.name,
                onValueChange = { value -> viewModel.edit { it.copy(name = value) } },
                label = stringResource(R.string.provider_name),
                modifier = Modifier.fillMaxWidth(),
            )
            FormField(
                value = current.baseUrl,
                onValueChange = { value -> viewModel.edit { it.copy(baseUrl = value) } },
                label = stringResource(R.string.provider_base_url),
                placeholder = current.format.defaultBaseUrl,
                isError = current.baseUrlError != null,
                supportingText = current.baseUrlError?.asString(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
            ApiKeysField(
                value = current.apiKey,
                onValueChange = { value -> viewModel.edit { it.copy(apiKey = value) } },
            )

            SectionHeader(stringResource(R.string.provider_section_headers))
            HeaderEditor(
                headers = current.headers,
                onChange = { headers -> viewModel.edit { it.copy(headers = headers) } },
            )
        }
    }
}

@Composable
internal fun ApiKeysField(value: String, onValueChange: (String) -> Unit) {
    var revealed by rememberSaveable { mutableStateOf(false) }
    FormField(
        value = value,
        onValueChange = onValueChange,
        label = stringResource(R.string.provider_api_key),
        supportingText = stringResource(R.string.provider_api_keys_hint),
        singleLine = false,
        visualTransformation = if (revealed) VisualTransformation.None else KeyLinesMask,
        trailingIcon = {
            IconButton(onClick = { revealed = !revealed }) {
                Icon(
                    if (revealed) MaterialSymbols.Rounded.Visibility_off else MaterialSymbols.Rounded.Visibility,
                    contentDescription = stringResource(if (revealed) R.string.provider_secret_hide else R.string.provider_secret_show),
                )
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

private object KeyLinesMask : VisualTransformation {
    override fun filter(text: AnnotatedString) =
        TransformedText(AnnotatedString(text.text.replace(NOT_NEWLINE, "\u2022")), OffsetMapping.Identity)

    private val NOT_NEWLINE = Regex("[^\n]")
}
