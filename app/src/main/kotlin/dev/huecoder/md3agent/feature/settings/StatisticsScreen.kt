package dev.huecoder.md3agent.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import dev.huecoder.md3agent.ui.text.pluralStringResource
import dev.huecoder.md3agent.ui.text.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.data.db.StatsDao
import dev.huecoder.md3agent.data.models.ModelCatalog
import dev.huecoder.md3agent.feature.chat.ReplyMetaFormat
import dev.huecoder.md3agent.ui.components.SectionHeader
import dev.huecoder.md3agent.ui.components.SettingsScaffold
import dev.huecoder.md3agent.ui.components.appViewModel
import dev.huecoder.md3agent.ui.components.groupedListColors
import dev.huecoder.md3agent.ui.text.asString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn

enum class StatsPeriod(val days: Int?) { WEEK(7), MONTH(30), ALL(null) }

@OptIn(ExperimentalCoroutinesApi::class)
class StatisticsViewModel(
    private val stats: StatsDao,
    private val catalog: ModelCatalog,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val _period = MutableStateFlow(StatsPeriod.MONTH)
    val period: StateFlow<StatsPeriod> = _period.asStateFlow()

    val report: StateFlow<UsageReport?> = _period
        .mapLatest { load(it) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    fun select(period: StatsPeriod) {
        _period.value = period
    }

    private suspend fun load(period: StatsPeriod): UsageReport {
        val since = period.days?.let { clock() - it * DAY_MS } ?: 0L
        val rows = stats.modelUsage(since)
        rows.mapNotNullTo(HashSet()) { it.providerId }.forEach { catalog.loadIfMissing(it) }
        return UsageReport(
            models = UsageStats.models(rows) { row -> row.modelId?.let { catalog.known(row.providerId, it) }?.price },
            tools = UsageStats.tools(stats.toolTraces(since)),
            activity = stats.activity(since),
        )
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val DAY_MS = 24 * 60 * 60 * 1000L
    }
}

private data class StatLine(val key: String, val title: String, val detail: String? = null, val value: String? = null)

private val PERIODS = listOf(
    Choice(StatsPeriod.WEEK, R.string.statistics_period_week),
    Choice(StatsPeriod.MONTH, R.string.statistics_period_month),
    Choice(StatsPeriod.ALL, R.string.statistics_period_all),
)

@Composable
fun StatisticsScreen(onBack: () -> Unit) {
    val viewModel = appViewModel { StatisticsViewModel(it.usageStats, it.modelCatalog) }
    val period by viewModel.period.collectAsStateWithLifecycle()
    val report by viewModel.report.collectAsStateWithLifecycle()

    SettingsScaffold(stringResource(R.string.settings_statistics), onBack) { padding ->
        val current = report
        val totals = current?.let { totalLines(it) }.orEmpty()
        val models = current?.models.orEmpty().map { modelLine(it) }
        val tools = current?.tools.orEmpty().map { tool ->
            StatLine(
                key = "tool:${tool.name}",
                title = tool.name,
                detail = if (tool.failed > 0) stringResource(R.string.statistics_failed_count, tool.failed) else null,
                value = pluralStringResource(R.plurals.statistics_call_count, tool.calls, tool.calls),
            )
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
        ) {
            item(key = "period") { SegmentedChoices(PERIODS, period, viewModel::select) }
            if (current == null) return@LazyColumn
            if (current.requests == 0 && current.tools.isEmpty()) {
                item(key = "empty") { Note(stringResource(R.string.statistics_empty)) }
                return@LazyColumn
            }
            item(key = "totals-header") { SectionHeader(stringResource(R.string.statistics_section_totals)) }
            statGroup(totals)
            if (models.isNotEmpty()) {
                item(key = "models-header") { SectionHeader(stringResource(R.string.statistics_section_models)) }
                statGroup(models)
            }
            item(key = "tools-header") { SectionHeader(stringResource(R.string.statistics_section_tools)) }
            if (tools.isEmpty()) item(key = "no-tools") { Note(stringResource(R.string.statistics_no_tools)) } else statGroup(tools)
        }
    }
}

@Composable
private fun totalLines(report: UsageReport): List<StatLine> {
    val cost = report.cost?.let(ReplyMetaFormat::cost)
    return listOf(
        StatLine("total:requests", stringResource(R.string.statistics_requests), value = report.requests.toString()),
        StatLine("total:input", stringResource(R.string.statistics_input), value = ReplyMetaFormat.tokens(report.promptTokens).asString()),
        StatLine("total:output", stringResource(R.string.statistics_output), value = ReplyMetaFormat.tokens(report.completionTokens).asString()),
        StatLine("total:cached", stringResource(R.string.statistics_cached), value = ReplyMetaFormat.tokens(report.cachedTokens).asString()),
        StatLine(
            key = "total:cost",
            title = stringResource(R.string.statistics_cost),
            detail = when {
                cost == null -> stringResource(R.string.statistics_cost_unknown)
                report.isCostPartial -> stringResource(R.string.statistics_cost_partial, cost)
                else -> null
            },
            value = cost,
        ),
        StatLine("total:chats", stringResource(R.string.statistics_chats), value = report.activity.chats.toString()),
        StatLine("total:messages", stringResource(R.string.statistics_messages), value = report.activity.messages.toString()),
    )
}

@Composable
private fun modelLine(stat: ModelStat): StatLine = StatLine(
    key = "model:${stat.modelId}",
    title = stat.modelId ?: stringResource(R.string.statistics_unknown_model),
    detail = listOf(
        pluralStringResource(R.plurals.statistics_request_count, stat.requests, stat.requests),
        ReplyMetaFormat.usage(stat.promptTokens, stat.completionTokens, stat.cachedTokens).asString(),
    ).joinToString(" · "),
    value = stat.cost?.let(ReplyMetaFormat::cost),
)

private fun LazyListScope.statGroup(lines: List<StatLine>) {
    itemsIndexed(lines, key = { _, line -> line.key }) { index, line ->
        val detail = line.detail
        val value = line.value
        SegmentedListItem(
            onClick = {},
            shapes = ListItemDefaults.segmentedShapes(index, lines.size),
            colors = groupedListColors(),
            supportingContent = if (detail != null) {
                { Text(detail) }
            } else {
                null
            },
            trailingContent = if (value != null) {
                { Text(value, style = MaterialTheme.typography.titleSmall) }
            } else {
                null
            },
        ) { Text(line.title) }
    }
}

@Composable
private fun Note(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 16.dp),
    )
}
