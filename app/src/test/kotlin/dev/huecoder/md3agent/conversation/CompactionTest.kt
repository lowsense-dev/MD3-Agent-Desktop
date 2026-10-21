package dev.huecoder.md3agent.conversation

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

class CompactionTest {
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
    fun `transcript keeps tool calls short and drops the oldest messages first`() {
        val call = ToolCall("c1", "read_file", "x".repeat(2_000))
        val messages = listOf(
            row(MessageRole.USER, "first " + "a".repeat(300_000)),
            row(MessageRole.ASSISTANT, "", calls = listOf(call)),
            row(MessageRole.TOOL, "y".repeat(5_000), toolCallId = "c1"),
            row(MessageRole.USER, "latest question"),
        )

        val text = Compaction.transcript("earlier summary", messages, windowTokens = 100_000)

        assertTrue(text.startsWith("Summary of what came before:\nearlier summary"))
        assertTrue("[1 older messages omitted]" in text)
        assertTrue("[called read_file(" in text && "latest question" in text)
        assertTrue(text.length < 10_000)
    }

    @Test
    fun `usage comes from the last reported reply after the summary`() {
        val history = listOf(
            row(MessageRole.USER, "q"),
            row(MessageRole.ASSISTANT, "a", prompt = 90_000, completion = 2_000),
        )
        val usage = Compaction.usage(history, windowTokens = 100_000)!!

        assertFalse(usage.estimated)
        assertEquals(92_000, usage.usedTokens)
        assertTrue(Compaction.needsCompaction(usage))
    }

    @Test
    fun `after compacting the usage is an estimate and never triggers another compaction`() {
        val history = listOf(
            row(MessageRole.USER, "q"),
            row(MessageRole.ASSISTANT, "a", prompt = 90_000, completion = 2_000),
            row(MessageRole.SYSTEM, "s".repeat(400)),
        )
        val usage = Compaction.usage(history, windowTokens = 100_000)!!

        assertTrue(usage.estimated)
        assertEquals(100, usage.usedTokens)
        assertFalse(Compaction.needsCompaction(usage))
    }

    @Test
    fun `an empty chat reports no usage and is not worth compacting`() {
        assertNull(Compaction.usage(emptyList(), 100_000))
        assertFalse(Compaction.worthCompacting(listOf(row(MessageRole.USER, "only one"))))
    }

    @Test
    fun `models without a reported window fall back to their line`() {
        assertEquals(200_000, ModelInfo("claude-sonnet-4-5").contextWindow)
        assertEquals(1_048_576, ModelInfo("gemini-2.5-pro").contextWindow)
        assertEquals(64_000, ModelInfo("custom", contextLength = 64_000).contextWindow)
        assertNull(ModelInfo("my-local-model").contextWindow)
    }
}
