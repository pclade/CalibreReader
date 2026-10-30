package ch.sakru.calibrereader.viewmodel

import androidx.lifecycle.viewModelScope
import ch.sakru.calibrereader.auth.AuthenticatedUser
import ch.sakru.calibrereader.model.Book
import ch.sakru.calibrereader.model.BookFile
import ch.sakru.calibrereader.model.LibrarySortMode
import ch.sakru.calibrereader.model.SavedLibrary
import ch.sakru.calibrereader.model.StorageProvider
import ch.sakru.calibrereader.storage.CloudItem
import ch.sakru.calibrereader.storage.CloudStorage
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class CalibreViewModelTest {
    @get:Rule val temporary = TemporaryFolder()
    private lateinit var dispatcher: TestDispatcher
    private val models = mutableListOf<CalibreViewModel>()

    @Before fun setUp() {
        dispatcher = StandardTestDispatcher()
        Dispatchers.setMain(dispatcher)
    }

    @After fun tearDown() {
        models.forEach { it.viewModelScope.cancel() }
        Dispatchers.resetMain()
    }

    @Test fun googleConsentKeepsLoadingUntilTheActivityResultCompletes() = runTest(dispatcher) {
        val model = model()
        val generation = model.beginAuthentication(StorageProvider.GOOGLE_DRIVE)
        model.finishPendingAuthentication(generation, StorageProvider.GOOGLE_DRIVE, keepPending = true) { null }
        advanceUntilIdle()
        assertTrue(model.uiState.value.isLoading)
        assertNull(model.uiState.value.errorMessage)
        model.completeAuthentication(generation, StorageProvider.GOOGLE_DRIVE,
            AuthenticatedUser("reader@example.invalid", "google-account"))
        assertEquals(CalibreScreen.LIBRARIES, model.uiState.value.screen)
        assertFalse(model.uiState.value.isLoading)
    }

    @Test fun cancelledGoogleConsentCannotCompleteAfterAProviderSwitch() = runTest(dispatcher) {
        val model = model()
        val generation = model.beginAuthentication(StorageProvider.GOOGLE_DRIVE)
        model.showProviders()
        model.beginAuthentication(StorageProvider.DROPBOX)
        model.completeAuthentication(generation, StorageProvider.GOOGLE_DRIVE,
            AuthenticatedUser("wrong@example.invalid", "old-google-account"))
        assertEquals(StorageProvider.DROPBOX, model.sessionState.value.activeProvider)
        assertTrue(model.sessionState.value.accountId.isBlank())
    }

    @Test fun anOldLoginCannotReplaceTheSelectedProvider() = runTest(dispatcher) {
        val model = model()
        val old = model.beginAuthentication(StorageProvider.ONEDRIVE)
        val current = model.beginAuthentication(StorageProvider.DROPBOX)
        model.completeAuthentication(old, StorageProvider.ONEDRIVE, AuthenticatedUser("wrong"))
        assertEquals(CalibreScreen.PROVIDERS, model.uiState.value.screen)
        model.completeAuthentication(current, StorageProvider.DROPBOX, AuthenticatedUser("display", "correct"))
        assertEquals(StorageProvider.DROPBOX, model.sessionState.value.activeProvider)
        assertEquals("correct", model.sessionState.value.accountId)
        assertEquals("display", model.uiState.value.userName)
    }

    @Test fun savedLibrariesAreFilteredByBothProviderAndAccount() = runTest(dispatcher) {
        val model = model()
        model.setSavedLibraries(listOf(
            library("one", StorageProvider.ONEDRIVE, "account"),
            library("dropbox", StorageProvider.DROPBOX, "account"),
            library("other-account", StorageProvider.DROPBOX, "other")
        ))
        authenticate(model)
        assertEquals(listOf("dropbox"), model.availableLibraries.map { it.id })
        val storage = FakeStorage()
        model.loadSavedLibraryAndBooks(library("wrong", StorageProvider.DROPBOX, "other"),
            storage, temporary.newFolder(), ::readBooks)
        advanceUntilIdle()
        assertTrue(storage.listed.isEmpty())
        assertNotNull(model.uiState.value.errorMessage)
    }

    @Test fun aLibraryAtTheDropboxRootCanBeSelectedAndSaved() = runTest(dispatcher) {
        val model = model()
        authenticate(model)
        val storage = FakeStorage()
        model.browseRoot(storage)
        advanceUntilIdle()
        assertTrue(model.uiState.value.calibreLibraryFound)
        assertEquals("", model.sessionState.value.selectedLibraryRootId)
        var saved = emptyList<SavedLibrary>()
        val folder = temporary.newFolder()
        model.useCurrentLibrary(storage, folder, ::readBooks) { saved = it }
        advanceUntilIdle()
        assertEquals("", saved.single().storageRootId)
        assertEquals("account", saved.single().account)
        assertEquals(StorageProvider.DROPBOX, saved.single().provider)
        assertEquals(CalibreScreen.BOOKS, model.uiState.value.screen)
        assertEquals(listOf(BOOK), model.uiState.value.books)
        assertTrue(folder.listFiles().orEmpty().isEmpty())
    }

    @Test fun matchingFolderIdsInDifferentAccountsDoNotOverwriteLibraries() = runTest(dispatcher) {
        val model = model()
        val storage = FakeStorage()
        for (account in listOf("account-a", "account-b")) {
            authenticate(model, account)
            model.browseRoot(storage)
            advanceUntilIdle()
            model.useCurrentLibrary(storage, temporary.newFolder(), ::readBooks) {}
            advanceUntilIdle()
        }
        val saved = model.uiState.value.savedLibraries
        assertEquals(2, saved.size)
        assertEquals(setOf("account-a", "account-b"), saved.map { it.account }.toSet())
        assertEquals(2, saved.map { it.id }.toSet().size)
        assertEquals("account-b", model.availableLibraries.single().account)
    }

    @Test fun folderNavigationClearsAnEarlierMetadataSelection() = runTest(dispatcher) {
        val model = model()
        authenticate(model)
        val storage = FakeStorage().apply {
            listing = { id ->
                if (id == "") listOf(METADATA, CloudItem("child", "Child", true)) else emptyList()
            }
        }
        model.browseRoot(storage)
        advanceUntilIdle()
        model.openFolder(CloudItem("child", "Child", true), storage)
        advanceUntilIdle()
        assertFalse(model.uiState.value.calibreLibraryFound)
        assertNull(model.sessionState.value.selectedLibraryRootId)
        model.navigateUp(storage)
        advanceUntilIdle()
        assertEquals(listOf("Dropbox"), model.sessionState.value.folderPath.map { it.name })
        assertTrue(model.uiState.value.calibreLibraryFound)
    }

    @Test fun aLateFolderResponseCannotRestoreAClosedSession() = runTest(dispatcher) {
        val model = model()
        authenticate(model)
        val result = CompletableDeferred<List<CloudItem>>()
        val storage = FakeStorage().apply {
            listing = { withContext(NonCancellable) { result.await() } }
        }
        model.browseRoot(storage)
        runCurrent()
        model.showProviders()
        result.complete(listOf(METADATA))
        advanceUntilIdle()
        assertEquals(CalibreScreen.PROVIDERS, model.uiState.value.screen)
        assertNull(model.sessionState.value.activeProvider)
        assertTrue(model.uiState.value.currentItems.isEmpty())
        assertFalse(model.uiState.value.calibreLibraryFound)
    }

    @Test fun aCancelledDownloadNeverOpensABookInAnotherSession() = runTest(dispatcher) {
        val model = model()
        authenticate(model)
        val storage = FakeStorage()
        model.loadSavedLibraryAndBooks(library("current"), storage, temporary.newFolder(), ::readBooks)
        advanceUntilIdle()
        val result = CompletableDeferred<ByteArray>()
        storage.pathDownload = { _, _ -> withContext(NonCancellable) { result.await() } }
        model.downloadBook(BOOK, BookFile("Book", "EPUB"), storage)
        runCurrent()
        model.showProviders()
        result.complete(byteArrayOf(1))
        advanceUntilIdle()
        val request = async { withTimeoutOrNull(1) { model.booksToOpen.first() } }
        advanceUntilIdle()
        assertNull(request.await())
        assertTrue(model.uiState.value.books.isEmpty())
    }

    @Test fun missingStoredCredentialsRequestExactlyTheSelectedInteractiveLogin() = runTest(dispatcher) {
        val model = model()
        val request = async { model.interactiveLoginRequests.first() }
        model.connect(StorageProvider.DROPBOX) { null }
        advanceUntilIdle()
        assertEquals(StorageProvider.DROPBOX, request.await().provider)
        assertTrue(model.uiState.value.isLoading)
        model.finishPendingAuthentication(request.await().generation, StorageProvider.DROPBOX) {
            AuthenticatedUser("display", "account")
        }
        advanceUntilIdle()
        assertEquals(CalibreScreen.LIBRARIES, model.uiState.value.screen)
        assertFalse(model.uiState.value.isLoading)
    }

    @Test fun searchAndSortStillWorkAfterACloudLibraryIsLoaded() = runTest(dispatcher) {
        val model = model()
        authenticate(model)
        val other = BOOK.copy(id = 2, title = "Alpha", authors = "ZZ Brown")
        model.loadSavedLibraryAndBooks(library("current"), FakeStorage(), temporary.newFolder()) {
            listOf(BOOK, other)
        }
        advanceUntilIdle()
        model.setSearchQuery("  aLPHa  ")
        assertEquals(listOf(2L), model.filteredBooks.map { it.id })
        model.setSearchQuery("brown")
        assertEquals(listOf(2L), model.filteredBooks.map { it.id })
        model.setSearchQuery("")
        model.setSortMode(LibrarySortMode.AUTHOR_DESC)
        assertEquals(listOf(2L, 1L), model.filteredBooks.map { it.id })
    }

    @Test fun hyphenatedCalibreFormatsKeepTheirActualExtension() = runTest(dispatcher) {
        val model = model()
        authenticate(model)
        var requestedPath = ""
        val storage = FakeStorage().apply {
            pathDownload = { _, path -> requestedPath = path; byteArrayOf(1) }
        }
        model.loadSavedLibraryAndBooks(library("current"), storage, temporary.newFolder(), ::readBooks)
        advanceUntilIdle()
        val opened = async { model.booksToOpen.first() }
        model.downloadBook(BOOK, BookFile("Book", "KFX-ZIP"), storage)
        advanceUntilIdle()
        assertTrue(requestedPath.endsWith("/Book.kfx-zip"))
        assertEquals("kfx-zip", opened.await().book.extension)
    }

    private fun model() = CalibreViewModel(dispatcher).also { models += it }
    private fun authenticate(model: CalibreViewModel, account: String = "account") {
        val generation = model.beginAuthentication(StorageProvider.DROPBOX)
        model.completeAuthentication(generation, StorageProvider.DROPBOX,
            AuthenticatedUser("Display " + account, account))
    }
    private fun library(id: String, provider: StorageProvider = StorageProvider.DROPBOX,
        account: String = "account") = SavedLibrary(id, "Library", "", account, provider)
    private fun readBooks(snapshot: File): List<Book> {
        assertEquals("metadata-fixture", snapshot.readText())
        return listOf(BOOK)
    }
    private class FakeStorage : CloudStorage {
        override val rootFolderId = ""
        val listed = mutableListOf<String?>()
        var listing: suspend (String?) -> List<CloudItem> = { listOf(METADATA) }
        var pathDownload: suspend (String, String) -> ByteArray = { _, _ -> byteArrayOf(1) }
        override suspend fun listChildren(folderId: String?): List<CloudItem> {
            listed += folderId
            return listing(folderId)
        }
        override suspend fun downloadFile(itemId: String): ByteArray = "metadata-fixture".toByteArray()
        override suspend fun downloadFileByPath(rootId: String, relativePath: String): ByteArray =
            pathDownload(rootId, relativePath)
    }
    private companion object {
        val METADATA = CloudItem("metadata-id", "metadata.db", false)
        val BOOK = Book(1, "Zeta", "Author", "Author/Zeta (1)", listOf("EPUB"),
            listOf(BookFile("Zeta", "EPUB")))
    }
}
