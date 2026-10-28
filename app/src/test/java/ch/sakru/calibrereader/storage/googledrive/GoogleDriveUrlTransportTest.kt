package ch.sakru.calibrereader.storage.googledrive

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
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

class GoogleDriveUrlTransportTest {
    @Test fun redirectsDoNotReceiveTheApiBearerTokenOrResourceKeys() = runBlocking {
        val initial = URL("https://www.googleapis.com/drive/v3/files/book?alt=media")
        val target = URL("https://fixture.googleusercontent.com/download")
        val api = FakeConnection(initial, 302, ByteArrayInputStream(byteArrayOf()), target.toString())
        val download = FakeConnection(target, 200, ByteArrayInputStream("epub".toByteArray()))
        val transport = GoogleDriveUrlTransport { if (it == initial) api else download }
        val response = transport.get(initial, mapOf(
            "Authorization" to "Bearer fixture-token", "X-Goog-Drive-Resource-Keys" to "book/key"
        ))
        assertEquals("epub", String(response.body))
        assertEquals("Bearer fixture-token", api.getRequestProperty("Authorization"))
        assertNull(download.getRequestProperty("Authorization"))
        assertNull(download.getRequestProperty("X-Goog-Drive-Resource-Keys"))
        assertTrue(api.disconnected && download.disconnected)
    }

    @Test fun untrustedRedirectIsRejectedBeforeOpeningAConnection() = runBlocking {
        val url = URL("https://www.googleapis.com/drive/v3/files/book")
        val connection = FakeConnection(url, 302, ByteArrayInputStream(byteArrayOf()), "https://example.invalid/steal")
        var opened = 0
        val transport = GoogleDriveUrlTransport { opened++; connection }
        assertTrue(runCatching { transport.get(url, mapOf("Authorization" to "Bearer fixture")) }
            .exceptionOrNull() is IOException)
        assertEquals(1, opened)
        assertTrue(connection.disconnected)
    }

    @Test fun cancelledDownloadClosesItsStreamAndConnection() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val release = CountDownLatch(1)
        val reads = AtomicInteger()
        val closed = AtomicBoolean()
        val body = object : InputStream() {
            override fun read(): Int = error("Expected a bulk read")
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                check(reads.incrementAndGet() == 1) { "Read continued after cancellation" }
                buffer[offset] = 65
                started.complete(Unit)
                check(release.await(10, TimeUnit.SECONDS))
                return 1
            }
            override fun close() { closed.set(true) }
        }
        val url = URL("https://www.googleapis.com/drive/v3/files/book?alt=media")
        val connection = FakeConnection(url, 200, body)
        val transport = GoogleDriveUrlTransport { connection }
        val job = launch { transport.get(url, emptyMap()) }
        try {
            withTimeout(10_000) { started.await() }
            job.cancel()
        } finally {
            release.countDown()
        }
        job.join()
        assertEquals(1, reads.get())
        assertTrue(closed.get())
        assertTrue(connection.disconnected)
    }

    private class FakeConnection(
        url: URL, private val status: Int, private val body: InputStream,
        private val location: String? = null
    ) : HttpURLConnection(url) {
        var disconnected = false
        override fun connect() = Unit
        override fun disconnect() { disconnected = true }
        override fun usingProxy() = false
        override fun getResponseCode() = status
        override fun getInputStream() = body
        override fun getErrorStream() = body
        override fun getHeaderField(name: String): String? = if (name == "Location") location else null
    }
}
