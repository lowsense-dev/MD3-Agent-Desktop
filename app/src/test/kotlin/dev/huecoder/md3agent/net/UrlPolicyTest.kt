package dev.huecoder.md3agent.net

import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class UrlPolicyTest {
    @Test
    fun `https is always allowed`() {
        assertTrue(UrlPolicy.allowsCredentials("https://api.openai.com/v1"))
    }

    @Test
    fun `cleartext only to this device or the local network`() {
        listOf(
            "http://localhost:11434/v1", "http://127.0.0.1:8080", "http://192.168.1.20:1234/v1", "http://10.0.0.5",
            "http://172.20.1.1", "http://[::1]:8080", "http://nas.local:5000", "http://ollama:11434", "http://100.101.1.2",
        ).forEach { assertTrue(it, UrlPolicy.allowsCredentials(it)) }
    }

    @Test
    fun `cleartext to public hosts is refused`() {
        listOf("http://api.openai.com/v1", "http://8.8.8.8", "http://172.32.0.1", "http://example.com.evil", "not a url")
            .forEach { assertFalse(it, UrlPolicy.allowsCredentials(it)) }
    }

    @Test
    fun `credential headers are recognised`() {
        listOf("Authorization", "x-api-key", "x-goog-api-key", "xi-api-key", "X-Auth-Token", "Cookie", "client-secret")
            .forEach { assertTrue(it, CleartextCredentialGuard.isCredential(it)) }
        listOf("Accept", "Content-Type", "User-Agent").forEach { assertFalse(it, CleartextCredentialGuard.isCredential(it)) }
    }

    @Test
    fun `a key is never sent to a public host over http`() {
        val client = OkHttpClient.Builder().addInterceptor(CleartextCredentialGuard()).build()
        val request = Request.Builder().url("http://api.example.com/v1/models").header("Authorization", "Bearer k").build()
        val failure = runCatching { client.newCall(request).execute() }.exceptionOrNull()
        assertTrue(failure is IOException && "Refusing" in failure.message.orEmpty())
    }
}
