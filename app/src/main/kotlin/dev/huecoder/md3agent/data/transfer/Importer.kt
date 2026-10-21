package dev.huecoder.md3agent.data.transfer

import dev.huecoder.md3agent.AppContainer
import dev.huecoder.md3agent.data.settings.AgentSettings
import dev.huecoder.md3agent.data.settings.ChatSettings
import dev.huecoder.md3agent.data.settings.InterfaceSettings
import dev.huecoder.md3agent.data.settings.ModelSelection
import dev.huecoder.md3agent.data.settings.NetworkSettings
import dev.huecoder.md3agent.data.settings.ProviderProfile
import dev.huecoder.md3agent.data.settings.ToolSettings
import dev.huecoder.md3agent.data.settings.VoiceSettings
import dev.huecoder.md3agent.llm.models.ReasoningEffort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.Base64

/** One thing the file would bring in; [conflict] means this computer already has something with that id. */
data class ImportItem(val key: String, val label: String, val conflict: Boolean)

class ImportPreview internal constructor(internal val payload: Decoded, val items: List<ImportItem>) {
    val conflicts: List<ImportItem> get() = items.filter { it.conflict }
}

internal class Decoded(
    val providers: List<ProviderProfile>,
    val selection: ModelSelection?,
    val agents: AgentSettings,
    val network: NetworkSettings,
    val tools: ToolSettings,
    val chat: ChatSettings,
    val voice: VoiceSettings,
    val ui: InterfaceSettings,
    val payload: TransferPayload,
)

/**
 * Reads a file exported by the phone app. [preview] decrypts and decodes everything before anything is written, so a
 * wrong password or a damaged file changes nothing. [apply] adds what is new and replaces existing items only when
 * their key is in `overwrite`; this computer's access settings (shell mode, file scope) are never taken over.
 */
class Importer(private val container: AppContainer) {
    suspend fun preview(bytes: ByteArray, password: CharArray): ImportPreview = withContext(Dispatchers.IO) {
        val header = ExportCrypto.decode(bytes)
        val plain = ExportCrypto.open(password, header)
        val decoded = try {
            decode(TransferFormat.json.decodeFromString(TransferPayload.serializer(), plain.decodeToString()))
        } catch (e: SerializationException) {
            throw MalformedTransferFile(e)
        } catch (e: IllegalArgumentException) {
            throw MalformedTransferFile(e)
        }
        ImportPreview(decoded, items(decoded))
    }

    suspend fun apply(preview: ImportPreview, overwrite: Set<String>) = withContext(Dispatchers.IO) {
        val data = preview.payload
        val conflicts = preview.conflicts.map { it.key }.toSet()
        fun take(key: String) = key !in conflicts || key in overwrite
        val settings = container.settings
        val takeSettings = take(SETTINGS_KEY)

        data.providers.filter { take("provider:${it.id}") }.forEach { settings.upsertProvider(it) }
        if (settings.current().activeSelection == null) data.selection?.let { settings.setActiveSelection(it) }
        settings.updateAgents { current ->
            data.agents.agents.filter { take("agent:${it.id}") }.fold(current) { acc, agent -> acc.upsert(agent) }
        }
        settings.updateTools { current ->
            val servers = data.tools.mcpServers.filter { take("mcp:${it.id}") }
            val merged = current.mcpServers.filterNot { mine -> servers.any { it.id == mine.id } } + servers
            (if (takeSettings) data.tools else current).copy(access = current.access, mcpServers = merged)
        }
        if (takeSettings) {
            settings.updateNetwork(data.network)
            settings.updateChat { data.chat }
            settings.updateInterface { current -> data.ui.copy(theme = current.theme) }
            settings.updateVoice { data.voice }
        }
        data.payload.memories.filter { take("memory:${it.id}") }.forEach { container.memories.restore(it) }
        val folder = container.attachments.directory
        data.payload.chats.filter { take("chat:${it.chat.id}") }.forEach { dump ->
            dump.files.forEach { (id, base64) ->
                // Attachment ids are file names the app chose; anything that could leave the folder is skipped.
                if (id.isNotBlank() && '/' !in id && id != "." && id != "..") folder.resolve(id).writeBytes(Base64.getDecoder().decode(base64))
            }
            if (container.chats.chat(dump.chat.id) != null) container.chats.deleteForever(listOf(dump.chat.id))
            container.chats.restoreChat(dump.chat.copy(folderId = null), dump.messages)
        }
    }

    private fun decode(payload: TransferPayload): Decoded {
        val json = TransferFormat.json
        val selection = (payload.activeSelection as? JsonObject)?.let { obj ->
            val provider = obj["providerId"]?.jsonPrimitive?.content
            val model = obj["modelId"]?.jsonPrimitive?.content
            if (provider == null || model == null) null else ModelSelection(provider, model, obj["reasoningEffort"]?.jsonPrimitive?.content?.let(ReasoningEffort::fromWire))
        }
        return Decoded(
            providers = json.decodeFromJsonElement(ListSerializer(ProviderProfile.serializer()), payload.providers),
            selection = selection,
            agents = json.decodeFromJsonElement(AgentSettings.serializer(), payload.agents),
            network = json.decodeFromJsonElement(NetworkSettings.serializer(), payload.network),
            tools = json.decodeFromJsonElement(ToolSettings.serializer(), payload.tools).migrated(),
            chat = json.decodeFromJsonElement(ChatSettings.serializer(), payload.chat),
            voice = json.decodeFromJsonElement(VoiceSettings.serializer(), payload.voice),
            ui = json.decodeFromJsonElement(InterfaceSettings.serializer(), payload.ui),
            payload = payload,
        )
    }

    private suspend fun items(data: Decoded): List<ImportItem> {
        val current = container.settings.current()
        val existingChats = container.chats.allChats().map { it.id }.toSet()
        return buildList {
            data.providers.forEach { add(ImportItem("provider:${it.id}", it.name, current.provider(it.id) != null)) }
            // The built-in main agent exists everywhere; it only conflicts when the two differ.
            data.agents.agents.forEach { agent -> add(ImportItem("agent:${agent.id}", agent.name, current.agents.find(agent.id)?.let { it != agent } == true)) }
            data.tools.mcpServers.forEach { server -> add(ImportItem("mcp:${server.id}", server.name, current.tools.mcpServers.any { it.id == server.id })) }
            data.payload.memories.forEach { add(ImportItem("memory:${it.id}", it.title, container.memories.get(it.id) != null)) }
            data.payload.chats.forEach { add(ImportItem("chat:${it.chat.id}", it.chat.title, it.chat.id in existingChats)) }
            add(ImportItem(SETTINGS_KEY, SETTINGS_KEY, conflict = true))
        }
    }

    companion object {
        /** Network, chat, voice, interface and tool options (everything but MCP servers and access) as one choice. */
        const val SETTINGS_KEY = "settings"
    }
}
