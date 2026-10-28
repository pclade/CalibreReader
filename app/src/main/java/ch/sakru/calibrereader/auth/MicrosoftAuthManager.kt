package ch.sakru.calibrereader.auth

import android.app.Activity
import android.content.Context
import com.microsoft.identity.client.AuthenticationCallback
import com.microsoft.identity.client.IAuthenticationResult
import com.microsoft.identity.client.IPublicClientApplication
import com.microsoft.identity.client.ISingleAccountPublicClientApplication
import com.microsoft.identity.client.PublicClientApplication
import com.microsoft.identity.client.SignInParameters
import com.microsoft.identity.client.exception.MsalException
import ch.sakru.calibrereader.R
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import java.util.concurrent.atomic.AtomicLong

class MicrosoftAuthManager(
    context: Context,
    private val authSession: AuthSession,
    private val onReadyChanged: (Boolean) -> Unit = {},
    private val onInitializationError: (Exception) -> Unit = {}
) {
    private var msalApp: ISingleAccountPublicClientApplication? = null
    private val sessionRevision = AtomicLong()
    private val sessionLock = Any()
    private val initialized = CompletableDeferred<Unit>()
    private val _ready = MutableStateFlow(false)
    val ready = _ready.asStateFlow()
    private val _initializationError = MutableStateFlow<String?>(null)
    val initializationError = _initializationError.asStateFlow()

    init {
        PublicClientApplication.createSingleAccountPublicClientApplication(
            context,
            R.raw.auth_config,
            object : IPublicClientApplication.ISingleAccountApplicationCreatedListener {

                override fun onCreated(
                    application: ISingleAccountPublicClientApplication
                ) {
                    msalApp = application
                    initialized.complete(Unit)
                    _ready.value = true
                    onReadyChanged(true)
                }

                override fun onError(exception: MsalException) {
                    initialized.completeExceptionally(exception)
                    _initializationError.value = "Die Microsoft-Anmeldung ist momentan nicht verfügbar."
                    onInitializationError(exception)
                }
            }
        )
    }

    /**
     * Starts an interactive Microsoft sign-in.
     *
     * The acquired access token is stored in the current authentication session.
     *
     * @param activity Android activity used by MSAL for interactive authentication.
     * @param onSuccess called with the authenticated application user.
     * @param onError called when authentication fails.
     * @param onCancel called when the user cancels authentication.
     */
    fun signIn(
        activity: Activity,
        onSuccess: (AuthenticatedUser) -> Unit,
        onError: (Exception) -> Unit,
        onCancel: () -> Unit
    ) {
        val revision = nextRevision()
        val app = msalApp

        if (app == null) {
            onError(
                IllegalStateException(
                    "MSAL ist noch nicht initialisiert."
                )
            )
            return
        }

        val callback =
            object : AuthenticationCallback {

                override fun onSuccess(
                    authenticationResult: IAuthenticationResult
                ) {
                    if (!setTokenIfCurrent(revision, authenticationResult.accessToken)) return

                    onSuccess(
                        AuthenticatedUser(
                            userName =
                                authenticationResult.account.username
                        )
                    )
                }

                override fun onError(
                    exception: MsalException
                ) {
                    onError(exception)
                }

                override fun onCancel() {
                    onCancel()
                }
            }

        val parameters =
            SignInParameters.builder()
                .withActivity(activity)
                .withScopes(
                    listOf(
                        "User.Read",
                        "Files.Read"
                    )
                )
                .withCallback(callback)
                .build()

        app.signIn(
            parameters
        )
    }
    /**
     * Restores an existing authenticated Microsoft session.
     *
     * If an account exists, a fresh access token is acquired silently
     * and stored in the authentication session.
     *
     * @param onSuccess called with the authenticated user when a session exists.
     * @param onNoAccount called when no authenticated account exists.
     * @param onError called when session restoration fails.
     */
    fun restoreSession(
        onSuccess: (AuthenticatedUser) -> Unit,
        onNoAccount: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        val revision = nextRevision()
        val app = msalApp

        if (app == null) {
            onError(
                IllegalStateException(
                    "MSAL ist noch nicht initialisiert."
                )
            )
            return
        }

        app.getCurrentAccountAsync(
            object :
                ISingleAccountPublicClientApplication.CurrentAccountCallback {

                override fun onAccountLoaded(
                    activeAccount: com.microsoft.identity.client.IAccount?
                ) {
                    if (revision != sessionRevision.get()) return
                    if (activeAccount == null) {
                        onNoAccount()
                        return
                    }

                    acquireTokenSilent(
                        account = activeAccount,
                        revision = revision,
                        onSuccess = onSuccess,
                        onError = onError
                    )
                }

                override fun onAccountChanged(
                    priorAccount: com.microsoft.identity.client.IAccount?,
                    currentAccount: com.microsoft.identity.client.IAccount?
                ) {
                    if (currentAccount == null && clearSessionIfCurrent(revision)) {
                        onNoAccount()
                    }
                }

                override fun onError(
                    exception: MsalException
                ) {
                    onError(exception)
                }
            }
        )
    }
    private fun acquireTokenSilent(
        account: com.microsoft.identity.client.IAccount,
        revision: Long,
        onSuccess: (AuthenticatedUser) -> Unit,
        onError: (Exception) -> Unit
    ) {
        val app = msalApp

        if (app == null) {
            onError(
                IllegalStateException(
                    "MSAL ist noch nicht initialisiert."
                )
            )
            return
        }

        app.acquireTokenSilentAsync(
            arrayOf(
                "User.Read",
                "Files.Read"
            ),
            account.authority,
            object :
                com.microsoft.identity.client.SilentAuthenticationCallback {

                override fun onSuccess(
                    authenticationResult: IAuthenticationResult
                ) {
                    if (!setTokenIfCurrent(revision, authenticationResult.accessToken)) return

                    onSuccess(
                        AuthenticatedUser(
                            userName =
                                authenticationResult.account.username
                        )
                    )
                }

                override fun onError(
                    exception: MsalException
                ) {
                    onError(exception)
                }
            }
        )
    }
    suspend fun restoreSessionAsync(): AuthenticatedUser? {
        initialized.await()
        return suspendCancellableCoroutine { continuation ->
            restoreSession(
                onSuccess = { if (continuation.isActive) continuation.resume(it) },
                onNoAccount = { if (continuation.isActive) continuation.resume(null) },
                onError = { if (continuation.isActive) continuation.resumeWithException(it) }
            )
        }
    }

    suspend fun signOutAsync() {
        initialized.await()
        val revision = nextRevision()
        suspendCancellableCoroutine<Unit> { continuation ->
            msalApp!!.signOut(object : ISingleAccountPublicClientApplication.SignOutCallback {
                override fun onSignOut() {
                    clearSessionIfCurrent(revision)
                    if (continuation.isActive) continuation.resume(Unit)
                }
                override fun onError(exception: MsalException) {
                    if (continuation.isActive) continuation.resumeWithException(exception)
                }
            })
        }
    }

    private fun nextRevision(): Long = synchronized(sessionLock) {
        sessionRevision.incrementAndGet()
    }

    private fun setTokenIfCurrent(revision: Long, token: String): Boolean = synchronized(sessionLock) {
        if (revision != sessionRevision.get()) return@synchronized false
        authSession.updateAccessToken(token)
        true
    }

    private fun clearSessionIfCurrent(revision: Long): Boolean = synchronized(sessionLock) {
        if (revision != sessionRevision.get()) return@synchronized false
        authSession.clear()
        true
    }
}
