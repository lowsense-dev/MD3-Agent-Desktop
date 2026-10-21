package dev.huecoder.md3agent.ui.math

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.scilab.forge.jlatexmath.DefaultTeXFont
import org.scilab.forge.jlatexmath.TeXConstants
import org.scilab.forge.jlatexmath.TeXFormula
import org.scilab.forge.jlatexmath.cyrillic.CyrillicRegistration
import org.scilab.forge.jlatexmath.greek.GreekRegistration
import java.awt.Color
import java.awt.image.BufferedImage

/** A rendered formula: its picture at the requested text size. */
class FormulaImage(val bitmap: ImageBitmap) {
    val intrinsicWidth: Int get() = bitmap.width
    val intrinsicHeight: Int get() = bitmap.height
}

internal object FormulaDrawables {
    private const val MAX_CACHED = 96

    private data class Key(val formula: MathFormula, val sizePx: Float, val color: Int)

    private class Entry(val image: FormulaImage?)

    private val cache = object : LinkedHashMap<Key, Entry>(MAX_CACHED, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, Entry>?) = size > MAX_CACHED
    }

    init {
        // Cyrillic and Greek inside \text{} need their font sets registered once.
        runCatching {
            DefaultTeXFont.registerAlphabet(CyrillicRegistration())
            DefaultTeXFont.registerAlphabet(GreekRegistration())
        }
    }

    fun of(formula: MathFormula, sizePx: Float, color: Int): FormulaImage? = synchronized(cache) {
        val key = Key(formula, sizePx, color)
        (cache[key] ?: Entry(build(formula, sizePx, color)).also { cache[key] = it }).image
    }

    private fun build(formula: MathFormula, sizePx: Float, color: Int): FormulaImage? = try {
        val style = if (formula.isDisplay) TeXConstants.STYLE_DISPLAY else TeXConstants.STYLE_TEXT
        val icon = TeXFormula(formula.latex).createTeXIcon(style, sizePx)
        val image = BufferedImage(icon.iconWidth.coerceAtLeast(1), icon.iconHeight.coerceAtLeast(1), BufferedImage.TYPE_INT_ARGB)
        icon.setForeground(Color(color, true))
        val graphics = image.createGraphics()
        try {
            graphics.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON)
            graphics.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING, java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            icon.paintIcon(null, graphics, 0, 0)
        } finally {
            graphics.dispose()
        }
        FormulaImage(image.toComposeImageBitmap())
    } catch (e: Exception) {
        null
    }
}
