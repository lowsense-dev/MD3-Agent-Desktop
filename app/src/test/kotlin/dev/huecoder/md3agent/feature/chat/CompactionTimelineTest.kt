package dev.huecoder.md3agent.feature.chat

import dev.huecoder.md3agent.data.db.MessageEntity
import dev.huecoder.md3agent.data.db.MessageRole
import dev.huecoder.md3agent.data.db.MessageStatus
import dev.huecoder.md3agent.llm.ChatTurn
import dev.huecoder.md3agent.llm.ToolCall
import dev.huecoder.md3agent.llm.models.ModelInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import dev.huecoder.md3agent.conversation.TurnAssembler

class CompactionTimelineTest {
    private var clock = 0L

    private fun row(
        role: MessageRole,
        content: String = "",
        prompt: Int? = null,
        completion: Int? = null,
        calls: List<ToolCall> = emptyList(),
        toolCallId: String? = null,
    ) = MessageEntity(
        id = "m${++clock}",
        chatId = "c",
        role = role,
        content = content,
        reasoning = null,
        status = MessageStatus.COMPLETE,
        errorText = null,
        createdAt = clock,
        modelId = "m",
        toolCallsJson = TurnAssembler.encodeToolCalls(calls),
        toolCallId = toolCallId,
        promptTokens = prompt,
        completionTokens = completion,
    )

    @Test
    fun `the model sees the summary instead of everything before it`() {
        val history = listOf(
            row(MessageRole.USER, "old question"),
            row(MessageRole.ASSISTANT, "old answer"),
            row(MessageRole.SYSTEM, "SUMMARY"),
            row(MessageRole.USER, "new question"),
        )

        val turns = TurnAssembler.assemble("sys", history, emptyMap())

        assertEquals(listOf(ChatTurn.Role.SYSTEM, ChatTurn.Role.USER), turns.map { it.role })
        assertTrue(turns[0].content.startsWith("sys") && turns[0].content.endsWith("SUMMARY"))
        assertEquals("new question", turns[1].content)
    }

    @Test
    fun `the timeline shows a divider where the chat was compacted`() {
        val history = listOf(row(MessageRole.USER, "q"), row(MessageRole.ASSISTANT, "a"), row(MessageRole.SYSTEM, "S"), row(MessageRole.USER, "next"))

        val items = ChatTimeline.build(history, draft = null)

        assertEquals(listOf(ChatItem.User::class, ChatItem.Reply::class, ChatItem.Compacted::class, ChatItem.User::class), items.map { it::class })
        assertEquals("S", (items[2] as ChatItem.Compacted).summary)
    }
}
