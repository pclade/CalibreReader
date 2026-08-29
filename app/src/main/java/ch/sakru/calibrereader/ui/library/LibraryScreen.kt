package ch.sakru.calibrereader.ui.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ch.sakru.calibrereader.calibre.CoverRepository
import ch.sakru.calibrereader.model.Book
import ch.sakru.calibrereader.model.BookFile
import ch.sakru.calibrereader.model.LibraryViewMode

class LibraryScreen {
}
/**
 * Displays a Calibre library and allows switching between
 * list and grid presentation.
 *
 * The screen is storage-provider neutral and operates only on
 * Calibre domain models and UI callbacks.
 */
@Composable
fun LibraryScreen(
    books: List<Book>,
    rootFolderId: String?,
    coverRepository: CoverRepository,
    viewMode: LibraryViewMode,
    onViewModeChange: (LibraryViewMode) -> Unit,
    onOpenBook: (Book, BookFile) -> Unit
) {

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Column(
                modifier = Modifier.weight(1f)
            ) {

                Text(
                    text = "Meine Bibliothek",
                    style =
                        MaterialTheme.typography.headlineMedium
                )

                Text(
                    text = "${books.size} Bücher"
                )
            }

            Button(
                onClick = {
                    onViewModeChange(
                        LibraryViewMode.LIST
                    )
                }
            ) {
                Text("☷")
            }

            Spacer(
                modifier = Modifier.width(8.dp)
            )

            Button(
                onClick = {
                    onViewModeChange(
                        LibraryViewMode.GRID
                    )
                }
            ) {
                Text("▦")
            }
        }

        Spacer(
            modifier = Modifier.height(16.dp)
        )

        when (viewMode) {

            LibraryViewMode.LIST -> {

                LibraryList(
                    books = books,
                    rootFolderId = rootFolderId,
                    coverRepository = coverRepository,
                    onOpenBook = onOpenBook
                )
            }

            LibraryViewMode.GRID -> {

                LibraryGrid(
                    books = books,
                    rootFolderId = rootFolderId,
                    coverRepository = coverRepository,
                    onOpenBook = onOpenBook
                )
            }
        }
    }
}
