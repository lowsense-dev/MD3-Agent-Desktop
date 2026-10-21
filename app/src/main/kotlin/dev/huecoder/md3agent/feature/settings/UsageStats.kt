package dev.huecoder.md3agent.feature.settings

import dev.huecoder.md3agent.conversation.TurnAssembler
import dev.huecoder.md3agent.data.db.ActivityCounts
import dev.huecoder.md3agent.data.db.MessageRole
import dev.huecoder.md3agent.data.db.MessageStatus
import dev.huecoder.md3agent.data.db.ModelUsageRow
import dev.huecoder.md3agent.data.db.ToolTraceRow
import dev.huecoder.md3agent.llm.models.ModelPrice

data class ModelStat(
    val modelId: String?,
    val requests: Int,
    val promptTokens: Long,
    val completionTokens: Long,
    val cachedTokens: Long,
    val cost: Double?,
) {
    val totalTokens: Long get() = promptTokens + completionTokens
}

data class ToolStat(val name: String, val calls: Int, val failed: Int)

data class UsageReport(val models: List<ModelStat>, val tools: List<ToolStat>, val activity: ActivityCounts) {
    val requests: Int get() = models.sumOf { it.requests }
    val promptTokens: Long get() = models.sumOf { it.promptTokens }
    val completionTokens: Long get() = models.sumOf { it.completionTokens }
    val cachedTokens: Long get() = models.sumOf { it.cachedTokens }
    val cost: Double? get() = models.mapNotNull { it.cost }.takeIf { it.isNotEmpty() }?.sum()
    val isCostPartial: Boolean get() = models.any { it.cost == null }
}

object UsageStats {
    fun models(rows: List<ModelUsageRow>, priceOf: (ModelUsageRow) -> ModelPrice?): List<ModelStat> =
        rows.groupBy { it.modelId }.map { (modelId, group) ->
            val costs = group.map { row -> priceOf(row)?.cost(row.promptTokens, row.completionTokens, row.cachedTokens) }
            ModelStat(
                modelId = modelId,
                requests = group.sumOf { it.requests },
                promptTokens = group.sumOf { it.promptTokens },
                completionTokens = group.sumOf { it.completionTokens },
                cachedTokens = group.sumOf { it.cachedTokens },
                cost = if (costs.any { it == null }) null else costs.filterNotNull().sum(),
            )
        }.sortedByDescending { it.totalTokens }

    fun tools(rows: List<ToolTraceRow>): List<ToolStat> {
        val failed = rows.filter { it.role == MessageRole.TOOL && it.status == MessageStatus.ERROR }.mapTo(HashSet()) { it.chatId to it.toolCallId }
        return rows.filter { it.role == MessageRole.ASSISTANT }
            .flatMap { row -> TurnAssembler.decodeToolCalls(row.toolCallsJson).map { call -> call.name to ((row.chatId to call.id) in failed) } }
            .groupBy({ it.first }, { it.second })
            .map { (name, outcomes) -> ToolStat(name, outcomes.size, outcomes.count { it }) }
            .sortedWith(compareByDescending<ToolStat> { it.calls }.thenBy { it.name })
    }
}
