package dev.huecoder.md3agent.mcp.auth

import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class McpOAuthTest {
    private val server = MockWebServer()
    private val http = OkHttpClient()

    @Before
    fun setUp() = server.start()

    @After
    fun tearDown() = server.close()

    private fun metadata(body: String) {
        repeat(2) { server.enqueue(MockResponse.Builder().code(404).build()) }
        server.enqueue(MockResponse.Builder().body(body).addHeader("Content-Type", "application/json").build())
    }

    @Test
    fun `a relative authorization endpoint is a sign-in error, not a crash`() = runTest {
        metadata("""{"authorization_endpoint":"/authorize","token_endpoint":"${server.url("/token")}"}""")

        val failure = runCatching { McpOAuth({ http }).discover(server.url("/mcp").toString(), null) }.exceptionOrNull()

        assertTrue("got $failure", failure is McpOAuthException)
    }

    @Test
    fun `valid endpoints are kept`() = runTest {
        metadata("""{"authorization_endpoint":"${server.url("/authorize")}","token_endpoint":"${server.url("/token")}","registration_endpoint":"junk"}""")

        val found = McpOAuth({ http }).discover(server.url("/mcp").toString(), null)

        assertEquals(server.url("/token").toString(), found.tokenEndpoint)
        assertEquals(null, found.registrationEndpoint)
    }
}
