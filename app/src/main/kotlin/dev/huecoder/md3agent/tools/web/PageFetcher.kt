package dev.huecoder.md3agent.tools.web

import dev.huecoder.md3agent.data.settings.FetchBackend
import dev.huecoder.md3agent.data.settings.FetchSettings
import dev.huecoder.md3agent.data.settings.FirecrawlSettings
import dev.huecoder.md3agent.net.HttpClientProvider
import dev.huecoder.md3agent.net.fetchText
import dev.huecoder.md3agent.tools.ToolArgumentException
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import java.io.IOException
import java.net.Inet6Address
import java.net.InetAddress

data class FetchedPage(val title: String?, val text: String)

class PageFetcher(private val httpClients: HttpClientProvider) {
    private val firecrawl = Firecrawl(httpClients)

    suspend fun fetch(url: HttpUrl, settings: FetchSettings, firecrawlSettings: FirecrawlSettings = FirecrawlSettings()): FetchedPage = when (settings.backend) {
        FetchBackend.DIRECT -> direct(url)
        FetchBackend.JINA_READER -> throughReader(FetchSettings.JINA_READER_URL, settings.jinaApiKey, url)
        FetchBackend.CUSTOM -> throughReader(settings.customUrl, settings.customApiKey, url)
        FetchBackend.FIRECRAWL -> firecrawl.scrape(url, firecrawlSettings)
    }

    private suspend fun direct(url: HttpUrl): FetchedPage {
        val request = Request.Builder().url(url).header("Accept", "text/html,text/plain;q=0.9,*/*;q=0.5").build()
        // checked per connection so redirects and every resolved address are covered
        val client = httpClients.current().newBuilder().addNetworkInterceptor { chain ->
            val address = chain.connection()?.route()?.socketAddress?.address
            if (address != null && isLocalAddress(address)) throw LocalAddressException(chain.request().url.host, address)
            chain.proceed(chain.request())
        }.build()
        return try {
            pageOf(client.fetchText(request))
        } catch (e: LocalAddressException) {
            throw ToolArgumentException(e.message.orEmpty())
        }
    }

    private suspend fun throughReader(template: String, apiKey: String, url: HttpUrl): FetchedPage {
        val readerUrl = readerAddress(template, url)
        val request = Request.Builder().url(readerUrl)
            .apply { apiKey.trim().takeIf { it.isNotEmpty() }?.let { header("Authorization", "Bearer $it") } }
            .build()
        return pageOf(httpClients.current().fetchText(request))
    }

    private fun readerAddress(template: String, url: HttpUrl): HttpUrl {
        val trimmed = template.trim()
        if (trimmed.isEmpty()) throw ToolArgumentException("Page reader address is not set in tool settings")
        val filled = if (FetchSettings.URL_PLACEHOLDER in trimmed) {
            trimmed.replace(FetchSettings.URL_PLACEHOLDER, url.toString())
        } else {
            trimmed + url.toString()
        }
        return filled.toHttpUrlOrNull() ?: throw ToolArgumentException("Page reader address is not a valid URL: \"$trimmed\"")
    }

    private fun pageOf(body: String): FetchedPage =
        if (looksLikeHtml(body)) FetchedPage(HtmlText.title(body), HtmlText.document(body)) else FetchedPage(null, body.trim())

    private fun looksLikeHtml(body: String): Boolean {
        val head = body.trimStart().take(HTML_SNIFF_CHARS).lowercase()
        return head.startsWith("<!doctype html") || head.startsWith("<html") || "<body" in head
    }

    private class LocalAddressException(host: String, address: InetAddress) :
        IOException("$host resolves to the local address ${address.hostAddress}, direct page reading only opens public addresses")

    companion object {
        private const val HTML_SNIFF_CHARS = 1_024
        private const val UNIQUE_LOCAL_MASK = 0xfe
        private const val UNIQUE_LOCAL_PREFIX = 0xfc

        fun isLocalAddress(address: InetAddress): Boolean =
            address.isLoopbackAddress || address.isLinkLocalAddress || address.isSiteLocalAddress || address.isAnyLocalAddress ||
                (address is Inet6Address && (address.address[0].toInt() and UNIQUE_LOCAL_MASK) == UNIQUE_LOCAL_PREFIX)
    }
}
