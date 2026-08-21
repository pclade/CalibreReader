package ch.sakru.calibrereader.storage.onedrive

import ch.sakru.calibrereader.onedrive.GraphClient
import ch.sakru.calibrereader.storage.CloudItem
import ch.sakru.calibrereader.storage.CloudStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Microsoft OneDrive implementation of [CloudStorage].
 *
 * This class acts as an adapter between the provider-neutral storage API
 * and Microsoft Graph.
 *
 * All operations are read-only.
 */
class OneDriveStorage(
    private val graphClient: GraphClient,
    private val accessTokenProvider: () -> String
) : CloudStorage {

    override suspend fun listChildren(
        folderId: String?
    ): List<CloudItem> =
        withContext(Dispatchers.IO) {

            val accessToken =
                accessTokenProvider()

            val items =
                if (folderId == null) {
                    graphClient.getRootItems(
                        accessToken
                    )
                } else {
                    graphClient.getChildren(
                        accessToken = accessToken,
                        itemId = folderId
                    )
                }

            items.map {
                CloudItem(
                    id = it.id,
                    name = it.name,
                    isFolder = it.isFolder
                )
            }
        }

    override suspend fun downloadFile(
        itemId: String
    ): ByteArray =
        withContext(Dispatchers.IO) {

            graphClient.downloadFile(
                accessTokenProvider(),
                itemId
            )
        }

    override suspend fun downloadFileByPath(
        rootId: String,
        relativePath: String
    ): ByteArray =
        withContext(Dispatchers.IO) {

            graphClient.downloadFileByRelativePath(
                accessToken = accessTokenProvider(),
                rootFolderId = rootId,
                relativePath = relativePath
            )
        }
}