package ch.sakru.calibrereader.storage.dropbox

import com.dropbox.core.DbxRequestConfig
import com.dropbox.core.http.HttpRequestor
import com.dropbox.core.oauth.DbxCredential
import com.dropbox.core.v2.DbxClientV2
import com.fasterxml.jackson.core.JsonFactory
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.IOException
import java.io.OutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

class DropboxStorageTest {
    @Test fun allPagesAreReadAndFoldersSortBeforeFiles() = runBlocking {
        val http = FakeHttp().apply {
            json("""{"entries":[$FILE,{".tag":"folder","name":"Zulu","id":"id:folder-z"}],"cursor":"next-page","has_more":true}""")
            json("""{"entries":[{".tag":"folder","name":"Alpha","id":"id:folder-a"}],"cursor":"done","has_more":false}""")
        }
        val items = storage(http).listChildren(null)
        assertEquals(listOf("Alpha", "Zulu", "test.epub"), items.map { it.name })
        assertEquals(listOf(true, true, false), items.map { it.isFolder })
        assertEquals(listOf("/2/files/list_folder", "/2/files/list_folder/continue"),
            http.requests.map { java.net.URI(it.url).path })
        assertEquals("", field(http.requests[0].body, "path"))
        assertEquals("next-page", field(http.requests[1].body, "cursor"))
    }

    @Test fun overlappingCursorPagesKeepOnlyTheLatestEntryForEachId() = runBlocking {
        val http = FakeHttp().apply {
            json("""{"entries":[{".tag":"folder","name":"Before","id":"id:same"}],"cursor":"next","has_more":true}""")
            json("""{"entries":[{".tag":"folder","name":"After","id":"id:same"}],"cursor":"done","has_more":false}""")
        }
        assertEquals(listOf("After"), storage(http).listChildren(null).map { it.name })
    }

    @Test fun relativeDownloadsKeepFolderIdAndUnicodeNames() = runBlocking {
        val http = FakeHttp().apply { download("epub") }
        assertArrayEquals("epub".toByteArray(),
            storage(http).downloadFileByPath("id:library", "Şenol/Über das Lesen (7)/Buch.epub"))
        assertEquals("id:library/Şenol/Über das Lesen (7)/Buch.epub",
            field(http.requests.single().headers.getValue("Dropbox-API-Arg"), "path"))
    }

    @Test fun storageRootSupportsLibraryFiles() = runBlocking {
        val http = FakeHttp().apply { download("cover") }
        val storage = storage(http)
        storage.downloadFileByPath(storage.rootFolderId, "Author/Book/cover.jpg")
        assertEquals("/Author/Book/cover.jpg",
            field(http.requests.single().headers.getValue("Dropbox-API-Arg"), "path"))
    }

    @Test fun unsafeRelativePathsNeverReachTheService() = runBlocking {
        val http = FakeHttp()
        for (path in listOf("", "/outside.epub", "../outside.epub", "Book/../../outside",
            "Book//file", "Book/./file", "Book\\file", "Book/\u0000file")) {
            try {
                storage(http).downloadFileByPath("id:library", path)
                fail("Unsafe path accepted: " + path)
            } catch (_: IllegalArgumentException) { }
        }
        assertTrue(http.requests.isEmpty())
    }

    @Test fun expiredTokenIsRefreshedWithoutAnAppSecret() = runBlocking {
        val http = FakeHttp().apply {
            json("""{"access_token":"new-access","token_type":"bearer","expires_in":14400}""")
            json("""{"entries":[],"cursor":"done","has_more":false}""")
        }
        val config = DbxRequestConfig.newBuilder("test").withHttpRequestor(http).build()
        val credential = DbxCredential("expired-access", 0L, "fixture-refresh", "publicappkey")
        DropboxStorage { DbxClientV2(config, credential) }.listChildren(null)
        assertEquals(2, http.requests.size)
        assertEquals("/oauth2/token", java.net.URI(http.requests[0].url).path)
        assertTrue(http.requests[0].body.contains("grant_type=refresh_token"))
        assertFalse(http.requests[0].body.contains("client_secret"))
        assertEquals("Bearer new-access", http.requests[1].headers["Authorization"])
    }

