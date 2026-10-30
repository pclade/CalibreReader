package ch.sakru.calibrereader.storage.dropbox

import ch.sakru.calibrereader.storage.CloudItem
import ch.sakru.calibrereader.storage.CloudStorage
import ch.sakru.calibrereader.storage.requireRelativeCloudPath
import com.dropbox.core.DbxException
import com.dropbox.core.InvalidAccessTokenException
import com.dropbox.core.NetworkIOException
import com.dropbox.core.oauth.DbxOAuthException
import com.dropbox.core.v2.DbxClientV2
import com.dropbox.core.v2.files.FileMetadata
import com.dropbox.core.v2.files.FolderMetadata
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Read-only adapter. The SDK refreshes short-lived access tokens when necessary. */
class DropboxStorage(private val clientProvider: () -> DbxClientV2) : CloudStorage {
    override val rootFolderId: String = ""

    override suspend fun listChildren(folderId: String?): List<CloudItem> = read {
        val files = clientProvider().files()
        var page = files.listFolder(folderId ?: rootFolderId)
        val items = linkedMapOf<String, CloudItem>()
        while (true) {
            ensureActive()
            for (entry in page.entries) {
                val item = when (entry) {
                    is FolderMetadata -> CloudItem(entry.id, entry.name, true)
                    is FileMetadata -> CloudItem(entry.id, entry.name, false)
                    else -> null
                }
                // Cursor pages can include a later update to an already-seen entry.
                if (item != null) items[item.id] = item
            }
            if (!page.hasMore) break
            page = files.listFolderContinue(page.cursor)
        }
        items.values.sortedWith(
            compareByDescending<CloudItem> { it.isFolder }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
        )
    }

    override suspend fun downloadFile(itemId: String): ByteArray = read {
        clientProvider().files().download(itemId).use { download ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                ensureActive()
                val count = download.inputStream.read(buffer)
                if (count < 0) break
                ensureActive()
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
    }

    override suspend fun downloadFileByPath(rootId: String, relativePath: String): ByteArray {
        val path = requireRelativeCloudPath(relativePath)
        return downloadFile(rootId + "/" + path)
    }

    private suspend fun <T> read(block: CoroutineScope.() -> T): T = withContext(Dispatchers.IO) {
        try {
            ensureActive()
            block()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: InvalidAccessTokenException) {
            throw IOException("Bitte von Dropbox abmelden und erneut verbinden.", error)
        } catch (error: DbxOAuthException) {
            throw IOException("Bitte von Dropbox abmelden und erneut verbinden.", error)
        } catch (error: NetworkIOException) {
            throw IOException("Dropbox ist nicht erreichbar. Bitte Internetverbindung prüfen.", error)
        } catch (error: DbxException) {
            throw IOException("Die Datei oder der Ordner konnte nicht aus Dropbox geladen werden. Bitte Zugriff prüfen und erneut versuchen.", error)
        } catch (error: IOException) {
            throw IOException("Der Download wurde unterbrochen. Bitte erneut versuchen.", error)
        }
    }
}
