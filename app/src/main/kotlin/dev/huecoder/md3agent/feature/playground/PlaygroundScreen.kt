package dev.huecoder.md3agent.feature.playground

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import dev.huecoder.md3agent.platform.LocalPlatformContext
import androidx.compose.ui.platform.LocalDensity
import dev.huecoder.md3agent.ui.text.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import dev.huecoder.md3agent.BuildConfig
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.ui.avatar.BotGeometry
import dev.huecoder.md3agent.ui.avatar.toPath
import dev.huecoder.md3agent.ui.haptics.rememberHaptics
import kotlinx.coroutines.delay

@Composable
fun PlaygroundScreen() {
    val density = LocalDensity.current
    val haptics = rememberHaptics()
    val accent = MaterialTheme.colorScheme.primary
    val playground = remember(accent) {
        with(density) {
            Playground(
                pixelsPerMeter = PHYSICS_METER.toPx(),
                palette = PlaygroundPalette(accent),
                pieceSizes = MIN_PIECE.toPx()..MAX_PIECE.toPx(),
                onImpact = haptics::impact,
            )
        }
    }
    var frame by remember { mutableLongStateOf(0L) }
    var showHint by remember { mutableStateOf(true) }

    LaunchedEffect(playground) {
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            playground.step((now - last) / NANOS_PER_SECOND)
            last = now
            frame++
        }
    }
    LaunchedEffect(Unit) {
        delay(HINT_MS)
        showHint = false
    }
    TiltGravity(playground)

    val versionLine = with(density) { MaterialTheme.typography.displayLarge.lineHeight.toDp() }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        Text(
            BuildConfig.VERSION_NAME,
            style = MaterialTheme.typography.displayLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = VERSION_ALPHA),
            modifier = Modifier.align(Alignment.TopCenter).systemBarsPadding().padding(top = VERSION_TOP_PADDING),
        )
        Canvas(
            Modifier
                .fillMaxSize()
                .onSizeChanged { playground.resize(it.toSize()) }
                .pointerInput(playground) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent().changes.forEach { change ->
                                val pointer = change.id.value
                                when {
                                    change.changedToDown() -> if (playground.grab(pointer, change.position)) haptics.tick()
                                    change.changedToUp() -> playground.release(pointer)
                                    change.pressed -> playground.drag(pointer, change.position)
                                }
                                change.consume()
                            }
                        }
                    }
                },
        ) {
            if (frame >= 0) playground.pieces.forEach { piece -> drawPiece(playground, piece) }
        }
        AnimatedVisibility(
            visible = showHint,
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter).systemBarsPadding().padding(top = VERSION_TOP_PADDING + versionLine + HINT_GAP),
        ) {
            Text(
                stringResource(R.string.playground_hint),
                style = MaterialTheme.typography.titleMediumEmphasized,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun DrawScope.drawPiece(playground: Playground, piece: Piece) {
    val origin = playground.origin(piece)
    val degrees = Math.toDegrees(playground.angle(piece).toDouble()).toFloat()
    translate(origin.x, origin.y) {
        rotate(degrees, pivot = Offset.Zero) {
            scale(piece.size, pivot = Offset.Zero) {
                drawPath(piece.shape.outline, piece.colors.body)
                val face = piece.face ?: return@scale
                val eyeRadius = piece.shape.eyeRadius ?: return@scale
                val gaze = BotGeometry.gazeTowards(face.gaze.x, face.gaze.y)
                val (left, right) = BotGeometry.eyes(gaze, face.blink, wobble = 0f, safeRadius = eyeRadius)
                drawPath(left.toPath(), piece.colors.eyes)
                drawPath(right.toPath(), piece.colors.eyes)
            }
        }
    }
}

/** A computer does not tilt: the pieces simply fall down. */
@Composable
private fun TiltGravity(playground: Playground) {
    LaunchedEffect(playground) { playground.pointGravity(Offset(0f, 1f)) }
}

private val PHYSICS_METER = 64.dp
private val MIN_PIECE = 22.dp
private val MAX_PIECE = 140.dp
private val VERSION_TOP_PADDING = 16.dp
private val HINT_GAP = 8.dp
private const val VERSION_ALPHA = 0.08f
private const val NANOS_PER_SECOND = 1_000_000_000f
private const val HINT_MS = 3_500L
