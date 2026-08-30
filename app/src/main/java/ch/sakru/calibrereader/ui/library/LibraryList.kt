package ch.sakru.calibrereader.ui.library

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import ch.sakru.calibrereader.calibre.CoverRepository
import ch.sakru.calibrereader.model.Book
import ch.sakru.calibrereader.model.BookFile
import ch.sakru.calibrereader.storage.CloudStorage
/**
 * Displays the books of a Calibre library as a vertical list.
 */
@Composable
fun LibraryList(
    books: List<Book>,
    rootFolderId: String?,
    cloudStorage: CloudStorage?,
    coverRepository: CoverRepository,
    onOpenBook: (Book, BookFile) -> Unit
) {

    LazyColumn {

        items(
            items = books,
            key = { it.id }
        ) { book ->

            BookRow(
                book = book,
                rootFolderId = rootFolderId,
                cloudStorage = cloudStorage,
                coverRepository = coverRepository,
                onOpenBook = onOpenBook
            )

            HorizontalDivider()
        }
    }
}
