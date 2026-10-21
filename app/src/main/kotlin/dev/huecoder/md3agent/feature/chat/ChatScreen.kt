package dev.huecoder.md3agent.feature.chat

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import dev.huecoder.md3agent.platform.BackHandler
import androidx.compose.foundation.layout.Arrangement
import dev.huecoder.md3agent.feature.models.ModelPickerSheet
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import dev.huecoder.md3agent.platform.LocalPlatformContext
import androidx.compose.ui.platform.LocalDensity
import dev.huecoder.md3agent.ui.text.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.huecoder.md3agent.LocalAppContainer
import dev.huecoder.md3agent.R
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.ui.draganddrop.dragData
import dev.huecoder.md3agent.data.settings.TypingStyle
import dev.huecoder.md3agent.conversation.ApprovalDecision
import dev.huecoder.md3agent.feature.chat.lightbox.LightboxHost
import dev.huecoder.md3agent.ui.components.AgentAvatar
import dev.huecoder.md3agent.ui.components.appViewModel
import dev.huecoder.md3agent.ui.components.savedAppViewModel
import dev.huecoder.md3agent.ui.text.UiText
import dev.huecoder.md3agent.ui.text.asString
import dev.huecoder.md3agent.ui.text.resolve
import dev.huecoder.md3agent.voice.DictationState
import dev.huecoder.md3agent.voice.DictationViewModel
import dev.huecoder.md3agent.ui.avatar.AvatarHandoff
import dev.huecoder.md3agent.ui.avatar.KnockoutStage
import dev.huecoder.md3agent.ui.avatar.ProvideAgentLook
import dev.huecoder.md3agent.ui.avatar.TypingAttention
import dev.huecoder.md3agent.ui.avatar.knockoutArea
import dev.huecoder.md3agent.ui.avatar.knockoutObstacle
import kotlinx.coroutines.launch

