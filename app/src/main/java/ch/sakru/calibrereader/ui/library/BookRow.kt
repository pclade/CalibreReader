package ch.sakru.calibrereader.ui.library

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
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

class BookRow {
}
/**
 * Displays a single book in the list representation.
 *
 * User interactions are exposed through callbacks so that this
 * composable remains independent of the storage provider.
 */
@Composable
fun BookRow(
    book: Book,
    rootFolderId: String?,
    coverRepository: CoverRepository,
    onOpenBook: (Book, BookFile) -> Unit
) {

    var bitmap by remember(book.id) {
        mutableStateOf(
            coverRepository.getCachedCover(book.id)
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

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {

        if (bitmap != null) {

            Image(
                bitmap =
                    bitmap!!.asImageBitmap(),

                contentDescription =
                    "Cover von ${book.title}",

                modifier = Modifier
                    .width(80.dp)
                    .height(120.dp),

                contentScale =
                    ContentScale.Crop
            )

        } else {

            Box(
                modifier = Modifier
                    .width(80.dp)
                    .height(120.dp),
                contentAlignment =
                    Alignment.Center
            ) {

                Text("📖")
            }
        }

        Spacer(
            modifier = Modifier.width(16.dp)
        )

        Column(
            modifier =
                Modifier.weight(1f)
        ) {

            Text(
                text = book.title,
                style =
                    MaterialTheme.typography.titleMedium
            )

            Spacer(
                modifier = Modifier.height(4.dp)
            )

            Text(
                text = book.authors,
                style =
                    MaterialTheme.typography.bodyMedium
            )

            Spacer(
                modifier = Modifier.height(6.dp)
            )

            Row {

                book.files.forEach { bookFile ->

                    Button(
                        onClick = {

                            android.util.Log.d(
                                "CalibreReader",
                                "FORMAT gedrückt: ${book.title} / ${bookFile.format}"
                            )

                            onOpenBook(
                                book,
                                bookFile
                            )
                        },
                        modifier = Modifier.padding(
                            end = 6.dp
                        )
                    ) {
                        Text(bookFile.format)
                    }                }
            }
        }
    }
}
