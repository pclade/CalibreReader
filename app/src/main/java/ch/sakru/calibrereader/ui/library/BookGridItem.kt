package ch.sakru.calibrereader.ui.library

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import ch.sakru.calibrereader.calibre.CoverRepository
import ch.sakru.calibrereader.model.Book
import ch.sakru.calibrereader.model.BookFile

class BookGridItem {
}
/**
 * Displays a single book cover in the grid representation.
 */
@Composable
fun BookGridItem(
    book: Book,
    rootFolderId: String?,
    coverRepository: CoverRepository,
    onOpenBook: (Book, BookFile) -> Unit
) {

    var bitmap by remember(book.id) {
        mutableStateOf(
            coverRepository
                .getCachedCover(book.id)
        )
    }

    LaunchedEffect(
        book.id,
        rootFolderId
    ) {

        if (
            bitmap == null &&
            rootFolderId != null
        ) {

            bitmap =
                kotlinx.coroutines.withContext(
                    kotlinx.coroutines.Dispatchers.IO
                ) {

                    coverRepository.loadCover(
                        book = book,
                        rootFolderId = rootFolderId
                    )
                }
        }
    }

    Column(
        modifier = Modifier
            .padding(5.dp)
            .clickable {

                val file =
                    book.files.firstOrNull()

                if (file != null) {
                    onOpenBook(
                        book,
                        file
                    )
                }
            }
    ) {

        if (bitmap != null) {

            Image(
                bitmap =
                    bitmap!!.asImageBitmap(),

                contentDescription =
                    book.title,

                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.67f),

                contentScale =
                    ContentScale.Crop
            )

        } else {

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.67f),

                contentAlignment =
                    Alignment.Center
            ) {

                Text("📖")
            }
        }
    }
}