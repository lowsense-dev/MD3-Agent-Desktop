package dev.huecoder.md3agent.voice

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.LineUnavailableException
import javax.sound.sampled.TargetDataLine
import kotlin.math.abs

/** Records the default microphone (PipeWire/PulseAudio through ALSA) as 16 kHz mono WAV for transcription. */
class VoiceRecorder(private val cacheDir: File) {
    private var line: TargetDataLine? = null
    private var pump: Thread? = null
    private val pcm = ByteArrayOutputStream()

    @Volatile
    private var peak = 0

    fun start() {
        cancel()
        val opened = try {
            (AudioSystem.getLine(DataLine.Info(TargetDataLine::class.java, FORMAT)) as TargetDataLine).apply {
                open(FORMAT)
                start()
            }
        } catch (e: LineUnavailableException) {
            throw IOException(MICROPHONE_UNAVAILABLE, e)
        } catch (e: IllegalArgumentException) {
            throw IOException(MICROPHONE_UNAVAILABLE, e)
        }
        synchronized(pcm) { pcm.reset() }
        line = opened
        pump = Thread({
            val buffer = ByteArray(BUFFER_BYTES)
            while (opened.isOpen) {
                val read = opened.read(buffer, 0, buffer.size)
                if (read <= 0) break
                var loudest = 0
                for (i in 0 until read - 1 step 2) loudest = maxOf(loudest, abs((buffer[i + 1].toInt() shl 8) or (buffer[i].toInt() and 0xFF)))
                peak = loudest
                synchronized(pcm) { pcm.write(buffer, 0, read) }
            }
        }, "voice-recorder").apply {
            isDaemon = true
            start()
        }
    }

    fun level(): Float = peak / MAX_AMPLITUDE

    fun stop(): File {
        val active = line ?: throw IOException(NOT_RECORDING)
        close(active)
        val bytes = synchronized(pcm) { pcm.toByteArray() }
        if (bytes.size < MIN_BYTES) throw RecordingTooShortException(IOException("${bytes.size} bytes"))
        cacheDir.mkdirs()
        return PcmWav.write(bytes, SAMPLE_RATE_HZ, File.createTempFile(FILE_PREFIX, FILE_SUFFIX, cacheDir))
    }

    fun cancel() {
        line?.let(::close)
        synchronized(pcm) { pcm.reset() }
    }

    private fun close(active: TargetDataLine) {
        line = null
        active.stop()
        active.close()
        pump?.join(JOIN_MS)
        pump = null
        peak = 0
    }

    companion object {
        const val MIME_TYPE = "audio/wav"
        private const val SAMPLE_RATE_HZ = 16_000
        private val FORMAT = AudioFormat(SAMPLE_RATE_HZ.toFloat(), 16, 1, true, false)
        private const val BUFFER_BYTES = 3_200
        private const val MIN_BYTES = SAMPLE_RATE_HZ // half a second of 16-bit mono
        private const val JOIN_MS = 500L
        private const val FILE_PREFIX = "dictation-"
        private const val FILE_SUFFIX = ".wav"
        private const val MAX_AMPLITUDE = 32_767f
        private const val MICROPHONE_UNAVAILABLE = "Microphone is unavailable"
        private const val NOT_RECORDING = "Recording is not in progress"

        /** Whether any capture device exists; without one the dictation button is hidden. */
        fun available(): Boolean = runCatching { AudioSystem.isLineSupported(DataLine.Info(TargetDataLine::class.java, FORMAT)) }.getOrDefault(false)
    }
}

class RecordingTooShortException(cause: Throwable) : IOException("Recording is too short", cause)
