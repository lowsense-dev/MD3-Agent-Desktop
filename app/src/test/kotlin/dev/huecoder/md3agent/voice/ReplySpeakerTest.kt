package dev.huecoder.md3agent.voice

import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.IOException

class ReplySpeakerTest {
    @Test
    fun `a failed reply is not started over by later stream updates`() = runTest {
        var started = 0
        val speaker = ReplySpeaker(backgroundScope) {
            started++
            object : SpeechSynthesizer {
                override suspend fun prepare(text: String): PreparedSpeech = throw IOException("offline")

                override suspend fun play(speech: PreparedSpeech) = Unit

                override fun stop() = Unit

                override fun release() = Unit
            }
        }

        speaker.follow("r", "The first sentence is here.\n")
        runCurrent()
        speaker.follow("r", "The first sentence is here.\nThe second one follows.\n")
        speaker.finish("r", "The first sentence is here.\nThe second one follows.\nDone.")
        runCurrent()

        assertEquals(1, started)
        assertNull(speaker.speakingId.value)
    }
}
