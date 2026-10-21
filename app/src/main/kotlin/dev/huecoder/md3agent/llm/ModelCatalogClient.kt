package dev.huecoder.md3agent.llm

import dev.huecoder.md3agent.data.settings.ApiFormat
import dev.huecoder.md3agent.data.settings.ProviderProfile
import dev.huecoder.md3agent.llm.models.ModelInfo
import dev.huecoder.md3agent.llm.models.ModelInfoParser
import dev.huecoder.md3agent.net.HttpClientProvider
import dev.huecoder.md3agent.net.bodyText
import dev.huecoder.md3agent.net.executeCancellable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.IOException

class ModelCatalogClient(private val httpClients: HttpClientProvider) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun listModels(provider: ProviderProfile): List<String> = listModelInfo(provider).map { it.id }

    suspend fun listModelInfo(provider: ProviderProfile): List<ModelInfo> = withContext(Dispatchers.IO) {
        KeyRotation.withFailover(provider) { fetch(it) }
    }

    private suspend fun fetch(provider: ProviderProfile): List<ModelInfo> {
        val endpoint = ProviderEndpoint(provider)
        val url = endpoint.url(MODELS_PATH).newBuilder().apply {
            when (provider.format) {
                ApiFormat.OPENAI, ApiFormat.ELEVENLABS -> Unit
                ApiFormat.ANTHROPIC -> addQueryParameter("limit", PAGE_SIZE)
                ApiFormat.GEMINI -> addQueryParameter("pageSize", PAGE_SIZE)
            }
        }.build()
        val request = endpoint.authorizedRequest(url).get().build()
        val call = httpClients.current().newCall(request)
        return try {
            call.executeCancellable { response ->
                val body = response.bodyText(MAX_BODY_BYTES)
                if (!response.isSuccessful) {
                    throw ProviderException.Http(response.code, "HTTP ${response.code}: ${body.take(ERROR_PREVIEW_CHARS)}")
                }
                ModelInfoParser.parseList(json.parseToJsonElement(body))
            }
        } catch (e: IOException) {
            if (call.isCanceled()) throw CancellationException("Model list request cancelled", e)
            throw ProviderException.Network(e)
        } catch (e: SerializationException) {
            throw ProviderException.MalformedResponse(UNKNOWN_FORMAT)
        } catch (e: IllegalArgumentException) {
            throw ProviderException.MalformedResponse(UNKNOWN_FORMAT)
        }
    }

    private companion object {
        const val MODELS_PATH = "models"
        const val PAGE_SIZE = "1000"
        const val ERROR_PREVIEW_CHARS = 300
        // Big aggregators list thousands of models with metadata; a cap still keeps a hostile server from exhausting memory.
        const val MAX_BODY_BYTES = 16L * 1024 * 1024
        const val UNKNOWN_FORMAT = "Server returned the model list in an unknown format"
    }
}
