package dev.huecoder.md3agent.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.huecoder.md3agent.ui.text.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Add
import com.composables.icons.materialsymbols.rounded.Close
import com.composables.icons.materialsymbols.rounded.Visibility
import com.composables.icons.materialsymbols.rounded.Visibility_off
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.data.settings.HeaderEntry
import dev.huecoder.md3agent.net.CleartextCredentialGuard
import dev.huecoder.md3agent.ui.components.FormField

@Composable
internal fun SecretField(value: String, onValueChange: (String) -> Unit, label: String, modifier: Modifier = Modifier) {
    var revealed by rememberSaveable { mutableStateOf(false) }
    FormField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        visualTransformation = if (revealed) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { revealed = !revealed }) {
                Icon(
                    if (revealed) MaterialSymbols.Rounded.Visibility_off else MaterialSymbols.Rounded.Visibility,
                    contentDescription = stringResource(if (revealed) R.string.provider_secret_hide else R.string.provider_secret_show),
                )
            }
        },
        modifier = modifier.fillMaxWidth(),
    )
}

/** OkHttp rejects these at request time; catching them while typing keeps a broken header from being saved. */
internal object HeaderRules {
    private const val TOKEN_SYMBOLS = "!#$%&'*+-.^_`|~"

    fun nameValid(name: String): Boolean = name.isBlank() || name.trim().all { it.isLetterOrDigit() && it.code < 128 || it in TOKEN_SYMBOLS }

    fun valueValid(value: String): Boolean = value.all { it == '\t' || it.code in 0x20..0x7e }

    fun allValid(headers: List<HeaderEntry>): Boolean = headers.all { nameValid(it.name) && valueValid(it.value) }
}

@Composable
internal fun HeaderEditor(headers: List<HeaderEntry>, onChange: (List<HeaderEntry>) -> Unit) {
    headers.forEachIndexed { index, header ->
        var revealed by rememberSaveable(index) { mutableStateOf(false) }
        val secret = CleartextCredentialGuard.isCredential(header.name.trim())
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FormField(
                value = header.name,
                onValueChange = { value -> onChange(headers.toMutableList().also { it[index] = header.copy(name = value) }) },
                label = stringResource(R.string.provider_header_name),
                isError = !HeaderRules.nameValid(header.name),
                supportingText = stringResource(R.string.provider_header_name_invalid).takeUnless { HeaderRules.nameValid(header.name) },
                modifier = Modifier.weight(1f),
            )
            FormField(
                value = header.value,
                onValueChange = { value -> onChange(headers.toMutableList().also { it[index] = header.copy(value = value) }) },
                label = stringResource(R.string.provider_header_value),
                isError = !HeaderRules.valueValid(header.value),
                supportingText = stringResource(R.string.provider_header_value_invalid).takeUnless { HeaderRules.valueValid(header.value) },
                visualTransformation = if (secret && !revealed) PasswordVisualTransformation() else VisualTransformation.None,
                trailingIcon = if (!secret) null else {
                    {
                        IconButton(onClick = { revealed = !revealed }) {
                            Icon(
                                if (revealed) MaterialSymbols.Rounded.Visibility_off else MaterialSymbols.Rounded.Visibility,
                                contentDescription = stringResource(if (revealed) R.string.provider_secret_hide else R.string.provider_secret_show),
                            )
                        }
                    }
                },
                modifier = Modifier.weight(1.4f),
            )
            IconButton(onClick = { onChange(headers.filterIndexed { i, _ -> i != index }) }) {
                Icon(MaterialSymbols.Rounded.Close, contentDescription = stringResource(R.string.provider_header_remove))
            }
        }
    }
    TextButton(onClick = { onChange(headers + HeaderEntry("", "")) }) {
        Icon(MaterialSymbols.Rounded.Add, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
        Text(stringResource(R.string.provider_header_add), Modifier.padding(start = ButtonDefaults.IconSpacing))
    }
}
