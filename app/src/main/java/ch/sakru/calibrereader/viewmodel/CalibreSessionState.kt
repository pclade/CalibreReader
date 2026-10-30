package ch.sakru.calibrereader.viewmodel

import ch.sakru.calibrereader.model.LibraryIdentity
import ch.sakru.calibrereader.model.StorageProvider
import ch.sakru.calibrereader.storage.CloudFolder

/** Technical library context. Authentication tokens are kept out of UI state. */
data class CalibreSessionState(
    val activeProvider: StorageProvider? = null,
    val accountId: String = "",
    val selectedLibraryRootId: String? = null,
    val metadataDbItemId: String? = null,
    val folderPath: List<CloudFolder> = emptyList()
) {
    val libraryIdentity: LibraryIdentity?
        get() {
            val provider = activeProvider ?: return null
            val rootId = selectedLibraryRootId ?: return null
            if (accountId.isBlank()) return null
            return LibraryIdentity(provider, accountId, rootId)
        }
}
