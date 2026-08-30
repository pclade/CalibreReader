package ch.sakru.calibrereader.model

/**
 * Represents a downloaded book file that is ready for local processing.
 *
 * @property bookId unique Calibre book identifier.
 * @property extension file extension of the downloaded book.
 * @property bytes downloaded file content.
 */
data class DownloadedBook(
    val bookId: Long,
    val extension: String,
    val bytes: ByteArray
)