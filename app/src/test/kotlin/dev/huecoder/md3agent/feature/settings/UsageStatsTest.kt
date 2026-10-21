package dev.huecoder.md3agent.feature.settings

import dev.huecoder.md3agent.conversation.TurnAssembler
import dev.huecoder.md3agent.data.db.ActivityCounts
import dev.huecoder.md3agent.data.db.MessageRole
import dev.huecoder.md3agent.data.db.MessageStatus
import dev.huecoder.md3agent.data.db.ModelUsageRow
import dev.huecoder.md3agent.data.db.ToolTraceRow
import dev.huecoder.md3agent.llm.ToolCall
import dev.huecoder.md3agent.llm.models.ModelPrice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageStatsTest {
    private fun calls(chatId: String, vararg calls: Pair<String, String>) =
        ToolTraceRow(chatId, MessageRole.ASSISTANT, TurnAssembler.encodeToolCalls(calls.map { (id, name) -> ToolCall(id, name, "{}") }), null, MessageStatus.COMPLETE)

    private fun result(chatId: String, callId: String, status: MessageStatus) = ToolTraceRow(chatId, MessageRole.TOOL, null, callId, status)

    @Test
    fun `tools are counted with failures matched by chat and call id`() {
        val rows = listOf(
            calls("a", "1" to "web_search", "2" to "mcp_github_search"),
            result("a", "1", MessageStatus.ERROR),
            result("a", "2", MessageStatus.COMPLETE),
            calls("b", "1" to "web_search"),
            result("b", "1", MessageStatus.COMPLETE),
            calls("b", "3" to "web_search"),
            result("b", "3", MessageStatus.CANCELLED),
        )

        assertEquals(
            listOf(ToolStat("web_search", 3, 1), ToolStat("mcp_github_search", 1, 0)),
            UsageStats.tools(rows),
        )
    }

    @Test
    fun `models merge across providers, sort by tokens and lose cost when a price is missing`() {
        val price = ModelPrice(prompt = 0.001, completion = 0.002)
        val rows = listOf(
            ModelUsageRow("small", "or", 2, 100, 10, 0),
            ModelUsageRow("big", "or", 1, 5_000, 100, 4_000),
            ModelUsageRow("big", "direct", 1, 1_000, 50, 0),
        )

        val models = UsageStats.models(rows) { if (it.providerId == "or") price else null }

        assertEquals(listOf("big", "small"), models.map { it.modelId })
        assertEquals(ModelStat("big", 2, 6_000, 150, 4_000, null), models[0])
        assertEquals(100 * 0.001 + 10 * 0.002, models[1].cost!!, 1e-9)
        val report = UsageReport(models, emptyList(), ActivityCounts(1, 2))
        assertEquals(4, report.requests)
        assertTrue(report.isCostPartial)
        assertEquals(models[1].cost!!, report.cost!!, 1e-12)
    }

    @Test
    fun `report without prices has no cost`() {
        val report = UsageReport(UsageStats.models(listOf(ModelUsageRow(null, null, 1, 10, 1, 0))) { null }, emptyList(), ActivityCounts(0, 0))
        assertNull(report.cost)
        assertFalse(report.models.isEmpty())
    }
}
