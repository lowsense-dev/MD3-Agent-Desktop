package dev.huecoder.md3agent.voice

import dev.huecoder.md3agent.data.settings.VoiceProvider
import dev.huecoder.md3agent.data.settings.ApiFormat
import dev.huecoder.md3agent.llm.ProviderEndpoint
import dev.huecoder.md3agent.llm.ProviderException
import dev.huecoder.md3agent.llm.wire.GeminiGenerate
import dev.huecoder.md3agent.net.HttpClientProvider
import dev.huecoder.md3agent.net.fetchToFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.util.Base64
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Clip
import javax.sound.sampled.LineEvent
import kotlin.coroutines.resume

sealed interface PreparedSpeech {
    data class Text(val text: String) : PreparedSpeech

    data class Audio(val file: File) : PreparedSpeech
}

interface SpeechSynthesizer {
    suspend fun prepare(text: String): PreparedSpeech

    suspend fun play(speech: PreparedSpeech)

    fun stop()

    fun release()
}

/** speech-dispatcher's spd-say, when installed; there is no built-in speech engine on Linux. */
class SystemSpeechSynthesizer(private val language: String) : SpeechSynthesizer {
    @Volatile
    private var process: Process? = null

    override suspend fun prepare(text: String): PreparedSpeech = PreparedSpeech.Text(text)

    override suspend fun play(speech: PreparedSpeech) {
        if (speech !is PreparedSpeech.Text) return
        val spdSay = dev.huecoder.md3agent.system.environment.Programs.find("spd-say") ?: throw IOException("No system text-to-speech: install speech-dispatcher or choose a voice provider")
        val command = buildList {
            add(spdSay.path)
            add("--wait")
            if (language.isNotBlank()) addAll(listOf("--language", language.substringBefore('-')))
            add(speech.text)
        }
        withContext(Dispatchers.IO) {
            val started = ProcessBuilder(command).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start()
            process = started
            try {
                kotlinx.coroutines.runInterruptible { started.waitFor() }
            } finally {
                if (started.isAlive) stop()
                process = null
            }
        }
    }

    override fun stop() {
        process?.destroy()
        runCatching { ProcessBuilder("spd-say", "--cancel").start() }
    }

    override fun release() = stop()
}

class ProviderSpeechSynthesizer(
    private val httpClients: HttpClientProvider,
    private val provider: VoiceProvider,
    private val cacheDir: File,
) : SpeechSynthesizer {
    @Volatile
    private var player: Clip? = null

    private val endpoint by lazy { ProviderEndpoint(provider.asEndpointProfile()) }

    override suspend fun prepare(text: String): PreparedSpeech = when (provider.format) {
        ApiFormat.GEMINI -> gemini(text)
        ApiFormat.ELEVENLABS -> elevenLabs(text)
        ApiFormat.OPENAI -> openAi(text)
        ApiFormat.ANTHROPIC -> throw ProviderException.InvalidConfiguration("Anthropic has no speech API")
    }

    private suspend fun openAi(text: String): PreparedSpeech {
        val body = buildJsonObject {
            put("model", provider.speechModel)
            put("voice", provider.speechVoice)
            put("input", text)
            put("response_format", RESPONSE_FORMAT)
        }.toString().toRequestBody(JSON)
        return fetchAudio(endpoint.authorizedRequest(SPEECH_PATH).post(body).build())
    }

    private suspend fun elevenLabs(text: String): PreparedSpeech {
        val url = endpoint.url(ELEVENLABS_SPEECH_PATH).newBuilder().addPathSegment(provider.speechVoice)
            .addQueryParameter("output_format", "pcm_$ELEVENLABS_RATE").build()
        val body = buildJsonObject {
            put("text", text)
            put("model_id", provider.speechModel)
        }.toString().toRequestBody(JSON)
        // Java Sound has no mp3 decoder, so ElevenLabs sends raw PCM, which only needs a WAV header.
        val raw = withTempFile(".pcm") { target -> httpClients.current().fetchToFile(endpoint.authorizedRequest(url).post(body).build(), target) }
        return withTempFile(".wav") { target -> PreparedSpeech.Audio(PcmWav.write(raw.readBytes(), ELEVENLABS_RATE, target)).also { raw.delete() } }
    }

    private suspend fun fetchAudio(request: Request): PreparedSpeech =
        withTempFile(".$RESPONSE_FORMAT") { target -> PreparedSpeech.Audio(httpClients.current().fetchToFile(request, target)) }

    private suspend fun gemini(text: String): PreparedSpeech = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            putJsonArray("contents") { addJsonObject { putJsonArray("parts") { addJsonObject { put("text", text) } } } }
            putJsonObject("generationConfig") {
                putJsonArray("responseModalities") { add("AUDIO") }
                putJsonObject("speechConfig") {
                    putJsonObject("voiceConfig") { putJsonObject("prebuiltVoiceConfig") { put("voiceName", provider.speechVoice) } }
                }
            }
        }
        val request = GeminiGenerate.request(endpoint, provider.speechModel, body)
        val response = withTempFile(".json") { target ->
            httpClients.current().fetchToFile(request, target).readText().also { target.delete() }
        }
        val audio = GeminiGenerate.inlineData(GeminiGenerate.parts(response))
            ?: throw ProviderException.MalformedResponse("Gemini returned no audio")
        withTempFile(".wav") { target ->
            PreparedSpeech.Audio(PcmWav.write(Base64.getDecoder().decode(audio.base64), PcmWav.sampleRateOf(audio.mimeType), target))
        }
    }

    private inline fun <T> withTempFile(suffix: String, block: (File) -> T): T {
        val target = File.createTempFile(FILE_PREFIX, suffix, cacheDir)
        return try {
            block(target)
        } catch (e: Exception) {
            target.delete()
            throw e
        }
    }

    override suspend fun play(speech: PreparedSpeech) {
        if (speech !is PreparedSpeech.Audio) return
        try {
            withContext(Dispatchers.IO) {
                val stream = AudioSystem.getAudioInputStream(speech.file)
                val clip = AudioSystem.getClip().also { player = it }
                stream.use { clip.open(it) }
                suspendCancellableCoroutine { continuation ->
                    clip.addLineListener { event -> if (event.type == LineEvent.Type.STOP && continuation.isActive) continuation.resume(Unit) }
                    continuation.invokeOnCancellation { clip.stop() }
                    clip.start()
                }
            }
        } finally {
            player?.close()
            player = null
            speech.file.delete()
        }
    }

    override fun stop() {
        runCatching { player?.stop() }
    }

    override fun release() = stop()

    private companion object {
        const val SPEECH_PATH = "audio/speech"
        const val ELEVENLABS_SPEECH_PATH = "text-to-speech"
        const val RESPONSE_FORMAT = "wav"
        const val ELEVENLABS_RATE = 24_000
        const val FILE_PREFIX = "speech"
        val JSON = "application/json".toMediaType()
    }
}
