package dev.huecoder.md3agent

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import dev.huecoder.md3agent.testing.renderScreen
import dev.huecoder.md3agent.ui.res.painterResource
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** Renders the launcher icon (the phone app's adaptive icon) at the sizes Linux desktops look for. */
class IconExportTest {
    @Test
    fun rendersLauncherIcons() {
        listOf(48, 128, 256, 512).forEach { size ->
            renderScreen("md3-agent-$size", size, size, density = 1f) {
                val side = with(LocalDensity.current) { size.toDp() }
                Box(Modifier.fillMaxSize().clip(RoundedCornerShape(22)).background(Color(0xFF3C4048)), contentAlignment = Alignment.Center) {
                    // The adaptive-icon foreground has a 1.5× bleed around the visible logo.
                    Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, modifier = Modifier.requiredSize(side * 1.5f))
                }
            }
            assertTrue(File(System.getProperty("screens.dir"), "md3-agent-$size.png").length() > 0)
        }
    }
}

class DrawablesLoadTest {
    /** Every bundled vector must load on the desktop: no @color/ or ?attr references, which only Android resolves. */
    @Test
    fun everyDrawableParses() {
        val density = androidx.compose.ui.unit.Density(1f)
        R.drawableNames.keys.forEach { id -> dev.huecoder.md3agent.ui.res.Drawables.vector(id, density) }
    }
}
