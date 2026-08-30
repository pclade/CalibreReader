package ch.sakru.calibrereader.calibre

import ch.sakru.calibrereader.model.Book
import ch.sakru.calibrereader.storage.CloudStorage
import java.util.concurrent.ConcurrentHashMap

/**
 * Loads and caches Calibre book covers.
 */
class CoverRepository {

    private val memoryCache =
        ConcurrentHashMap<Long, ByteArray>()

    /**
     * Returns a cached cover if available.
     */
    fun getCachedCover(
        bookId: Long
    ): ByteArray? =
        memoryCache[bookId]

    /**
     * Loads a cover from the selected cloud storage.
     *
     * @param book book whose cover should be loaded.
     * @param rootFolderId root folder of the active Calibre library.
     * @param cloudStorage active cloud storage implementation.
     */
    suspend fun loadCover(
        book: Book,
        rootFolderId: String,
        cloudStorage: CloudStorage
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
                "Cover not loaded: ${book.title}",
                e
            )

            null
        }
    }
}