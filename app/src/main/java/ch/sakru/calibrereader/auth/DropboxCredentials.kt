package ch.sakru.calibrereader.auth

import com.dropbox.core.oauth.DbxCredential

interface DropboxCredentials {
    var awaitingOAuth: Boolean
    fun read(): DbxCredential?
    fun save(credential: DbxCredential)
    fun clear()
}
