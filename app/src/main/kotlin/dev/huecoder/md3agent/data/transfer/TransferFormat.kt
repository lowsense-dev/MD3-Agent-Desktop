package dev.huecoder.md3agent.data.transfer

import dev.huecoder.md3agent.data.db.ChatEntity
import dev.huecoder.md3agent.data.db.MemoryEntity
import dev.huecoder.md3agent.data.db.MessageEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import java.io.IOException

// Shared by MD3 Agent for Android (export) and for Linux (import); keep both copies identical.

/** The file on disk: only this header is readable, everything else is in [data], sealed with the user's password. */
@Serializable
data class TransferHeader(
    val format: Int,
    val app: String = "md3-agent",
    val createdAt: Long,
    val kdf: Kdf,
    val cipher: CipherInfo,
    val data: String,
)

@Serializable
data class Kdf(val name: String = "PBKDF2-HMAC-SHA256", val iterations: Int, val salt: String)

@Serializable
data class CipherInfo(val name: String = "AES-256-GCM", val iv: String)

/**
 * Settings sections travel as JSON written by the exporting app's own models, so each side reads them with its own
 * models and simply ignores fields it does not know (the phone's Termux mode, the desktop's theme and so on).
 */
@Serializable
data class TransferPayload(
    val providers: JsonArray,
    val activeSelection: JsonElement = JsonNull,
    val agents: JsonObject,
    val network: JsonObject,
    val tools: JsonObject,
    val chat: JsonObject,
    val voice: JsonObject,
    val ui: JsonObject,
    val memories: List<MemoryEntity> = emptyList(),
    val chats: List<ChatDump> = emptyList(),
)

/** One chat with its messages; [files] maps attachment id to the file's bytes in Base64. */
@Serializable
data class ChatDump(val chat: ChatEntity, val messages: List<MessageEntity>, val files: Map<String, String> = emptyMap())

class UnsupportedTransferFormat(val format: Int) : IOException("This file is from a newer MD3 Agent (format $format). Update the app to import it.")

class MalformedTransferFile(cause: Throwable? = null) : IOException("This is not an MD3 Agent export, or the file is damaged", cause)

class WrongTransferPassword : IOException("Wrong password")

object TransferFormat {
    const val CURRENT = 1

    val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
        explicitNulls = false
    }
}
