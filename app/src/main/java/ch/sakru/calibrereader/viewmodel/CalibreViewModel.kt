package ch.sakru.calibrereader.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.sakru.calibrereader.auth.AuthenticatedUser
import ch.sakru.calibrereader.model.Book
import ch.sakru.calibrereader.model.BookFile
import ch.sakru.calibrereader.model.DownloadedBook
import ch.sakru.calibrereader.model.LibraryIdentity
import ch.sakru.calibrereader.model.LibrarySortMode
import ch.sakru.calibrereader.model.LibraryViewMode
import ch.sakru.calibrereader.model.SavedLibrary
import ch.sakru.calibrereader.model.StorageProvider
import ch.sakru.calibrereader.storage.CloudFolder
import ch.sakru.calibrereader.storage.CloudItem
import ch.sakru.calibrereader.storage.CloudStorage
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class BookOpenRequest(val libraryIdentity: LibraryIdentity, val book: DownloadedBook)
data class InteractiveLoginRequest(val provider: StorageProvider, val generation: Long)

/** Coordinates one active account while retaining saved libraries for every provider. */
class CalibreViewModel(private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO) : ViewModel() {
    private val _uiState = MutableStateFlow(CalibreUiState())
    val uiState = _uiState.asStateFlow()
    private val _sessionState = MutableStateFlow(CalibreSessionState())
    val sessionState = _sessionState.asStateFlow()
    private var storageJob: Job? = null
    private var downloadJob: Job? = null
    private var authJob: Job? = null
    private var authenticationGeneration = 0L
    private val openRequests = Channel<BookOpenRequest>(1, BufferOverflow.DROP_OLDEST)
    val booksToOpen = openRequests.receiveAsFlow()
    private val loginRequests = Channel<InteractiveLoginRequest>(1, BufferOverflow.DROP_OLDEST)
    val interactiveLoginRequests = loginRequests.receiveAsFlow()

    val filteredBooks: List<Book>
        get() {
            val state = uiState.value
            val query = state.searchQuery.trim()
            val filtered = if (query.isBlank()) state.books else state.books.filter {
                it.title.contains(query, ignoreCase = true) ||
                    it.authors.contains(query, ignoreCase = true)
            }
            return when (state.sortMode) {
                LibrarySortMode.TITLE_ASC -> filtered.sortedBy { it.title.lowercase() }
                LibrarySortMode.TITLE_DESC -> filtered.sortedByDescending { it.title.lowercase() }
                LibrarySortMode.AUTHOR_ASC -> filtered.sortedBy { it.authors.lowercase() }
                LibrarySortMode.AUTHOR_DESC -> filtered.sortedByDescending { it.authors.lowercase() }
            }
        }

    val availableLibraries: List<SavedLibrary>
        get() = uiState.value.savedLibraries.filter {
            it.provider == sessionState.value.activeProvider &&
                it.account == sessionState.value.accountId
        }

    val currentAuthenticationGeneration: Long get() = authenticationGeneration

    fun setSavedLibraries(libraries: List<SavedLibrary>) {
        _uiState.update { it.copy(savedLibraries = libraries) }
    }

    fun setLibraryViewMode(mode: LibraryViewMode) {
        _uiState.update { it.copy(libraryViewMode = mode) }
    }

    fun setMsalReady(ready: Boolean) {
        _uiState.update { it.copy(msalReady = ready) }
    }

    fun setError(message: String?) {
        _uiState.update { it.copy(errorMessage = message) }
    }

