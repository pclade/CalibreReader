package ch.sakru.calibrereader.viewmodel

import androidx.lifecycle.ViewModel
import ch.sakru.calibrereader.model.LibraryViewMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Coordinates application state and user actions for CalibreReader.
 *
 * The ViewModel exposes immutable state to the UI and keeps state-changing
 * operations outside of Compose screens.
 */
class CalibreViewModel : ViewModel() {

    private val _uiState =
        MutableStateFlow(CalibreUiState())

    /**
     * Current immutable UI state observed by Compose.
     */
    val uiState: StateFlow<CalibreUiState> =
        _uiState.asStateFlow()

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
}