package ch.sakru.calibrereader.app

import android.content.Context
import ch.sakru.calibrereader.auth.AuthSession
import ch.sakru.calibrereader.auth.DropboxAuthManager
import ch.sakru.calibrereader.auth.DropboxCredentialStore
import ch.sakru.calibrereader.auth.GoogleDriveAccountStore
import ch.sakru.calibrereader.auth.GoogleDriveAuthManager
import ch.sakru.calibrereader.auth.GooglePlayDriveAuthorization
import ch.sakru.calibrereader.auth.MicrosoftAuthManager
import ch.sakru.calibrereader.calibre.CalibreRepository
import ch.sakru.calibrereader.calibre.CoverRepository
import ch.sakru.calibrereader.calibre.LibraryStorage
import ch.sakru.calibrereader.model.StorageProvider
import ch.sakru.calibrereader.onedrive.GraphClient
import ch.sakru.calibrereader.platform.BookOpener
import ch.sakru.calibrereader.platform.FeedbackOpener
import ch.sakru.calibrereader.storage.StorageFactory
import ch.sakru.calibrereader.storage.dropbox.DropboxStorage
import ch.sakru.calibrereader.storage.googledrive.GoogleDriveApiFactory
import ch.sakru.calibrereader.storage.googledrive.GoogleDriveClient
import ch.sakru.calibrereader.storage.googledrive.GoogleDriveStorage
import ch.sakru.calibrereader.storage.onedrive.OneDriveStorage

/**
 * Application composition root.
 *
 * Creates and connects the main application services and repositories.
 *
 * This keeps dependency construction out of UI classes such as MainActivity.
 */
class CalibreReaderApp(
    context: Context
) {
    val authSession =
        AuthSession()

    val libraryStorage =
        LibraryStorage(
            context.applicationContext
        )

    val calibreRepository =
        CalibreRepository()

    private val graphClient =
        GraphClient()

    val oneDriveStorage =
        OneDriveStorage(
            graphClient = graphClient,
            accessTokenProvider = {
                authSession.accessToken
            }
        )

    val dropboxAuthManager =
        DropboxAuthManager(
            DropboxCredentialStore(context.applicationContext)
        )

    val microsoftAuthManager =
        MicrosoftAuthManager(
            context.applicationContext,
            authSession
        )

    val googleDriveAuthorization =
        GooglePlayDriveAuthorization(
            context.applicationContext
        )

    val googleDriveAuthManager =
        GoogleDriveAuthManager(
            googleDriveAuthorization,
            GoogleDriveAccountStore(context.applicationContext),
            GoogleDriveApiFactory {
                GoogleDriveClient(it)
            }
        )

    val storageFactory =
        StorageFactory(
            storages =
                mapOf(
                    StorageProvider.ONEDRIVE to oneDriveStorage,
                    StorageProvider.DROPBOX to DropboxStorage(
                        dropboxAuthManager::requireClient
                    ),
                    StorageProvider.GOOGLE_DRIVE to GoogleDriveStorage(
                        googleDriveAuthManager::requireClient
                    )
                )
        )

    val coverRepository =
        CoverRepository()

    val bookOpener =
        BookOpener(
            context.applicationContext
        )

    val feedbackOpener =
        FeedbackOpener(
            context.applicationContext
        )
}
