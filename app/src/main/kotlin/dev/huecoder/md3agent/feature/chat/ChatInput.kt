package dev.huecoder.md3agent.feature.chat

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import dev.huecoder.md3agent.system.clipboard.ClipboardContent
import kotlinx.coroutines.launch
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import dev.huecoder.md3agent.ui.text.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Arrow_upward
import com.composables.icons.materialsymbols.rounded.Attach_file
import com.composables.icons.materialsymbols.rounded.Check
import com.composables.icons.materialsymbols.rounded.Close
import com.composables.icons.materialsymbols.rounded.Mic
import com.composables.icons.materialsymbols.rounded.Folder_open
import com.composables.icons.materialsymbols.rounded.Image
import com.composables.icons.materialsymbols.roundedfilled.Stop
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.data.attachments.Attachment
import dev.huecoder.md3agent.ui.haptics.rememberHaptics
import dev.huecoder.md3agent.voice.DictationState
import dev.huecoder.md3agent.ui.components.ShapeBadge
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.foundation.layout.Arrangement

private val InlineShape = RoundedCornerShape(percent = 50)
private val StackedShape = RoundedCornerShape(28.dp)
private const val MAX_VISIBLE_LINES = 8
private const val SENT_FADE_MS = 160

@Composable
fun ChatInput(
    text: String,
    onTextChange: (String) -> Unit,
    isStreaming: Boolean,
    canSend: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
    attachments: List<Attachment> = emptyList(),
    isImporting: Boolean = false,
    onAttach: ((AttachSource) -> Unit)? = null,
    onRemoveAttachment: (Attachment) -> Unit = {},
    onPasteFiles: ((List<java.io.File>) -> Unit)? = null,
    onPasteImage: ((ByteArray, String) -> Unit)? = null,
    dictation: DictationControls? = null,
    focusRequester: FocusRequester = remember { FocusRequester() },
    onLetterTyped: (Offset) -> Unit = {},
    steerable: Boolean = false,
) {
    val haptics = rememberHaptics()
    val dictationState = dictation?.state ?: DictationState.Idle
    val isDictating = dictationState != DictationState.Idle
    val steering = steerable && isStreaming && canSend && !isDictating
    val action = when {
        isStreaming && !steering -> ComposerAction.STOP
        dictationState is DictationState.Recording -> ComposerAction.FINISH_DICTATION
        canSend || dictation == null || dictationState == DictationState.Transcribing -> ComposerAction.SEND
        else -> ComposerAction.DICTATE
    }
    var wrappedAt by remember { mutableStateOf<Int?>(null) }
    val settledAt = ComposerStacking.settle(wrappedAt, text)
    if (settledAt != wrappedAt) wrappedAt = settledAt
    val stacked = ComposerStacking.isStacked(settledAt, text) && !isDictating
    var sentText by remember { mutableStateOf<String?>(null) }
    val hasLeading = if (isDictating) dictation?.onCancel != null else onAttach != null

    val leadingButton = @Composable {
        if (isDictating) dictation?.onCancel?.let { CancelDictationButton(it) } else onAttach?.let { AttachMenu(it) }
    }
    val actionButton = @Composable {
        if (steering) {
            StopButton(onClick = { haptics.confirm(); onStop() })
            Spacer(Modifier.width(SIDE_BUTTON_GAP))
        }
        ActionButton(
            action = action,
            enabled = action != ComposerAction.SEND || (canSend && !isDictating),
            onClick = {
                haptics.confirm()
                when (action) {
                    ComposerAction.STOP -> onStop()
                    ComposerAction.SEND -> {
                        sentText = text.ifBlank { null }
                        onSend()
                    }
                    ComposerAction.DICTATE -> dictation?.onStart?.invoke()
                    ComposerAction.FINISH_DICTATION -> dictation?.onFinish?.invoke()
                }
            },
        )
    }
    val field = @Composable { fieldModifier: Modifier ->
        if (isDictating && dictation != null) {
            DictationStrip(dictationState, onDismissError = dictation.onDismissError, modifier = fieldModifier)
        } else {
            Box(fieldModifier) {
                MessageField(
                    text = text,
                    onTextChange = onTextChange,
                    focusRequester = focusRequester,
                    onLineCount = { lines, laidOut -> wrappedAt = ComposerStacking.wrappedAfter(wrappedAt, laidOut, lines) },
                    onLetterTyped = onLetterTyped,
                    onPasteFiles = onPasteFiles,
                    onPasteImage = onPasteImage,
                    onEnter = { if (canSend && action == ComposerAction.SEND) { onSend(); true } else false },
                    modifier = Modifier.fillMaxWidth(),
                )
                sentText?.let { SentTextFade(it, onFaded = { sentText = null }) }
            }
        }
    }

    Surface(
        shape = if (stacked || attachments.isNotEmpty() || isImporting) StackedShape else InlineShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(COMPOSER_INSET).animateContentSize()) {
            if (attachments.isNotEmpty() || isImporting) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 6.dp, top = 6.dp, end = 6.dp, bottom = 4.dp)) {
                    AttachmentStrip(attachments, onRemove = onRemoveAttachment, modifier = Modifier.weight(1f, fill = false))
                    if (isImporting) CircularProgressIndicator(Modifier.padding(start = 8.dp).size(20.dp), strokeWidth = 2.dp)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!stacked) leadingButton()
                field(Modifier.weight(1f).padding(start = if (stacked || !hasLeading) FIELD_SIDE_INSET else 0.dp, end = if (stacked) FIELD_SIDE_INSET else 4.dp))
                if (!stacked) actionButton()
            }
            if (stacked) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    leadingButton()
                    Spacer(Modifier.weight(1f))
                    actionButton()
                }
            }
        }
    }
}

