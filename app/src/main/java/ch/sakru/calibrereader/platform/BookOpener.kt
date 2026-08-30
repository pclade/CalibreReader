package ch.sakru.calibrereader.platform

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import ch.sakru.calibrereader.model.DownloadedBook
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Opens downloaded book files using an installed Android reader application.
 *
 * This class contains Android-specific file and intent handling.
 */
class BookOpener(
    private val context: Context
) {

    /**
     * Writes a downloaded book to local storage and opens it
     * using an appropriate Android application.
     *
     * @param downloadedBook downloaded book content.
     */
    suspend fun open(
        downloadedBook: DownloadedBook
    ) {
        val localFile =
            withContext(Dispatchers.IO) {

                val booksDirectory =
                    File(
                        context.filesDir,
                        "books"
                    )

                booksDirectory.mkdirs()

                val file =
                    File(
                        booksDirectory,
                        "${downloadedBook.bookId}.${downloadedBook.extension}"
                    )

                file.writeBytes(
                    downloadedBook.bytes
                )

                file
            }

        openLocalBook(
            file = localFile,
            extension = downloadedBook.extension
        )
    }

    /**
     * Opens a local book file with an installed Android application.
     */
    private fun openLocalBook(
        file: File,
        extension: String
    ) {
        val uri =
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

        val mimeType =
            when (extension.lowercase()) {

                "pdf" ->
                    "application/pdf"

                "epub" ->
                    "application/epub+zip"

                else ->
                    "application/octet-stream"
            }

        val intent =
            Intent(
                Intent.ACTION_VIEW
            ).apply {

                setDataAndType(
                    uri,
                    mimeType
                )

                addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }

        context.startActivity(
            Intent.createChooser(
                intent,
                "Buch öffnen mit"
            ).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK
                )
            }
        )
    }
}