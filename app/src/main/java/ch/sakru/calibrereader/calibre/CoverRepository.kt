package ch.sakru.calibrereader.calibre

import ch.sakru.calibrereader.model.Book
import ch.sakru.calibrereader.model.LibraryIdentity
import ch.sakru.calibrereader.storage.CloudStorage
import kotlinx.coroutines.CancellationException

/** Bounded cover cache, isolated by provider, account, library and book. */
class CoverRepository(private val maxBytes: Int = 16 * 1024 * 1024) {
    private data class CoverKey(val library: LibraryIdentity, val bookId: Long)
    private val memoryCache = LinkedHashMap<CoverKey, ByteArray>(16, 0.75f, true)
    private var byteCount = 0

    @Synchronized
    fun getCachedCover(libraryIdentity: LibraryIdentity, bookId: Long): ByteArray? =
        memoryCache[CoverKey(libraryIdentity, bookId)]

    suspend fun loadCover(
        book: Book,
        libraryIdentity: LibraryIdentity,
        cloudStorage: CloudStorage
    ): ByteArray? {
        getCachedCover(libraryIdentity, book.id)?.let { return it }
        return try {
            val bytes = cloudStorage.downloadFileByPath(
                libraryIdentity.rootFolderId, book.path + "/cover.jpg"
            )
            cache(CoverKey(libraryIdentity, book.id), bytes)
            bytes
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
    }

    @Synchronized
    fun clear() {
        memoryCache.clear()
        byteCount = 0
    }

    @Synchronized
    private fun cache(key: CoverKey, bytes: ByteArray) {
        if (bytes.size > maxBytes) return
        memoryCache.remove(key)?.let { byteCount -= it.size }
        memoryCache[key] = bytes
        byteCount += bytes.size
        val iterator = memoryCache.entries.iterator()
        while (byteCount > maxBytes && iterator.hasNext()) {
            byteCount -= iterator.next().value.size
            iterator.remove()
        }
    }
}
