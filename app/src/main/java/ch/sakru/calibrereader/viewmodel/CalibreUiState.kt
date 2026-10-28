package ch.sakru.calibrereader.viewmodel

import ch.sakru.calibrereader.model.Book
import ch.sakru.calibrereader.model.LibrarySortMode
import ch.sakru.calibrereader.model.LibraryViewMode
import ch.sakru.calibrereader.model.SavedLibrary
import ch.sakru.calibrereader.storage.CloudItem

enum class CalibreScreen { PROVIDERS, LIBRARIES, FOLDERS, BOOKS }

data class CalibreUiState(
    val screen: CalibreScreen = CalibreScreen.PROVIDERS,
    val libraryViewMode: LibraryViewMode = LibraryViewMode.LIST,
    val sortMode: LibrarySortMode = LibrarySortMode.TITLE_ASC,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val books: List<Book> = emptyList(),
    val savedLibraries: List<SavedLibrary> = emptyList(),
    val currentItems: List<CloudItem> = emptyList(),
    val calibreLibraryFound: Boolean = false,
    val userName: String = "",
    val msalReady: Boolean = false,
    val searchQuery: String = ""
)