    @Test fun failedDownloadIsReportedToTheCaller() {
        val http = FakeHttp().apply { json("service unavailable", 500) }
        val error = assertThrows(IOException::class.java) {
            runBlocking { storage(http).downloadFile("id:book") }
        }
        assertTrue(error.message!!.contains("Dropbox"))
    }

    @Test fun cancelledDownloadClosesTheStreamWithoutReadingFurtherChunks() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val release = CountDownLatch(1)
        val reads = AtomicInteger()
        val closed = AtomicBoolean()
        val http = FakeHttp().apply {
            stream(object : InputStream() {
                override fun read(): Int = error("Expected bulk reads")
                override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                    check(reads.incrementAndGet() == 1) { "Read continued after cancellation." }
                    buffer[offset] = 65
                    started.complete(Unit)
                    check(release.await(10, TimeUnit.SECONDS))
                    return 1
                }
                override fun close() { closed.set(true) }
            })
        }
        val job = launch { storage(http).downloadFile("id:book") }
        try {
            withTimeout(10_000) { started.await() }
            job.cancel()
        } finally {
            release.countDown()
        }
        job.join()
        assertTrue(closed.get())
        assertEquals(1, reads.get())
    }

    private fun storage(http: FakeHttp): DropboxStorage {
        val config = DbxRequestConfig.newBuilder("test").withHttpRequestor(http).build()
        val client = DbxClientV2(config, "fixture-access")
        return DropboxStorage { client }
    }

    private fun field(json: String, name: String): String =
        JsonFactory().createParser(json).use { parser ->
            parser.nextToken()
            while (parser.nextToken() != null) {
                if (parser.currentName == name) {
                    parser.nextToken()
                    return@use parser.text
                }
                parser.skipChildren()
            }
            error("Missing field " + name)
        }

    private data class Request(val url: String, val headers: Map<String, String>, val body: String)

    private class FakeHttp : HttpRequestor() {
        val requests = mutableListOf<Request>()
        private val responses = ArrayDeque<Response>()
        fun json(body: String, status: Int = 200) {
            responses.addLast(Response(status, ByteArrayInputStream(body.toByteArray()),
                mapOf("Content-Type" to listOf("application/json"))))
        }
        fun download(body: String) = stream(ByteArrayInputStream(body.toByteArray()))
        fun stream(body: InputStream) {
            responses.addLast(Response(200, body, mapOf(
                "Dropbox-API-Result" to listOf(FILE), "Content-Type" to listOf("application/octet-stream"))))
        }
        override fun doGet(url: String, headers: Iterable<Header>): Response = respond(url, headers, "")
        override fun startPost(url: String, headers: Iterable<Header>): Uploader = object : Uploader() {
            val bytes = ByteArrayOutputStream()
            override fun getBody(): OutputStream = bytes
            override fun close() = Unit
            override fun abort() = Unit
            override fun finish(): Response = respond(url, headers, bytes.toString(Charsets.UTF_8.name()))
        }
        override fun startPut(url: String, headers: Iterable<Header>): Uploader =
            error("The read-only adapter must never upload files.")
        private fun respond(url: String, headers: Iterable<Header>, body: String): Response {
            requests += Request(url, headers.associate { it.key to it.value }, body)
            check(responses.isNotEmpty()) { "Unexpected HTTP request: " + url }
            return responses.removeFirst()
        }
    }
    private companion object {
        const val FILE = """{".tag":"file","name":"test.epub","id":"id:book","client_modified":"2026-01-01T00:00:00Z","server_modified":"2026-01-01T00:00:00Z","rev":"0123456789","size":4}"""
    }
}
