package dev.huecoder.md3agent.mcp.auth

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OAuthLoopbackTest {
    @Test
    fun deliversQueryParametersAndAnswersBrowser() = runBlocking {
        val received = CompletableDeferred<Map<String, String>>()
        val loopback = OAuthLoopback.start { received.complete(it) }
        try {
            assertTrue(loopback.redirectUri.matches(Regex("""http://127\.0\.0\.1:\d+/callback""")))
            val response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI("${loopback.redirectUri}?code=abc%20d&state=s1.x")).build(),
                HttpResponse.BodyHandlers.ofString(),
            )
            assertEquals(200, response.statusCode())
            assertEquals(mapOf("code" to "abc d", "state" to "s1.x"), withTimeout(5_000) { received.await() })
        } finally {
            loopback.close()
        }
    }

    @Test
    fun ignoresOtherPaths() = runBlocking {
        val loopback = OAuthLoopback.start { error("must not be called") }
        try {
            val other = loopback.redirectUri.replace("/callback", "/favicon.ico")
            val response = HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI(other)).build(), HttpResponse.BodyHandlers.ofString())
            assertEquals(404, response.statusCode())
        } finally {
            loopback.close()
        }
    }
}
