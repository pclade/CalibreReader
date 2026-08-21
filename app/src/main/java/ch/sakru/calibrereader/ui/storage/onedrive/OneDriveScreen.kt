package ch.sakru.calibrereader.ui.storage.onedrive

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ch.sakru.calibrereader.storage.CloudItem

class OneDriveScreen {

}

/**
 * Displays the contents of the currently selected OneDrive folder.
 *
 * The screen itself operates on provider-neutral [CloudItem] objects and
 * delegates navigation and library selection to its caller.
 *
 * @param userName name of the currently authenticated user.
 * @param currentPath current navigation path.
 * @param isLoading indicates whether cloud data is currently being loaded.
 * @param items files and folders contained in the current folder.
 * @param errorMessage optional error message to display.
 * @param calibreLibraryFound indicates whether the current folder contains
 * a Calibre library.
 * @param onFolderClick invoked when the user selects a folder.
 * @param onUseLibrary invoked when the detected Calibre library should be used.
 */
@Composable
fun OneDriveScreen(
    userName: String,
    currentPath: List<String>,
    isLoading: Boolean,
    items: List<CloudItem>,
    errorMessage: String?,
    calibreLibraryFound: Boolean,
    onFolderClick: (CloudItem) -> Unit,
    onUseLibrary: () -> Unit
) {

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {

        Text(
            text = "Calibre Reader",
            style =
                MaterialTheme.typography.headlineMedium
        )

        Text(text = userName)

        Spacer(
            modifier = Modifier.height(12.dp)
        )

        Text(
            text = currentPath.joinToString(" / "),
            style =
                MaterialTheme.typography.bodyMedium
        )

        Spacer(
            modifier = Modifier.height(20.dp)
        )

        if (calibreLibraryFound) {

            Text(
                text =
                    "✓ Calibre-Bibliothek erkannt",
                style =
                    MaterialTheme.typography.titleMedium
            )

            Spacer(
                modifier = Modifier.height(12.dp)
            )

            Button(
                onClick = onUseLibrary
            ) {
                Text(
                    "Diese Bibliothek verwenden"
                )
            }

            Spacer(
                modifier = Modifier.height(20.dp)
            )
        }

        if (isLoading) {

            CircularProgressIndicator()

            Spacer(
                modifier = Modifier.height(16.dp)
            )
        }

        errorMessage?.let {

            Text(text = it)

            Spacer(
                modifier = Modifier.height(16.dp)
            )
        }

        LazyColumn {

            items(items) { item ->

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            enabled = item.isFolder
                        ) {
                            onFolderClick(item)
                        }
                        .padding(
                            vertical = 14.dp
                        )
                ) {

                    Text(
                        text =
                            if (item.isFolder) {
                                "📁  ${item.name}"
                            } else {
                                "📄  ${item.name}"
                            }
                    )
                }

                HorizontalDivider()
            }
        }
    }
}
