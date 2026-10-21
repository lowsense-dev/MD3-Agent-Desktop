package dev.huecoder.md3agent.ui.res

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.loadXmlImageVector
import androidx.compose.ui.unit.Density
import dev.huecoder.md3agent.R
import org.xml.sax.InputSource
import java.util.concurrent.ConcurrentHashMap

/** Android vector drawables from resources/drawable, addressed by the generated R.drawable ids. */
object Drawables {
    private val cache = ConcurrentHashMap<Pair<Int, Float>, ImageVector>()

    fun vector(id: Int, density: Density): ImageVector = cache.getOrPut(id to density.density) {
        val name = R.drawableNames[id]
        val stream = Drawables::class.java.getResourceAsStream("/drawable/$name.xml") ?: error("No drawable $name")
        stream.use { loadXmlImageVector(InputSource(it), density) }
    }
}

@Composable
fun vectorResource(id: Int): ImageVector {
    val density = LocalDensity.current
    return remember(id, density) { Drawables.vector(id, density) }
}

@Composable
fun painterResource(id: Int): Painter = rememberVectorPainter(vectorResource(id))
