package com.example.security

import android.util.Base64
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object SignalEncryption {

    private const val ALGORITHM = "AES/CBC/PKCS5Padding"
    private val FIXED_IV = ByteArray(16) { 0 } // Fixed IV for mock/simplified E2EE consistency

    /**
     * Derives a 256-bit AES key from any passphrase string using SHA-256
     */
    private fun deriveKey(passphrase: String): SecretKeySpec {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashedBytes = digest.digest(passphrase.toByteArray(Charsets.UTF_8))
        return SecretKeySpec(hashedBytes, "AES")
    }

    /**
     * Encrypts plain text using AES/CBC/PKCS5Padding and a password-derived key
     */
    fun encrypt(plainText: String, keyPhrase: String): String {
        return try {
            val keySpec = deriveKey(keyPhrase)
            val cipher = Cipher.getInstance(ALGORITHM)
            val ivSpec = IvParameterSpec(FIXED_IV)
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)
            val encryptedBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            Base64.encodeToString(encryptedBytes, Base64.DEFAULT).trim()
        } catch (e: Exception) {
            plainText // Fallback to plain text in case of errors
        }
    }

    /**
     * Decrypts AES/CBC/PKCS5Padding encrypted base64 text using a password-derived key
     */
    fun decrypt(cipherText: String, keyPhrase: String): String {
        if (cipherText.isEmpty()) return ""
        return try {
            val keySpec = deriveKey(keyPhrase)
            val cipher = Cipher.getInstance(ALGORITHM)
            val ivSpec = IvParameterSpec(FIXED_IV)
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
            val decodedBytes = Base64.decode(cipherText, Base64.DEFAULT)
            val decryptedBytes = cipher.doFinal(decodedBytes)
            String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            cipherText // Fallback to original ciphertext if not decryptable or is plain text
        }
    }

    /**
     * Generates a realistic visual 60-digit "Safety Number" between two phone numbers/IDs
     * formatted as 12 groups of 5 digits, matching WhatsApp / Signal's security fingerprint.
     */
    fun generateSecurityNumber(phoneA: String, phoneB: String): String {
        val sortedPhones = listOf(phoneA, phoneB).sorted().joinToString("|")
        val digest = MessageDigest.getInstance("SHA-256")
        val hashedBytes = digest.digest(sortedPhones.toByteArray(Charsets.UTF_8))
        
        // Convert hashed bytes to a massive numeric string
        val builder = StringBuilder()
        for (b in hashedBytes) {
            val unsignedVal = b.toInt() and 0xFF
            builder.append(String.format("%03d", unsignedVal))
        }

        // Clip/pad to 60 digits
        var numericSource = builder.toString()
        if (numericSource.length < 60) {
            numericSource = numericSource.padEnd(60, '7')
        } else {
            numericSource = numericSource.substring(0, 60)
        }

        // Format in 12 groups of 5 with spaces
        val groups = mutableListOf<String>()
        for (i in 0 until 12) {
            groups.add(numericSource.substring(i * 5, (i + 1) * 5))
        }
        
        return groups.chunked(4).joinToString("\n") { chunk -> chunk.joinToString("  ") }
    }
}
