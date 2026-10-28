package ch.sakru.calibrereader.ui.libraries

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ch.sakru.calibrereader.model.SavedLibrary

@Composable
fun LibrarySelectionScreen(
    providerName: String,
    userName: String,
    libraries: List<SavedLibrary>,
    isLoading: Boolean,
    errorMessage: String?,
    onLibraryClick: (SavedLibrary) -> Unit,
    onAddLibrary: () -> Unit,
    onChangeProvider: () -> Unit,
    onSignOut: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onChangeProvider) { Text("Speicher wechseln") }
            TextButton(onClick = onSignOut, enabled = !isLoading) { Text("Abmelden") }
        }
        Text("Meine Bibliotheken", style = MaterialTheme.typography.headlineMedium)
        Text(providerName + " · " + userName, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(16.dp))
        if (isLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
        errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (libraries.isEmpty()) Text("Für dieses Konto ist noch keine Bibliothek gespeichert.")
        LazyColumn(Modifier.weight(1f)) {
            items(libraries, key = { it.id }) { library ->
                Column(Modifier.fillMaxWidth()
                    .clickable(enabled = !isLoading) { onLibraryClick(library) }
                    .padding(vertical = 16.dp)) {
                    Text("📚 " + library.name, style = MaterialTheme.typography.titleMedium)
                }
                HorizontalDivider()
            }
        }
        Button(
            onClick = onAddLibrary, enabled = !isLoading, modifier = Modifier.fillMaxWidth()
        ) {
            Text("Bibliothek hinzufügen")
        }
    }
}
