package ch.sakru.calibrereader.calibre

import ch.sakru.calibrereader.model.Book
import ch.sakru.calibrereader.model.LibraryIdentity
import ch.sakru.calibrereader.model.StorageProvider
import ch.sakru.calibrereader.storage.CloudItem
import ch.sakru.calibrereader.storage.CloudStorage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class CoverRepositoryTest {
    private val book = Book(1, "Book", "Author", "Author/Book", emptyList(), emptyList())

    @Test fun identicalBookIdsRemainIsolatedByProviderAccountAndLibrary() = runBlocking {
        val cache = CoverRepository()
        val identities = listOf(
            LibraryIdentity(StorageProvider.ONEDRIVE, "account-a", "same-root"),
            LibraryIdentity(StorageProvider.DROPBOX, "account-a", "same-root"),
            LibraryIdentity(StorageProvider.DROPBOX, "account-b", "same-root"),
            LibraryIdentity(StorageProvider.DROPBOX, "account-b", "different-root")
        )
        for ((index, identity) in identities.withIndex()) {
            cache.loadCover(book, identity, FakeStorage { byteArrayOf(index.toByte()) })
        }
        for ((index, identity) in identities.withIndex()) {
            assertArrayEquals(byteArrayOf(index.toByte()), cache.getCachedCover(identity, book.id))
        }
    }

    @Test fun memoryBudgetEvictsTheLeastRecentlyUsedLibraryCover() = runBlocking {
        val cache = CoverRepository(maxBytes = 4)
        val identity = LibraryIdentity(StorageProvider.DROPBOX, "account", "root")
        for (id in 1L..2L) cache.loadCover(book.copy(id = id), identity, FakeStorage { byteArrayOf(1, 2) })
        cache.getCachedCover(identity, 1)
        cache.loadCover(book.copy(id = 3), identity, FakeStorage { byteArrayOf(3, 4) })
        assertNotNull(cache.getCachedCover(identity, 1))
        assertNull(cache.getCachedCover(identity, 2))
        assertNotNull(cache.getCachedCover(identity, 3))
    }

    @Test fun cancellationIsNotConvertedIntoAMissingCover() {
        val cache = CoverRepository()
        val identity = LibraryIdentity(StorageProvider.DROPBOX, "account", "root")
        assertThrows(CancellationException::class.java) {
            runBlocking { cache.loadCover(book, identity, FakeStorage { throw CancellationException() }) }
        }
    }

    private class FakeStorage(val download: () -> ByteArray) : CloudStorage {
        override val rootFolderId = ""
        override suspend fun listChildren(folderId: String?): List<CloudItem> = emptyList()
        override suspend fun downloadFile(itemId: String): ByteArray = download()
        override suspend fun downloadFileByPath(rootId: String, relativePath: String): ByteArray = download()
    }
}
