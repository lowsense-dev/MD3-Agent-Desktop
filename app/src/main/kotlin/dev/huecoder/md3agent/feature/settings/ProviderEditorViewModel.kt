package dev.huecoder.md3agent.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.data.settings.ApiFormat
import dev.huecoder.md3agent.data.settings.HeaderEntry
import dev.huecoder.md3agent.data.settings.ProviderProfile
import dev.huecoder.md3agent.data.settings.SettingsRepository
import dev.huecoder.md3agent.data.models.ModelCatalog
import dev.huecoder.md3agent.ui.text.UiText
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class ProviderForm(
    val id: String,
    val isNew: Boolean,
    val name: String = "",
    val baseUrl: String = "",
    val apiKey: String = "",
    val headers: List<HeaderEntry> = emptyList(),
    val format: ApiFormat = ApiFormat.OPENAI,
) {
    fun withFormat(next: ApiFormat) = copy(format = next, baseUrl = next.adoptBaseUrl(baseUrl))

    // A name the user typed survives switching presets; an empty one or another preset's name follows the preset.
    fun withPreset(preset: ProviderPreset) = copy(
        format = preset.format,
        baseUrl = preset.baseUrl,
        name = if (name.isBlank() || ProviderPreset.isPresetName(name)) preset.displayName else name,
    )

    val preset: ProviderPreset?
        get() = ProviderPreset.matching(format, baseUrl)

    val baseUrlError: UiText?
        get() = endpointUrlError(baseUrl, carriesSecrets = apiKey.isNotBlank() || headers.any { it.name.isNotBlank() }, R.string.provider_base_url_invalid)

    val canSave: Boolean
        get() = name.isNotBlank() && baseUrl.isNotBlank() && baseUrlError == null && HeaderRules.allValid(headers)

    fun toProfile() = ProviderProfile(
        id = id,
        name = name.trim(),
        baseUrl = baseUrl.trim(),
        apiKey = apiKey.trim(),
        extraHeaders = headers.filter { it.name.isNotBlank() },
        format = format,
    )
}

class ProviderEditorViewModel(
    private val providerId: String?,
    private val settings: SettingsRepository,
    private val catalog: ModelCatalog,
) : ViewModel() {
    private val draft = FormDraft<ProviderForm>(viewModelScope)
    val form: StateFlow<ProviderForm?> = draft.value

    val isDirty: StateFlow<Boolean> = draft.isDirty

    init {
        viewModelScope.launch {
            val existing = settings.current().provider(providerId)
            draft.load(if (existing == null) {
                ProviderForm(id = settings.newProviderId(), isNew = true)
            } else {
                ProviderForm(
                    id = existing.id,
                    isNew = false,
                    name = existing.name,
                    baseUrl = existing.baseUrl,
                    apiKey = existing.apiKey,
                    headers = existing.extraHeaders,
                    format = existing.format,
                )
            })
        }
    }

    fun edit(transform: (ProviderForm) -> ProviderForm) = draft.edit(transform)

    fun save(onSaved: () -> Unit) {
        val current = draft.current?.takeIf { it.canSave } ?: return
        viewModelScope.launch {
            if (!settings.upsertProvider(current.toProfile())) return@launch
            draft.markSaved()
            onSaved()
        }
    }

    fun delete(onDeleted: () -> Unit) {
        val id = providerId ?: return onDeleted()
        viewModelScope.launch {
            if (!settings.deleteProvider(id)) return@launch
            catalog.forget(id)
            onDeleted()
        }
    }
}
