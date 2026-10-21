package dev.huecoder.md3agent.llm

import dev.huecoder.md3agent.data.settings.ProviderProfile
import dev.huecoder.md3agent.net.HttpStatusException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

object KeyRotation {
    private val turns = ConcurrentHashMap<String, AtomicInteger>()

    fun order(providerId: String, keys: List<String>): List<String> {
        if (keys.size < 2) return keys
        val start = Math.floorMod(turns.getOrPut(providerId) { AtomicInteger() }.getAndIncrement(), keys.size)
        return keys.drop(start) + keys.take(start)
    }

    /**
     * Runs [attempt], moving to the next key on auth, quota and server errors. Once no key is left (or there is only one),
     * rate limits and overloads are retried on the same key after a growing pause, honouring Retry-After.
     * Nothing is retried once [canRetry] says output already reached the user.
     */
    suspend fun <T> withFailover(
        provider: ProviderProfile,
        canRetry: () -> Boolean = { true },
        pause: suspend (Long) -> Unit = { delay(it) },
        attempt: suspend (ProviderProfile) -> T,
    ): T {
        val keys = order(provider.id, provider.apiKeys)
        val profiles = if (keys.size < 2) listOf(provider) else keys.map { provider.copy(apiKey = it) }
        var index = 0
        var retries = 0
        while (true) {
            try {
                return attempt(profiles[index])
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (!canRetry()) throw e
                val code = statusOf(e) ?: throw e
                when {
                    index + 1 < profiles.size && (code in FAILOVER_CODES || code in SERVER_ERRORS) -> index++
                    code in TRANSIENT_CODES && retries < MAX_RETRIES -> pause(backoffMs(retries++, (e as? ProviderException.Http)?.retryAfterSeconds))
                    else -> throw e
                }
            }
        }
    }

    internal fun backoffMs(retry: Int, retryAfterSeconds: Long?): Long =
        retryAfterSeconds?.let { (it * MS_PER_SECOND).coerceIn(0, MAX_PAUSE_MS) } ?: (BASE_PAUSE_MS shl retry).coerceAtMost(MAX_PAUSE_MS)

    private fun statusOf(e: Exception): Int? = (e as? ProviderException.Http)?.code ?: (e as? HttpStatusException)?.code

    private val FAILOVER_CODES = setOf(401, 402, 403, 429)
    private val SERVER_ERRORS = 500..599
    // 529 is Anthropic's "overloaded".
    private val TRANSIENT_CODES = setOf(429, 500, 502, 503, 504, 529)
    private const val MAX_RETRIES = 3
    private const val BASE_PAUSE_MS = 1_000L
    private const val MAX_PAUSE_MS = 20_000L
    private const val MS_PER_SECOND = 1_000L
}
