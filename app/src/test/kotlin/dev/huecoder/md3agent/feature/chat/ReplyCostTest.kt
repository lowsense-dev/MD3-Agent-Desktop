package dev.huecoder.md3agent.feature.chat

import dev.huecoder.md3agent.data.db.MessageEntity
import dev.huecoder.md3agent.data.db.MessageRole
import dev.huecoder.md3agent.data.db.MessageStatus
import dev.huecoder.md3agent.llm.models.ModelInfo
import dev.huecoder.md3agent.llm.models.ModelPrice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReplyCostTest {
    private var clock = 0L

    private fun row(role: MessageRole, modelId: String? = null, prompt: Int? = null, completion: Int? = null, cached: Int? = null) = MessageEntity(
        id = "m${++clock}", chatId = "c", role = role, content = "x", reasoning = null, status = MessageStatus.COMPLETE,
        errorText = null, createdAt = clock, modelId = modelId, toolCallsJson = null, toolCallId = null,
        promptTokens = prompt, completionTokens = completion, cachedTokens = cached,
    )

    private val priced = ModelInfo("p", contextLength = 1_000, price = ModelPrice(prompt = 0.001, completion = 0.002, cacheRead = 0.0001))

    @Test
    fun `cost sums rounds and context share comes from the last round`() {
        val messages = listOf(
            row(MessageRole.USER),
            row(MessageRole.ASSISTANT, "p", prompt = 100, completion = 10),
            row(MessageRole.ASSISTANT, "p", prompt = 400, completion = 20, cached = 100),
        )

        val reply = ChatTimeline.build(messages, draft = null) { if (it == "p") priced else null }[1] as ChatItem.Reply

        assertEquals(100 * 0.001 + 10 * 0.002 + 300 * 0.001 + 100 * 0.0001 + 20 * 0.002, reply.cost!!, 1e-9)
        assertEquals(0.4f, reply.contextShare!!, 1e-6f)
    }

    @Test
    fun `cost is unknown when any round has no price`() {
        val messages = listOf(
            row(MessageRole.USER),
            row(MessageRole.ASSISTANT, "p", prompt = 100, completion = 10),
            row(MessageRole.ASSISTANT, "free-guess", prompt = 400, completion = 20),
        )

        val reply = ChatTimeline.build(messages, draft = null) { if (it == "p") priced else null }[1] as ChatItem.Reply

        assertNull(reply.cost)
        assertNull(reply.contextShare)
    }
}
