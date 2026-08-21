package com.omniguard.core.data.crypto

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Interface providing AES-256-GCM authenticated encryption and decryption for sensitive local storage.
 */
interface EncryptionEngine {
    fun encrypt(plaintext: String): String
    fun decrypt(ciphertext: String): String
}

/**
 * AES-256-GCM encryption engine implementation for local database security and GDPR-compliant storage.
 */
class AesEncryptionEngine(
    secretKey: SecretKey? = null
) : EncryptionEngine {

    private val key: SecretKey = secretKey ?: generateKey()
    private val secureRandom = SecureRandom()

    override fun encrypt(plaintext: String): String {
        val iv = ByteArray(GCM_IV_LENGTH_BYTES)
        secureRandom.nextBytes(iv)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.ENCRYPT_MODE, key, spec)

        val ciphertextBytes = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val combined = ByteArray(iv.size + ciphertextBytes.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(ciphertextBytes, 0, combined, iv.size, ciphertextBytes.size)

        return Base64.getEncoder().encodeToString(combined)
    }

    override fun decrypt(ciphertext: String): String {
        val decoded = Base64.getDecoder().decode(ciphertext)
        require(decoded.size > GCM_IV_LENGTH_BYTES) { "Invalid encrypted payload size" }

        val iv = ByteArray(GCM_IV_LENGTH_BYTES)
        System.arraycopy(decoded, 0, iv, 0, GCM_IV_LENGTH_BYTES)

        val ciphertextLength = decoded.size - GCM_IV_LENGTH_BYTES
        val cipherBytes = ByteArray(ciphertextLength)
        System.arraycopy(decoded, GCM_IV_LENGTH_BYTES, cipherBytes, 0, ciphertextLength)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.DECRYPT_MODE, key, spec)

        val decryptedBytes = cipher.doFinal(cipherBytes)
        return String(decryptedBytes, Charsets.UTF_8)
    }

    companion object {
        private const val ALGORITHM = "AES"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_SIZE_BITS = 256
        private const val GCM_IV_LENGTH_BYTES = 12
        private const val GCM_TAG_LENGTH_BITS = 128

        fun generateKey(): SecretKey {
            val keyGen = KeyGenerator.getInstance(ALGORITHM)
            keyGen.init(KEY_SIZE_BITS)
            return keyGen.generateKey()
        }

        fun fromKeyBytes(keyBytes: ByteArray): SecretKey {
            return SecretKeySpec(keyBytes, ALGORITHM)
        }
    }
}
