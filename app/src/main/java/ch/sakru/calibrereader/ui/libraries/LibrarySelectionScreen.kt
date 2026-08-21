package ch.sakru.calibrereader.ui.libraries

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ch.sakru.calibrereader.model.SavedLibrary

class LibrarySelectionScreen {
}
/**
 * Displays the list of previously saved Calibre libraries.
 *
 * The screen is storage-provider neutral. A saved library may reside on
 * OneDrive, Google Drive, or another supported provider.
 *
 * @param libraries libraries available to the current user.
 * @param onLibraryClick invoked when a saved library is selected.
 * @param onAddLibrary invoked when the user wants to add another library.
 */
@Composable
fun LibrarySelectionScreen(
    libraries: List<SavedLibrary>,
    onLibraryClick: (SavedLibrary) -> Unit,
    onAddLibrary: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {

        Text(
            text = "Meine Bibliotheken",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(
            modifier = Modifier.height(20.dp)
        )

        if (libraries.isEmpty()) {

            Text("Noch keine Bibliothek gespeichert.")

            Spacer(
                modifier = Modifier.height(20.dp)
            )
        }

        LazyColumn(
            modifier = Modifier.weight(1f)
        ) {

            items(libraries) { library ->

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onLibraryClick(library)
                        }
                        .padding(vertical = 16.dp)
                ) {

                    Text(
                        text = "📚 ${library.name}",
                        style = MaterialTheme.typography.titleMedium
                    )

                    Spacer(
                        modifier = Modifier.height(4.dp)
                    )

                    Text(
                        text = library.account,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                HorizontalDivider()
            }
        }

        Button(
            onClick = onAddLibrary,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("+ Bibliothek hinzufügen")
        }
    }
}
