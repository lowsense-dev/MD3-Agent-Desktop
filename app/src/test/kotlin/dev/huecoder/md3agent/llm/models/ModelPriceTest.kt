package dev.huecoder.md3agent.llm.models

import dev.huecoder.md3agent.llm.TokenUsage
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ModelPriceTest {
    @Test
    fun `openrouter string prices are parsed per token and bad ones dropped`() {
        val body = """
            {"data":[
              {"id":"a/priced","pricing":{"prompt":"0.000003","completion":"0.000015","input_cache_read":"0.0000003"}},
              {"id":"b/router","pricing":{"prompt":"-1","completion":"-1"}},
              {"id":"c/plain"}]}
        """.trimIndent()
        val (priced, router, plain) = ModelInfoParser.parseList(Json.parseToJsonElement(body))
        assertEquals(ModelPrice(0.000003, 0.000015, 0.0000003), priced.price)
        assertNull(router.price)
        assertNull(plain.price)
    }

    @Test
    fun `cached tokens are a part of the prompt and billed at the cache price`() {
        val price = ModelPrice(prompt = 0.000003, completion = 0.000015, cacheRead = 0.0000003)
        val cost = price.cost(TokenUsage(promptTokens = 10_000, completionTokens = 1_000, cachedTokens = 8_000))
        assertEquals(2_000 * 0.000003 + 8_000 * 0.0000003 + 1_000 * 0.000015, cost, 1e-12)
    }

    @Test
    fun `without a cache price cached tokens cost as prompt tokens`() {
        val price = ModelPrice(prompt = 0.000001, completion = 0.000002)
        assertEquals(1_000 * 0.000001 + 10 * 0.000002, price.cost(TokenUsage(1_000, 10, 600)), 1e-12)
    }
}
