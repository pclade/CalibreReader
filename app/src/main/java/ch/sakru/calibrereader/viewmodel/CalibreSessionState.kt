package ch.sakru.calibrereader.viewmodel

import ch.sakru.calibrereader.model.StorageProvider

/**
 * Represents technical state of the currently selected Calibre library.
 *
 * This state is not directly rendered by the UI.
 */
data class CalibreSessionState(
    val selectedLibraryRootId: String? = null,
    val metadataDbItemId: String? = null,
    val activeProvider: StorageProvider? = null
)