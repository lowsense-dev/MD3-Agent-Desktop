package dev.huecoder.md3agent.conversation

import dev.huecoder.md3agent.data.attachments.Attachment
import dev.huecoder.md3agent.data.attachments.AttachmentContent
import dev.huecoder.md3agent.data.attachments.AttachmentStore
import dev.huecoder.md3agent.data.chat.ChatRepository
import dev.huecoder.md3agent.data.db.ChatEntity
import dev.huecoder.md3agent.data.db.MessageEntity
import dev.huecoder.md3agent.data.db.MessageRole
import dev.huecoder.md3agent.data.db.MessageStatus
import dev.huecoder.md3agent.data.settings.AgentProfile
import dev.huecoder.md3agent.data.settings.AppSettings
import dev.huecoder.md3agent.data.settings.ProviderProfile
import dev.huecoder.md3agent.data.settings.SettingsRepository
import dev.huecoder.md3agent.data.settings.ToolPermission
import dev.huecoder.md3agent.data.settings.ToolSettings
import dev.huecoder.md3agent.llm.ChatCompletionClient
import dev.huecoder.md3agent.llm.ChatTurn
import dev.huecoder.md3agent.llm.CompletionOptions
import dev.huecoder.md3agent.llm.models.ModelFamily
import dev.huecoder.md3agent.llm.models.ModelOutput
import dev.huecoder.md3agent.llm.models.ReasoningEffort
import dev.huecoder.md3agent.data.models.ModelCatalog
import dev.huecoder.md3agent.data.settings.ModelSelection
import dev.huecoder.md3agent.llm.CompletionEvent
import dev.huecoder.md3agent.llm.ProviderException
import dev.huecoder.md3agent.llm.ReasoningBlock
import dev.huecoder.md3agent.llm.ReasoningTrace
import dev.huecoder.md3agent.llm.ToolCall
import dev.huecoder.md3agent.llm.ToolCallAccumulator
import dev.huecoder.md3agent.llm.TokenUsage
import dev.huecoder.md3agent.llm.ToolSpec
import dev.huecoder.md3agent.llm.carriesReasoning
import dev.huecoder.md3agent.llm.promptFingerprint
import dev.huecoder.md3agent.llm.rejectsReplayedReasoning
import dev.huecoder.md3agent.llm.withoutReasoning
import dev.huecoder.md3agent.prompt.SystemPromptRenderer
import dev.huecoder.md3agent.tools.ToolContext
import dev.huecoder.md3agent.tools.ToolOutcome
import dev.huecoder.md3agent.tools.ToolRegistry
import dev.huecoder.md3agent.platform.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.fold
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import dev.huecoder.md3agent.data.memory.MemoryStore

