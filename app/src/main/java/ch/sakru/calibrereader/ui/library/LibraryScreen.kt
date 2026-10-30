package ch.sakru.calibrereader.ui.library

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ch.sakru.calibrereader.R
import ch.sakru.calibrereader.calibre.CoverRepository
import ch.sakru.calibrereader.model.Book
import ch.sakru.calibrereader.model.BookFile
import ch.sakru.calibrereader.model.LibraryIdentity
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
    var sortMenuExpanded by remember { mutableStateOf(false) }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(16.dp)
    ) {
        TextButton(onClick = onBack) {
            Text(stringResource(R.string.libraries))
        }

        if (isLoading) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }

        errorMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = stringResource(R.string.my_library),
                    style = MaterialTheme.typography.headlineMedium
                )

                Text(
                    text =
                        if (searchQuery.isBlank()) {
                            stringResource(R.string.books_count, totalBookCount)
                        } else {
                            stringResource(
                                R.string.books_filtered_count,
                                visibleBookCount,
                                totalBookCount
                            )
                        }
                )
            }

            Box {
                val currentSortLabel = sortModeLabel(sortMode)

                TextButton(
                    onClick = { sortMenuExpanded = true }
                ) {
                    Text(
                        stringResource(
                            R.string.sort_prefix,
                            currentSortLabel
                        )
                    )
                }

                DropdownMenu(
                    expanded = sortMenuExpanded,
                    onDismissRequest = { sortMenuExpanded = false }
                ) {
                    LibrarySortMode.entries.forEach { mode ->
                        DropdownMenuItem(
                            text = { Text(sortModeLabel(mode)) },
                            onClick = {
                                onSortModeChange(mode)
                                sortMenuExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.width(8.dp))

            Button(
                onClick = {
                    onViewModeChange(
                        LibraryViewMode.LIST
                    )
                }
            ) {
                Text("☷")
            }

            Spacer(Modifier.width(8.dp))

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

        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium,
            label = {
                Text(stringResource(R.string.search_label))
            },
            placeholder = {
                Text(stringResource(R.string.search_hint))
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

        Spacer(Modifier.height(16.dp))

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

@Composable
private fun sortModeLabel(
    mode: LibrarySortMode
): String =
    stringResource(
        when (mode) {
            LibrarySortMode.TITLE_ASC -> R.string.sort_title_asc
            LibrarySortMode.TITLE_DESC -> R.string.sort_title_desc
            LibrarySortMode.AUTHOR_ASC -> R.string.sort_author_asc
            LibrarySortMode.AUTHOR_DESC -> R.string.sort_author_desc
        }
    )
