package ch.sakru.calibrereader.ui.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ch.sakru.calibrereader.model.StorageProvider

@Composable
fun ProviderSelectionScreen(
    msalReady: Boolean,
    microsoftError: String?,
    dropboxConfigured: Boolean,
    isLoading: Boolean,
    errorMessage: String?,
    onSelect: (StorageProvider) -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("Calibre To Go", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(12.dp))
        Text("Wo liegt deine Calibre-Bibliothek?")
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = { onSelect(StorageProvider.ONEDRIVE) },
            enabled = msalReady && !isLoading,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Mit OneDrive verbinden") }
        microsoftError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { onSelect(StorageProvider.DROPBOX) },
            enabled = dropboxConfigured && !isLoading,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Mit Dropbox verbinden") }
        if (!dropboxConfigured) {
            Text("Dropbox ist in dieser Testversion noch nicht eingerichtet.",
                style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(16.dp))
        Text("Deine Bibliotheken werden nur gelesen.", style = MaterialTheme.typography.bodyMedium)
        if (isLoading) {
            Spacer(Modifier.height(20.dp))
            CircularProgressIndicator()
            TextButton(onClick = onCancel) { Text("Abbrechen") }
        }
        errorMessage?.let {
            Spacer(Modifier.height(16.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }
    }
}
