package ch.sakru.calibrereader.storage

import ch.sakru.calibrereader.model.StorageProvider

/**
 * Resolves a cloud storage implementation for a storage provider.
 */
class StorageFactory(
    private val storages: Map<StorageProvider, CloudStorage>
) {

    /**
     * Returns the cloud storage implementation for the given provider.
     *
     * @throws IllegalArgumentException if no storage implementation
     * is registered for the provider.
     */
    fun getStorage(
        provider: StorageProvider
    ): CloudStorage {
        return storages[provider]
            ?: throw IllegalArgumentException(
                "No cloud storage registered for provider: $provider"
            )
    }
}