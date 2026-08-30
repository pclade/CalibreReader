package ch.sakru.calibrereader.viewmodel

import androidx.lifecycle.ViewModel
import ch.sakru.calibrereader.model.LibraryViewMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ch.sakru.calibrereader.model.Book
import ch.sakru.calibrereader.model.SavedLibrary
import ch.sakru.calibrereader.storage.CloudItem
import androidx.lifecycle.viewModelScope
import ch.sakru.calibrereader.storage.CloudStorage
import kotlinx.coroutines.launch

/**
 * Coordinates application state and user actions for CalibreReader.
 *
 * The ViewModel exposes immutable state to the UI and keeps state-changing
 * operations outside of Compose screens.
 */
class CalibreViewModel : ViewModel() {
    private val _sessionState =
        MutableStateFlow(CalibreSessionState())

    val sessionState: StateFlow<CalibreSessionState> = _sessionState.asStateFlow()
    private val _uiState = MutableStateFlow(CalibreUiState())

    /**
     * Current immutable UI state observed by Compose.
     */
    val uiState: StateFlow<CalibreUiState> =_uiState.asStateFlow()

    /**
     * Changes the presentation mode of the Calibre library.
     *
     * @param viewMode desired list or grid presentation.
     */
    fun setLibraryViewMode(
        viewMode: LibraryViewMode
    ) {
        _uiState.value =
            _uiState.value.copy(
                libraryViewMode = viewMode
            )
    }
    /**
     * Updates the loading state.
     *
     * @param loading true while an operation is in progress.
     */
    fun setLoading(
        loading: Boolean
    ) {
        _uiState.value =
            _uiState.value.copy(
                isLoading = loading
            )
    }

    /**
     * Updates the current error message.
     *
     * @param message error message to display, or null to clear it.
     */
    fun setError(
        message: String?
    ) {
        _uiState.value =
            _uiState.value.copy(
                errorMessage = message
            )
    }

    /**
     * Clears the current error message.
     */
    fun clearError() {
        setError(null)
    }
    /**
     * Updates the books currently displayed in the selected Calibre library.
     *
     * @param books books loaded from the Calibre metadata database.
     */
    fun setBooks(
        books: List<Book>
    ) {
        _uiState.value =
            _uiState.value.copy(
                books = books
            )
    }
    /**
     * Updates the list of saved Calibre libraries.
     *
     * @param libraries libraries persisted by the application.
     */
    fun setSavedLibraries(
        libraries: List<SavedLibrary>
    ) {
        _uiState.value =
            _uiState.value.copy(
                savedLibraries = libraries
            )
    }
    /**
     * Updates the items of the currently displayed cloud folder.
     */
    fun setCurrentItems(
        items: List<CloudItem>
    ) {
        _uiState.value =
            _uiState.value.copy(
                currentItems = items
            )
    }

    /**
     * Updates the currently displayed cloud navigation path.
     */
    fun setCurrentPath(
        path: List<String>
    ) {
        _uiState.value =
            _uiState.value.copy(
                currentPath = path
            )
    }
    /**
     * Updates whether the current cloud folder contains a Calibre library.
     */
    fun setCalibreLibraryFound(
        found: Boolean
    ) {
        _uiState.value =
            _uiState.value.copy(
                calibreLibraryFound = found
            )
    }

    /**
     * Updates whether a Calibre library has been loaded successfully.
     */
    fun setLibraryLoaded(
        loaded: Boolean
    ) {
        _uiState.value =
            _uiState.value.copy(
                libraryLoaded = loaded
            )
    }

    /**
     * Controls whether the saved library selection screen is displayed.
     */
    fun setShowLibrarySelection(
        show: Boolean
    ) {
        _uiState.value =
            _uiState.value.copy(
                showLibrarySelection = show
            )
    }
    /**
     * Updates the authentication state.
     */
    fun setLoggedIn(
        loggedIn: Boolean
    ) {
        _uiState.value =
            _uiState.value.copy(
                loggedIn = loggedIn
            )
    }

    /**
     * Updates the display name of the authenticated user.
     */
    fun setUserName(
        userName: String
    ) {
        _uiState.value =
            _uiState.value.copy(
                userName = userName
            )
    }
    /**
     * Updates whether the Microsoft authentication client is ready.
     */
    fun setMsalReady(
        ready: Boolean
    ) {
        _uiState.value =
            _uiState.value.copy(
                msalReady = ready
            )
    }
    /**
     * Selects the root folder of the active Calibre library.
     */
    fun setSelectedLibraryRootId(
        rootId: String?
    ) {
        _sessionState.value =
            _sessionState.value.copy(
                selectedLibraryRootId = rootId
            )
    }

    /**
     * Stores the cloud item identifier of the active metadata.db file.
     */
    fun setMetadataDbItemId(
        itemId: String?
    ) {
        _sessionState.value =
            _sessionState.value.copy(
                metadataDbItemId = itemId
            )
    }
    fun loadRootFolder(
        cloudStorage: CloudStorage
    ) {
        setLoading(true)
        clearError()

        viewModelScope.launch {
            try {
                val items =
                    cloudStorage.listChildren()

                setCurrentItems(
                    items
                )

                setCurrentPath(
                    listOf("OneDrive")
                )

                setLoading(
                    false
                )

            } catch (e: Exception) {

                setError(
                    e.message
                        ?: e.javaClass.simpleName
                )

                setLoading(
                    false
                )
            }
        }
    }

    fun openFolder(
        item: CloudItem,
        cloudStorage: CloudStorage
    ) {
        if (!item.isFolder) {
            return
        }

        setLoading(true)
        clearError()
        setCalibreLibraryFound(false)

        viewModelScope.launch {
            try {
                val children =
                    cloudStorage.listChildren(
                        item.id
                    )

                val metadataItem =
                    children.firstOrNull {
                        it.name.equals(
                            "metadata.db",
                            ignoreCase = true
                        )
                    }

                setCurrentItems(
                    children
                )

                val currentPath =
                    uiState.value.currentPath

                setCurrentPath(
                    currentPath + item.name
                )

                if (metadataItem != null) {

                    setCalibreLibraryFound(
                        true
                    )

                    setSelectedLibraryRootId(
                        item.id
                    )

                    setMetadataDbItemId(
                        metadataItem.id
                    )
                }

                setLoading(
                    false
                )

            } catch (e: Exception) {

                setError(
                    e.message
                        ?: e.javaClass.simpleName
                )

                setLoading(
                    false
                )
            }
        }
    }

    fun loadSavedLibrary(
        library: SavedLibrary,
        cloudStorage: CloudStorage,
        onReady: () -> Unit
    ) {
        setSelectedLibraryRootId(
            library.storageRootId
        )

        setLoading(true)
        clearError()

        viewModelScope.launch {
            try {
                val children =
                    cloudStorage.listChildren(
                        library.storageRootId
                    )

                val metadataItem =
                    children.firstOrNull {
                        it.name.equals(
                            "metadata.db",
                            ignoreCase = true
                        )
                    }

                if (metadataItem == null) {
                    setError(
                        "metadata.db wurde nicht gefunden."
                    )

                    setLoading(
                        false
                    )

                    return@launch
                }

                setMetadataDbItemId(
                    metadataItem.id
                )

                setCurrentPath(
                    listOf(
                        "OneDrive",
                        library.name
                    )
                )

                onReady()

            } catch (e: Exception) {

                setError(
                    e.message
                        ?: e.javaClass.simpleName
                )

                setLoading(
                    false
                )
            }
        }
    }

}