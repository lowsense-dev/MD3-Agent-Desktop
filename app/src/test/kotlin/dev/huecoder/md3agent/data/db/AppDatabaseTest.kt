package dev.huecoder.md3agent.data.db

import androidx.sqlite.SQLiteException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AppDatabaseTest {
    private val db = AppDatabase.build(createTempDirectory("md3-db").toFile().resolve("test.db").path)

    private fun message(chatId: String) = MessageEntity(
        id = "m1", chatId = chatId, role = MessageRole.USER, content = "Привет", reasoning = null,
        status = MessageStatus.COMPLETE, errorText = null, createdAt = 1L, modelId = null, toolCallsJson = null, toolCallId = null,
    )

    @Test
    fun insertsAndReadsChatInTempDir() = runBlocking {
        db.chatDao().insert(ChatEntity(id = "c1", title = "Test", createdAt = 1L, updatedAt = 1L, providerId = null, modelId = null))
        db.messageDao().upsert(message("c1"))
        assertEquals("Test", db.chatDao().get("c1")?.title)
        assertEquals(listOf("Привет"), db.messageDao().observeForChat("c1").first().map { it.content })
        db.close()
    }

    @Test
    fun messageForMissingChatIsAConstraintError() = runBlocking {
        // ChatRepository.save relies on this to drop replies for chats deleted mid-stream.
        val error = assertFailsWith<SQLiteException> { db.messageDao().upsert(message("gone")) }
        assertTrue(error.message.orEmpty().contains("constraint", ignoreCase = true), error.message)
        db.close()
    }
}
