package dev.huecoder.md3agent.data.transfer

import dev.huecoder.md3agent.AppContainer
import dev.huecoder.md3agent.data.attachments.Attachment
import dev.huecoder.md3agent.data.attachments.AttachmentStore
import dev.huecoder.md3agent.data.chat.ChatRepository
import dev.huecoder.md3agent.data.memory.MemoryStore
import dev.huecoder.md3agent.data.settings.AgentSettings
import dev.huecoder.md3agent.data.settings.ChatSettings
import dev.huecoder.md3agent.data.settings.InterfaceSettings
import dev.huecoder.md3agent.data.settings.NetworkSettings
import dev.huecoder.md3agent.data.settings.ProviderProfile
import dev.huecoder.md3agent.data.settings.SettingsRepository
import dev.huecoder.md3agent.data.settings.ToolSettings
import dev.huecoder.md3agent.data.settings.VoiceSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.util.Base64

// Shared by MD3 Agent for Android and for Linux; keep both copies identical.

/** Writes settings with their keys, agents, MCP servers, memory and optionally chats into one password-sealed file. */
class Exporter(
    private val settings: SettingsRepository,
    private val chats: ChatRepository,
    private val memories: MemoryStore,
    private val attachments: AttachmentStore,
) {
    constructor(container: AppContainer) : this(container.settings, container.chats, container.memories, container.attachments)

    suspend fun export(password: CharArray, includeChats: Boolean, iterations: Int = ExportCrypto.ITERATIONS): ByteArray = withContext(Dispatchers.IO) {
        val current = settings.current()
        val json = TransferFormat.json
        fun <T> obj(serializer: KSerializer<T>, value: T): JsonObject = json.encodeToJsonElement(serializer, value).jsonObject
        val payload = TransferPayload(
            providers = json.encodeToJsonElement(ListSerializer(ProviderProfile.serializer()), current.providers) as JsonArray,
            activeSelection = current.activeSelection?.let { selection ->
                buildJsonObject {
                    put("providerId", selection.providerId)
                    put("modelId", selection.modelId)
                    selection.reasoningEffort?.let { put("reasoningEffort", it.wireName) }
                }
            } ?: JsonNull,
            agents = obj(AgentSettings.serializer(), current.agents),
            network = obj(NetworkSettings.serializer(), current.network),
            tools = obj(ToolSettings.serializer(), current.tools),
            chat = obj(ChatSettings.serializer(), current.chat),
            voice = obj(VoiceSettings.serializer(), current.voice),
            ui = obj(InterfaceSettings.serializer(), current.ui),
            memories = memories.all(),
            chats = if (includeChats) chats.allChats().map { chat -> dump(chat.id, chat) } else emptyList(),
        )
        val plain = json.encodeToString(TransferPayload.serializer(), payload).toByteArray()
        ExportCrypto.encode(ExportCrypto.seal(password, plain, iterations))
    }

    private suspend fun dump(chatId: String, chat: dev.huecoder.md3agent.data.db.ChatEntity): ChatDump {
        val messages = chats.history(chatId)
        val files = messages.flatMap { Attachment.decode(it.attachmentsJson) }
            .mapNotNull { attachment -> attachments.file(attachment).takeIf { it.isFile }?.let { attachment.id to Base64.getEncoder().encodeToString(it.readBytes()) } }
            .toMap()
        return ChatDump(chat, messages, files)
    }
}
