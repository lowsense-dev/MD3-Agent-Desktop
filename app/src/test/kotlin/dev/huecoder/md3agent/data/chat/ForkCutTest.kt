package dev.huecoder.md3agent.data.chat

import dev.huecoder.md3agent.data.db.MessageEntity
import dev.huecoder.md3agent.data.db.MessageRole
import dev.huecoder.md3agent.data.db.MessageStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class ForkCutTest {
    private val history = listOf(
        row("u1", MessageRole.USER),
        row("a1", MessageRole.ASSISTANT),
        row("t1", MessageRole.TOOL),
        row("a2", MessageRole.ASSISTANT),
        row("u2", MessageRole.USER),
        row("a3", MessageRole.ASSISTANT),
    )

    private fun row(id: String, role: MessageRole) = MessageEntity(
        id = id,
        chatId = "c",
        role = role,
        content = id,
        reasoning = null,
        status = MessageStatus.COMPLETE,
        errorText = null,
        createdAt = 0L,
        modelId = null,
        toolCallsJson = null,
        toolCallId = null,
    )

    private fun cut(id: String) = ChatRepository.forkCut(history, id).map { it.id }

    @Test
    fun `reply keeps its tool results and later rounds`() {
        assertEquals(listOf("u1", "a1", "t1", "a2"), cut("a1"))
    }

    @Test
    fun `prompt ends the fork`() {
        assertEquals(listOf("u1", "a1", "t1", "a2", "u2"), cut("u2"))
    }

    @Test
    fun `latest reply keeps everything`() {
        assertEquals(history.map { it.id }, cut("a3"))
    }

    @Test
    fun `unknown message forks nothing`() {
        assertEquals(emptyList<String>(), cut("missing"))
    }
}