    fun setLoading(loading: Boolean) {
        _uiState.update { it.copy(isLoading = loading) }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun setSortMode(mode: LibrarySortMode) {
        _uiState.update { it.copy(sortMode = mode) }
    }

    fun beginAuthentication(provider: StorageProvider): Long {
        resetSession(provider, loading = true)
        return authenticationGeneration
    }

    fun connect(provider: StorageProvider, restore: suspend () -> AuthenticatedUser?) {
        val generation = beginAuthentication(provider)
        authJob = viewModelScope.launch {
            try {
                val user = restore()
                if (!isCurrentAuthentication(generation, provider)) return@launch
                if (user == null) loginRequests.send(InteractiveLoginRequest(provider, generation))
                else completeAuthentication(generation, provider, user)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                authenticationFailed(generation, provider,
                    if (provider == StorageProvider.GOOGLE_DRIVE && !error.message.isNullOrBlank()) error.message!!
                    else "Die Verbindung konnte nicht hergestellt werden. Bitte Internetverbindung prüfen und erneut versuchen.")
            }
        }
    }

    fun finishPendingAuthentication(
        generation: Long, provider: StorageProvider, keepPending: Boolean = false,
        finish: suspend () -> AuthenticatedUser?
    ) {
        if (!isCurrentAuthentication(generation, provider)) return
        authJob?.cancel()
        authJob = viewModelScope.launch {
            try {
                val user = finish()
                if (user != null) completeAuthentication(generation, provider, user)
                else if (!keepPending) authenticationFailed(generation, provider, "Anmeldung abgebrochen. Bitte erneut verbinden.")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                authenticationFailed(generation, provider,
                    if (provider == StorageProvider.GOOGLE_DRIVE && !error.message.isNullOrBlank()) error.message!!
                    else "Die Anmeldung konnte nicht abgeschlossen werden. Bitte erneut verbinden.")
            }
        }
    }

    fun signOut(disconnect: suspend () -> Unit) {
        val provider = sessionState.value.activeProvider ?: return
        val generation = beginAuthentication(provider)
        authJob = viewModelScope.launch {
            try {
                disconnect()
                if (isCurrentAuthentication(generation, provider)) showProviders()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                authenticationFailed(generation, provider, "Abmelden fehlgeschlagen. Bitte erneut versuchen.")
            }
        }
    }

    fun isCurrentAuthentication(generation: Long, provider: StorageProvider): Boolean =
        generation == authenticationGeneration && sessionState.value.activeProvider == provider

    fun completeAuthentication(generation: Long, provider: StorageProvider, user: AuthenticatedUser) {
        if (!isCurrentAuthentication(generation, provider)) return
        require(user.accountId.isNotBlank())
        _sessionState.update { it.copy(accountId = user.accountId) }
        _uiState.update {
            it.copy(screen = CalibreScreen.LIBRARIES, userName = user.userName,
                isLoading = false, errorMessage = null)
        }
    }

    fun authenticationFailed(generation: Long, provider: StorageProvider, message: String) {
        if (!isCurrentAuthentication(generation, provider)) return
        _uiState.update { it.copy(isLoading = false, errorMessage = message) }
    }

    fun showProviders() = resetSession(null, loading = false)

    fun showLibraries() {
        cancelOperations()
        _sessionState.update {
            it.copy(selectedLibraryRootId = null, metadataDbItemId = null, folderPath = emptyList())
        }
        _uiState.update {
            it.copy(screen = CalibreScreen.LIBRARIES, books = emptyList(), currentItems = emptyList(),
                searchQuery = "", errorMessage = null, isLoading = false, calibreLibraryFound = false)
        }
    }

    fun browseRoot(cloudStorage: CloudStorage) {
        val session = sessionState.value
        val provider = session.activeProvider ?: return
        if (session.accountId.isBlank()) return
        navigate(listOf(CloudFolder(cloudStorage.rootFolderId, provider.displayName)), cloudStorage)
    }

    fun openFolder(item: CloudItem, cloudStorage: CloudStorage) {
        if (!item.isFolder || uiState.value.isLoading) return
        navigate(sessionState.value.folderPath + CloudFolder(item.id, item.name), cloudStorage)
    }

    fun navigateUp(cloudStorage: CloudStorage) {
        val path = sessionState.value.folderPath
        if (path.size > 1) navigate(path.dropLast(1), cloudStorage) else showLibraries()
    }

    fun retryFolder(cloudStorage: CloudStorage) {
        val path = sessionState.value.folderPath
        if (path.isEmpty()) browseRoot(cloudStorage) else navigate(path, cloudStorage)
    }

    private fun navigate(path: List<CloudFolder>, cloudStorage: CloudStorage) {
        storageJob?.cancel()
        _sessionState.update {
            it.copy(folderPath = path, selectedLibraryRootId = null, metadataDbItemId = null)
        }
        _uiState.update {
            it.copy(screen = CalibreScreen.FOLDERS, currentItems = emptyList(),
                calibreLibraryFound = false, isLoading = true, errorMessage = null)
        }
        storageJob = viewModelScope.launch {
            try {
                val items = cloudStorage.listChildren(path.last().id)
                ensureActive()
                val metadata = items.firstOrNull { !it.isFolder && it.name.equals("metadata.db", true) }
                _sessionState.update {
                    it.copy(selectedLibraryRootId = if (metadata != null) path.last().id else null,
                        metadataDbItemId = metadata?.id)
                }
                _uiState.update {
                    it.copy(currentItems = items, calibreLibraryFound = metadata != null, isLoading = false)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = error.message) }
            }
        }
    }

