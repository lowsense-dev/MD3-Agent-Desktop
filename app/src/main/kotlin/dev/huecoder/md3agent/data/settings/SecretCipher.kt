package dev.huecoder.md3agent.data.settings

import dev.huecoder.md3agent.system.secrets.SecretStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

interface TextCipher {
    fun encrypt(plainText: String): String

    fun decrypt(encoded: String): String
}

/** AES-256-GCM with the key from [store]; the stored format (IV followed by ciphertext, Base64) matches the phone app. */
class SecretCipher(private val store: SecretStore) : TextCipher {
    private val key: SecretKey by lazy { SecretKeySpec(store.loadOrCreateKey(), "AES") }

    override fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val payload = cipher.iv + cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(payload)
    }

    override fun decrypt(encoded: String): String {
        val payload = Base64.getDecoder().decode(encoded)
        val iv = payload.copyOfRange(0, IV_SIZE_BYTES)
        val cipherText = payload.copyOfRange(IV_SIZE_BYTES, payload.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_SIZE_BITS, iv))
        return String(cipher.doFinal(cipherText), Charsets.UTF_8)
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_SIZE_BITS = 128
        const val IV_SIZE_BYTES = 12
    }
}
