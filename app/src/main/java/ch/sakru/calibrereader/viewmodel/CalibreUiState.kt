package ch.sakru.calibrereader.viewmodel

import ch.sakru.calibrereader.model.LibraryViewMode
import ch.sakru.calibrereader.model.Book
import ch.sakru.calibrereader.model.SavedLibrary
import ch.sakru.calibrereader.storage.CloudItem

/**
 * Represents the complete UI state of the CalibreReader application.
 *
 * The state is immutable. Changes are created by the [CalibreViewModel]
 * and observed by the Compose UI.
 */
data class CalibreUiState(
    val libraryViewMode: LibraryViewMode = LibraryViewMode.LIST,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val books: List<Book> = emptyList(),
    val savedLibraries: List<SavedLibrary> = emptyList(),
    val currentItems: List<CloudItem> = emptyList(),
    val currentPath: List<String> = emptyList(),
    val calibreLibraryFound: Boolean = false,
    val libraryLoaded: Boolean = false,
    val showLibrarySelection: Boolean = true,
    val loggedIn: Boolean = false,
    val userName: String = "",
    val msalReady: Boolean = false
)