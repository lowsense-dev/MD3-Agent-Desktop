package dev.huecoder.md3agent.feature.chat

import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.llm.TokenUsage
import dev.huecoder.md3agent.ui.text.UiText
import org.junit.Assert.assertEquals
import org.junit.Test

class ReplyMetaFormatTest {
    @Test
    fun `cost keeps more digits for small amounts`() {
        assertEquals("$0", ReplyMetaFormat.cost(0.0))
        assertEquals("<$0.0001", ReplyMetaFormat.cost(0.00004))
        assertEquals("$0.0123", ReplyMetaFormat.cost(0.01234))
        assertEquals("$1.50", ReplyMetaFormat.cost(1.5))
    }

    @Test
    fun `tokens switch to k and M`() {
        assertEquals(UiText.Raw("999"), ReplyMetaFormat.tokens(999))
        assertEquals(UiText.Resource(R.string.chat_meta_tokens_thousands, listOf(12.3)), ReplyMetaFormat.tokens(12_300))
        assertEquals(UiText.Resource(R.string.chat_meta_tokens_millions, listOf(2.5)), ReplyMetaFormat.tokens(2_500_000))
    }

    @Test
    fun `cache shows up only when something was cached`() {
        assertEquals(R.string.chat_meta_tokens_in_out, (ReplyMetaFormat.usage(TokenUsage(10, 5)) as UiText.Resource).id)
        assertEquals(R.string.chat_meta_tokens_in_out_cached, (ReplyMetaFormat.usage(TokenUsage(10, 5, 4)) as UiText.Resource).id)
    }
}
