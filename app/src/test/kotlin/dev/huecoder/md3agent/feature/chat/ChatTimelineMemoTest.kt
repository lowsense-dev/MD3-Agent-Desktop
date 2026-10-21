package dev.huecoder.md3agent.feature.chat

import dev.huecoder.md3agent.conversation.StreamingDraft
import dev.huecoder.md3agent.data.db.MessageEntity
import dev.huecoder.md3agent.data.db.MessageRole
import dev.huecoder.md3agent.data.db.MessageStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test

class ChatTimelineMemoTest {
    private var clock = 0L

    private fun row(role: MessageRole, content: String, status: MessageStatus = MessageStatus.COMPLETE) = MessageEntity(
        id = "m${++clock}", chatId = "c", role = role, content = content, reasoning = null, status = status,
        errorText = null, createdAt = clock, modelId = "m", toolCallsJson = null, toolCallId = null,
    )

    private fun draft(id: String, text: String) =
        StreamingDraft(id, content = text, reasoning = "", toolCalls = emptyList(), runningCallIds = emptySet(), awaitingCallId = null, usage = null)

    @Test
    fun `streaming the latest reply keeps earlier replies as the same instances`() {
        val memo = ChatTimeline.ReplyMemo()
        val streaming = row(MessageRole.ASSISTANT, "", MessageStatus.STREAMING)
        val history = listOf(row(MessageRole.USER, "q1"), row(MessageRole.ASSISTANT, "a1"), row(MessageRole.USER, "q2"), streaming)

        val first = ChatTimeline.build(history, draft(streaming.id, "He"), memo = memo)
        val second = ChatTimeline.build(history, draft(streaming.id, "Hello"), memo = memo)

        assertSame(first[1], second[1])
        assertNotSame(first[3], second[3])
        assertEquals(ChatTimeline.build(history, draft(streaming.id, "Hello")), second)
    }

    @Test
    fun `an edited reply is rebuilt`() {
        val memo = ChatTimeline.ReplyMemo()
        val answer = row(MessageRole.ASSISTANT, "a1")
        val question = row(MessageRole.USER, "q1")
        val before = ChatTimeline.build(listOf(question, answer), null, memo = memo)
        val after = ChatTimeline.build(listOf(question, answer.copy(content = "a1 edited")), null, memo = memo)

        assertNotSame(before[1], after[1])
    }
}
