package dev.huecoder.md3agent.data.db

import androidx.room.Dao
import androidx.room.Query

data class ModelUsageRow(
    val modelId: String?,
    val providerId: String?,
    val requests: Int,
    val promptTokens: Long,
    val completionTokens: Long,
    val cachedTokens: Long,
)

data class ToolTraceRow(
    val chatId: String,
    val role: MessageRole,
    val toolCallsJson: String?,
    val toolCallId: String?,
    val status: MessageStatus,
)

data class ActivityCounts(val chats: Int, val messages: Int)

@Dao
interface StatsDao {
    @Query(
        """
        SELECT m.modelId, c.providerId, COUNT(*) AS requests,
            COALESCE(SUM(m.promptTokens), 0) AS promptTokens,
            COALESCE(SUM(m.completionTokens), 0) AS completionTokens,
            COALESCE(SUM(m.cachedTokens), 0) AS cachedTokens
        FROM messages m JOIN chats c ON c.id = m.chatId
        WHERE m.role = 'ASSISTANT' AND m.promptTokens IS NOT NULL AND m.createdAt >= :since
        GROUP BY m.modelId, c.providerId
        """,
    )
    suspend fun modelUsage(since: Long): List<ModelUsageRow>

    @Query(
        """
        SELECT chatId, role, toolCallsJson, toolCallId, status FROM messages
        WHERE createdAt >= :since AND ((role = 'ASSISTANT' AND toolCallsJson IS NOT NULL) OR role = 'TOOL')
        """,
    )
    suspend fun toolTraces(since: Long): List<ToolTraceRow>

    @Query("SELECT COUNT(DISTINCT chatId) AS chats, COUNT(*) AS messages FROM messages WHERE role = 'USER' AND createdAt >= :since")
    suspend fun activity(since: Long): ActivityCounts
}