    fun useCurrentLibrary(
        cloudStorage: CloudStorage,
        cacheDirectory: File,
        loadBooks: (File) -> List<Book>,
        saveLibraries: suspend (List<SavedLibrary>) -> Unit
    ) {
        val session = sessionState.value
        val identity = session.libraryIdentity ?: return
        if (session.metadataDbItemId == null) return
        val existing = uiState.value.savedLibraries.firstOrNull {
            it.provider == identity.provider && it.account == identity.accountId &&
                it.storageRootId == identity.rootFolderId
        }
        val id = existing?.id ?: UUID.nameUUIDFromBytes(
            listOf(identity.provider.name, identity.accountId, identity.rootFolderId)
                .joinToString("\u0000").toByteArray(Charsets.UTF_8)
        ).toString()
        val library = SavedLibrary(id, session.folderPath.last().name,
            identity.rootFolderId, identity.accountId, identity.provider)
        loadLibrary(library, cloudStorage, cacheDirectory, loadBooks, saveLibraries)
    }

    fun loadSavedLibraryAndBooks(
        library: SavedLibrary,
        cloudStorage: CloudStorage,
        cacheDirectory: File,
        loadBooks: (File) -> List<Book>
    ) {
        if (library.provider != sessionState.value.activeProvider ||
            library.account != sessionState.value.accountId) {
            setError("Diese Bibliothek gehört zu einem anderen Cloud-Konto.")
            return
        }
        loadLibrary(library, cloudStorage, cacheDirectory, loadBooks, null)
    }

    private fun loadLibrary(
        library: SavedLibrary,
        cloudStorage: CloudStorage,
        cacheDirectory: File,
        loadBooks: (File) -> List<Book>,
        saveLibraries: (suspend (List<SavedLibrary>) -> Unit)?
    ) {
        storageJob?.cancel()
        downloadJob?.cancel()
        _uiState.update { it.copy(isLoading = true, errorMessage = null, books = emptyList()) }
        storageJob = viewModelScope.launch {
            try {
                val metadata = cloudStorage.listChildren(library.storageRootId).firstOrNull {
                    !it.isFolder && it.name.equals("metadata.db", true)
                } ?: error("metadata.db wurde in dieser Bibliothek nicht gefunden.")
                val data = cloudStorage.downloadFile(metadata.id)
                ensureActive()
                val books = withContext(ioDispatcher) {
                    // Separate snapshots prevent an old request from overwriting another library.
                    val snapshot = File.createTempFile("calibre-metadata-", ".db", cacheDirectory)
                    try {
                        snapshot.writeBytes(data)
                        loadBooks(snapshot)
                    } finally {
                        snapshot.delete()
                    }
                }
                ensureActive()
                if (saveLibraries != null) {
                    val libraries = uiState.value.savedLibraries.filterNot {
                        it.provider == library.provider && it.account == library.account &&
                            it.storageRootId == library.storageRootId
                    } + library
                    saveLibraries(libraries)
                    ensureActive()
                    setSavedLibraries(libraries)
                }
                _sessionState.update {
                    it.copy(selectedLibraryRootId = library.storageRootId, metadataDbItemId = metadata.id)
                }
                _uiState.update {
                    it.copy(screen = CalibreScreen.BOOKS, books = books, searchQuery = "",
                        isLoading = false, errorMessage = null)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = error.message) }
            }
        }
    }

    fun changeViewMode(mode: LibraryViewMode, save: suspend (LibraryViewMode) -> Unit) {
        setLibraryViewMode(mode)
        viewModelScope.launch {
            try {
                save(mode)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                setError(error.message)
            }
        }
    }

    fun downloadBook(book: Book, bookFile: BookFile, cloudStorage: CloudStorage) {
        val identity = sessionState.value.libraryIdentity ?: return
        downloadJob?.cancel()
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        downloadJob = viewModelScope.launch {
            try {
                val extension = bookFile.format.lowercase()
                require(extension.matches(Regex("[a-z0-9_-]+"))) { "Ungültiges Buchformat." }
                val bytes = cloudStorage.downloadFileByPath(
                    identity.rootFolderId, book.path + "/" + bookFile.name + "." + extension
                )
                ensureActive()
                _uiState.update { it.copy(isLoading = false) }
                openRequests.send(BookOpenRequest(identity, DownloadedBook(book.id, extension, bytes)))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = error.message) }
            }
        }
    }

    private fun resetSession(provider: StorageProvider?, loading: Boolean) {
        authJob?.cancel()
        cancelOperations()
        authenticationGeneration++
        _sessionState.value = CalibreSessionState(activeProvider = provider)
        val previous = uiState.value
        _uiState.value = CalibreUiState(
            libraryViewMode = previous.libraryViewMode, sortMode = previous.sortMode,
            savedLibraries = previous.savedLibraries, msalReady = previous.msalReady,
            isLoading = loading
        )
    }

    private fun cancelOperations() {
        storageJob?.cancel()
        downloadJob?.cancel()
        while (openRequests.tryReceive().isSuccess) { /* discard obsolete book-open requests */ }
        while (loginRequests.tryReceive().isSuccess) { /* discard obsolete sign-in requests */ }
    }
}
