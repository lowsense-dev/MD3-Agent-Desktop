package dev.huecoder.md3agent.data.attachments

import kotlinx.coroutines.runBlocking
import java.io.RandomAccessFile
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertFailsWith

class AttachmentLimitsTest {
    @Test
    fun anOversizedPictureIsRefusedBeforeItIsRead() = runBlocking {
        val dir = createTempDirectory("md3-att").toFile()
        // A sparse 65 MB file: refused by its size alone, nothing is loaded into memory.
        val huge = dir.resolve("huge.png").also { RandomAccessFile(it, "rw").use { raf -> raf.setLength(65L * 1024 * 1024) } }
        val store = AttachmentStore(dir.resolve("store")) { dir }
        assertFailsWith<AttachmentTooLargeException> { store.import(huge) }
        Unit
    }
}
