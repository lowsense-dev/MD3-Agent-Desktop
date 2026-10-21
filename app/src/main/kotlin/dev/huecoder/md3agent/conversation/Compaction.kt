package dev.huecoder.md3agent.conversation

import dev.huecoder.md3agent.data.attachments.Attachment
import dev.huecoder.md3agent.data.db.MessageEntity
import dev.huecoder.md3agent.data.db.MessageRole
import dev.huecoder.md3agent.data.db.MessageStatus

/** How much of the model's context a chat fills; [estimated] when no reply since the last compaction reported usage. */
data class ContextUsage(val usedTokens: Int, val windowTokens: Int?, val estimated: Boolean) {
    val share: Float? get() = windowTokens?.takeIf { it > 0 }?.let { (usedTokens.toFloat() / it).coerceIn(0f, 1f) }
}

/**
 * Session compaction: the conversation so far is summarized into a SYSTEM row, and from then on the model sees that
 * summary instead of everything before it. The rows stay in the chat for the user; only what is sent to the model shrinks.
 */
object Compaction {
    const val AUTO_THRESHOLD = 0.85f
    const val MIN_MESSAGES = 2
    private const val CHARS_PER_TOKEN = 4
    private const val TOOL_ARGS_CHARS = 400
    private const val TOOL_RESULT_CHARS = 1_500
    private const val FALLBACK_WINDOW = 128_000
    // The summary request must itself fit: leave room for the instructions and the answer.
    private const val TRANSCRIPT_SHARE = 0.6

    const val INSTRUCTIONS = "You are compacting a conversation so it can continue in a fresh context. " +
        "Write a handoff summary that lets you, with no other memory, pick up exactly where things stand. Include:\n" +
        "- what the user wants overall and their stated preferences and constraints;\n" +
        "- what has been done so far, key decisions and their reasons, and results of tool calls that still matter;\n" +
        "- exact details needed to continue: names, numbers, file paths, links, ids, code snippets, settings;\n" +
        "- open questions and the next steps.\n" +
        "Be concise and factual, use short sections or bullets, do not invent anything, and do not call tools. " +
        "Write in the language the user writes in."

    const val SUMMARY_HEADER = "Earlier part of this conversation, compacted into a summary (treat it as what was said before):"

    fun isCompaction(message: MessageEntity): Boolean = message.role == MessageRole.SYSTEM && message.status == MessageStatus.COMPLETE

    /** What the model still sees: everything after the latest summary, or the whole chat if it was never compacted. */
    fun activeHistory(history: List<MessageEntity>): List<MessageEntity> {
        val cut = history.indexOfLast(::isCompaction)
        return if (cut < 0) history else history.subList(cut + 1, history.size)
    }

    fun latestSummary(history: List<MessageEntity>): String? = history.lastOrNull(::isCompaction)?.content

    fun worthCompacting(active: List<MessageEntity>): Boolean = active.count { it.role == MessageRole.USER || it.role == MessageRole.ASSISTANT } >= MIN_MESSAGES

    /**
     * Plain-text transcript to summarize. Tool calls and results are kept short; if it is still too long for the model,
     * the oldest messages are dropped first (the previous summary always stays).
     */
    fun transcript(earlierSummary: String?, messages: List<MessageEntity>, windowTokens: Int?): String {
        val budget = ((windowTokens ?: FALLBACK_WINDOW) * CHARS_PER_TOKEN * TRANSCRIPT_SHARE).toInt()
        val blocks = messages.mapNotNull(::block)
        val kept = ArrayDeque<String>()
        var size = earlierSummary?.length ?: 0
        for (block in blocks.asReversed()) {
            if (size + block.length > budget && kept.isNotEmpty()) break
            kept.addFirst(block)
            size += block.length
        }
        return buildString {
            earlierSummary?.let { append("Summary of what came before:\n").append(it).append("\n\n") }
            if (kept.size < blocks.size) append("[${blocks.size - kept.size} older messages omitted]\n\n")
            append(kept.joinToString("\n\n"))
            append("\n\nWrite the handoff summary of everything above now.")
        }
    }

    /** Context in use: the last reported prompt plus answer since the latest summary, else a rough estimate from text length. */
    fun usage(history: List<MessageEntity>, windowTokens: Int?): ContextUsage? {
        val active = activeHistory(history)
        active.lastOrNull { it.role == MessageRole.ASSISTANT && it.promptTokens != null }?.let { reported ->
            val after = active.subList(active.indexOf(reported) + 1, active.size)
            val used = reported.promptTokens!! + (reported.completionTokens ?: 0) + after.sumOf { it.content.length } / CHARS_PER_TOKEN
            return ContextUsage(used, windowTokens, estimated = false)
        }
        val chars = (latestSummary(history)?.length ?: 0) + active.sumOf { it.content.length }
        return if (chars == 0) null else ContextUsage(chars / CHARS_PER_TOKEN, windowTokens, estimated = true)
    }

    fun needsCompaction(usage: ContextUsage?, threshold: Float = AUTO_THRESHOLD): Boolean = usage != null && !usage.estimated && (usage.share ?: 0f) >= threshold

    private fun block(message: MessageEntity): String? = when (message.role) {
        MessageRole.USER -> {
            val files = Attachment.decode(message.attachmentsJson).map { it.name }
            ("User: " + message.content.trim() + files.takeIf { it.isNotEmpty() }?.joinToString(prefix = " [files: ", postfix = "]").orEmpty())
                .takeIf { message.content.isNotBlank() || files.isNotEmpty() }
        }
        MessageRole.ASSISTANT -> {
            val calls = TurnAssembler.decodeToolCalls(message.toolCallsJson).map { "[called ${it.name}(${it.arguments.clip(TOOL_ARGS_CHARS)})]" }
            val text = message.content.trim().takeIf { it.isNotEmpty() && message.status != MessageStatus.ERROR }
            listOfNotNull(text, calls.joinToString("\n").takeIf { it.isNotEmpty() }).joinToString("\n").takeIf { it.isNotEmpty() }?.let { "Assistant: $it" }
        }
        MessageRole.TOOL -> "Tool result${if (message.status == MessageStatus.ERROR) " (error)" else ""}: ${message.content.trim().clip(TOOL_RESULT_CHARS)}"
        MessageRole.SYSTEM -> null
    }

    private fun String.clip(max: Int) = if (length <= max) this else take(max) + " […]"
}
