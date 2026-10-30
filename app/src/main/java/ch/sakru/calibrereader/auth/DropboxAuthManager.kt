package ch.sakru.calibrereader.auth

import android.app.Activity
import ch.sakru.calibrereader.BuildConfig
import com.dropbox.core.DbxRequestConfig
import com.dropbox.core.InvalidAccessTokenException
import com.dropbox.core.android.Auth
import com.dropbox.core.android.AuthActivity
import com.dropbox.core.oauth.DbxCredential
import com.dropbox.core.oauth.DbxOAuthException
import com.dropbox.core.v2.DbxClientV2
import com.dropbox.core.v2.common.PathRoot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class DropboxAuthManager(
    private val store: DropboxCredentials,
    private val appKey: String = BuildConfig.DROPBOX_APP_KEY,
    private val requestConfig: DbxRequestConfig = DbxRequestConfig.newBuilder("CalibreToGo/Android").build()
) {
    val isConfigured: Boolean get() = appKey.isNotBlank()
    val awaitingOAuth: Boolean get() = store.awaitingOAuth
    private val sessionLock = Any()
    private var sessionRevision = 0L
    @Volatile private var client: DbxClientV2? = null

    fun requireClient(): DbxClientV2 =
        client ?: error("Connect to Dropbox first.")

    fun startSignIn(activity: Activity) {
        check(isConfigured) { "Dropbox ist in dieser App-Version noch nicht eingerichtet." }
        val revision = synchronized(sessionLock) {
            sessionRevision++
            AuthActivity.result = null
            store.awaitingOAuth = true
            sessionRevision
        }
        try {
            Auth.startOAuth2PKCE(
                activity, appKey, requestConfig,
                listOf("account_info.read", "files.metadata.read", "files.content.read")
            )
        } catch (error: Exception) {
            synchronized(sessionLock) {
                if (revision == sessionRevision) store.awaitingOAuth = false
            }
            throw error
        }
    }

    suspend fun finishSignIn(): AuthenticatedUser? {
        val credential: DbxCredential
        val revision: Long
        synchronized(sessionLock) {
            if (!store.awaitingOAuth) return null
            store.awaitingOAuth = false
            credential = Auth.getDbxCredential() ?: return null
            AuthActivity.result = null
            revision = ++sessionRevision
        }
        check(credential.appKey == appKey && !credential.refreshToken.isNullOrBlank()) {
            "Dropbox did not return a complete sign-in. Please connect again."
        }
        return withContext(Dispatchers.IO) {
            currentCoroutineContext().ensureActive()
            synchronized(sessionLock) {
                checkCurrent(revision)
                store.save(credential)
            }
            connect(credential, revision)
        }
    }

    suspend fun restoreSession(): AuthenticatedUser? = withContext(Dispatchers.IO) {
        val revision: Long
        val credential: DbxCredential
        synchronized(sessionLock) {
            revision = ++sessionRevision
            credential = store.read() ?: return@withContext null
        }
        if (credential.appKey != appKey) {
            clearIfCurrent(revision)
            return@withContext null
        }
        try {
            connect(credential, revision)
        } catch (_: InvalidAccessTokenException) {
            clearIfCurrent(revision)
            null
        } catch (error: DbxOAuthException) {
            if (error.dbxOAuthError.error == "invalid_grant") {
                clearIfCurrent(revision)
                null
            } else {
                throw error
            }
        }
    }

    fun cancelPendingSignIn() {
        synchronized(sessionLock) {
            sessionRevision++
            store.awaitingOAuth = false
            AuthActivity.result = null
        }
    }

    fun signOut() {
        synchronized(sessionLock) {
            sessionRevision++
            client = null
            store.clear()
        }
    }

    private suspend fun connect(credential: DbxCredential, revision: Long): AuthenticatedUser {
        val connection = DbxClientV2(requestConfig, credential)
        val account = connection.users().currentAccount
        val rootedConnection = connection.withPathRoot(PathRoot.root(account.rootInfo.rootNamespaceId))
        currentCoroutineContext().ensureActive()
        synchronized(sessionLock) {
            checkCurrent(revision)
            store.save(credential)
            client = rootedConnection
        }
        return AuthenticatedUser(userName = account.email, accountId = account.accountId)
    }

    private fun clearIfCurrent(revision: Long) {
        synchronized(sessionLock) {
            if (revision == sessionRevision) {
                sessionRevision++
                client = null
                store.clear()
            }
        }
    }

    private fun checkCurrent(revision: Long) {
        if (revision != sessionRevision) throw CancellationException("Obsolete Dropbox session.")
    }
}
