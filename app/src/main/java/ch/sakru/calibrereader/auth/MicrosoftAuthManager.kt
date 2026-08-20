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

class MicrosoftAuthManager(
    context: Context,
    private val onReadyChanged: (Boolean) -> Unit,
    private val onInitializationError: (Exception) -> Unit
) {
    private var msalApp: ISingleAccountPublicClientApplication? = null

    init {
        PublicClientApplication.createSingleAccountPublicClientApplication(
            context,
            R.raw.auth_config,
            object : IPublicClientApplication.ISingleAccountApplicationCreatedListener {

                override fun onCreated(
                    application: ISingleAccountPublicClientApplication
                ) {
                    msalApp = application
                    onReadyChanged(true)
                }

                override fun onError(exception: MsalException) {
                    onInitializationError(exception)
                }
            }
        )
    }

    fun signIn(
        activity: Activity,
        onSuccess: (IAuthenticationResult) -> Unit,
        onError: (Exception) -> Unit,
        onCancel: () -> Unit
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

        val callback = object : AuthenticationCallback {

            override fun onSuccess(
                authenticationResult: IAuthenticationResult
            ) {
                onSuccess(authenticationResult)
            }

            override fun onError(exception: MsalException) {
                onError(exception)
            }

            override fun onCancel() {
                onCancel()
            }
        }

        val parameters = SignInParameters.builder()
            .withActivity(activity)
            .withScopes(
                listOf(
                    "User.Read",
                    "Files.Read"
                )
            )
            .withCallback(callback)
            .build()

        app.signIn(parameters)
    }

    fun getCurrentAccount(
        onAccountFound: (com.microsoft.identity.client.IAccount?) -> Unit,
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

        app.getCurrentAccountAsync(
            object :
                ISingleAccountPublicClientApplication.CurrentAccountCallback {

                override fun onAccountLoaded(
                    activeAccount: com.microsoft.identity.client.IAccount?
                ) {
                    onAccountFound(activeAccount)
                }

                override fun onAccountChanged(
                    priorAccount: com.microsoft.identity.client.IAccount?,
                    currentAccount: com.microsoft.identity.client.IAccount?
                ) {
                    onAccountFound(currentAccount)
                }

                override fun onError(exception: MsalException) {
                    onError(exception)
                }
            }
        )
    }

    fun acquireTokenSilent(
        account: com.microsoft.identity.client.IAccount,
        onSuccess: (IAuthenticationResult) -> Unit,
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
                    onSuccess(authenticationResult)
                }

                override fun onError(exception: MsalException) {
                    onError(exception)
                }
            }
        )
    }
}
