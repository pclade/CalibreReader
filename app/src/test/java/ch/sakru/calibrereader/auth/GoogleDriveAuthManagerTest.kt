package ch.sakru.calibrereader.auth

import ch.sakru.calibrereader.storage.googledrive.*
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GoogleDriveAuthManagerTest {
    @Test fun firstConnectionRequestsAnAccountAndSavesItsStableDriveIdentity() = runTest {
        val store = MemoryAccounts(null)
        val authorization = FakeAuthorization()
        val manager = GoogleDriveAuthManager(authorization, store, factory(),
            StandardTestDispatcher(testScheduler))
        assertNull(manager.restoreSession())
        assertTrue(authorization.calls.isEmpty())
        val user = manager.startSignIn(4)
        assertEquals("permission-a", user!!.accountId)
        assertEquals(ACCOUNT_A, store.account)
        assertEquals(listOf(null to true), authorization.calls)
    }

    @Test fun restoredAccountRenewsTokensForTheSameEmail() = runTest {
        val store = MemoryAccounts(ACCOUNT_A)
        val authorization = FakeAuthorization()
        val manager = GoogleDriveAuthManager(authorization, store, factory(),
            StandardTestDispatcher(testScheduler))
        assertEquals(ACCOUNT_A.permissionId, manager.restoreSession()!!.accountId)
        authorization.token = "access-renewed"
        val bytes = manager.requireClient().download(GoogleDriveReference("book"))
        assertEquals("access-renewed", String(bytes))
        assertEquals(ACCOUNT_A.email to false, authorization.calls.last())
    }

    @Test fun partialConsentIsRejectedWithoutPublishingASession() = runTest {
        val store = MemoryAccounts(null)
        val authorization = FakeAuthorization().apply { scopes = emptySet() }
        val manager = GoogleDriveAuthManager(authorization, store, factory(),
            StandardTestDispatcher(testScheduler))
        val error = runCatching { manager.startSignIn(1) }.exceptionOrNull()
        assertTrue(error is IOException)
        assertTrue(error!!.message!!.contains("Leseberechtigung"))
        assertNull(store.account)
        assertThrows(IllegalStateException::class.java) { manager.requireClient() }
    }

    @Test fun aDifferentAccountCannotReplaceTheStoredAccountDuringRestore() = runTest {
        val store = MemoryAccounts(ACCOUNT_A)
        val manager = GoogleDriveAuthManager(FakeAuthorization(), store, factory { ACCOUNT_B },
            StandardTestDispatcher(testScheduler))
        assertTrue(runCatching { manager.restoreSession() }.exceptionOrNull() is IOException)
        assertEquals(ACCOUNT_A, store.account)
        assertThrows(IllegalStateException::class.java) { manager.requireClient() }
    }

    @Test fun aLateRestoreCannotOverwriteANewerGoogleAccount() = runTest {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val authorization = FakeAuthorization()
        val store = MemoryAccounts(ACCOUNT_A)
        val manager = GoogleDriveAuthManager(authorization, store, factory { token ->
            if (token == "access-a") {
                started.complete(Unit)
                release.await()
                ACCOUNT_A
            } else ACCOUNT_B
        }, StandardTestDispatcher(testScheduler))
        val old = async { runCatching { manager.restoreSession() } }
        runCurrent()
        started.await()
        manager.signOut()
        store.account = ACCOUNT_B
        authorization.token = "access-b"
        assertEquals(ACCOUNT_B.permissionId, manager.restoreSession()!!.accountId)
        release.complete(Unit)
        assertTrue(old.await().exceptionOrNull() is CancellationException)
        assertEquals(ACCOUNT_B, store.account)
        assertEquals("access-b", String(manager.requireClient().download(GoogleDriveReference("book"))))
    }

    @Test fun aCapturedClientCannotReadAfterSignOutOrAccountChange() = runTest {
        val store = MemoryAccounts(ACCOUNT_A)
        val authorization = FakeAuthorization()
        val manager = GoogleDriveAuthManager(authorization, store, factory(),
            StandardTestDispatcher(testScheduler))
        manager.restoreSession()
        val oldClient = manager.requireClient()
        manager.signOut()
        assertEquals(listOf(ACCOUNT_A.email), authorization.revoked)
        assertNull(store.account)
        assertTrue(runCatching { oldClient.download(GoogleDriveReference("book")) }
            .exceptionOrNull() is CancellationException)
    }

    @Test fun failedRevocationKeepsTheAccountHintForARetry() = runTest {
        val store = MemoryAccounts(ACCOUNT_A)
        val authorization = FakeAuthorization().apply { revokeFails = true }
        val manager = GoogleDriveAuthManager(authorization, store, factory(),
            StandardTestDispatcher(testScheduler))
        assertTrue(runCatching { manager.signOut() }.exceptionOrNull() is IOException)
        assertEquals(ACCOUNT_A, store.account)
        authorization.revokeFails = false
        manager.signOut()
        assertNull(store.account)
    }

    private class MemoryAccounts(var account: GoogleDriveAccount?) : GoogleDriveAccounts {
        override fun read() = account
        override fun save(account: GoogleDriveAccount) { this.account = account }
        override fun clear() { account = null }
    }

    private class FakeAuthorization : GoogleDriveAuthorization {
        var token = "access-a"
        var scopes = setOf(GOOGLE_DRIVE_READONLY_SCOPE)
        var revokeFails = false
        val calls = mutableListOf<Pair<String?, Boolean>>()
        val revoked = mutableListOf<String>()
        override suspend fun authorize(email: String?, chooseAccount: Boolean): GoogleDriveGrant {
            calls += email to chooseAccount
            return GoogleDriveGrant.Granted(token, scopes)
        }
        override suspend fun clearToken(token: String) = Unit
        override suspend fun revokeAccess(email: String) {
            if (revokeFails) throw IOException("fixture offline")
            revoked += email
        }
    }

    private fun factory(profile: suspend (String) -> GoogleDriveAccount = { ACCOUNT_A }) =
        GoogleDriveApiFactory { tokens ->
            object : GoogleDriveApi {
                override suspend fun currentAccount() = profile(tokens.accessToken())
                override suspend fun download(reference: GoogleDriveReference) = tokens.accessToken().toByteArray()
                override suspend fun listChildren(folder: GoogleDriveReference, pageToken: String?): GoogleDrivePage =
                    error("Unexpected folder request")
                override suspend fun getItem(reference: GoogleDriveReference): GoogleDriveItem =
                    error("Unexpected metadata request")
            }
        }

    private companion object {
        val ACCOUNT_A = GoogleDriveAccount("a@example.invalid", "permission-a")
        val ACCOUNT_B = GoogleDriveAccount("b@example.invalid", "permission-b")
    }
}