@Composable
private fun BoxScope.SentTextFade(text: String, onFaded: () -> Unit) {
    val alpha = remember(text) { Animatable(1f) }
    val colors = MaterialTheme.colorScheme
    LaunchedEffect(text) {
        alpha.animateTo(0f, tween(SENT_FADE_MS))
        onFaded()
    }
    Box(
        Modifier
            .matchParentSize()
            .graphicsLayer { this.alpha = alpha.value }
            .background(colors.surfaceContainerHigh),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(vertical = FIELD_VERTICAL_INSET),
        )
    }
}

@Composable
private fun StopButton(onClick: () -> Unit) {
    FilledTonalIconButton(onClick = onClick, shape = CircleShape, modifier = Modifier.size(ACTION_SIZE)) {
        Icon(MaterialSymbols.RoundedFilled.Stop, contentDescription = stringResource(R.string.chat_stop))
    }
}

@Composable
private fun CancelDictationButton(onClick: () -> Unit) {
    IconButton(onClick = onClick, shapes = IconButtonDefaults.shapes()) {
        Icon(MaterialSymbols.Rounded.Close, contentDescription = stringResource(R.string.dictation_cancel))
    }
}

enum class AttachSource { PHOTOS, FILES }

private class AttachItem(val source: AttachSource, val icon: ImageVector, val label: Int, val hint: Int)

private val ATTACH_ITEMS = listOf(
    AttachItem(AttachSource.PHOTOS, MaterialSymbols.Rounded.Image, R.string.chat_attach_photos, R.string.chat_attach_photos_hint),
    AttachItem(AttachSource.FILES, MaterialSymbols.Rounded.Folder_open, R.string.chat_attach_files, R.string.chat_attach_files_hint),
)

