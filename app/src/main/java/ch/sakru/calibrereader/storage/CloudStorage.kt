package ch.sakru.calibrereader.storage

/**
 * Provider-neutral abstraction for read-only cloud storage access.
 *
 * Implementations may use Microsoft OneDrive, Google Drive, or another
 * compatible storage provider.
 *
 * CalibreReader intentionally exposes read-only operations only.
 */
interface CloudStorage {

    /**
     * Returns the direct children of a folder.
     *
     * @param folderId provider-specific folder identifier.
     * If null, the storage root is returned.
     */
    suspend fun listChildren(
        folderId: String? = null
    ): List<CloudItem>

    /**
     * Downloads a file using its provider-specific item identifier.
     *
     * @param itemId identifier of the file.
     * @return complete file content.
     */
    suspend fun downloadFile(
        itemId: String
    ): ByteArray

    /**
     * Downloads a file relative to a previously selected storage root.
     *
     * @param rootId identifier of the library root folder.
     * @param relativePath relative path below the root folder.
     */
    suspend fun downloadFileByPath(
        rootId: String,
        relativePath: String
    ): ByteArray
}