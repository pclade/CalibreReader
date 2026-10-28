package ch.sakru.calibrereader.auth

import android.accounts.Account
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import ch.sakru.calibrereader.storage.googledrive.GOOGLE_DRIVE_READONLY_SCOPE
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.ClearTokenRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.RevokeAccessRequest
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

sealed interface GoogleDriveGrant {
    data class Granted(val token: String, val scopes: Set<String>) : GoogleDriveGrant
    data class Consent(val intent: PendingIntent) : GoogleDriveGrant
}

interface GoogleDriveAuthorization {
    suspend fun authorize(email: String?, chooseAccount: Boolean = false): GoogleDriveGrant
    suspend fun clearToken(token: String)
    suspend fun revokeAccess(email: String)
}

/** On-device authorization only: no backend, client secret or stored refresh token. */
class GooglePlayDriveAuthorization(context: Context) : GoogleDriveAuthorization {
    private val client = Identity.getAuthorizationClient(context.applicationContext)
    private val scopes = listOf(Scope(GOOGLE_DRIVE_READONLY_SCOPE))

    override suspend fun authorize(email: String?, chooseAccount: Boolean): GoogleDriveGrant {
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(scopes)
            .setOptOutIncludingGrantedScopes(true)
        if (email != null) request.setAccount(Account(email, "com.google"))
        if (chooseAccount) request.setPrompt(AuthorizationRequest.Prompt.SELECT_ACCOUNT)
        return try {
            client.authorize(request.build()).awaitResult().toGrant()
        } catch (error: ApiException) {
            val message = when (error.statusCode) {
                CommonStatusCodes.DEVELOPER_ERROR ->
                    "Google Drive ist in dieser App-Version noch nicht korrekt eingerichtet."
                CommonStatusCodes.NETWORK_ERROR ->
                    "Google Drive ist nicht erreichbar. Bitte Internetverbindung prüfen."
                else -> "Die Google-Drive-Anmeldung ist fehlgeschlagen. Bitte erneut verbinden."
            }
            throw IOException(message, error)
        }
    }

    fun readResult(intent: Intent): GoogleDriveGrant =
        client.getAuthorizationResultFromIntent(intent).toGrant()

    override suspend fun clearToken(token: String) {
        client.clearToken(ClearTokenRequest.builder().setToken(token).build()).awaitResult()
    }

    override suspend fun revokeAccess(email: String) {
        client.revokeAccess(RevokeAccessRequest.builder()
            .setAccount(Account(email, "com.google")).setScopes(scopes).build()).awaitResult()
    }

    private fun AuthorizationResult.toGrant(): GoogleDriveGrant =
        if (hasResolution()) GoogleDriveGrant.Consent(
            pendingIntent ?: throw IOException("Die Google-Anmeldung konnte nicht geöffnet werden.")
        ) else GoogleDriveGrant.Granted(
            accessToken ?: throw IOException("Google hat keinen Zugriffstoken zurückgegeben."),
            grantedScopes.toSet()
        )
}

private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { value -> if (continuation.isActive) continuation.resume(value) }
    addOnFailureListener { error -> if (continuation.isActive) continuation.resumeWithException(error) }
    addOnCanceledListener { continuation.cancel() }
}
