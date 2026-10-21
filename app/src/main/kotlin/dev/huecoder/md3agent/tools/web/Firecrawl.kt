package dev.huecoder.md3agent.tools.web

import dev.huecoder.md3agent.data.settings.FirecrawlSettings
import dev.huecoder.md3agent.net.HttpClientProvider
import dev.huecoder.md3agent.net.fetchText
import dev.huecoder.md3agent.tools.ToolArgumentException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class Firecrawl(private val httpClients: HttpClientProvider) {
    suspend fun search(query: String, limit: Int, settings: FirecrawlSettings): List<SearchHit> =
        parseSearch(post(settings, "search", buildJsonObject { put("query", query); put("limit", limit) }))

    suspend fun scrape(url: HttpUrl, settings: FirecrawlSettings): FetchedPage = parseScrape(
        post(
            settings,
            "scrape",
            buildJsonObject {
                put("url", url.toString())
                putJsonArray("formats") { add(JsonPrimitive("markdown")) }
                put("onlyMainContent", true)
            },
        ),
    )

    private suspend fun post(settings: FirecrawlSettings, path: String, body: JsonObject): String {
        val url = endpoint(settings.baseUrl, path)
            ?: throw ToolArgumentException("Firecrawl address is not a valid URL in tool settings: \"${settings.baseUrl}\"")
        val request = Request.Builder().url(url)
            .header("Accept", "application/json")
            .apply { settings.apiKey.trim().takeIf { it.isNotEmpty() }?.let { header("Authorization", "Bearer $it") } }
            .post(body.toString().toRequestBody(JSON_TYPE))
            .build()
        return httpClients.current().fetchText(request)
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }
        private val JSON_TYPE = "application/json".toMediaType()
        private val API_VERSIONS = setOf("v1", "v2")

        fun endpoint(base: String, path: String): HttpUrl? {
            val url = base.trim().toHttpUrlOrNull() ?: return null
            val segments = url.pathSegments.filter { it.isNotEmpty() }.let { if (it.lastOrNull() in API_VERSIONS) it.dropLast(1) else it }
            return url.newBuilder().query(null).fragment(null).encodedPath("/")
                .apply { segments.forEach { addPathSegment(it) } }
                .addPathSegment("v2").addPathSegment(path)
                .build()
        }

        fun parseSearch(body: String): List<SearchHit> {
            val data = successData(body)
            val hits = (data as? JsonArray) ?: ((data as? JsonObject)?.get("web") as? JsonArray) ?: return emptyList()
            return hits.mapNotNull { hit ->
                val item = hit as? JsonObject ?: return@mapNotNull null
                val url = item.text("url")?.takeIf { it.startsWith("http") } ?: return@mapNotNull null
                SearchHit(item.text("title").orEmpty().ifEmpty { url }, url, item.text("description").orEmpty())
            }
        }

        fun parseScrape(body: String): FetchedPage {
            val data = successData(body) as? JsonObject ?: throw IllegalStateException("Firecrawl returned no page")
            val title = (data["metadata"] as? JsonObject)?.text("title")?.takeIf { it.isNotBlank() }
            return FetchedPage(title, data.text("markdown").orEmpty().trim())
        }

        private fun successData(body: String): JsonElement? {
            val root = json.parseToJsonElement(body).jsonObject
            if ((root["success"] as? JsonPrimitive)?.content == "false") {
                throw IllegalStateException("Firecrawl error: ${root.text("error") ?: "request failed"}")
            }
            return root["data"]
        }

        private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
    }
}
