package ch.sakru.calibrereader.storage.googledrive

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

data class GoogleDriveHttpResponse(val status: Int, val body: ByteArray)

fun interface GoogleDriveTransport {
    suspend fun get(url: URL, headers: Map<String, String>): GoogleDriveHttpResponse
}

/** GET requests only. Bearer tokens are never forwarded to a download redirect host. */
class GoogleDriveUrlTransport(
    private val openConnection: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection }
) : GoogleDriveTransport {
    override suspend fun get(url: URL, headers: Map<String, String>): GoogleDriveHttpResponse =
        withContext(Dispatchers.IO) {
            var target = url
            repeat(5) {
                ensureActive()
                val connection = openConnection(target)
                try {
                    connection.requestMethod = "GET"
                    connection.instanceFollowRedirects = false
                    connection.connectTimeout = 20_000
                    connection.readTimeout = 30_000
                    // Signed download URLs need no OAuth header.
                    if (target.host == url.host) headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
                    val status = connection.responseCode
                    if (status in setOf(301, 302, 303, 307, 308)) {
                        val redirect = connection.getHeaderField("Location")
                            ?: throw IOException("Google Drive hat ein ungültiges Downloadziel geliefert.")
                        val next = URL(target, redirect)
                        val trusted = next.host == "www.googleapis.com" ||
                            next.host.endsWith(".googleusercontent.com") ||
                            next.host == "drive.usercontent.google.com"
                        if (next.protocol != "https" || !trusted) {
                            throw IOException("Das Downloadziel von Google Drive konnte nicht bestätigt werden.")
                        }
                        target = next
                    } else {
                        val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                        val output = ByteArrayOutputStream()
                        stream?.use {
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            while (true) {
                                ensureActive()
                                val count = it.read(buffer)
                                if (count < 0) break
                                ensureActive()
                                output.write(buffer, 0, count)
                                if (status !in 200..299 && output.size() >= 65_536) break
                            }
                        }
                        return@withContext GoogleDriveHttpResponse(status, output.toByteArray())
                    }
                } finally {
                    connection.disconnect()
                }
            }
            throw IOException("Google Drive hat zu viele Download-Weiterleitungen geliefert.")
        }
}

