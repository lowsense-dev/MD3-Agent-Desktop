package dev.huecoder.md3agent.feature.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class TypingRevealTest {
    private val text = "a MATHSLOT0X b MATHSLOT12X"

    @Test
    fun `a cut inside a math slot moves to its end`() {
        assertEquals(12, outsideMathSlot(text, 5))
        assertEquals(26, outsideMathSlot(text, 20))
    }

    @Test
    fun `a cut outside or at the edge of a slot stays`() {
        assertEquals(2, outsideMathSlot(text, 2))
        assertEquals(12, outsideMathSlot(text, 12))
        assertEquals(13, outsideMathSlot(text, 13))
        assertEquals(26, outsideMathSlot(text, 26))
    }

    @Test
    fun `with a fade the cut moves a whole word at a time`() {
        val text = "Hello brave new world"
        assertEquals(5, wordEnd(text, 2))
        assertEquals(6, wordEnd(text, 6))
        assertEquals(text.length, wordEnd(text, text.length))
        assertEquals(11, visibleCut(text, 8, fades = true))
        assertEquals(8, visibleCut(text, 8, fades = false))
    }

    @Test
    fun `a very long token is not waited for`() {
        val link = "see https://" + "x".repeat(100)
        assertEquals(20, wordEnd(link, 20))
    }
}
