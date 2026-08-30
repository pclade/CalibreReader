package ch.sakru.calibrereader.calibre

import ch.sakru.calibrereader.model.Book
import ch.sakru.calibrereader.storage.CloudStorage
import java.util.concurrent.ConcurrentHashMap

/**
 * Loads and caches raw book cover data from cloud storage.
 *
 * The repository is independent of a specific cloud provider and does not
 * depend on Android-specific image classes.
 *
 * @property cloudStorage cloud storage implementation used to load cover files.
 */
class CoverRepository(
    private val cloudStorage: CloudStorage
) {

    private val memoryCache =
        ConcurrentHashMap<Long, ByteArray>()

    /**
     * Returns cached cover data if available.
     *
     * @param bookId Calibre book identifier.
     * @return raw image data or null if the cover has not been loaded yet.
     */
    fun getCachedCover(
        bookId: Long
    ): ByteArray? {
        return memoryCache[bookId]
    }

    /**
     * Loads the cover image of a book.
     *
     * The expected Calibre cover location is:
     * `<book path>/cover.jpg`.
     *
     * @param book book whose cover should be loaded.
     * @param rootFolderId cloud storage ID of the Calibre library root.
     * @return raw JPEG image data or null if no cover could be loaded.
     */
    suspend fun loadCover(
        book: Book,
        rootFolderId: String
    ): ByteArray? {

        memoryCache[book.id]?.let {
            return it
        }

        return try {

            val coverPath =
                "${book.path}/cover.jpg"

            val bytes =
                cloudStorage.downloadFileByPath(
                    rootId = rootFolderId,
                    relativePath = coverPath
                )

            memoryCache[book.id] =
                bytes

            bytes

        } catch (e: Exception) {

            android.util.Log.e(
                "CalibreReader",
                "Cover nicht geladen: ${book.title}",
                e
            )

            null
        }
    }
}