/** Small read-only Drive v3 client; the authorization service supplies fresh tokens. */
class GoogleDriveClient(
    private val tokens: GoogleDriveTokens,
    private val transport: GoogleDriveTransport = GoogleDriveUrlTransport()
) : GoogleDriveApi {
    private val json = JsonFactory()

    override suspend fun currentAccount(): GoogleDriveAccount {
        var email: String? = null
        var permissionId: String? = null
        parse(get("about", mapOf("fields" to "user(emailAddress,permissionId)"))) { parser ->
            parser.fields { name ->
                if (name == "user") parser.fields { field ->
                    when (field) {
                        "emailAddress" -> email = parser.stringValue()
                        "permissionId" -> permissionId = parser.stringValue()
                    }
                }
            }
        }
        return GoogleDriveAccount(
            email?.takeIf { it.isNotBlank() }
                ?: throw IOException("Google Drive hat keine Konto-Adresse zurückgegeben."),
            permissionId?.takeIf { it.isNotBlank() }
                ?: throw IOException("Google Drive hat keine Konto-ID zurückgegeben.")
        )
    }

    override suspend fun listChildren(folder: GoogleDriveReference, pageToken: String?): GoogleDrivePage {
        val query = linkedMapOf(
            "q" to ("'" + folder.id + "' in parents and trashed = false"),
            "spaces" to "drive",
            "pageSize" to "1000",
            "supportsAllDrives" to "true",
            "includeItemsFromAllDrives" to "true",
            "fields" to ("nextPageToken,incompleteSearch,files(" + ITEM_FIELDS + ")")
        )
        if (pageToken != null) query["pageToken"] = pageToken
        val items = mutableListOf<GoogleDriveItem>()
        var next: String? = null
        parse(get("files", query, folder)) { parser ->
            parser.fields { name ->
                when (name) {
                    "nextPageToken" -> next = parser.stringValue().takeIf { it.isNotBlank() }
                    "incompleteSearch" -> if (parser.booleanValue) {
                        throw IOException("Google Drive hat den Ordner nicht vollständig geladen. Bitte erneut versuchen.")
                    }
                    "files" -> {
                        if (parser.currentToken != JsonToken.START_ARRAY) throw IOException("Ungültige Drive-Dateiliste.")
                        while (parser.nextToken() != JsonToken.END_ARRAY) {
                            currentCoroutineContext().ensureActive()
                            items += parser.item()
                        }
                    }
                }
            }
        }
        return GoogleDrivePage(items, next)
    }

    override suspend fun getItem(reference: GoogleDriveReference): GoogleDriveItem {
        var item: GoogleDriveItem? = null
        parse(get("files/" + encode(reference.id), mapOf(
            "fields" to ITEM_FIELDS, "supportsAllDrives" to "true"
        ), reference)) { item = it.item() }
        return item ?: throw IOException("Die Drive-Datei konnte nicht gelesen werden.")
    }

    override suspend fun download(reference: GoogleDriveReference): ByteArray =
        get("files/" + encode(reference.id), mapOf("alt" to "media", "supportsAllDrives" to "true"), reference)

    private suspend fun get(
        path: String, parameters: Map<String, String>, reference: GoogleDriveReference? = null
    ): ByteArray {
        val url = URL("https://www.googleapis.com/drive/v3/" + path + "?" +
            parameters.entries.joinToString("&") { encode(it.key) + "=" + encode(it.value) })
        repeat(2) { attempt ->
            currentCoroutineContext().ensureActive()
            val token = tokens.accessToken()
            val headers = linkedMapOf("Authorization" to ("Bearer " + token))
            reference?.resourceKey?.let {
                headers["X-Goog-Drive-Resource-Keys"] = reference.id + "/" + it
            }
            val response = try {
                transport.get(url, headers)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: IOException) {
                throw IOException("Google Drive ist nicht erreichbar oder der Download wurde unterbrochen. Bitte erneut versuchen.", error)
            }
            currentCoroutineContext().ensureActive()
            if (response.status in 200..299) return response.body
            if (response.status == 401 && attempt == 0) {
                tokens.invalidate(token)
            } else {
                throw IOException(errorMessage(response))
            }
        }
        error("Unreachable")
    }

    private fun errorMessage(response: GoogleDriveHttpResponse): String {
        val reason = runCatching {
            json.createParser(response.body).use { parser ->
                while (parser.nextToken() != null) {
                    if (parser.currentToken == JsonToken.FIELD_NAME && parser.currentName == "reason") {
                        parser.nextToken()
                        return@use parser.valueAsString
                    }
                }
                null
            }
        }.getOrNull()
        return when {
            response.status == 401 ->
                "Die Google-Drive-Anmeldung ist abgelaufen. Bitte erneut verbinden."
            response.status == 429 || response.status >= 500 ||
                reason in setOf("rateLimitExceeded", "userRateLimitExceeded", "dailyLimitExceeded") ->
                "Google Drive ist vorübergehend ausgelastet. Bitte später erneut versuchen."
            response.status == 404 ->
                "Die Datei oder der Ordner wurde in Google Drive nicht gefunden. Bitte Freigabe und Bibliotheksordner prüfen."
            response.status == 403 && reason in setOf("accessNotConfigured", "SERVICE_DISABLED") ->
                "Die Google Drive API ist für diese App noch nicht aktiviert."
            response.status == 403 ->
                "Google Drive hat den Zugriff verweigert. Bitte Leseberechtigung und Freigabe prüfen."
            else -> "Die Daten konnten nicht aus Google Drive geladen werden. Bitte erneut versuchen."
        }
    }

    private suspend fun parse(bytes: ByteArray, read: suspend (JsonParser) -> Unit) =
        withContext(Dispatchers.IO) {
            try {
                json.createParser(bytes).use { parser ->
                    parser.nextToken()
                    read(parser)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: JsonProcessingException) {
                throw IOException("Die Antwort von Google Drive konnte nicht gelesen werden.", error)
            } catch (error: IOException) {
                throw error
            } catch (error: Exception) {
                throw IOException("Die Antwort von Google Drive konnte nicht gelesen werden.", error)
            }
        }

    private fun JsonParser.item(): GoogleDriveItem {
        var id: String? = null
        var name: String? = null
        var mime: String? = null
        var key: String? = null
        var targetId: String? = null
        var targetKey: String? = null
        var targetMime: String? = null
        fields { field ->
            when (field) {
                "id" -> id = stringValue()
                "name" -> name = stringValue()
                "mimeType" -> mime = stringValue()
                "resourceKey" -> key = stringValue()
                "shortcutDetails" -> fields { detail ->
                    when (detail) {
                        "targetId" -> targetId = stringValue()
                        "targetResourceKey" -> targetKey = stringValue()
                        "targetMimeType" -> targetMime = stringValue()
                    }
                }
            }
        }
        return GoogleDriveItem(
            GoogleDriveReference(id ?: throw IOException("Drive-Datei ohne ID."), key),
            name ?: throw IOException("Drive-Datei ohne Namen."),
            mime ?: throw IOException("Drive-Datei ohne Dateityp."),
            targetId?.let { GoogleDriveReference(it, targetKey) }, targetMime
        )
    }

    private inline fun JsonParser.fields(read: (String) -> Unit) {
        if (currentToken != JsonToken.START_OBJECT) throw IOException("Ungültige Drive-Antwort.")
        while (nextToken() != JsonToken.END_OBJECT) {
            if (currentToken != JsonToken.FIELD_NAME) throw IOException("Unvollständige Drive-Antwort.")
            val name = currentName
            if (nextToken() == null) throw IOException("Unvollständige Drive-Antwort.")
            read(name)
            skipChildren()
        }
    }

    private fun JsonParser.stringValue(): String {
        if (currentToken != JsonToken.VALUE_STRING) throw IOException("Ungültiger Text in der Drive-Antwort.")
        return text
    }

    private companion object {
        const val ITEM_FIELDS = "id,name,mimeType,resourceKey,shortcutDetails(targetId,targetMimeType,targetResourceKey)"
        fun encode(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
    }
}
