package ch.sakru.calibrereader.auth

import android.app.PendingIntent
import ch.sakru.calibrereader.storage.googledrive.GOOGLE_DRIVE_READONLY_SCOPE
import ch.sakru.calibrereader.storage.googledrive.GoogleDriveAccount
import ch.sakru.calibrereader.storage.googledrive.GoogleDriveApi
import ch.sakru.calibrereader.storage.googledrive.GoogleDriveApiFactory
import ch.sakru.calibrereader.storage.googledrive.GoogleDriveTokens
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

data class GoogleDriveConsent(
    val id: String,
    val revision: Long,
    val generation: Long,
    val intent: PendingIntent,
    val expectedAccount: GoogleDriveAccount?,
    val launched: Boolean = false
)

/** A client belongs to exactly one authorization attempt and one Drive account. */
class GoogleDriveAuthManager(
    private val authorization: GoogleDriveAuthorization,
    private val store: GoogleDriveAccounts,
    private val apiFactory: GoogleDriveApiFactory,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val lock = Any()
    private var revision = 0L
    private var client: GoogleDriveApi? = null
    private val _consent = MutableStateFlow<GoogleDriveConsent?>(null)
    val consent = _consent.asStateFlow()

    fun requireClient(): GoogleDriveApi = synchronized(lock) {
        client ?: error("Connect to Google Drive first.")
    }

    suspend fun restoreSession(): AuthenticatedUser? = withContext(ioDispatcher) {
        val (attempt, account) = beginAttempt()
        if (account == null) return@withContext null
        val grant = authorization.authorize(account.email)
        checkCurrent(attempt)
        // Consent is launched only after an explicit interactive sign-in request.
        if (grant is GoogleDriveGrant.Granted) connect(grant, attempt, account)
        else null
    }

    suspend fun startSignIn(generation: Long): AuthenticatedUser? = withContext(ioDispatcher) {
        val (attempt, expected) = beginAttempt()
        val grant = authorization.authorize(expected?.email, chooseAccount = expected == null)
        currentCoroutineContext().ensureActive()
        synchronized(lock) {
            checkCurrent(attempt)
            if (grant is GoogleDriveGrant.Consent) {
                _consent.value = GoogleDriveConsent(UUID.randomUUID().toString(), attempt, generation, grant.intent, expected)
            }
        }
        if (grant is GoogleDriveGrant.Granted) connect(grant, attempt, expected) else null
    }

    fun markConsentLaunched(attempt: Long): Boolean = synchronized(lock) {
        val pending = _consent.value
        if (pending == null || pending.revision != attempt || pending.launched) false
        else {
            _consent.value = pending.copy(launched = true)
            true
        }
    }

    suspend fun finishSignIn(attempt: Long, grant: GoogleDriveGrant): AuthenticatedUser =
        withContext(ioDispatcher) {
            val pending = synchronized(lock) {
                checkCurrent(attempt)
                val value = _consent.value
                check(value != null && value.revision == attempt)
                _consent.value = null
                value
            }
            check(grant is GoogleDriveGrant.Granted) { "Google sign-in has not finished yet." }
            connect(grant, attempt, pending.expectedAccount)
        }

    fun cancelPendingSignIn() {
        synchronized(lock) {
            revision++
            _consent.value = null
            client = null
        }
    }

    suspend fun signOut() = withContext(ioDispatcher) {
        val (attempt, account) = beginAttempt()
        if (account != null) authorization.revokeAccess(account.email)
        currentCoroutineContext().ensureActive()
        synchronized(lock) {
            checkCurrent(attempt)
            store.clear()
        }
    }

    private fun beginAttempt(): Pair<Long, GoogleDriveAccount?> = synchronized(lock) {
        val account = store.read()
        revision++
        client = null
        _consent.value = null
        revision to account
    }

    private suspend fun connect(
        grant: GoogleDriveGrant.Granted,
        attempt: Long,
        expected: GoogleDriveAccount?
    ): AuthenticatedUser {
        validate(grant)
        val initial = apiFactory.create(object : GoogleDriveTokens {
            override suspend fun accessToken(): String {
                checkCurrent(attempt)
                return grant.token
            }
            override suspend fun invalidate(token: String) { authorization.clearToken(token) }
        })
        val account = initial.currentAccount()
        currentCoroutineContext().ensureActive()
        checkCurrent(attempt)
        if (expected != null && account.permissionId != expected.permissionId) {
            throw IOException("Google used a different account. Please sign out and connect again.")
        }
        val connected = apiFactory.create(object : GoogleDriveTokens {
            override suspend fun accessToken(): String {
                checkCurrent(attempt)
                val renewed = authorization.authorize(account.email)
                currentCoroutineContext().ensureActive()
                checkCurrent(attempt)
                if (renewed !is GoogleDriveGrant.Granted) {
                    throw IOException("Google Drive access must be confirmed again. Please reconnect.")
                }
                validate(renewed)
                return renewed.token
            }
            override suspend fun invalidate(token: String) {
                checkCurrent(attempt)
                authorization.clearToken(token)
            }
        })
        synchronized(lock) {
            checkCurrent(attempt)
            store.save(account)
            client = connected
        }
        return AuthenticatedUser(account.email, account.permissionId)
    }

    private fun validate(grant: GoogleDriveGrant.Granted) {
        if (grant.token.isBlank() || GOOGLE_DRIVE_READONLY_SCOPE !in grant.scopes) {
            throw IOException("Google Drive needs read access to the Calibre library. Please reconnect and allow access.")
        }
    }

    private fun checkCurrent(attempt: Long) = synchronized(lock) {
        if (attempt != revision) throw CancellationException("Obsolete Google Drive session.")
    }
}
