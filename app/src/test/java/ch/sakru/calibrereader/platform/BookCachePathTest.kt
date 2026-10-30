package ch.sakru.calibrereader.platform

import ch.sakru.calibrereader.model.LibraryIdentity
import ch.sakru.calibrereader.model.StorageProvider
import org.junit.Assert.*
import org.junit.Test

class BookCachePathTest {
    @Test fun downloadedBooksWithMatchingIdsDoNotOverwriteAnotherLibrary() {
        val identities = listOf(
            LibraryIdentity(StorageProvider.ONEDRIVE, "first@example.invalid", "same-root"),
            LibraryIdentity(StorageProvider.DROPBOX, "first@example.invalid", "same-root"),
            LibraryIdentity(StorageProvider.DROPBOX, "second@example.invalid", "same-root"),
            LibraryIdentity(StorageProvider.DROPBOX, "second@example.invalid", "other-root")
        )
        val paths = identities.map { downloadedBookRelativePath(it, 1, "EPUB") }
        assertEquals(4, paths.toSet().size)
        assertTrue(paths.all { it.endsWith("/1.epub") && !it.contains("@") })
        assertEquals(paths.first(), downloadedBookRelativePath(identities.first(), 1, "epub"))
    }

    @Test fun fileExtensionsCannotEscapeTheBookDirectory() {
        val library = LibraryIdentity(StorageProvider.DROPBOX, "account", "")
        for (extension in listOf("", "../outside", "epub/../../outside", "epub\\file")) {
            assertThrows(IllegalArgumentException::class.java) {
                downloadedBookRelativePath(library, 1, extension)
            }
        }
        assertTrue(downloadedBookRelativePath(library, 1, "KFX-ZIP").endsWith(".kfx-zip"))
    }
}
