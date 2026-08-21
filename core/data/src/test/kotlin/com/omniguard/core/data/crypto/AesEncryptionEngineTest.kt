package com.omniguard.core.data.crypto

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AesEncryptionEngineTest {

    private lateinit var encryptionEngine: AesEncryptionEngine

    @BeforeEach
    fun setup() {
        encryptionEngine = AesEncryptionEngine()
    }

    @Test
    fun `encrypt and decrypt returns original plaintext payload`() {
        val original = "LAT:37.7749,LNG:-122.4194,SPEED:25.4,TIMESTAMP:1724240000000"
        val encrypted = encryptionEngine.encrypt(original)

        assertNotEquals(original, encrypted)
        val decrypted = encryptionEngine.decrypt(encrypted)
        assertEquals(original, decrypted)
    }

    @Test
    fun `encrypting same plaintext produces unique ciphertexts due to random IV`() {
        val plaintext = "Sensitive Transit Coordinate"
        val cipher1 = encryptionEngine.encrypt(plaintext)
        val cipher2 = encryptionEngine.encrypt(plaintext)

        assertNotEquals(cipher1, cipher2)
        assertEquals(plaintext, encryptionEngine.decrypt(cipher1))
        assertEquals(plaintext, encryptionEngine.decrypt(cipher2))
    }

    @Test
    fun `decrypting corrupted or short ciphertext throws exception`() {
        val invalidCiphertext = "YWJj" // Too short base64 string
        assertThrows(IllegalArgumentException::class.java) {
            encryptionEngine.decrypt(invalidCiphertext)
        }
    }
}
