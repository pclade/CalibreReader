package ch.sakru.calibrereader.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ch.sakru.calibrereader.calibre.CoverRepository
import ch.sakru.calibrereader.model.Book
import ch.sakru.calibrereader.model.LibraryIdentity
import ch.sakru.calibrereader.model.BookFile
import ch.sakru.calibrereader.model.LibrarySortMode
import ch.sakru.calibrereader.model.LibraryViewMode
import ch.sakru.calibrereader.storage.CloudStorage

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
    totalBookCount: Int,
    visibleBookCount: Int,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    sortMode: LibrarySortMode,
    onSortModeChange: (LibrarySortMode) -> Unit,
    libraryIdentity: LibraryIdentity,
    cloudStorage: CloudStorage?,
    coverRepository: CoverRepository,
    viewMode: LibraryViewMode,
    onViewModeChange: (LibraryViewMode) -> Unit,
    onOpenBook: (Book, BookFile) -> Unit,
    isLoading: Boolean,
    errorMessage: String?,
    onBack: () -> Unit
) {
    var sortMenuExpanded by
    remember {
        mutableStateOf(false)
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(16.dp)
    ) {

        TextButton(onClick = onBack) { Text("Bibliotheken") }
        if (isLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
        errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {

            // Title and number of books
            Column(
                modifier = Modifier.weight(1f)
            ) {

                Text(
                    text = "Meine Bibliothek",
                    style = MaterialTheme.typography.headlineMedium
                )

                Text(
                    text =
                        if (searchQuery.isBlank()) {
                            "$totalBookCount Bücher"
                        } else {
                            "$visibleBookCount von $totalBookCount Büchern"
                        }
                )
            }

            // Sort selection
            Box {

                TextButton(
                    onClick = {
                        sortMenuExpanded = true
                    }
                ) {
                    Text(
                        text = "Sort: ${sortMode.displayName}"
                    )
                }

                DropdownMenu(
                    expanded = sortMenuExpanded,
                    onDismissRequest = {
                        sortMenuExpanded = false
                    }
                ) {

                    LibrarySortMode.entries.forEach { mode ->

                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = mode.displayName
                                )
                            },
                            onClick = {

                                onSortModeChange(
                                    mode
                                )

                                sortMenuExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(
                modifier = Modifier.width(8.dp)
            )

            // List view
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

            // Grid view
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
            modifier = Modifier.height(8.dp)
        )

        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium,
            label = {
                Text("Search")
            },
            placeholder = {
                Text("Title or author")
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {

                    IconButton(
                        onClick = {
                            onSearchQueryChange("")
                        }
                    ) {
                        Text("✕")
                    }
                }
            }
        )

        Spacer(
            modifier = Modifier.height(16.dp)
        )

        when (viewMode) {

            LibraryViewMode.LIST -> {

                LibraryList(
                    books = books,
                    libraryIdentity = libraryIdentity,
                    cloudStorage = cloudStorage,
                    coverRepository = coverRepository,
                    onOpenBook = onOpenBook
                )
            }

            LibraryViewMode.GRID -> {

                LibraryGrid(
                    books = books,
                    libraryIdentity = libraryIdentity,
                    cloudStorage = cloudStorage,
                    coverRepository = coverRepository,
                    onOpenBook = onOpenBook
                )
            }
        }
    }
}