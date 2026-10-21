package dev.huecoder.md3agent.data.transfer

import kotlinx.serialization.SerializationException
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

// Shared by MD3 Agent for Android and for Linux; keep both copies identical.

/** Password-based sealing of export files: PBKDF2-HMAC-SHA256 (600 000 rounds) derives an AES-256-GCM key. */
object ExportCrypto {
    const val ITERATIONS = 600_000
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val KEY_BITS = 256
    private const val TAG_BITS = 128

    fun seal(password: CharArray, plain: ByteArray, iterations: Int = ITERATIONS, createdAt: Long = System.currentTimeMillis()): TransferHeader {
        val random = SecureRandom()
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key(password, salt, iterations), GCMParameterSpec(TAG_BITS, iv))
        return TransferHeader(
            format = TransferFormat.CURRENT,
            createdAt = createdAt,
            kdf = Kdf(iterations = iterations, salt = b64(salt)),
            cipher = CipherInfo(iv = b64(iv)),
            data = b64(cipher.doFinal(plain)),
        )
    }

    fun open(password: CharArray, header: TransferHeader): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        try {
            cipher.init(Cipher.DECRYPT_MODE, key(password, unb64(header.kdf.salt), header.kdf.iterations), GCMParameterSpec(TAG_BITS, unb64(header.cipher.iv)))
            return cipher.doFinal(unb64(header.data))
        } catch (e: AEADBadTagException) {
            throw WrongTransferPassword()
        } catch (e: IllegalArgumentException) {
            throw MalformedTransferFile(e)
        }
    }

    fun encode(header: TransferHeader): ByteArray = TransferFormat.json.encodeToString(TransferHeader.serializer(), header).toByteArray()

    fun decode(bytes: ByteArray): TransferHeader {
        val header = try {
            TransferFormat.json.decodeFromString(TransferHeader.serializer(), bytes.decodeToString())
        } catch (e: SerializationException) {
            throw MalformedTransferFile(e)
        } catch (e: IllegalArgumentException) {
            throw MalformedTransferFile(e)
        }
        if (header.format > TransferFormat.CURRENT) throw UnsupportedTransferFormat(header.format)
        if (header.format < 1 || header.kdf.iterations < 1) throw MalformedTransferFile()
        return header
    }

    private fun key(password: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, iterations, KEY_BITS)
        try {
            return SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    private fun b64(bytes: ByteArray) = Base64.getEncoder().encodeToString(bytes)

    private fun unb64(text: String) = Base64.getDecoder().decode(text)

    private const val TRANSFORMATION = "AES/GCM/NoPadding"
}
