package dev.huecoder.md3agent.data.chat

import dev.huecoder.md3agent.data.db.MessageHit
import dev.huecoder.md3agent.data.db.MessageRole
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class MessageScanTest {
    private val messages = List(1200) { i -> hit("m$i", if (i % 400 == 7) "Скажи ПРИВЕТ миру" else "пока $i") }

    private fun hit(id: String, content: String) = MessageHit(id, "c", "Chat", MessageRole.USER, 0L, content)

    private suspend fun page(limit: Int, offset: Int) = messages.drop(offset).take(limit)

    @Test
    fun `cyrillic matches ignore case across pages`() = runTest {
        assertEquals(listOf("m7", "m407", "m807"), ChatRepository.scanMatches("привет", 10, ::page).map { it.messageId })
    }

    @Test
    fun `scan stops at the limit`() = runTest {
        assertEquals(listOf("m7", "m407"), ChatRepository.scanMatches("Привет", 2, ::page).map { it.messageId })
    }
}
