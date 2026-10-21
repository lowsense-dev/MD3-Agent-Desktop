package dev.huecoder.md3agent.testing

import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import java.io.File

/** Renders [content] off-screen and saves it as build/screens/<name>.png, so screens can be checked without a window. */
fun renderScreen(name: String, width: Int, height: Int, density: Float = 1.5f, frames: Int = 3, content: @Composable () -> Unit): Image {
    val scene = ImageComposeScene(width, height, Density(density), content = content)
    try {
        var image = scene.render(0)
        // A few frames so first-frame effects (LaunchedEffect, animations at t=0) settle.
        for (frame in 1 until frames) image = scene.render(frame * 16_000_000L)
        val dir = File(System.getProperty("screens.dir") ?: "build/screens").apply { mkdirs() }
        File(dir, "$name.png").writeBytes(image.encodeToData(EncodedImageFormat.PNG)!!.bytes)
        return image
    } finally {
        scene.close()
    }
}
