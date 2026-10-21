package dev.huecoder.md3agent.net

import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.IOException

class HttpCallsTest {
    private val server = MockWebServer()
    private val client = OkHttpClient()

    @Before
    fun setUp() = server.start()

    @After
    fun tearDown() = server.close()

    @Test
    fun `a chunked download over the limit fails and leaves no file`() = runBlocking {
        server.enqueue(MockResponse.Builder().chunkedBody("x".repeat(100), 10).build())
        val target = File.createTempFile("download", ".bin")

        try {
            client.fetchToFile(Request.Builder().url(server.url("/")).build(), target, maxBytes = 50)
            fail("expected IOException")
        } catch (e: IOException) {
            assertFalse(target.exists())
        }
    }

    @Test
    fun `a chunked download within the limit is saved whole`() = runBlocking {
        server.enqueue(MockResponse.Builder().chunkedBody("x".repeat(100), 10).build())
        val target = File.createTempFile("download", ".bin").apply { deleteOnExit() }

        client.fetchToFile(Request.Builder().url(server.url("/")).build(), target, maxBytes = 100)

        assertEquals(100L, target.length())
    }
}
