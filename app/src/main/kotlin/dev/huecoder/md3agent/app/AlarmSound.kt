package dev.huecoder.md3agent.app

import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Clip
import kotlin.math.PI
import kotlin.math.sin

/** A soft two-tone chime, generated in memory and looped while an alarm window is open. */
object AlarmSound {
    private const val RATE = 22_050f

    fun start(): Clip? = runCatching {
        val pcm = chime()
        AudioSystem.getClip().apply {
            open(AudioFormat(RATE, 16, 1, true, false), pcm, 0, pcm.size)
            loop(Clip.LOOP_CONTINUOUSLY)
        }
    }.getOrNull()

    private fun chime(): ByteArray {
        val tones = listOf(880.0 to 0.18, 0.0 to 0.06, 1175.0 to 0.24, 0.0 to 0.9)
        val samples = tones.flatMap { (hz, seconds) ->
            val count = (RATE * seconds).toInt()
            (0 until count).map { i ->
                if (hz == 0.0) 0.0 else sin(2 * PI * hz * i / RATE) * (1.0 - i.toDouble() / count) * 0.35
            }
        }
        return ByteArray(samples.size * 2).also { bytes ->
            samples.forEachIndexed { i, v ->
                val s = (v * Short.MAX_VALUE).toInt()
                bytes[2 * i] = s.toByte()
                bytes[2 * i + 1] = (s shr 8).toByte()
            }
        }
    }
}
