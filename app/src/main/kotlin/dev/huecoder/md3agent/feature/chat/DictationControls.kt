package dev.huecoder.md3agent.feature.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import dev.huecoder.md3agent.platform.LocalPlatformContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.huecoder.md3agent.voice.DictationState
import dev.huecoder.md3agent.voice.DictationViewModel

class DictationControls(
    val state: DictationState,
    val onStart: () -> Unit,
    val onFinish: () -> Unit,
    val onCancel: () -> Unit,
    val onDismissError: () -> Unit,
)

@Composable
fun rememberDictationControls(
    model: DictationViewModel,
    onTranscript: (String) -> Unit,
    @Suppress("UNUSED_PARAMETER") onPermissionMissing: () -> Unit = {},
): DictationControls {
    val state by model.state.collectAsStateWithLifecycle()
    val deliver by rememberUpdatedState(onTranscript)
    LaunchedEffect(model) { model.transcripts.collect { deliver(it) } }
    return DictationControls(
        state = state,
        onStart = {
            // Desktop apps need no runtime permission for the microphone.
            model.start()
        },
        onFinish = model::finish,
        onCancel = model::cancel,
        onDismissError = model::dismissError,
    )
}

fun appendDictated(current: String, dictated: String): String =
    if (current.isBlank()) dictated else current.trimEnd() + " " + dictated
