package ch.sakru.calibrereader.viewmodel

import ch.sakru.calibrereader.model.LibraryViewMode

/**
 * Represents the complete UI state of the CalibreReader application.
 *
 * The state is immutable. Changes are created by the [CalibreViewModel]
 * and observed by the Compose UI.
 */
data class CalibreUiState(
    val libraryViewMode: LibraryViewMode = LibraryViewMode.LIST,
    val isLoading: Boolean = false,
    val errorMessage: String? = null

)