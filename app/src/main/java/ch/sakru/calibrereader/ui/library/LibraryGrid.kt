package ch.sakru.calibrereader.ui.library

import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import ch.sakru.calibrereader.calibre.CoverRepository
import ch.sakru.calibrereader.model.Book
import ch.sakru.calibrereader.model.BookFile

class LibraryGrid {
}
/**
 * Displays the books of a Calibre library in an adaptive cover grid.
 */
@Composable
fun LibraryGrid(
    books: List<Book>,
    accessToken: String,
    rootFolderId: String?,
    coverRepository: CoverRepository,
    onOpenBook: (Book, BookFile) -> Unit
) {

    LazyVerticalGrid(
        columns = GridCells.Adaptive(
            minSize = 110.dp
        )
    ) {

        items(
            items = books,
            key = { it.id }
        ) { book ->

            BookGridItem(
                book = book,
                accessToken = accessToken,
                rootFolderId = rootFolderId,
                coverRepository = coverRepository,
                onOpenBook = onOpenBook
            )
        }
    }
}
