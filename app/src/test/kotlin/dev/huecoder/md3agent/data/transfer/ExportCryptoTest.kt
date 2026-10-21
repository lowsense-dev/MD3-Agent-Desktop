package dev.huecoder.md3agent.data.transfer

import dev.huecoder.md3agent.data.db.MemoryEntity
import dev.huecoder.md3agent.data.db.MemoryTier
import dev.huecoder.md3agent.data.settings.ProviderProfile
import dev.huecoder.md3agent.testing.testContainer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExportCryptoTest {
    private val password = "correct horse".toCharArray()

    @Test
    fun roundTrip() {
        val sealed = ExportCrypto.seal(password, "секрет sk-123".toByteArray(), iterations = 1_000, createdAt = 5)
        val bytes = ExportCrypto.encode(sealed)
        assertFalse(bytes.decodeToString().contains("sk-123"))
        assertContentEquals("секрет sk-123".toByteArray(), ExportCrypto.open(password, ExportCrypto.decode(bytes)))
    }

    @Test
    fun wrongPasswordFailsWithoutPartialImport() = runBlocking {
        val phone = testContainer()
        phone.settings.upsertProvider(ProviderProfile(id = "p1", name = "OpenAI", baseUrl = "https://api.openai.com/v1", apiKey = "sk-phone"))
        phone.memories.let { phone.usageStats } // container fully built
        val file = Exporter(phone).export(password, includeChats = false, iterations = 1_000)

        val desktop = testContainer()
        assertFailsWith<WrongTransferPassword> { Importer(desktop).preview(file, "wrong".toCharArray()) }
        assertTrue(desktop.settings.current().providers.isEmpty())
    }

    @Test
    fun truncatedFileFails() {
        val bytes = ExportCrypto.encode(ExportCrypto.seal(password, ByteArray(100), iterations = 1_000, createdAt = 0))
        assertFailsWith<MalformedTransferFile> { ExportCrypto.decode(bytes.copyOf(bytes.size / 2)) }
    }

    @Test
    fun unknownFormatRejected() {
        val bytes = ExportCrypto.encode(ExportCrypto.seal(password, ByteArray(4), iterations = 1_000, createdAt = 0).copy(format = 99))
        val error = assertFailsWith<UnsupportedTransferFormat> { ExportCrypto.decode(bytes) }
        assertEquals(99, error.format)
    }
}
