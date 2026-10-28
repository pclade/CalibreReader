package ch.sakru.calibrereader.auth

import java.security.GeneralSecurityException
import java.util.Base64
import javax.crypto.spec.SecretKeySpec
import org.junit.Assert.*
import org.junit.Test

class CredentialCipherTest {
    private val key = SecretKeySpec(ByteArray(32) { it.toByte() }, "AES")
    private val cipher = CredentialCipher { key }

    @Test fun roundTripUsesFreshIvAndDoesNotStorePlaintext() {
        val value = """{"access_token":"fixture-access","refresh_token":"fixture-refresh","account":"Şenol"}"""
        val first = cipher.encrypt(value)
        val second = cipher.encrypt(value)
        assertNotEquals(first, second)
        assertFalse(first.contains("fixture-access"))
        assertEquals(value, cipher.decrypt(first))
        assertEquals(value, cipher.decrypt(second))
    }

    @Test fun modifiedCiphertextIsRejected() {
        val parts = cipher.encrypt("fixture-token").split(":").toMutableList()
        val encrypted = Base64.getDecoder().decode(parts[2])
        encrypted[0] = (encrypted[0].toInt() xor 1).toByte()
        parts[2] = Base64.getEncoder().encodeToString(encrypted)
        assertThrows(GeneralSecurityException::class.java) { cipher.decrypt(parts.joinToString(":")) }
    }

    @Test fun differentDeviceKeyCannotReadCredential() {
        val different = CredentialCipher { SecretKeySpec(ByteArray(32) { (it + 9).toByte() }, "AES") }
        assertThrows(GeneralSecurityException::class.java) {
            different.decrypt(cipher.encrypt("fixture-token"))
        }
    }

    @Test fun credentialsFromAnotherProviderCannotBeReusedEvenWithTheSameKey() {
        val google = CredentialCipher({ key }, "calibre-to-go/google-drive/v1")
        assertThrows(GeneralSecurityException::class.java) {
            google.decrypt(cipher.encrypt("fixture-account"))
        }
        val account = google.encrypt("a@example.invalid")
        assertEquals("a@example.invalid", google.decrypt(account))
    }

    @Test fun malformedAndUnsupportedFormatsAreRejected() {
        for (value in listOf("", "plaintext-token", "2:a:b", "1:invalid:a")) {
            assertThrows(IllegalArgumentException::class.java) { cipher.decrypt(value) }
        }
    }
}
