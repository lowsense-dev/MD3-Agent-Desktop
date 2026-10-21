package dev.huecoder.md3agent.llm

import dev.huecoder.md3agent.data.settings.ApiFormat
import dev.huecoder.md3agent.data.settings.ProviderProfile
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request

class ProviderEndpoint(private val provider: ProviderProfile) {
    private val base: HttpUrl = normalizeBase(provider.baseUrl)
        ?: throw ProviderException.InvalidConfiguration("Invalid provider address: ${provider.baseUrl}")

    fun url(path: String): HttpUrl = base.newBuilder().addPathSegments(path).build()

    fun authorizedRequest(path: String): Request.Builder = authorizedRequest(url(path))

    fun authorizedRequest(url: HttpUrl): Request.Builder {
        val builder = Request.Builder().url(url)
        val key = KeyRotation.order(provider.id, provider.apiKeys).firstOrNull().orEmpty()
        when (provider.format) {
            ApiFormat.OPENAI -> if (key.isNotEmpty()) builder.safeHeader("Authorization", "Bearer $key")
            ApiFormat.ANTHROPIC -> {
                if (key.isNotEmpty()) builder.safeHeader("x-api-key", key)
                builder.header("anthropic-version", ANTHROPIC_VERSION)
            }
            ApiFormat.GEMINI -> if (key.isNotEmpty()) builder.safeHeader("x-goog-api-key", key)
            ApiFormat.ELEVENLABS -> if (key.isNotEmpty()) builder.safeHeader("xi-api-key", key)
        }
        provider.extraHeaders.filter { it.name.isNotBlank() }.forEach { builder.safeHeader(it.name.trim(), it.value) }
        return builder
    }

    private fun Request.Builder.safeHeader(name: String, value: String) {
        try {
            header(name, value)
        } catch (e: IllegalArgumentException) {
            throw ProviderException.InvalidConfiguration("Header \"$name\" has characters that can't be sent. Use plain ASCII in the API key and headers.")
        }
    }

    private companion object {
        const val ANTHROPIC_VERSION = "2023-06-01"
        val KNOWN_ENDPOINT_SUFFIXES = listOf("/chat/completions", "/messages", "/models")

        fun normalizeBase(raw: String): HttpUrl? {
            var trimmed = raw.trim().trimEnd('/')
            KNOWN_ENDPOINT_SUFFIXES.forEach { suffix -> trimmed = trimmed.removeSuffix(suffix) }
            return trimmed.toHttpUrlOrNull()
        }
    }
}

sealed class ProviderException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class InvalidConfiguration(message: String) : ProviderException(message)
    class Http(val code: Int, message: String, val retryAfterSeconds: Long? = null) : ProviderException(message)
    class Network(cause: Throwable) : ProviderException(cause.message ?: "Network error", cause)
    class MalformedResponse(message: String) : ProviderException(message)
}
