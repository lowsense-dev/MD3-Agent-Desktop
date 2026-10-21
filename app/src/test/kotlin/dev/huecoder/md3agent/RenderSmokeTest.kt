package dev.huecoder.md3agent

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.toShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.huecoder.md3agent.testing.renderScreen
import org.jetbrains.skia.Bitmap
import kotlin.test.Test
import kotlin.test.assertTrue

class RenderSmokeTest {
    @Test
    fun rendersExpressiveComponents() {
        val image = renderScreen("smoke", 400, 300) {
            MaterialExpressiveTheme {
                Surface(color = MaterialTheme.colorScheme.surface) {
                    Column {
                        LoadingIndicator()
                        Surface(shape = MaterialShapes.Cookie9Sided.toShape(), color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(96.dp)) {}
                    }
                }
            }
        }
        val bitmap = Bitmap.makeFromImage(image)
        val colors = (0 until 400 step 8).flatMap { x -> (0 until 300 step 8).map { y -> bitmap.getColor(x, y) } }.toSet()
        assertTrue(colors.size > 2, "rendered image is blank")
    }
}