class ConversationRunner(
    private val chats: ChatRepository,
    private val settings: SettingsRepository,
    private val completions: ChatCompletionClient,
    private val systemPrompt: SystemPromptRenderer,
    private val tools: ToolRegistry,
    private val attachments: AttachmentStore,
    private val approvals: ToolApprovals,
    private val prompts: UserPrompts,
    private val catalog: ModelCatalog,
    private val generatedImages: GeneratedImages,
    private val memories: MemoryStore,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val jobs = ConcurrentHashMap<String, Job>()
    private val titler = ChatTitler(chats, completions, catalog)
    private val _drafts = MutableStateFlow<Map<String, StreamingDraft>>(emptyMap())

    val drafts: StateFlow<Map<String, StreamingDraft>> = _drafts.asStateFlow()

    private val _compacting = MutableStateFlow<Set<String>>(emptySet())

    // The prompt id each chat last searched long-term memory for: one search per message, not per tool round.
    private val recalled = ConcurrentHashMap<String, String>()

    /** Chats whose history is being summarized right now. */
    val compacting: StateFlow<Set<String>> = _compacting.asStateFlow()

    val activity: Flow<Map<String, ChatActivity>> = combine(_drafts, prompts.pending) { drafts, asked ->
        drafts.mapValues { (chatId, draft) ->
            if (draft.awaitingCallId != null || chatId in asked) ChatActivity.NEEDS_INPUT else ChatActivity.WORKING
        }
    }.distinctUntilChanged()

    init {
        scope.launch { chats.recoverInterruptedStreams() }
    }

    suspend fun startChat(text: String, attached: List<Attachment>, selection: ModelSelection? = null, agentId: String? = null): ChatEntity {
        val current = settings.current()
        val chosen = selection ?: current.activeSelection ?: throw NoModelConfiguredException()
        val chat = chats.createChat(titleFrom(text, attached), chosen, current.agent(agentId).id)
        send(chat.id, text, attached)
        return chat
    }

    suspend fun send(chatId: String, text: String, attached: List<Attachment>) {
        chats.appendUserMessage(chatId, text.trim(), attached, systemPrompt.contextNote())
        launchReply(chatId)
    }

    suspend fun regenerate(assistantMessage: MessageEntity) {
        // Join so the cancelled reply's final save cannot write the truncated row back.
        jobs.remove(assistantMessage.chatId)?.cancelAndJoin()
        val history = chats.history(assistantMessage.chatId)
        val prompt = history.lastOrNull { it.role == MessageRole.USER && it.createdAt < assistantMessage.createdAt } ?: return
        chats.truncateAfter(prompt)
        launchReply(assistantMessage.chatId)
    }

    suspend fun resend(chatId: String, messageId: String, text: String, attached: List<Attachment>) {
        jobs.remove(chatId)?.cancelAndJoin()
        chats.history(chatId).firstOrNull { it.id == messageId && it.role == MessageRole.USER }?.let { chats.truncateFrom(it, keep = attached) }
        send(chatId, text, attached)
    }

    fun cancel(chatId: String) {
        jobs.remove(chatId)?.cancel()
    }

    fun cancelAll() {
        jobs.keys.toList().forEach(::cancel)
    }

    /** Summarizes the chat so far (like Codex's /compact); the model then continues from the summary. */
    fun compact(chatId: String) = launchJob(chatId) {
        try {
            val model = resolveModel(chatId, settings.current()) ?: return@launchJob chats.save(errorMessage(chatId, NO_MODEL_SELECTED))
            summarize(chatId, model, chats.history(chatId), pending = null)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Compaction failed", e)
            chats.save(errorMessage(chatId, COMPACTION_FAILED + (e.message ?: e.javaClass.simpleName)))
        }
    }

    private fun launchReply(chatId: String) = launchJob(chatId) { generateReply(chatId) }

    private fun launchJob(chatId: String, work: suspend () -> Unit) {
        cancel(chatId)
        val job = scope.launch(start = CoroutineStart.LAZY) { work() }
        jobs[chatId] = job
        job.invokeOnCompletion { jobs.remove(chatId, job) }
        job.start()
    }

    private suspend fun generateReply(chatId: String) {
        try {
            val current = settings.current()
            val model = resolveModel(chatId, current) ?: return chats.save(errorMessage(chatId, NO_MODEL_SELECTED))
            val agent = current.agents.forChat(chats.chat(chatId)?.agentId)
            val toolSettings = agent.tools(current.tools)
            if (current.chat.autoCompact) autoCompact(chatId, model, current.chat.compactAt)
            while (true) {
                val round = streamRound(chatId, model, agent, toolSettings) ?: return
                if (round.toolCalls.isEmpty()) return labelLater(chatId, model, current)
                val endsTurn = runTools(chatId, round, toolSettings)
                if (endsTurn) return labelLater(chatId, model, current)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Anything outside a streamed round (catalog lookup, history, DB, tools) ends the turn as an error row, never a crash.
            Log.e(TAG, "Reply failed", e)
            try {
                chats.save(errorMessage(chatId, e.message ?: e.javaClass.simpleName))
            } catch (saveFailure: Exception) {
                Log.e(TAG, "Could not record the failure", saveFailure)
            }
        } finally {
            val self = currentCoroutineContext()[Job]
            _drafts.update { drafts -> if (jobs[chatId].let { it == null || it === self }) drafts - chatId else drafts }
        }
    }

    /** Compacts before answering when the last turn filled most of the window; on failure the reply goes on with full history. */
    private suspend fun autoCompact(chatId: String, model: ResolvedModel, threshold: Float) {
        val history = chats.history(chatId)
        val window = catalog.infoLoaded(model.provider.id, model.modelId).contextWindow
        if (!Compaction.needsCompaction(Compaction.usage(history, window), threshold)) return
        try {
            summarize(chatId, model, history, pending = history.lastOrNull()?.takeIf { it.role == MessageRole.USER })
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Auto compaction failed, answering with the full history", e)
        }
    }

    private suspend fun summarize(chatId: String, model: ResolvedModel, history: List<MessageEntity>, pending: MessageEntity?): Boolean {
        val active = Compaction.activeHistory(history).filter { it.id != pending?.id }
        if (!Compaction.worthCompacting(active)) return false
        val info = catalog.infoLoaded(model.provider.id, model.modelId)
        val traits = info.traits
        val turns = listOf(
            ChatTurn(ChatTurn.Role.SYSTEM, Compaction.INSTRUCTIONS),
            ChatTurn(ChatTurn.Role.USER, Compaction.transcript(Compaction.latestSummary(history), active, info.contextWindow)),
        )
        val options = CompletionOptions(reasoningEffort = traits.reasoningEfforts.minOrNull().takeIf { traits.reasoning }, reasoningControl = traits.reasoningControl)
        _compacting.update { it + chatId }
        try {
            val summary = completions.stream(model.provider, model.modelId, turns, options = options)
                .filterIsInstance<CompletionEvent.ContentDelta>()
                .fold(StringBuilder()) { text, delta -> text.append(delta.text) }
                .toString()
                .trim()
            if (summary.isEmpty()) throw IllegalStateException(EMPTY_SUMMARY)
            chats.appendCompaction(chatId, summary, model.modelId, pending)
            return true
        } finally {
            _compacting.update { it - chatId }
        }
    }

    private fun labelLater(chatId: String, model: ResolvedModel, current: AppSettings) {
        if (current.chat.autoTitle) scope.launch {
            try {
                titler.labelIfNeeded(chatId, model)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Labeling failed", e)
            }
        }
    }

    private suspend fun streamRound(chatId: String, model: ResolvedModel, agent: AgentProfile, toolSettings: ToolSettings): StreamingDraft? {
        tools.awaitReady()
        val info = catalog.infoLoaded(model.provider.id, model.modelId)
        val traits = info.traits
        val options = CompletionOptions(
            reasoningEffort = model.reasoningEffort?.takeIf { traits.reasoning },
            reasoningControl = traits.reasoningControl,
            output = traits.output,
            explicitCaching = info.family == ModelFamily.CLAUDE,
        )
        val toolSpecs = if (traits.output == ModelOutput.IMAGE || info.toolUse == false) emptyList() else tools.specs(toolSettings)
        val turns = buildTurns(chatId, agent, toolSettings, chats.history(chatId))
        val prompt = promptFingerprint(turns, toolSpecs)
        val message = chats.startAssistantMessage(chatId, model.modelId)
        val accumulator = ToolCallAccumulator()
        var finishReason: String? = null
        var draft = StreamingDraft(message.id, content = "", reasoning = "", toolCalls = emptyList(), runningCallIds = emptySet(), awaitingCallId = null, usage = null)
        val content = StringBuilder()
        val reasoning = StringBuilder()
        fun latest() = draft.copy(content = content.toString(), reasoning = reasoning.toString(), toolCalls = accumulator.calls())
        publish(chatId, draft)
        var publishedAt = System.nanoTime()
        try {
            streamWithReplayFallback(model, turns, toolSpecs, options).collect { event ->
                var urgent = false
                when (event) {
                    is CompletionEvent.ContentDelta -> content.append(event.text)
                    is CompletionEvent.ReasoningDelta -> reasoning.append(event.text)
                    is CompletionEvent.ReasoningBlockDone -> draft = draft.copy(reasoningBlocks = draft.reasoningBlocks + event.block)
                    is CompletionEvent.ImageOutput -> draft = draft.copy(images = draft.images + event.url).also { urgent = true }
                    is CompletionEvent.ToolCallDelta -> {
                        accumulator.add(event)
                        urgent = !event.id.isNullOrEmpty() || !event.name.isNullOrEmpty()
                    }
                    is CompletionEvent.Finished -> finishReason = event.reason
                    is CompletionEvent.Usage -> draft = draft.copy(usage = event.usage)
                }
                val now = System.nanoTime()
                if (urgent || now - publishedAt >= PUBLISH_INTERVAL_NS) {
                    publishedAt = now
                    publish(chatId, latest())
                }
            }
            draft = latest()
            publish(chatId, draft)
            // A reply cut at the output limit is kept as complete, with a note so it does not pass for a full answer.
            val cutOff = TRUNCATED_REASON.takeIf { finishReason?.lowercase() in OUTPUT_LIMIT_REASONS }
            persist(message, draft, MessageStatus.COMPLETE, errorText = cutOff, trace = reasoningTrace(model, prompt, draft))
            return draft
        } catch (e: CancellationException) {
            draft = latest()
            withContext(NonCancellable) { persist(message, draft, MessageStatus.CANCELLED, errorText = null, trace = reasoningTrace(model, prompt, draft)) }
            throw e
        } catch (e: Exception) {
            persist(message, latest(), MessageStatus.ERROR, errorText = e.message ?: e.javaClass.simpleName, trace = null)
            return null
        }
    }

    private suspend fun runTools(chatId: String, round: StreamingDraft, toolSettings: ToolSettings): Boolean {
        for (call in round.toolCalls) {
            val outcome = if (isApproved(chatId, round, call, toolSettings)) {
                publish(chatId, round.copy(runningCallIds = setOf(call.id)))
                tools.run(call, toolSettings, ToolContext(chatId))
            } else {
                ToolOutcome.denied()
            }
            chats.appendToolResult(chatId, call.id, outcome.content, outcome.messageStatus, outcome.attachments)
        }
        return round.toolCalls.all(tools::endsTurn)
    }

    private suspend fun isApproved(chatId: String, round: StreamingDraft, call: ToolCall, settings: ToolSettings): Boolean {
        if (tools.permission(call.name, settings) != ToolPermission.ASK || approvals.isAllowedForSession(chatId, call.name)) return true
        publish(chatId, round.copy(awaitingCallId = call.id))
        return approvals.request(chatId, call)
    }

    private suspend fun buildTurns(chatId: String, agent: AgentProfile, toolSettings: ToolSettings, fullHistory: List<MessageEntity>): List<ChatTurn> {
        val history = withRecall(chatId, fullHistory)
        val contents = mutableMapOf<String, AttachmentContent>()
        Compaction.activeHistory(history).flatMap { Attachment.decode(it.attachmentsJson) }.forEach { attachment ->
            attachments.contentFor(attachment)?.let { contents[attachment.id] = it }
        }
        return TurnAssembler.assemble(systemPrompt.render(agent, tools.promptNotes(toolSettings), memories.instant()), history, contents)
    }

    /**
     * Hands the model long-term memories relevant to the latest prompt up front. They are written once into that message's
     * context block, not the system prompt, so every later request keeps the same prefix and stays in the provider's cache.
     */
    private suspend fun withRecall(chatId: String, history: List<MessageEntity>): List<MessageEntity> {
        val index = history.indexOfLast { it.role == MessageRole.USER }
        val prompt = history.getOrNull(index)?.takeIf { it.content.isNotBlank() } ?: return history
        if (recalled.put(chatId, prompt.id) == prompt.id || prompt.contextNote?.contains(RECALL_HEADER) == true) return history
        val found = try {
            memories.search(prompt.content, RECALL_LIMIT).matches.filter { it.score >= RECALL_MIN_SCORE }.map { it.item }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Memory recall failed", e)
            emptyList()
        }
        if (found.isEmpty()) return history
        val recall = found.joinToString("\n", prefix = "$RECALL_HEADER\n") { "- [${it.id}] ${it.title}: ${it.content}" }
        val noted = prompt.copy(contextNote = listOfNotNull(prompt.contextNote, recall).joinToString("\n"))
        chats.save(noted)
        return history.toMutableList().also { it[index] = noted }
    }

    private suspend fun resolveModel(chatId: String, current: AppSettings): ResolvedModel? {
        val chat = chats.chat(chatId)
        val chatProvider = current.provider(chat?.providerId)
        if (chatProvider != null && !chat?.modelId.isNullOrBlank()) {
            return ResolvedModel(chatProvider, chat.modelId, chat.reasoningEffort?.let(ReasoningEffort::fromWire))
        }
        val active = current.activeSelection ?: return null
        val provider = current.provider(active.providerId) ?: return null
        return ResolvedModel(provider, active.modelId, active.reasoningEffort)
    }

    private fun streamWithReplayFallback(model: ResolvedModel, turns: List<ChatTurn>, toolSpecs: List<ToolSpec>, options: CompletionOptions): Flow<CompletionEvent> =
        completions.stream(model.provider, model.modelId, turns, toolSpecs, options).catch { failure ->
            if (failure !is ProviderException.Http || !failure.rejectsReplayedReasoning() || !turns.carriesReasoning()) throw failure
            emitAll(completions.stream(model.provider, model.modelId, turns.withoutReasoning(), toolSpecs, options))
        }

    private fun reasoningTrace(model: ResolvedModel, prompt: String, draft: StreamingDraft): ReasoningTrace? =
        draft.reasoningBlocks.takeIf { it.isNotEmpty() }?.let { ReasoningTrace(model.provider.format, model.modelId, prompt, it) }

    private suspend fun persist(message: MessageEntity, draft: StreamingDraft, status: MessageStatus, errorText: String?, trace: ReasoningTrace?) {
        val images = if (draft.images.isEmpty()) emptyList() else generatedImages.store(draft.images)
        chats.save(
            message.copy(
                attachmentsJson = Attachment.encode(images),
                content = draft.content,
                reasoning = draft.reasoning.ifEmpty { null },
                reasoningTraceJson = trace?.let(TurnAssembler::encodeTrace),
                status = status,
                errorText = errorText,
                toolCallsJson = TurnAssembler.encodeToolCalls(draft.toolCalls),
                finishedAt = clock(),
                promptTokens = draft.usage?.promptTokens,
                completionTokens = draft.usage?.completionTokens,
                cachedTokens = draft.usage?.cachedTokens?.takeIf { it > 0 },
            ),
        )
    }

    private suspend fun errorMessage(chatId: String, text: String): MessageEntity =
        chats.startAssistantMessage(chatId, modelId = "").copy(status = MessageStatus.ERROR, errorText = text)

    private fun publish(chatId: String, draft: StreamingDraft) = _drafts.update { it + (chatId to draft) }

    private fun titleFrom(text: String, attached: List<Attachment>): String {
        val singleLine = text.trim().replace(WHITESPACE, " ").ifEmpty { attached.firstOrNull()?.name.orEmpty() }
        return if (singleLine.length <= TITLE_MAX_CHARS) singleLine else singleLine.take(TITLE_MAX_CHARS).trimEnd() + "…"
    }

    private companion object {
        const val TITLE_MAX_CHARS = 48
        const val PUBLISH_INTERVAL_NS = 50_000_000L
        const val NO_MODEL_SELECTED = "No model selected. Add a provider in settings."
        const val TRUNCATED_REASON = "Reply was cut off: the model hit its output token limit."
        const val TAG = "ConversationRunner"
        const val RECALL_LIMIT = 5
        const val RECALL_HEADER = "Long-term memories that may be relevant (ids in brackets):"
        // A single shared common word scores ~0.1; this keeps only memories that actually match the prompt.
        const val RECALL_MIN_SCORE = 0.2f
        const val COMPACTION_FAILED = "Could not compact the chat: "
        const val EMPTY_SUMMARY = "the model returned an empty summary"
        val OUTPUT_LIMIT_REASONS = setOf("length", "max_tokens", "max_output_tokens")
        val WHITESPACE = Regex("""\s+""")
    }
}

private val ToolOutcome.messageStatus: MessageStatus
    get() = when {
        isDenied -> MessageStatus.CANCELLED
        isError -> MessageStatus.ERROR
        else -> MessageStatus.COMPLETE
    }

enum class ChatActivity { WORKING, NEEDS_INPUT }

data class StreamingDraft(
    val messageId: String,
    val content: String,
    val reasoning: String,
    val toolCalls: List<ToolCall>,
    val runningCallIds: Set<String>,
    val awaitingCallId: String?,
    val usage: TokenUsage?,
    val images: List<String> = emptyList(),
    val reasoningBlocks: List<ReasoningBlock> = emptyList(),
)

data class ResolvedModel(val provider: ProviderProfile, val modelId: String, val reasoningEffort: ReasoningEffort?)

class NoModelConfiguredException : IllegalStateException("Add a provider and choose a model in settings first")
