package dev.huecoder.md3agent.data.chat

import dev.huecoder.md3agent.data.prefs.JsonPrefs
import java.io.File

class ChatDrafts(file: File) {
    private val prefs = JsonPrefs(file)

    fun read(chatId: String?): String = prefs.getString(keyOf(chatId)).orEmpty()

    fun write(chatId: String?, text: String) {
        if (text.isBlank()) prefs.remove(keyOf(chatId)) else prefs.putString(keyOf(chatId), text)
    }

    fun forget(chatId: String) = prefs.remove(keyOf(chatId))

    private fun keyOf(chatId: String?) = chatId ?: NEW_CHAT_KEY

    private companion object {
        const val NEW_CHAT_KEY = "new"
    }
}
