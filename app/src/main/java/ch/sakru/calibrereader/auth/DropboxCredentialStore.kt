package ch.sakru.calibrereader.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.dropbox.core.oauth.DbxCredential
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/** Tokens stay encrypted; the device key is never exported. */
class DropboxCredentialStore(context: Context) : DropboxCredentials {
    private val preferences =
        context.getSharedPreferences("cloud_credentials", Context.MODE_PRIVATE)
    private val cipher = CredentialCipher(::getKey)

    override var awaitingOAuth: Boolean
        get() = preferences.getBoolean("dropbox_pending", false)
        set(value) {
            check(preferences.edit().putBoolean("dropbox_pending", value).commit()) {
                "Die Dropbox-Anmeldung konnte nicht gespeichert werden."
            }
        }

    override fun read(): DbxCredential? {
        val encrypted = preferences.getString("dropbox_credential", null) ?: return null
        return try {
            DbxCredential.Reader.readFully(cipher.decrypt(encrypted))
        } catch (_: Exception) {
            clear()
            null
        }
    }

    override fun save(credential: DbxCredential) {
        val encrypted = cipher.encrypt(DbxCredential.Writer.writeToString(credential))
        check(preferences.edit().putString("dropbox_credential", encrypted).commit()) {
            "Die Dropbox-Anmeldung konnte nicht gespeichert werden."
        }
    }

    override fun clear() {
        check(preferences.edit().clear().commit()) {
            "Die Dropbox-Anmeldung konnte nicht entfernt werden."
        }
    }

    private fun getKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build()
            )
        }.generateKey()
    }

    private companion object {
        const val KEY_ALIAS = "calibre_to_go_dropbox_credentials_v1"
    }
}
