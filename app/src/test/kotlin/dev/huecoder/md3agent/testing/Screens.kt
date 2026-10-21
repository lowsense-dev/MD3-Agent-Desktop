package dev.huecoder.md3agent.testing

import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.io.File

/** Renders [content] off-screen and saves it as build/screens/<name>.png, so screens can be checked without a window. */
fun renderScreen(name: String, width: Int, height: Int, density: Float = 1.5f, frames: Int = 3, content: @Composable () -> Unit): Image =
    // Lifecycle and navigation insist on the main (Swing) thread, as they would in the app window.
    runBlocking(Dispatchers.Main) { renderOnMain(name, width, height, density, frames, content) }

private suspend fun renderOnMain(name: String, width: Int, height: Int, density: Float, frames: Int, content: @Composable () -> Unit): Image {
    val scene = ImageComposeScene(width, height, Density(density), coroutineContext = Dispatchers.Main, content = content)
    try {
        var image = scene.render(0)
        // Frames spaced in real time too, so database flows and LaunchedEffects get to deliver before the last frame.
        for (frame in 1 until frames) {
            delay(FRAME_PAUSE_MS)
            image = scene.render(frame * 16_000_000L)
        }
        val dir = File(System.getProperty("screens.dir") ?: "build/screens").apply { mkdirs() }
        File(dir, "$name.png").writeBytes(image.encodeToData(EncodedImageFormat.PNG)!!.bytes)
        return image
    } finally {
        scene.close()
    }
}

private const val FRAME_PAUSE_MS = 40L
