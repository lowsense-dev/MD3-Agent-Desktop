package dev.huecoder.md3agent.voice

import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.sound.sampled.AudioSystem
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals

class WavTest {
    @Test
    fun writesValidWavHeader() {
        val pcm = ByteArray(32_000) { (it % 7).toByte() }
        val file = PcmWav.write(pcm, 16_000, createTempDirectory("md3-wav").toFile().resolve("a.wav"))
        val header = ByteBuffer.wrap(file.readBytes(), 0, 44).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals("RIFF", String(file.readBytes().copyOfRange(0, 4)))
        assertEquals(16_000, header.getInt(24))
        assertEquals(pcm.size, header.getInt(40))
        // Java Sound itself must read it back, since that is what plays and what the recorder writes.
        val format = AudioSystem.getAudioInputStream(file).use { it.format }
        assertEquals(16_000f, format.sampleRate)
        assertEquals(1, format.channels)
    }
}
