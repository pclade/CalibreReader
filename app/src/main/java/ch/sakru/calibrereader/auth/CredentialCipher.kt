package ch.sakru.calibrereader.auth

import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Authenticated encryption; each saved credential gets a fresh random IV. */
class CredentialCipher private constructor(
    private val keyProvider: () -> SecretKey,
    private val associatedData: ByteArray
) {
    constructor(keyProvider: () -> SecretKey) : this(keyProvider, "calibre-to-go/dropbox/v1")
    constructor(keyProvider: () -> SecretKey, context: String) :
        this(keyProvider, context.toByteArray(Charsets.UTF_8))

    fun encrypt(plaintext: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, keyProvider())
        cipher.updateAAD(associatedData)
        return listOf(
            "1",
            Base64.getEncoder().encodeToString(cipher.iv),
            Base64.getEncoder().encodeToString(cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8)))
        ).joinToString(":")
    }

    fun decrypt(value: String): String {
        val parts = value.split(":", limit = 3)
        require(parts.size == 3 && parts[0] == "1") { "Unsupported credential format." }
        val iv = Base64.getDecoder().decode(parts[1])
        require(iv.size == 12) { "Invalid credential IV." }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, keyProvider(), GCMParameterSpec(128, iv))
        cipher.updateAAD(associatedData)
        return String(cipher.doFinal(Base64.getDecoder().decode(parts[2])), Charsets.UTF_8)
    }

}
