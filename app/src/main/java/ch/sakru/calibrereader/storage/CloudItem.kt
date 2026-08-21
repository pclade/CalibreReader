package ch.sakru.calibrereader.storage

/**
 * Represents a file or folder provided by a cloud storage service.
 *
 * This model is provider-neutral and must not expose implementation details
 * of OneDrive, Google Drive, or other storage providers.
 *
 * @property id provider-specific identifier of the item.
 * @property name display name of the file or folder.
 * @property isFolder true if the item represents a folder.
 */
data class CloudItem(
    val id: String,
    val name: String,
    val isFolder: Boolean
)