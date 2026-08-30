package ch.sakru.calibrereader.app

import android.content.Context
import ch.sakru.calibrereader.auth.AuthSession
import ch.sakru.calibrereader.calibre.CalibreRepository
import ch.sakru.calibrereader.calibre.CoverRepository
import ch.sakru.calibrereader.calibre.LibraryStorage
import ch.sakru.calibrereader.onedrive.GraphClient
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

    /**
     * Current authentication session.
     */
    val authSession =
        AuthSession()

    /**
     * Persistent application settings and saved libraries.
     */
    val libraryStorage =
        LibraryStorage(
            context.applicationContext
        )

    /**
     * Calibre metadata repository.
     */
    val calibreRepository =
        CalibreRepository()

    private val graphClient =
        GraphClient()

    /**
     * OneDrive cloud storage implementation.
     */
    val oneDriveStorage =
        OneDriveStorage(
            graphClient = graphClient,
            accessTokenProvider = {
                authSession.accessToken
            }
        )

    /**
     * Provider-neutral book cover repository.
     */
    val coverRepository =
        CoverRepository(
            cloudStorage = oneDriveStorage
        )
}