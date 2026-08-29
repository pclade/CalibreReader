package ch.sakru.calibrereader.calibre

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import ch.sakru.calibrereader.model.Book
import ch.sakru.calibrereader.storage.CloudStorage
import java.util.concurrent.ConcurrentHashMap

/**
 * Loads and caches book covers from cloud storage.
 *
 * The repository is independent of a specific cloud provider and accesses
 * files exclusively through the [CloudStorage] abstraction.
 *
 * Covers are cached in memory by book ID to avoid unnecessary downloads.
 *
 * @property cloudStorage cloud storage implementation used to load cover files.
 */
class CoverRepository(
    private val cloudStorage: CloudStorage
) {

    private val memoryCache =
        ConcurrentHashMap<Long, Bitmap>()

    /**
     * Returns a previously cached cover if available.
     *
     * @param bookId Calibre book identifier.
     * @return cached cover or null if the cover has not been loaded yet.
     */
    fun getCachedCover(
        bookId: Long
    ): Bitmap? {
        return memoryCache[bookId]
    }

    /**
     * Loads the cover of a book from cloud storage.
     *
     * The expected Calibre cover location is:
     * `<book path>/cover.jpg`.
     *
     * @param book book whose cover should be loaded.
     * @param rootFolderId cloud storage ID of the Calibre library root.
     * @return decoded cover bitmap or null if no cover could be loaded.
     */
    suspend fun loadCover(
        book: Book,
        rootFolderId: String
    ): Bitmap? {

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

            val bitmap =
                BitmapFactory.decodeByteArray(
                    bytes,
                    0,
                    bytes.size
                )

            if (bitmap != null) {
                memoryCache[book.id] = bitmap
            }

            bitmap

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