@Composable
fun ChatScreen(chatId: String?, onBack: () -> Unit, onOpenChat: (String) -> Unit, focusMessageId: String? = null) {
    val viewModel = savedAppViewModel(key = "chat-$chatId") { app, saved ->
        ChatViewModel(chatId, focusMessageId, app.chats, app.conversations, app.attachments, app.approvals, app.prompts, app.settings, app.modelCatalog, app.applicationScope, app::newSpeechSynthesizer, saved)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val error by viewModel.errors.collectAsStateWithLifecycle()
    val restoredText by viewModel.restoredText.collectAsStateWithLifecycle()
    val speakingId by viewModel.speakingId.collectAsStateWithLifecycle()
    val prompt by viewModel.pendingPrompt.collectAsStateWithLifecycle()
    val editing by viewModel.editing.collectAsStateWithLifecycle()
    val yolo by viewModel.yolo.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    val context = LocalPlatformContext.current
    var input by rememberSaveable { mutableStateOf(viewModel.draft()) }
    val updateInput = { text: String ->
        input = text
        if (viewModel.editing.value == null) viewModel.saveDraft(text)
    }
    val cancelEdit: () -> Unit = { viewModel.cancelEdit()?.let { input = it } }
    val startEdit: (ChatItem.User) -> Unit = { item ->
        viewModel.startEdit(item, input)
        input = item.content
    }
    val forkTitle = stringResource(R.string.chat_fork_title, state.title.asString())
    BackHandler(enabled = editing != null) { cancelEdit() }
    var pickingModel by rememberSaveable { mutableStateOf(false) }
    val typingAttention = remember { TypingAttention() }
    val knockout = remember { KnockoutStage() }
    val handoff = remember { AvatarHandoff() }
    val sendEntrance = remember { SendEntrance() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { knockout.abort() }
    val hasMessages = state.messages.isNotEmpty()
    val density = LocalDensity.current
    var composerHeight by remember { mutableStateOf(ComposerClearance) }
    LaunchedEffect(hasMessages) {
        if (chatId == null && hasMessages && !knockout.isPlaying) handoff.play()
    }
    val container = LocalAppContainer.current
    val pickerScope = rememberCoroutineScope()
    val pickTitle = stringResource(R.string.chat_attach_files)
    val pick = { imagesOnly: Boolean ->
        pickerScope.launch {
            val files = container.filePicker.open(pickTitle, multiple = true, imagesOnly = imagesOnly)
            if (files.isNotEmpty()) viewModel.attach(files)
        }
    }
    val dictationModel = appViewModel(key = "dictation-$chatId") { DictationViewModel(it.newVoiceRecorder(), it.transcriptions, it.settings) }
    val dictation = rememberDictationControls(
        model = dictationModel,
        onTranscript = {
            updateInput(appendDictated(input, it))
            viewModel.noteDictation()
        },
    )

    LaunchedEffect(restoredText) {
        restoredText?.let {
            updateInput(listOf(it, input).filter(String::isNotBlank).joinToString("\n"))
            viewModel.consumeRestoredText()
        }
    }

    LaunchedEffect(error) {
        error?.let {
            snackbarHost.showSnackbar(it.resolve())
            viewModel.consumeError()
        }
    }

    if (pickingModel) {
        ModelPickerSheet(
            model = state.selection,
            agentId = state.agentId,
            onSelectModel = viewModel::selectModel,
            onSelectAgent = viewModel::selectAgent,
            onDismiss = { pickingModel = false },
        )
    }

    ProvideAgentLook(state.agentId) {
        LightboxHost {
            val dropTarget = remember(viewModel) { FileDropTarget(viewModel::attach) }
            Scaffold(
                modifier = Modifier.dragAndDropTarget(shouldStartDragAndDrop = { true }, target = dropTarget),
                topBar = {
                    ChatTopBar(
                        title = state.title.asString(),
                        state = state,
                        onBack = onBack,
                        onPickModel = { pickingModel = true },
                        onRename = viewModel::rename,
                        onDelete = { viewModel.delete(); onBack() },
                        onLookChange = viewModel::setLook,
                        yolo = yolo,
                        onYoloChange = viewModel::setYolo,
                        onCompact = viewModel::compact,
                    )
                },
                snackbarHost = { SnackbarHost(snackbarHost) },
            ) { padding ->
                val topBar = padding.calculateTopPadding()
                Box(Modifier.fillMaxSize().imePadding()) {
                    Box(Modifier.matchParentSize().padding(top = topBar).knockoutArea(knockout))
                    val isEmpty = state.messages.isEmpty()
                    if (!isEmpty) {
                        MessageList(
                            messages = state.messages,
                            showUsage = state.showTokenUsage,
                            typing = state.typing,
                            speakingId = speakingId,
                            onRegenerate = viewModel::regenerate,
                            onToggleSpeech = viewModel::toggleSpeech,
                            onSend = {
                                cancelEdit()
                                viewModel.send(it)
                            },
                            onApproval = viewModel::respondToApproval,
                            onEdit = startEdit.takeUnless { state.isStreaming },
                            onFork = { viewModel.fork(it.id, forkTitle, onOpenChat) },
                            topPadding = topBar,
                            bottomPadding = composerHeight + COMPOSER_GAP,
                            scrollButtonPadding = composerHeight,
                            focusItemId = state.focusItemId,
                            onFocusShown = viewModel::focusShown,
                            avatarHandoff = handoff,
                            sendEntrance = sendEntrance,
                        )
                    }
                    if (isEmpty || handoff.isActive) {
                        EmptyConversation(
                            Modifier.align(Alignment.Center).padding(top = topBar),
                            interactive = isEmpty,
                            typing = typingAttention,
                            listeningLevel = dictation.listeningLevel(),
                            knockout = knockout,
                            handoff = handoff,
                        )
                    }
                    // Messages fade out under the transparent top bar instead of running into its text.
                    val fade = MaterialTheme.colorScheme.background
                    Box(
                        Modifier.fillMaxWidth().height(topBar + TOP_FADE_EXTRA)
                            .background(Brush.verticalGradient(0f to fade, (topBar - TOP_FADE_SOLID_GAP) / (topBar + TOP_FADE_EXTRA) to fade, 1f to Color.Transparent)),
                    )
                    val composerModifier = Modifier
                        .align(Alignment.BottomCenter)
                        .onSizeChanged { composerHeight = with(density) { it.height.toDp() } }
                        .knockoutObstacle(knockout, COMPOSER_OBSTACLE)
                        .navigationBarsPadding()
                        .padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 12.dp)
                    val asked = prompt
                    if (asked != null) {
                        PromptPanel(asked, onAnswer = { viewModel.answerPrompt(asked, it) }, modifier = composerModifier)
                    } else {
                        Column(composerModifier) {
                            if (editing != null) EditingBanner(onCancel = cancelEdit)
                            ChatInput(
                                text = input,
                                onTextChange = updateInput,
                                isStreaming = state.isStreaming,
                                canSend = state.canSend(input),
                                onSend = {
                                    sendEntrance.arm()
                                    val restored = viewModel.editing.value?.previousText
                                    viewModel.send(input)
                                    if (restored != null) input = restored else updateInput("")
                                },
                                onStop = viewModel::stop,
                                attachments = state.pendingAttachments,
                                isImporting = state.isImporting,
                                onAttach = { source ->
                                    when (source) {
                                        AttachSource.PHOTOS -> pick(true)
                                        AttachSource.FILES -> pick(false)
                                    }
                                },
                                onRemoveAttachment = viewModel::removeAttachment,
                                onPasteFiles = viewModel::attach,
                                onPasteImage = viewModel::attachImage,
                                dictation = dictation,
                                onLetterTyped = typingAttention::onLetter,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MessageList(
    messages: List<ChatItem>,
    showUsage: Boolean,
    typing: TypingStyle,
    speakingId: String?,
    onRegenerate: (ChatItem.Reply) -> Unit,
    onToggleSpeech: (ChatItem.Reply) -> Unit,
    onSend: (String) -> Unit,
    onApproval: (ToolCallUi, ApprovalDecision) -> Unit,
    modifier: Modifier = Modifier.fillMaxSize(),
    topPadding: Dp = 0.dp,
    bottomPadding: Dp = ComposerClearance,
    scrollButtonPadding: Dp = bottomPadding,
    focusItemId: String? = null,
    onFocusShown: () -> Unit = {},
    avatarHandoff: AvatarHandoff? = null,
    sendEntrance: SendEntrance? = null,
    onEdit: ((ChatItem.User) -> Unit)? = null,
    onFork: ((ChatItem) -> Unit)? = null,
) {
    val handoffTargetId = if (avatarHandoff?.isActive == true) messages.firstOrNull { it is ChatItem.Reply }?.id else null
    val newestId = messages.lastOrNull()?.id
    val newestUserId = messages.lastOrNull { it is ChatItem.User }?.id
    val listState = rememberLazyListState()
    var seenUserId by remember { mutableStateOf(newestUserId) }
    LaunchedEffect(newestUserId) {
        if (seenUserId != null && newestUserId != seenUserId) listState.animateScrollToItem(0)
        seenUserId = newestUserId
    }
    val anchor = rememberScrollAnchor(listState)
    val atBottom by remember(anchor) { derivedStateOf { anchor.isAtBottom } }
    val scope = rememberCoroutineScope()
    val flash = remember { Animatable(0f) }
    var flashing by remember { mutableStateOf<String?>(null) }
    val flashColor = MaterialTheme.colorScheme.primaryContainer
    LaunchedEffect(focusItemId) {
        val target = focusItemId ?: return@LaunchedEffect
        val index = messages.asReversed().indexOfFirst { it.id == target }
        if (index >= 0) {
            listState.animateScrollToItem(index)
            flashing = target
            flash.snapTo(1f)
            flash.animateTo(0f, tween(FOCUS_FLASH_MS))
            flashing = null
        }
        onFocusShown()
    }
    Box(modifier) {
        LazyColumn(
            state = listState,
            reverseLayout = true,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp + topPadding, bottom = bottomPadding),
            verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.Bottom),
        ) {
            items(messages.asReversed(), key = { it.id }) { item ->
                val keepingPlace = remember(anchor, item.id) { anchor.keepingPlace(item.id) }
                val placed = Modifier
                    .animateItem(placementSpec = null)
                    .then(anchor.shifted)
                    .then(if (item.id == newestId) keepingPlace else Modifier)
                    .then(
                        if (item.id == flashing) {
                            Modifier.drawBehind {
                                drawRoundRect(flashColor.copy(alpha = flash.value), cornerRadius = CornerRadius(FOCUS_CORNER.toPx()))
                            }
                        } else {
                            Modifier
                        },
                    )
                when (item) {
                    is ChatItem.User -> UserMessage(
                        item,
                        placed.sentEntrance(sendEntrance, item.id == newestUserId, item.id),
                        onEdit = onEdit?.let { edit -> { edit(item) } },
                        onFork = onFork?.let { fork -> { fork(item) } },
                    )
                    is ChatItem.Reply -> AssistantReply(
                        item,
                        showUsage = showUsage,
                        typing = typing,
                        isSpeaking = item.id == speakingId,
                        onRegenerate = { onRegenerate(item) },
                        onToggleSpeech = { onToggleSpeech(item) },
                        onSend = onSend,
                        onApproval = onApproval,
                        modifier = placed,
                        avatarHandoff = avatarHandoff.takeIf { item.id == handoffTargetId },
                        onFork = onFork?.let { fork -> { fork(item) } },
                    )
                    is ChatItem.Compacted -> CompactedDivider(item, placed)
                }
            }
        }
        ScrollToBottomButton(
            visible = !atBottom,
            onClick = { scope.launch { listState.animateScrollToItem(0) } },
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = scrollButtonPadding + SCROLL_BUTTON_MARGIN),
        )
    }
}

@Composable
fun EmptyConversation(
    modifier: Modifier = Modifier,
    avatarSize: Dp = 96.dp,
    style: TextStyle = MaterialTheme.typography.displaySmallEmphasized,
    interactive: Boolean = false,
    typing: TypingAttention? = null,
    listeningLevel: Float? = null,
    knockout: KnockoutStage? = null,
    handoff: AvatarHandoff? = null,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        AgentAvatar(
            isWorking = false,
            size = avatarSize,
            modifier = handoff?.avatarModifier() ?: Modifier,
            interactive = interactive,
            typing = typing,
            listeningLevel = listeningLevel,
            knockout = knockout,
        )
        Text(
            stringResource(R.string.chat_empty_greeting),
            style = style,
            modifier = Modifier
                .knockoutObstacle(knockout, GREETING_OBSTACLE)
                .graphicsLayer { alpha = handoff?.greetingAlpha ?: 1f },
        )
    }
}

private fun DictationControls.listeningLevel(): Float? =
    (state as? DictationState.Recording)?.let { it.levels.lastOrNull() ?: 0f }

private const val FOCUS_FLASH_MS = 1_600
private val FOCUS_CORNER = 20.dp
private val SCROLL_BUTTON_MARGIN = 12.dp
private val COMPOSER_GAP = 8.dp

private const val GREETING_OBSTACLE = "greeting"
private const val COMPOSER_OBSTACLE = "composer"
private val TOP_FADE_EXTRA = 24.dp
// The fade is solid down to just under the bar's buttons, so nothing shows through behind them.
private val TOP_FADE_SOLID_GAP = 16.dp


private val ComposerClearance = 104.dp

/** Files dragged from a file manager onto the chat are attached like picked ones. */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
private class FileDropTarget(private val onFiles: (List<java.io.File>) -> Unit) : androidx.compose.ui.draganddrop.DragAndDropTarget {
    override fun onDrop(event: androidx.compose.ui.draganddrop.DragAndDropEvent): Boolean {
        val data = event.dragData() as? androidx.compose.ui.draganddrop.DragData.FilesList ?: return false
        val files = dev.huecoder.md3agent.system.clipboard.ClipboardParser.uriList(data.readFiles().joinToString("\n"))
        if (files.isEmpty()) return false
        onFiles(files)
        return true
    }
}
