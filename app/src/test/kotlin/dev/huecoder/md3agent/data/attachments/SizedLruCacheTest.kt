package dev.huecoder.md3agent.data.attachments

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SizedLruCacheTest {
    private val cache = SizedLruCache<String, String>(maxWeight = 10) { it.length.toLong() }

    @Test
    fun `the least recently used entries go first once the weight cap is passed`() {
        cache.put("a", "aaaa")
        cache.put("b", "bbbb")
        cache.get("a")
        cache.put("c", "cccc")

        assertNull(cache.get("b"))
        assertEquals("aaaa", cache.get("a"))
        assertEquals("cccc", cache.get("c"))
    }

    @Test
    fun `a value heavier than the cap is not cached`() {
        cache.put("big", "x".repeat(11))
        assertNull(cache.get("big"))
    }
}
