package dev.huecoder.md3agent.tools.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FirecrawlTest {
    @Test
    fun `endpoint accepts bases with or without slash and api version`() {
        listOf("https://api.firecrawl.dev", "https://api.firecrawl.dev/", "https://api.firecrawl.dev/v1", "https://api.firecrawl.dev/v2/").forEach {
            assertEquals(it, "https://api.firecrawl.dev/v2/search", Firecrawl.endpoint(it, "search").toString())
        }
        assertEquals("http://192.168.1.5:3002/fc/v2/scrape", Firecrawl.endpoint(" http://192.168.1.5:3002/fc/ ", "scrape").toString())
        assertNull(Firecrawl.endpoint("not a url", "search"))
    }

    @Test
    fun `search reads v2 and v1 shaped results`() {
        val v2 = """{"success":true,"data":{"web":[{"url":"https://a.org","title":"A","description":"about a"},{"title":"no url"}]}}"""
        val v1 = """{"success":true,"data":[{"url":"https://b.org","description":"b"}]}"""

        assertEquals(listOf(SearchHit("A", "https://a.org", "about a")), Firecrawl.parseSearch(v2))
        assertEquals(listOf(SearchHit("https://b.org", "https://b.org", "b")), Firecrawl.parseSearch(v1))
    }

    @Test
    fun `scrape returns markdown with the page title`() {
        val body = """{"success":true,"data":{"markdown":"# Hi\n\ntext\n","metadata":{"title":"Hi page"}}}"""

        assertEquals(FetchedPage("Hi page", "# Hi\n\ntext"), Firecrawl.parseScrape(body))
    }

    @Test(expected = IllegalStateException::class)
    fun `unsuccessful answer is an error`() {
        Firecrawl.parseSearch("""{"success":false,"error":"Unauthorized"}""")
    }
}
