package ch.sakru.calibrereader.platform

import ch.sakru.calibrereader.model.LibraryIdentity
import java.security.MessageDigest

/** A book ID only identifies a book inside one Calibre database. */
fun downloadedBookRelativePath(library: LibraryIdentity, bookId: Long, extension: String): String {
    val normalizedExtension = extension.lowercase()
    require(normalizedExtension.matches(Regex("[a-z0-9_-]+"))) { "Ungültiges Buchformat." }
    val namespace = listOf(library.provider.name, library.accountId, library.rootFolderId)
        .joinToString("\u0000").toByteArray(Charsets.UTF_8)
    val hash = MessageDigest.getInstance("SHA-256").digest(namespace)
        .joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
    return hash + "/" + bookId + "." + normalizedExtension
}
