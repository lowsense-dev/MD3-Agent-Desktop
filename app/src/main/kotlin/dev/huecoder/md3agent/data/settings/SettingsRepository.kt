package dev.huecoder.md3agent.data.settings

import dev.huecoder.md3agent.platform.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import dev.huecoder.md3agent.system.paths.AppPaths
import okio.Path.Companion.toOkioPath
import dev.huecoder.md3agent.llm.models.ReasoningEffort
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.util.UUID

class SettingsRepository(
    private val store: DataStore<Preferences>,
    private val cipher: TextCipher,
    scope: CoroutineScope,
) {
    constructor(paths: AppPaths, cipher: TextCipher, scope: CoroutineScope) : this(
        PreferenceDataStoreFactory.createWithPath(scope = scope) { paths.config.resolve("settings.preferences_pb").toOkioPath() },
        cipher,
        scope,
    )

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val providerListSerializer = ListSerializer(ProviderProfile.serializer())
    private val providersCache = LastDecoded<List<ProviderProfile>>()
    private val toolsCache = LastDecoded<ToolSettings>()
    private val voiceCache = LastDecoded<VoiceSettings>()

    val settings: Flow<AppSettings> = store.data
        .map(::toAppSettings)
        .distinctUntilChanged()
        .shareIn(scope, SharingStarted.Eagerly, replay = 1)

    private val _writeFailures = MutableSharedFlow<SettingsWriteException>(extraBufferCapacity = 4)

    /** Writes refused because the stored value is unreadable; the app shows these so a skipped save is never silent. */
    val writeFailures: SharedFlow<SettingsWriteException> = _writeFailures.asSharedFlow()

    val networkSettings: Flow<NetworkSettings> = settings.map { it.network }.distinctUntilChanged()


    suspend fun current(): AppSettings = withContext(Dispatchers.Default) { toAppSettings(store.data.first()) }

    // Writes return false when they were refused (see readable); the reason is also published on writeFailures.
    suspend fun upsertProvider(provider: ProviderProfile): Boolean = write {
        editProviders { providers ->
            val index = providers.indexOfFirst { it.id == provider.id }
            if (index == -1) providers + provider else providers.toMutableList().also { it[index] = provider }
        }
    }

    suspend fun deleteProvider(providerId: String): Boolean = write {
        editProviders { providers -> providers.filterNot { it.id == providerId } }
        store.edit { prefs ->
            if (prefs[Keys.ACTIVE_PROVIDER] == providerId) {
                prefs.remove(Keys.ACTIVE_PROVIDER)
                prefs.remove(Keys.ACTIVE_MODEL)
            }
        }
    }

    suspend fun setActiveSelection(selection: ModelSelection): Boolean = write {
        store.edit { prefs ->
            prefs[Keys.ACTIVE_PROVIDER] = selection.providerId
            prefs[Keys.ACTIVE_MODEL] = selection.modelId
            val effort = selection.reasoningEffort
            if (effort == null) prefs.remove(Keys.ACTIVE_REASONING) else prefs[Keys.ACTIVE_REASONING] = effort.wireName
        }
    }

    suspend fun updateAgents(transform: (AgentSettings) -> AgentSettings): Boolean = write {
        store.edit { prefs ->
            prefs[Keys.AGENTS] = json.encodeToString(AgentSettings.serializer(), transform(decodeAgents(prefs, strict = true)))
            prefs.remove(Keys.LEGACY_AGENT)
        }
    }

    suspend fun updateNetwork(network: NetworkSettings): Boolean = write {
        store.edit { prefs -> prefs[Keys.NETWORK] = json.encodeToString(NetworkSettings.serializer(), network) }
    }

    suspend fun updateTools(transform: (ToolSettings) -> ToolSettings): Boolean = write {
        store.edit { prefs ->
            val current = readable("Tool settings") { storedTools(prefs[Keys.TOOLS_ENCRYPTED]) }
            prefs[Keys.TOOLS_ENCRYPTED] = cipher.encrypt(json.encodeToString(ToolSettings.serializer(), transform(current)))
        }
    }

    suspend fun updateChat(transform: (ChatSettings) -> ChatSettings): Boolean = write {
        store.edit { prefs ->
            val current = decodeStrict(prefs[Keys.CHAT], ChatSettings.serializer(), ChatSettings())
            prefs[Keys.CHAT] = json.encodeToString(ChatSettings.serializer(), transform(current))
        }
    }

    suspend fun updateInterface(transform: (InterfaceSettings) -> InterfaceSettings): Boolean = write {
        store.edit { prefs ->
            val current = decodeStrict(prefs[Keys.INTERFACE], InterfaceSettings.serializer(), InterfaceSettings())
            prefs[Keys.INTERFACE] = json.encodeToString(InterfaceSettings.serializer(), transform(current))
        }
    }

    suspend fun updateVoice(transform: (VoiceSettings) -> VoiceSettings): Boolean = write {
        store.edit { prefs ->
            val current = readable("Voice settings") {
                storedVoice(prefs, storedProviders(prefs[Keys.PROVIDERS_ENCRYPTED]), prefs[Keys.ACTIVE_PROVIDER])
            }
            prefs[Keys.VOICE_ENCRYPTED] = cipher.encrypt(json.encodeToString(VoiceSettings.serializer(), transform(current)))
            prefs.remove(Keys.LEGACY_SPEECH)
        }
    }

    fun newProviderId(): String = UUID.randomUUID().toString()

    private suspend fun editProviders(transform: (List<ProviderProfile>) -> List<ProviderProfile>) {
        store.edit { prefs ->
            val current = readable("Provider profiles") { storedProviders(prefs[Keys.PROVIDERS_ENCRYPTED]) }
            prefs[Keys.PROVIDERS_ENCRYPTED] = cipher.encrypt(json.encodeToString(providerListSerializer, transform(current)))
        }
    }

    private suspend fun write(edit: suspend () -> Unit): Boolean = try {
        edit()
        true
    } catch (e: SettingsWriteException) {
        Log.e(TAG, e.message.orEmpty(), e)
        _writeFailures.tryEmit(e)
        false
    }

    /** A write must start from what is actually stored; if that cannot be read, refuse rather than overwrite it with defaults. */
    private fun <T> readable(what: String, read: () -> T): T = try {
        read()
    } catch (e: SettingsWriteException) {
        throw e
    } catch (e: Exception) {
        throw SettingsWriteException("$what could not be read, so nothing was saved", e)
    }

    private fun toAppSettings(prefs: Preferences): AppSettings {
        val providers = decodeProviders(prefs[Keys.PROVIDERS_ENCRYPTED])
        val activeProvider = prefs[Keys.ACTIVE_PROVIDER]
        val activeModel = prefs[Keys.ACTIVE_MODEL]
        val selection = if (activeProvider != null && activeModel != null && providers.any { it.id == activeProvider }) {
            ModelSelection(activeProvider, activeModel, prefs[Keys.ACTIVE_REASONING]?.let(ReasoningEffort::fromWire))
        } else {
            null
        }
        return AppSettings(
            providers = providers,
            activeSelection = selection,
            agents = decodeAgents(prefs),
            network = decodeOrDefault(prefs[Keys.NETWORK], NetworkSettings.serializer(), NetworkSettings()),
            tools = decodeTools(prefs[Keys.TOOLS_ENCRYPTED]),
            chat = decodeOrDefault(prefs[Keys.CHAT], ChatSettings.serializer(), ChatSettings()),
            voice = decodeVoice(prefs, providers, activeProvider),
            ui = decodeOrDefault(prefs[Keys.INTERFACE], InterfaceSettings.serializer(), InterfaceSettings()),
        )
    }

    private fun decodeProviders(encrypted: String?): List<ProviderProfile> =
        decryptedOrNull("Provider profiles could not be read, showing none") { storedProviders(encrypted) } ?: emptyList()

    // The stored* readers throw on unreadable data (and cache only successes): reads fall back to defaults, writes refuse.
    private fun storedProviders(encrypted: String?): List<ProviderProfile> =
        if (encrypted == null) emptyList() else providersCache.of(encrypted) { decodeStrict(cipher.decrypt(it), providerListSerializer, emptyList()) }

    private fun decodeVoice(prefs: Preferences, providers: List<ProviderProfile>, activeProviderId: String?): VoiceSettings =
        decryptedOrNull("Voice settings could not be read, using defaults") { storedVoice(prefs, providers, activeProviderId) } ?: VoiceSettings()

    private fun storedVoice(prefs: Preferences, providers: List<ProviderProfile>, activeProviderId: String?): VoiceSettings {
        val encrypted = prefs[Keys.VOICE_ENCRYPTED]
        if (encrypted == null) {
            val legacy = prefs[Keys.LEGACY_SPEECH] ?: return VoiceSettings()
            return LegacySpeechMigration.toVoice(
                decodeStrict(legacy, LegacySpeechSettings.serializer(), LegacySpeechSettings()),
                providers,
                activeProviderId,
            )
        }
        return voiceCache.of(encrypted) { decodeStrict(cipher.decrypt(it), VoiceSettings.serializer(), VoiceSettings()) }
    }

    private fun decodeAgents(prefs: Preferences, strict: Boolean = false): AgentSettings {
        fun <T> decode(raw: String, serializer: KSerializer<T>, default: T) = if (strict) decodeStrict(raw, serializer, default) else decodeOrDefault(raw, serializer, default)
        val stored = prefs[Keys.AGENTS]
        if (stored != null) return decode(stored, AgentSettings.serializer(), AgentSettings()).takeIf { it.agents.isNotEmpty() } ?: AgentSettings()
        val legacy = prefs[Keys.LEGACY_AGENT] ?: return AgentSettings()
        return decode(legacy, LegacyAgentProfile.serializer(), LegacyAgentProfile()).toAgents()
    }

    private fun decodeTools(encrypted: String?): ToolSettings =
        decryptedOrNull("Tool settings could not be read, using defaults") { storedTools(encrypted) } ?: ToolSettings()

    private fun storedTools(encrypted: String?): ToolSettings =
        if (encrypted == null) ToolSettings() else toolsCache.of(encrypted) { decodeStrict(cipher.decrypt(it), ToolSettings.serializer(), ToolSettings()).migrated() }

    private fun <T> decryptedOrNull(failure: String, decode: () -> T): T? = try {
        decode()
    } catch (e: Exception) {
        Log.e(TAG, failure, e)
        null
    }

    private fun <T> decodeStrict(raw: String?, serializer: KSerializer<T>, default: T): T {
        if (raw == null) return default
        return try {
            json.decodeFromString(serializer, raw)
        } catch (e: SerializationException) {
            throw SettingsWriteException("Setting ${serializer.descriptor.serialName} is unreadable, so nothing was saved", e)
        }
    }

    private fun <T> decodeOrDefault(raw: String?, serializer: KSerializer<T>, default: T): T {
        if (raw == null) return default
        return try {
            json.decodeFromString(serializer, raw)
        } catch (e: SerializationException) {
            Log.e(TAG, "Setting ${serializer.descriptor.serialName} is corrupted, using defaults", e)
            default
        }
    }

    private object Keys {
        val PROVIDERS_ENCRYPTED = stringPreferencesKey("providers_encrypted")
        val ACTIVE_PROVIDER = stringPreferencesKey("active_provider")
        val ACTIVE_MODEL = stringPreferencesKey("active_model")
        val ACTIVE_REASONING = stringPreferencesKey("active_reasoning")
        val LEGACY_AGENT = stringPreferencesKey("agent")
        val AGENTS = stringPreferencesKey("agents")
        val NETWORK = stringPreferencesKey("network")
        val TOOLS_ENCRYPTED = stringPreferencesKey("tools_encrypted")
        val CHAT = stringPreferencesKey("chat")
        val INTERFACE = stringPreferencesKey("interface")
        val LEGACY_SPEECH = stringPreferencesKey("speech")
        val VOICE_ENCRYPTED = stringPreferencesKey("voice_encrypted")
    }

    private class LastDecoded<T> {
        private var last: Pair<String, T>? = null

        @Synchronized
        fun of(encrypted: String, decode: (String) -> T): T {
            last?.takeIf { it.first == encrypted }?.let { return it.second }
            return decode(encrypted).also { last = encrypted to it }
        }
    }

    private companion object {
        const val TAG = "SettingsRepository"
    }
}
