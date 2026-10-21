package dev.huecoder.md3agent.net

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/**
 * Where API keys, bearer tokens and OAuth secrets may be sent. Cleartext stays allowed app-wide so self-hosted
 * endpoints on the phone or the home network keep working; everything else must be https.
 */
object UrlPolicy {
    private val LOCAL_SUFFIXES = listOf(".local", ".lan", ".home.arpa", ".internal", ".localhost")

    fun allowsCredentials(url: String): Boolean = url.trim().toHttpUrlOrNull()?.let(::allowsCredentials) ?: false

    fun allowsCredentials(url: HttpUrl): Boolean = url.isHttps || isLocalHost(url.host)

    fun isLocalHost(host: String): Boolean {
        val h = host.lowercase().removePrefix("[").removeSuffix("]")
        if (h == "localhost" || '.' !in h && ':' !in h) return true
        if (LOCAL_SUFFIXES.any { h.endsWith(it) }) return true
        if (':' in h) return h == "::1" || h.startsWith("fe80:") || h.startsWith("fc") || h.startsWith("fd")
        val octets = h.split('.').map { it.toIntOrNull() ?: return false }
        if (octets.size != 4) return false
        val (a, b) = octets
        return a == 127 || a == 10 || (a == 192 && b == 168) || (a == 172 && b in 16..31) || (a == 169 && b == 254) || (a == 100 && b in 64..127)
    }
}

/**
 * Last line of defence for [UrlPolicy]: refuses any request (including each redirect hop, as a network interceptor)
 * that carries a key, token or cookie over cleartext to a host outside this device and the local network.
 */
class CleartextCredentialGuard : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!UrlPolicy.allowsCredentials(request.url) && request.headers.names().any(::isCredential)) {
            throw IOException("Refusing to send credentials over unencrypted http to ${request.url.host}; use https")
        }
        return chain.proceed(request)
    }

    companion object {
        private val EXACT = setOf("authorization", "proxy-authorization", "cookie", "api-key")
        private val PARTS = listOf("token", "secret", "api-key", "apikey", "auth")

        fun isCredential(name: String): Boolean = name.lowercase().let { lower -> lower in EXACT || PARTS.any { it in lower } || lower.endsWith("-key") }
    }
}
