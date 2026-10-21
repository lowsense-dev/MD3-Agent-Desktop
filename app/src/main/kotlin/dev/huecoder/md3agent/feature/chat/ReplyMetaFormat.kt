package dev.huecoder.md3agent.feature.chat

import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.llm.TokenUsage
import dev.huecoder.md3agent.ui.text.UiText
import java.util.Locale
import kotlin.math.roundToInt

object ReplyMetaFormat {
    private const val MS_PER_SECOND = 1_000.0
    private const val SECONDS_PER_MINUTE = 60
    private const val THOUSAND = 1_000
    private const val MILLION = 1_000_000
    private const val PERCENT = 100
    private const val MIN_SHOWN_USD = 0.0001

    fun duration(millis: Long): UiText {
        val seconds = millis / MS_PER_SECOND
        if (seconds < SECONDS_PER_MINUTE) return UiText.Resource(R.string.chat_meta_duration_seconds, listOf(seconds))
        val whole = seconds.toLong()
        return UiText.Resource(R.string.chat_meta_duration_minutes, listOf(whole / SECONDS_PER_MINUTE, whole % SECONDS_PER_MINUTE))
    }

    fun usage(usage: TokenUsage): UiText = usage(usage.promptTokens.toLong(), usage.completionTokens.toLong(), usage.cachedTokens.toLong())

    fun usage(prompt: Long, completion: Long, cached: Long): UiText =
        if (cached > 0) {
            UiText.Resource(R.string.chat_meta_tokens_in_out_cached, listOf(tokens(prompt), tokens(completion), tokens(cached)))
        } else {
            UiText.Resource(R.string.chat_meta_tokens_in_out, listOf(tokens(prompt), tokens(completion)))
        }

    fun context(share: Float): UiText = UiText.Resource(R.string.chat_meta_context, listOf((share * PERCENT).roundToInt()))

    fun tokens(count: Long): UiText = when {
        count < THOUSAND -> UiText.Raw(count.toString())
        count < MILLION -> UiText.Resource(R.string.chat_meta_tokens_thousands, listOf(count.toDouble() / THOUSAND))
        else -> UiText.Resource(R.string.chat_meta_tokens_millions, listOf(count.toDouble() / MILLION))
    }

    fun cost(usd: Double): String = when {
        usd <= 0.0 -> "$0"
        usd < MIN_SHOWN_USD -> "<$0.0001"
        usd < 1.0 -> String.format(Locale.ROOT, "$%.4f", usd)
        else -> String.format(Locale.ROOT, "$%.2f", usd)
    }
}
