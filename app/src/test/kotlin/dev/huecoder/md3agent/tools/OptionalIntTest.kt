package dev.huecoder.md3agent.tools

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OptionalIntTest {
    private fun args(raw: String) = Json.parseToJsonElement(raw).jsonObject

    @Test
    fun `integral numbers in any shape are read`() {
        assertEquals(16000, args("""{"offset":16000}""").optionalInt("offset"))
        assertEquals(16000, args("""{"offset":16000.0}""").optionalInt("offset"))
        assertEquals(16000, args("""{"offset":"16000"}""").optionalInt("offset"))
    }

    @Test
    fun `fractions, words and missing values are not integers`() {
        assertNull(args("""{"offset":1.5}""").optionalInt("offset"))
        assertNull(args("""{"offset":"soon"}""").optionalInt("offset"))
        assertNull(args("""{}""").optionalInt("offset"))
    }
}
