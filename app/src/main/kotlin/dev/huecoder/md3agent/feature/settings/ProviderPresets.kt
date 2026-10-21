package dev.huecoder.md3agent.feature.settings

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.huecoder.md3agent.ui.res.painterResource
import dev.huecoder.md3agent.ui.text.stringResource
import androidx.compose.ui.unit.dp
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Hub
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.data.settings.ApiFormat
import dev.huecoder.md3agent.llm.models.ModelVendor
import dev.huecoder.md3agent.ui.components.iconRes

/** Known endpoints that fill the provider form in one tap; the user still adds the key. */
enum class ProviderPreset(val displayName: String, val format: ApiFormat, val baseUrl: String, val vendor: ModelVendor?) {
    OPENAI("OpenAI", ApiFormat.OPENAI, ApiFormat.OPENAI.defaultBaseUrl, ModelVendor.OPENAI),
    ANTHROPIC("Anthropic", ApiFormat.ANTHROPIC, ApiFormat.ANTHROPIC.defaultBaseUrl, ModelVendor.ANTHROPIC),
    GOOGLE("Google", ApiFormat.GEMINI, ApiFormat.GEMINI.defaultBaseUrl, ModelVendor.GOOGLE),
    DEEPSEEK("DeepSeek", ApiFormat.OPENAI, "https://api.deepseek.com/v1", ModelVendor.DEEPSEEK),
    OPENROUTER("OpenRouter", ApiFormat.OPENAI, "https://openrouter.ai/api/v1", null),
    ;

    companion object {
        fun matching(format: ApiFormat, baseUrl: String): ProviderPreset? =
            entries.firstOrNull { it.format == format && it.baseUrl == baseUrl.trim().removeSuffix("/") }

        fun isPresetName(name: String) = entries.any { it.displayName == name.trim() }
    }
}

@Composable
internal fun ProviderPresetRow(selected: ProviderPreset?, onSelect: (ProviderPreset) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.provider_preset), style = MaterialTheme.typography.bodyLarge)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ProviderPreset.entries.forEach { preset ->
                FilterChip(
                    selected = preset == selected,
                    onClick = { onSelect(preset) },
                    label = { Text(preset.displayName) },
                    leadingIcon = {
                        val iconModifier = Modifier.size(FilterChipDefaults.IconSize)
                        val vendor = preset.vendor
                        if (vendor != null) Icon(painterResource(vendor.iconRes), contentDescription = null, modifier = iconModifier)
                        else Icon(MaterialSymbols.Rounded.Hub, contentDescription = null, modifier = iconModifier)
                    },
                )
            }
        }
    }
}
