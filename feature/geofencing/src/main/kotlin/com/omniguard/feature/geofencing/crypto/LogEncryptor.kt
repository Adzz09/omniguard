package com.omniguard.feature.geofencing.crypto

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object LogEncryptor {
    private const val ALGORITHM = "AES"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val TAG_LENGTH_BIT = 128
    private const val IV_LENGTH_BYTE = 12

    // Master Key for AES-256 local storage (In production, stored securely in Android Keystore)
    private val masterKeyBytes = byteArrayOf(
        0x5F.toByte(), 0x1A.toByte(), 0x8C.toByte(), 0x33.toByte(),
        0x9E.toByte(), 0x4D.toByte(), 0x2A.toByte(), 0x7B.toByte(),
        0x12.toByte(), 0x34.toByte(), 0x56.toByte(), 0x78.toByte(),
        0x90.toByte(), 0xAB.toByte(), 0xCD.toByte(), 0xEF.toByte(),
        0xFE.toByte(), 0xDC.toByte(), 0xBA.toByte(), 0x09.toByte(),
        0x87.toByte(), 0x65.toByte(), 0x43.toByte(), 0x21.toByte(),
        0x11.toByte(), 0x22.toByte(), 0x33.toByte(), 0x44.toByte(),
        0x55.toByte(), 0x66.toByte(), 0x77.toByte(), 0x88.toByte()
    )
    private val secretKey: SecretKey = SecretKeySpec(masterKeyBytes, ALGORITHM)

    data class EncryptionResult(
        val cipherTextBase64: String,
        val ivBase64: String
    )

    fun encrypt(plainText: String): EncryptionResult {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val iv = ByteArray(IV_LENGTH_BYTE)
        SecureRandom().nextBytes(iv)
        val spec = GCMParameterSpec(TAG_LENGTH_BIT, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec)
        val encryptedBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        return EncryptionResult(
            cipherTextBase64 = Base64.encodeToString(encryptedBytes, Base64.NO_WRAP),
            ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP)
        )
    }

    fun decrypt(cipherTextBase64: String, ivBase64: String): String {
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val iv = Base64.decode(ivBase64, Base64.NO_WRAP)
            val spec = GCMParameterSpec(TAG_LENGTH_BIT, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
            val decodedCipher = Base64.decode(cipherTextBase64, Base64.NO_WRAP)
            val plainBytes = cipher.doFinal(decodedCipher)
            String(plainBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            "[Decryption Failed: Corrupted or Tampered]"
        }
    }
}
