package dev.huecoder.md3agent.system.secrets

import dev.huecoder.md3agent.data.settings.SecretCipher
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class SecretStoreTest {
    @Test
    fun fileKeyIsCreatedWith0600AndReused() {
        val dir = createTempDirectory("md3-key").toFile()
        val file = dir.resolve("sub/key")
        val first = FileKeyStore(file).loadOrCreateKey()
        assertEquals(32, first.size)
        assertEquals(setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE), Files.getPosixFilePermissions(file.toPath()))
        assertContentEquals(first, FileKeyStore(file).loadOrCreateKey())
    }

    @Test
    fun cipherRoundTrips() {
        val store = FileKeyStore(createTempDirectory("md3-key").toFile().resolve("key"))
        val cipher = SecretCipher(store)
        val sealed = cipher.encrypt("sk-test-123")
        assertNotEquals("sk-test-123", sealed)
        assertEquals("sk-test-123", cipher.decrypt(sealed))
    }
}
