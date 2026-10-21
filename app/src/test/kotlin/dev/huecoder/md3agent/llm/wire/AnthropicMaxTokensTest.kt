package dev.huecoder.md3agent.llm.wire

import org.junit.Assert.assertEquals
import org.junit.Test

class AnthropicMaxTokensTest {
    @Test
    fun `older models stay within their output cap`() {
        assertEquals(4_096, AnthropicChatWire.maxTokens("claude-3-haiku-20240307", null))
        assertEquals(8_192, AnthropicChatWire.maxTokens("claude-3-5-sonnet-latest", null))
        assertEquals(32_000, AnthropicChatWire.maxTokens("claude-opus-4-20250514", 24_000))
        assertEquals(32_000, AnthropicChatWire.maxTokens("claude-opus-4-1", null))
    }

    @Test
    fun `newer models get answer room on top of the thinking budget`() {
        assertEquals(32_000, AnthropicChatWire.maxTokens("claude-sonnet-4-5", null))
        assertEquals(48_384, AnthropicChatWire.maxTokens("claude-opus-4-5", 16_384))
        assertEquals(56_000, AnthropicChatWire.maxTokens("anthropic/claude-sonnet-5-5", 24_000))
    }
}
