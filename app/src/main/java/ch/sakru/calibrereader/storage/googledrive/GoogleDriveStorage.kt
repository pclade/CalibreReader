package ch.sakru.calibrereader.storage.googledrive

import ch.sakru.calibrereader.storage.CloudItem
import ch.sakru.calibrereader.storage.CloudStorage
import ch.sakru.calibrereader.storage.requireRelativeCloudPath
import java.io.IOException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Resolves Calibre paths through folder IDs; duplicate Drive names are never guessed. */
class GoogleDriveStorage(private val clientProvider: () -> GoogleDriveApi) : CloudStorage {
    override val rootFolderId = "root"
    private data class CacheKey(val client: GoogleDriveApi, val folder: GoogleDriveReference)
    private data class CachedFolder(val items: List<GoogleDriveItem>, val savedAt: Long)
    private val folders = LinkedHashMap<CacheKey, CachedFolder>(16, 0.75f, true)

    override suspend fun listChildren(folderId: String?): List<CloudItem> {
        val client = clientProvider()
        val folder = GoogleDriveReference.decode(folderId ?: rootFolderId)
        return listAll(client, folder).map {
            CloudItem(it.resolvedReference.encode(), it.name, it.isFolder)
        }.sortedWith(compareByDescending<CloudItem> { it.isFolder }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    }

    override suspend fun downloadFile(itemId: String): ByteArray {
        val client = clientProvider()
        val reference = GoogleDriveReference.decode(itemId)
        val item = client.getItem(reference).let {
            if (it.reference.resourceKey == null) it.copy(reference =
                GoogleDriveReference(it.reference.id, reference.resourceKey)) else it
        }
        return download(client, item)
    }

    override suspend fun downloadFileByPath(rootId: String, relativePath: String): ByteArray {
        val segments = requireRelativeCloudPath(relativePath).split('/')
        val client = clientProvider()
        var folder = GoogleDriveReference.decode(rootId)
        for ((index, segment) in segments.withIndex()) {
            currentCoroutineContext().ensureActive()
            val matches = children(client, folder).filter { it.name == segment }
            if (matches.isEmpty()) {
                throw IOException("Die Datei oder der Ordner \"" + segment + "\" fehlt in der Google-Drive-Bibliothek.")
            }
            if (matches.size > 1) {
                throw IOException("Der Name \"" + segment + "\" existiert mehrfach in diesem Drive-Ordner. Bitte die Dubletten in Google Drive bereinigen.")
            }
            val item = matches.single()
            if (index == segments.lastIndex) return download(client, item)
            if (!item.isFolder) throw IOException("Ein Teil des Calibre-Pfads ist kein Google-Drive-Ordner.")
            folder = item.resolvedReference
        }
        error("Empty paths are rejected before lookup")
    }

    private suspend fun download(client: GoogleDriveApi, initial: GoogleDriveItem): ByteArray {
        var item = initial
        val visited = mutableSetOf<String>()
        while (item.mimeType == GOOGLE_DRIVE_SHORTCUT) {
            currentCoroutineContext().ensureActive()
            val target = item.resolvedReference
            if (!visited.add(target.id) || visited.size > 8) {
                throw IOException("Die Google-Drive-Verknüpfung enthält einen Kreis oder zu viele Weiterleitungen.")
            }
            item = client.getItem(target).let {
                if (it.reference.resourceKey == null) it.copy(reference =
                    GoogleDriveReference(it.reference.id, target.resourceKey)) else it
            }
        }
        if (item.isFolder || item.mimeType.startsWith("application/vnd.google-apps.")) {
            throw IOException("Dieser Eintrag ist keine herunterladbare Calibre-Datei.")
        }
        val bytes = client.download(item.reference)
        currentCoroutineContext().ensureActive()
        return bytes
    }

    private suspend fun children(
        client: GoogleDriveApi, folder: GoogleDriveReference
    ): List<GoogleDriveItem> {
        val key = CacheKey(client, folder)
        synchronized(folders) {
            folders[key]?.let {
                if (System.nanoTime() - it.savedAt < 30_000_000_000L) return it.items
            }
        }
        return listAll(client, folder)
    }

    private suspend fun listAll(
        client: GoogleDriveApi, folder: GoogleDriveReference
    ): List<GoogleDriveItem> {
        val items = linkedMapOf<String, GoogleDriveItem>()
        val seenTokens = mutableSetOf<String>()
        var token: String? = null
        do {
            currentCoroutineContext().ensureActive()
            val page = client.listChildren(folder, token)
            currentCoroutineContext().ensureActive()
            page.items.forEach { items[it.reference.id] = it }
            token = page.nextPageToken
            if (token != null && !seenTokens.add(token)) {
                throw IOException("Google Drive hat dieselbe Ordnerseite mehrfach geliefert. Bitte erneut versuchen.")
            }
        } while (token != null)
        val result = items.values.toList()
        synchronized(folders) {
            folders[CacheKey(client, folder)] = CachedFolder(result, System.nanoTime())
            while (folders.size > 128) folders.remove(folders.keys.first())
        }
        return result
    }
}
