package dev.huecoder.md3agent.system.secrets

import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import java.security.SecureRandom

/** Source of the key that encrypts API keys and tokens in the settings file. */
interface SecretStore {
    fun loadOrCreateKey(): ByteArray
}

/** Fallback when no Secret Service is running: 32 random bytes in a file only the user can read. */
class FileKeyStore(private val file: File) : SecretStore {
    override fun loadOrCreateKey(): ByteArray {
        if (file.isFile) {
            val stored = file.readBytes()
            if (stored.size == KEY_BYTES) return stored
        }
        file.parentFile?.mkdirs()
        val key = ByteArray(KEY_BYTES).also(SecureRandom()::nextBytes)
        // Created with owner-only permissions from the start, so the key is never readable by others even briefly.
        val temp = File(file.path + ".tmp").apply { delete() }
        Files.createFile(temp.toPath(), PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))
        temp.writeBytes(key)
        Files.move(temp.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE)
        return key
    }

    private companion object {
        const val KEY_BYTES = 32
    }
}