/** Expressive menu group: one rounded container, items with large outer and small inner corners, icons in shapes. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AttachMenu(onPick: (AttachSource) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val badges = listOf(colors.primaryContainer to colors.onPrimaryContainer, colors.tertiaryContainer to colors.onTertiaryContainer, colors.secondaryContainer to colors.onSecondaryContainer)
    Box {
        IconButton(onClick = { open = true }, shapes = IconButtonDefaults.shapes()) {
            Icon(MaterialSymbols.Rounded.Attach_file, contentDescription = stringResource(R.string.chat_attach_file))
        }
        DropdownMenuPopup(expanded = open, onDismissRequest = { open = false }) {
            Surface(shape = RoundedCornerShape(MENU_OUTER_CORNER), color = colors.surfaceContainerHigh, shadowElevation = 6.dp, modifier = Modifier.width(MENU_WIDTH)) {
                Column(Modifier.padding(MENU_PADDING), verticalArrangement = Arrangement.spacedBy(MENU_GAP)) {
                    ATTACH_ITEMS.forEachIndexed { index, item ->
                        val (container, content) = badges[index % badges.size]
                        Surface(
                            onClick = { open = false; onPick(item.source) },
                            shape = itemShape(index, ATTACH_ITEMS.size),
                            color = colors.surfaceContainerLowest,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                ShapeBadge(item.icon, size = 40.dp, containerColor = container, contentColor = content)
                                Column(Modifier.padding(start = 14.dp)) {
                                    Text(stringResource(item.label), style = MaterialTheme.typography.titleMediumEmphasized)
                                    Text(stringResource(item.hint), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun itemShape(index: Int, count: Int): RoundedCornerShape {
    val top = if (index == 0) MENU_ITEM_OUTER else MENU_ITEM_INNER
    val bottom = if (index == count - 1) MENU_ITEM_OUTER else MENU_ITEM_INNER
    return RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
}

private val MENU_WIDTH = 264.dp
private val MENU_OUTER_CORNER = 28.dp
private val MENU_PADDING = 6.dp
private val MENU_GAP = 2.dp
private val MENU_ITEM_OUTER = 22.dp
private val MENU_ITEM_INNER = 6.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageField(
    text: String,
    onTextChange: (String) -> Unit,
    focusRequester: FocusRequester,
    onLineCount: (lineCount: Int, laidOutText: String) -> Unit,
    onLetterTyped: (Offset) -> Unit,
    onPasteFiles: ((List<java.io.File>) -> Unit)?,
    onPasteImage: ((ByteArray, String) -> Unit)?,
    onEnter: () -> Boolean,
    modifier: Modifier,
) {
    val clipboard = dev.huecoder.md3agent.LocalAppContainer.current.clipboard
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val colors = MaterialTheme.colorScheme
    val style = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface)
    val caret = remember { CaretTracker() }
    val field = rememberTextFieldState(text)
    val latestText by rememberUpdatedState(text)
    val latestOnTextChange by rememberUpdatedState(onTextChange)
    val reportEdits = remember {
        InputTransformation {
            val edited = asCharSequence().toString()
            if (!originalText.contentEquals(edited)) {
                caret.onEdit(originalText.toString(), edited)
                latestOnTextChange(edited)
            }
        }
    }
    LaunchedEffect(text) {
        if (field.text.toString() != latestText) field.setTextAndPlaceCursorAtEnd(latestText)
    }
    BasicTextField(
        state = field,
        inputTransformation = reportEdits,
        textStyle = style,
        lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = MAX_VISIBLE_LINES),
        cursorBrush = SolidColor(colors.primary),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        onTextLayout = { layout ->
            layout()?.let { result ->
                onLineCount(result.lineCount, result.layoutInput.text.text)
                caret.onLayout(result)?.let(onLetterTyped)
            }
        },
        modifier = modifier
            .heightIn(min = FIELD_MIN_HEIGHT)
            .focusRequester(focusRequester)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when {
                    // Enter sends like a chat app; Shift+Enter falls through to the field and adds a line.
                    event.key == Key.Enter && !event.isShiftPressed && !event.isCtrlPressed && !event.isAltPressed -> onEnter()
                    // Ctrl+V reads the clipboard itself, so copied files and pictures attach instead of pasting their paths.
                    event.key == Key.V && event.isCtrlPressed && !event.isAltPressed && onPasteFiles != null -> {
                        scope.launch {
                            when (val pasted = clipboard.read()) {
                                is ClipboardContent.Files -> onPasteFiles(pasted.files)
                                is ClipboardContent.Image -> onPasteImage?.invoke(pasted.bytes, pasted.mimeType)
                                is ClipboardContent.Text -> {
                                    field.edit { replace(selection.min, selection.max, pasted.text) }
                                    latestOnTextChange(field.text.toString())
                                }
                                null -> Unit
                            }
                        }
                        true
                    }
                    else -> false
                }
            },
        decorator = { inner ->
            Box(contentAlignment = Alignment.CenterStart, modifier = Modifier.padding(vertical = FIELD_VERTICAL_INSET)) {
                if (field.text.isEmpty()) Text(stringResource(R.string.chat_input_placeholder), style = style, color = colors.onSurfaceVariant)
                Box(Modifier.onGloballyPositioned { caret.textCoordinates = it }) { inner() }
            }
        },
    )
}

private class CaretTracker {
    var textCoordinates: LayoutCoordinates? = null
    private var pendingIndex: Int? = null

    fun onEdit(before: String, after: String) {
        val prefix = before.commonPrefixWith(after).length
        val suffix = before.substring(prefix).commonSuffixWith(after.substring(prefix)).length
        pendingIndex = after.length - suffix
    }

    fun onLayout(layout: TextLayoutResult): Offset? {
        val index = pendingIndex ?: return null
        pendingIndex = null
        val coordinates = textCoordinates?.takeIf { it.isAttached } ?: return null
        val cursor = layout.getCursorRect(index.coerceIn(0, layout.layoutInput.text.length))
        return coordinates.localToRoot(cursor.center)
    }
}

private val COMPOSER_INSET = 6.dp
private val FIELD_SIDE_INSET = 12.dp
private val FIELD_VERTICAL_INSET = 12.dp
private val FIELD_MIN_HEIGHT = 48.dp
private val ACTION_SIZE = 48.dp
private val SIDE_BUTTON_GAP = 6.dp

private enum class ComposerAction(val icon: ImageVector, val description: Int) {
    SEND(MaterialSymbols.Rounded.Arrow_upward, R.string.chat_send),
    STOP(MaterialSymbols.RoundedFilled.Stop, R.string.chat_stop),
    DICTATE(MaterialSymbols.Rounded.Mic, R.string.dictation_start),
    FINISH_DICTATION(MaterialSymbols.Rounded.Check, R.string.dictation_finish),
}

@Composable
private fun ActionButton(action: ComposerAction, enabled: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    FilledIconButton(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        colors = IconButtonDefaults.filledIconButtonColors(
            disabledContainerColor = scheme.surfaceContainerHighest,
            disabledContentColor = scheme.onSurfaceVariant,
        ),
        modifier = Modifier.size(ACTION_SIZE),
    ) {
        AnimatedContent(
            targetState = action,
            transitionSpec = { scaleIn() togetherWith scaleOut() },
            label = "composer-action-icon",
        ) { shown ->
            Icon(shown.icon, contentDescription = stringResource(shown.description))
        }
    }
}
