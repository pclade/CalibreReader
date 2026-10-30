package ch.sakru.calibrereader.storage.googledrive

import java.io.IOException
import java.net.URL
import java.net.URLDecoder
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class GoogleDriveStorageTest {
    @Test fun listsAllPagesIncludingAnEmptyPageAndDeduplicatesById() = runBlocking {
        val http = FakeHttp().apply {
            json("""{"files":[{"id":"book","name":"Before.epub","mimeType":"application/epub+zip"}],"nextPageToken":"page-2"}""")
            json("""{"files":[],"nextPageToken":"page-3"}""")
            json("""{"files":[{"id":"book","name":"After.epub","mimeType":"application/epub+zip"},{"id":"folder","name":"Zulu","mimeType":"application/vnd.google-apps.folder"}]}""")
        }
        val items = storage(http).listChildren(null)
        assertEquals(listOf("Zulu", "After.epub"), items.map { it.name })
        assertEquals("'root' in parents and trashed = false", query(http.requests[0].url)["q"])
        assertEquals("page-2", query(http.requests[1].url)["pageToken"])
        assertEquals("page-3", query(http.requests[2].url)["pageToken"])
        assertEquals("true", query(http.requests[0].url)["supportsAllDrives"])
    }

    @Test fun resolvesUnicodeCalibrePathsByIdsAndReusesDirectoryListingsForCovers() = runBlocking {
        val http = FakeHttp().apply {
            json("""{"files":[{"id":"author","name":"Şenol O'Neil","mimeType":"application/vnd.google-apps.folder"}]}""")
            json("""{"files":[{"id":"title","name":"Über das Lesen (7)","mimeType":"application/vnd.google-apps.folder"}]}""")
            json("""{"files":[{"id":"epub","name":"Buch.epub","mimeType":"application/epub+zip"},{"id":"cover","name":"cover.jpg","mimeType":"image/jpeg"}]}""")
            bytes("epub bytes")
            bytes("cover bytes")
        }
        val storage = storage(http)
        assertEquals("epub bytes", String(storage.downloadFileByPath("library",
            "Şenol O'Neil/Über das Lesen (7)/Buch.epub")))
        assertEquals("cover bytes", String(storage.downloadFileByPath("library",
            "Şenol O'Neil/Über das Lesen (7)/cover.jpg")))
        assertEquals(5, http.requests.size)
        assertEquals("/drive/v3/files/epub", http.requests[3].url.path)
        assertEquals("media", query(http.requests[3].url)["alt"])
        assertEquals("/drive/v3/files/cover", http.requests[4].url.path)
    }

    @Test fun libraryAtMyDriveRootCanDownloadMetadata() = runBlocking {
        val http = FakeHttp().apply {
            json("""{"files":[{"id":"metadata","name":"metadata.db","mimeType":"application/octet-stream"}]}""")
            bytes("sqlite fixture")
        }
        val storage = storage(http)
        assertEquals("sqlite fixture", String(storage.downloadFileByPath(storage.rootFolderId, "metadata.db")))
    }

    @Test fun duplicateNamesFailInsteadOfOpeningAnArbitraryBook() = runBlocking {
        val http = FakeHttp().apply {
            json("""{"files":[{"id":"one","name":"Book.epub","mimeType":"application/epub+zip"},{"id":"two","name":"Book.epub","mimeType":"application/epub+zip"}]}""")
        }
        val error = runCatching { storage(http).downloadFileByPath("library", "Book.epub") }.exceptionOrNull()
        assertTrue(error is IOException)
        assertTrue(error!!.message!!.contains("mehrfach"))
        assertEquals(1, http.requests.size)
    }

    @Test fun folderAndFileShortcutsPreserveResourceKeys() = runBlocking {
        val http = FakeHttp().apply {
            json("""{"files":[{"id":"link","name":"Calibre","mimeType":"application/vnd.google-apps.shortcut","shortcutDetails":{"targetId":"library","targetMimeType":"application/vnd.google-apps.folder","targetResourceKey":"folder-key"}}]}""")
            json("""{"files":[{"id":"epub-link","name":"Book.epub","mimeType":"application/vnd.google-apps.shortcut","shortcutDetails":{"targetId":"book","targetMimeType":"application/epub+zip","targetResourceKey":"file-key"}}]}""")
            json("""{"id":"book","name":"Original.epub","mimeType":"application/epub+zip"}""")
            bytes("linked epub")
        }
        val storage = storage(http)
        val folder = storage.listChildren(null).single()
        assertTrue(folder.isFolder)
        assertEquals("library|folder-key", folder.id)
        storage.listChildren(folder.id)
        assertEquals("linked epub", String(storage.downloadFileByPath(folder.id, "Book.epub")))
        assertEquals("library/folder-key", http.requests[1].headers["X-Goog-Drive-Resource-Keys"])
        assertEquals("book/file-key", http.requests.last().headers["X-Goog-Drive-Resource-Keys"])
    }

    @Test fun savedFileReferenceRetainsItsResourceKeyEvenIfMetadataOmitsIt() = runBlocking {
        val http = FakeHttp().apply {
            json("""{"id":"book","name":"Book.epub","mimeType":"application/epub+zip"}""")
            bytes("book")
        }
        assertEquals("book", String(storage(http).downloadFile("book|file-key")))
        assertTrue(http.requests.all { it.headers["X-Goog-Drive-Resource-Keys"] == "book/file-key" })
    }

    @Test fun unsafePathsNeverReachGoogleDrive() = runBlocking {
        val http = FakeHttp()
        val storage = storage(http)
        for (path in listOf("", "/outside.epub", "../outside.epub", "Book//file", "Book/./file",
            "Book/../../outside", "Book\\file", "Book/\u0000file")) {
            assertTrue(runCatching { storage.downloadFileByPath("library", path) }
                .exceptionOrNull() is IllegalArgumentException)
        }
        assertTrue(http.requests.isEmpty())
    }

    @Test fun accountSwitchCannotReuseAnotherAccountsDirectoryCache() = runBlocking {
        val first = FakeHttp().apply {
            json("""{"files":[{"id":"book-a","name":"Book.epub","mimeType":"application/epub+zip"}]}""")
            bytes("account a")
        }
        val second = FakeHttp().apply {
            json("""{"files":[{"id":"book-b","name":"Book.epub","mimeType":"application/epub+zip"}]}""")
            bytes("account b")
        }
        var client: GoogleDriveApi = GoogleDriveClient(FakeTokens(), first)
        val storage = GoogleDriveStorage { client }
        assertEquals("account a", String(storage.downloadFileByPath("library", "Book.epub")))
        client = GoogleDriveClient(FakeTokens(), second)
        assertEquals("account b", String(storage.downloadFileByPath("library", "Book.epub")))
        assertEquals("/drive/v3/files/book-b", second.requests.last().url.path)
    }

    @Test fun aRepeatedPageTokenCannotCauseAnEndlessLoop() = runBlocking {
        val http = FakeHttp().apply {
            json("""{"files":[],"nextPageToken":"same"}""")
            json("""{"files":[],"nextPageToken":"same"}""")
        }
        assertTrue(runCatching { storage(http).listChildren(null) }.exceptionOrNull() is IOException)
        assertEquals(2, http.requests.size)
    }

    @Test fun incompleteSearchIsReportedInsteadOfCachingAnIncompleteLibrary() = runBlocking {
        val http = FakeHttp().apply { json("""{"files":[],"incompleteSearch":true}""") }
        val error = runCatching { storage(http).listChildren(null) }.exceptionOrNull()
        assertTrue(error is IOException)
        assertTrue(error!!.message!!.contains("nicht vollständig"))
    }

    @Test fun googleWorkspaceDocumentsAreRejectedAsBookFiles() = runBlocking {
        val http = FakeHttp().apply {
            json("""{"id":"doc","name":"Book.epub","mimeType":"application/vnd.google-apps.document"}""")
        }
        assertTrue(runCatching { storage(http).downloadFile("doc") }.exceptionOrNull() is IOException)
        assertEquals(1, http.requests.size)
    }

    @Test fun invalidTokenIsClearedAndTheRequestIsRetriedOnce() = runBlocking {
        val http = FakeHttp().apply {
            json("""{"error":{"code":401}}""", 401)
            json("""{"user":{"emailAddress":"a@example.invalid","permissionId":"account-a"}}""")
        }
        val tokens = FakeTokens()
        val account = GoogleDriveClient(tokens, http).currentAccount()
        assertEquals("account-a", account.permissionId)
        assertEquals(listOf("old-access"), tokens.invalidated)
        assertEquals(listOf("Bearer old-access", "Bearer new-access"),
            http.requests.map { it.headers["Authorization"] })
    }

    @Test fun persistentUnauthorizedErrorDoesNotRetryIndefinitely() = runBlocking {
        val http = FakeHttp().apply {
            json("{}", 401)
            json("{}", 401)
        }
        val tokens = FakeTokens()
        val error = runCatching { GoogleDriveClient(tokens, http).currentAccount() }.exceptionOrNull()
        assertTrue(error is IOException)
        assertTrue(error!!.message!!.contains("abgelaufen"))
        assertEquals(2, http.requests.size)
    }

    @Test fun quotaFailuresHaveAnActionableMessage() = runBlocking {
        val http = FakeHttp().apply {
            json("""{"error":{"errors":[{"reason":"userRateLimitExceeded"}]}}""", 403)
        }
        val error = runCatching { storage(http).listChildren(null) }.exceptionOrNull()
        assertTrue(error is IOException)
        assertTrue(error!!.message!!.contains("später"))
    }

    private fun storage(http: FakeHttp): GoogleDriveStorage {
        val client = GoogleDriveClient(FakeTokens(), http)
        return GoogleDriveStorage { client }
    }

    private fun query(url: URL): Map<String, String> = url.query.split('&').associate {
        val parts = it.split('=', limit = 2)
        URLDecoder.decode(parts[0], "UTF-8") to URLDecoder.decode(parts[1], "UTF-8")
    }

    private class FakeTokens : GoogleDriveTokens {
        var current = "old-access"
        val invalidated = mutableListOf<String>()
        override suspend fun accessToken() = current
        override suspend fun invalidate(token: String) { invalidated += token; current = "new-access" }
    }

    private data class Request(val url: URL, val headers: Map<String, String>)

    private class FakeHttp : GoogleDriveTransport {
        val requests = mutableListOf<Request>()
        val responses = ArrayDeque<GoogleDriveHttpResponse>()
        fun json(body: String, status: Int = 200) { responses.addLast(GoogleDriveHttpResponse(status, body.toByteArray())) }
        fun bytes(body: String) = json(body)
        override suspend fun get(url: URL, headers: Map<String, String>): GoogleDriveHttpResponse {
            requests += Request(url, headers.toMap())
            check(responses.isNotEmpty()) { "Unexpected Drive request: " + url }
            return responses.removeFirst()
        }
    }
}
