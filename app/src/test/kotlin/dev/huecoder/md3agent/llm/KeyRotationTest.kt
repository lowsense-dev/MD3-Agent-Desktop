package dev.huecoder.md3agent.llm

import dev.huecoder.md3agent.data.settings.ProviderProfile
import dev.huecoder.md3agent.net.HttpStatusException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class KeyRotationTest {
    private fun provider(id: String, apiKey: String) = ProviderProfile(id = id, name = "Test", baseUrl = "https://a", apiKey = apiKey)

    @Test
    fun `keys come one per line without blanks`() {
        assertEquals(listOf("k1", "k2"), provider("p", " k1 \n\n\tk2\r\n").apiKeys)
    }

    @Test
    fun `successive requests start from the next key`() {
        val keys = listOf("a", "b", "c")
        val starts = List(4) { KeyRotation.order("order-test", keys).first() }
        assertEquals(listOf("a", "b", "c", "a"), starts)
        assertEquals(listOf("b", "c", "a"), KeyRotation.order("order-test", keys))
    }

    @Test
    fun `auth, quota and server errors move on to the next key`() = runTest {
        val tried = mutableListOf<String>()
        val codes = mutableListOf(401, 429, 503)
        val result = KeyRotation.withFailover(provider("failover-test", "a\nb\nc\nd")) { keyed ->
            tried += keyed.apiKey
            codes.removeFirstOrNull()?.let { throw if (it == 503) HttpStatusException(it, "down") else ProviderException.Http(it, "HTTP $it") }
            "ok"
        }
        assertEquals("ok", result)
        assertEquals(listOf("a", "b", "c", "d"), tried)
    }

    @Test
    fun `the last error surfaces when every key fails`() = runTest {
        val tried = mutableListOf<String>()
        try {
            KeyRotation.withFailover<String>(provider("exhausted-test", "a\nb")) { keyed ->
                tried += keyed.apiKey
                throw ProviderException.Http(if (keyed.apiKey == "a") 403 else 402, "HTTP ${keyed.apiKey}")
            }
            fail("expected ProviderException.Http")
        } catch (e: ProviderException.Http) {
            assertEquals(402, e.code)
        }
        assertEquals(listOf("a", "b"), tried)
    }

    @Test
    fun `other errors and started streams are not retried`() = runTest {
        val tried = mutableListOf<String>()
        listOf<Pair<Exception, Boolean>>(ProviderException.Http(400, "bad request") to true, ProviderException.Http(429, "slow") to false)
            .forEachIndexed { index, (failure, retryable) ->
                try {
                    KeyRotation.withFailover<String>(provider("no-retry-$index", "a\nb"), canRetry = { retryable }) { keyed ->
                        tried += keyed.apiKey
                        throw failure
                    }
                    fail("expected the failure to surface")
                } catch (e: ProviderException.Http) {
                    assertEquals(failure, e)
                }
            }
        assertEquals(listOf("a", "a"), tried)
    }

    @Test
    fun `a single key retries rate limits and overloads after a pause`() = runTest {
        val pauses = mutableListOf<Long>()
        val codes = mutableListOf(429, 529, 503)
        var calls = 0
        val result = KeyRotation.withFailover(provider("backoff-test", "only"), pause = { pauses += it }) {
            calls++
            codes.removeFirstOrNull()?.let { throw ProviderException.Http(it, "HTTP $it", retryAfterSeconds = if (it == 429) 5 else null) }
            "ok"
        }
        assertEquals("ok", result)
        assertEquals(4, calls)
        assertEquals(listOf(5_000L, 2_000L, 4_000L), pauses)
    }

    @Test
    fun `retries stop after a few attempts`() = runTest {
        var calls = 0
        try {
            KeyRotation.withFailover<String>(provider("backoff-limit", "only"), pause = {}) { calls++; throw ProviderException.Http(503, "down") }
            fail("expected ProviderException.Http")
        } catch (e: ProviderException.Http) {
            assertEquals(503, e.code)
        }
        assertEquals(4, calls)
    }
}
