package dev.huecoder.md3agent.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.huecoder.md3agent.data.settings.AgentShape
import dev.huecoder.md3agent.testing.renderScreen
import dev.huecoder.md3agent.ui.avatar.BotAvatar
import dev.huecoder.md3agent.ui.math.FormulaDrawables
import dev.huecoder.md3agent.ui.math.MathFormula
import dev.huecoder.md3agent.ui.theme.AssistantTheme
import org.jetbrains.skia.Bitmap
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FoundationRenderTest {
    @Test
    fun rendersAvatarAndTypeInBothThemes() {
        listOf(false, true).forEach { dark ->
            val image = renderScreen("foundation-${if (dark) "dark" else "light"}", 520, 260) {
                AssistantTheme(darkTheme = dark) {
                    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            BotAvatar(isWorking = false, restShape = AgentShape.COOKIE_9, size = 72.dp)
                            BotAvatar(isWorking = true, restShape = AgentShape.CIRCLE, size = 72.dp)
                        }
                        Text("Чем помочь? Google Sans", style = MaterialTheme.typography.headlineMediumEmphasized, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
            val bitmap = Bitmap.makeFromImage(image)
            assertTrue((0 until 520 step 10).map { bitmap.getColor(it, 60) }.toSet().size > 2)
        }
    }

    @Test
    fun formulasRenderIncludingCyrillicText() {
        val image = FormulaDrawables.of(MathFormula("\\int_0^1 x^2\\,dx = \\frac{1}{3}\\quad \\text{площадь}", isDisplay = true), 32f, 0xFF000000.toInt())
        assertNotNull(image)
        assertTrue(image.intrinsicWidth > 100 && image.intrinsicHeight > 20)
    }
}
