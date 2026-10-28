package ch.sakru.calibrereader.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import ch.sakru.calibrereader.storage.googledrive.GoogleDriveAccount
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

interface GoogleDriveAccounts {
    fun read(): GoogleDriveAccount?
    fun save(account: GoogleDriveAccount)
    fun clear()
}

/** Only an encrypted account hint is persisted. Tokens remain with Google Play services. */
class GoogleDriveAccountStore(context: Context) : GoogleDriveAccounts {
    private val preferences = context.getSharedPreferences("google_drive_account", Context.MODE_PRIVATE)
    private val cipher = CredentialCipher(::getKey, "calibre-to-go/google-drive/v1")

    override fun read(): GoogleDriveAccount? {
        val value = preferences.getString("account", null) ?: return null
        return try {
            val parts = cipher.decrypt(value).split('\u0000')
            require(parts.size == 2)
            GoogleDriveAccount(parts[0], parts[1])
        } catch (_: Exception) {
            clear()
            null
        }
    }

    override fun save(account: GoogleDriveAccount) {
        require(!account.email.contains('\u0000') && !account.permissionId.contains('\u0000'))
        val value = cipher.encrypt(account.email + "\u0000" + account.permissionId)
        check(preferences.edit().putString("account", value).commit()) {
            "Das Google-Drive-Konto konnte nicht gespeichert werden."
        }
    }

    override fun clear() {
        check(preferences.edit().remove("account").commit()) {
            "Das Google-Drive-Konto konnte nicht entfernt werden."
        }
    }

    private fun getKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }

    private companion object {
        const val KEY_ALIAS = "calibre_to_go_google_drive_account_v1"
    }
}
