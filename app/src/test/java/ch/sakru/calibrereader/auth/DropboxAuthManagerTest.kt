package ch.sakru.calibrereader.auth

import com.dropbox.core.DbxRequestConfig
import com.dropbox.core.http.HttpRequestor
import com.dropbox.core.oauth.DbxCredential
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test

class DropboxAuthManagerTest {
    @Test fun lateRestoreCannotOverwriteTheNewAccountAfterSignOut() = runBlocking {
        val http = AccountHttp()
        val store = MemoryCredentials(credential("a"))
        val config = DbxRequestConfig.newBuilder("test").withHttpRequestor(http).build()
        val manager = DropboxAuthManager(store, "publicappkey", config)
        val old = async(Dispatchers.Default) { runCatching { manager.restoreSession() } }
        try {
            assertTrue(withContext(Dispatchers.IO) { http.oldStarted.await(10, TimeUnit.SECONDS) })
            manager.signOut()
            store.credential = credential("b")
            assertEquals(accountId("b"), manager.restoreSession()!!.accountId)
        } finally {
            http.releaseOld.countDown()
        }
        assertTrue(old.await().exceptionOrNull() is CancellationException)
        assertEquals("access-b", store.credential!!.accessToken)
        manager.requireClient().files().listFolder("")
        assertEquals("Bearer access-b", http.lastFileAuthorization)
    }

    private class MemoryCredentials(@Volatile var credential: DbxCredential?) : DropboxCredentials {
        override var awaitingOAuth = false
        override fun read(): DbxCredential? = credential
        override fun save(credential: DbxCredential) { this.credential = credential }
        override fun clear() { credential = null; awaitingOAuth = false }
    }

    private class AccountHttp : HttpRequestor() {
        val oldStarted = CountDownLatch(1)
        val releaseOld = CountDownLatch(1)
        @Volatile var lastFileAuthorization: String? = null
        override fun doGet(url: String, headers: Iterable<Header>): Response = error("Unexpected GET")
        override fun startPut(url: String, headers: Iterable<Header>): Uploader = error("Unexpected upload")
        override fun startPost(url: String, headers: Iterable<Header>): Uploader = object : Uploader() {
            private val body = ByteArrayOutputStream()
            override fun getBody(): OutputStream = body
            override fun close() = Unit
            override fun abort() = Unit
            override fun finish(): Response {
                val authorization = headers.associate { it.key to it.value }.getValue("Authorization")
                val json = when {
                    url.endsWith("/users/get_current_account") -> {
                        val suffix = if (authorization == "Bearer access-a") "a" else "b"
                        if (suffix == "a") {
                            oldStarted.countDown()
                            check(releaseOld.await(10, TimeUnit.SECONDS))
                        }
                        account(suffix)
                    }
                    url.endsWith("/files/list_folder") -> {
                        lastFileAuthorization = authorization
                        """{"entries":[],"cursor":"done","has_more":false}"""
                    }
                    else -> error("Unexpected endpoint: " + url)
                }
                return Response(200, ByteArrayInputStream(json.toByteArray()),
                    mapOf("Content-Type" to listOf("application/json")))
            }
        }
    }

    private companion object {
        fun accountId(suffix: String) = "dbid:" + suffix.repeat(35)
        fun credential(suffix: String) = DbxCredential(
            "access-" + suffix, System.currentTimeMillis() + 3_600_000L,
            "refresh-" + suffix, "publicappkey"
        )
        fun account(suffix: String): String {
            val id = accountId(suffix)
            return """{"account_id":"$id","name":{"given_name":"Test","surname":"User","familiar_name":"Test","display_name":"Test User","abbreviated_name":"TU"},"email":"$suffix@example.invalid","email_verified":true,"disabled":false,"locale":"en","referral_link":"https://www.dropbox.com/referrals/fixture","is_paired":false,"account_type":{".tag":"basic"},"root_info":{".tag":"user","root_namespace_id":"1","home_namespace_id":"1"}}"""
        }
    }
}
