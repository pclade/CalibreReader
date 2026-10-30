package ch.sakru.calibrereader.ui.storage

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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ch.sakru.calibrereader.R
import ch.sakru.calibrereader.storage.CloudItem

@Composable
fun CloudFolderScreen(
    providerName: String,
    userName: String,
    currentPath: List<String>,
    isLoading: Boolean,
    items: List<CloudItem>,
    errorMessage: String?,
    calibreLibraryFound: Boolean,
    onFolderClick: (CloudItem) -> Unit,
    onUseLibrary: () -> Unit,
    onBack: () -> Unit,
    onRetry: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
        Text(providerName, style = MaterialTheme.typography.headlineMedium)
        Text(userName, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(12.dp))
        Text(currentPath.joinToString(" / "))
        Spacer(Modifier.height(12.dp))
        if (isLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
        errorMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
            TextButton(onClick = onRetry, enabled = !isLoading) {
                Text(stringResource(R.string.retry))
            }
        }
        if (calibreLibraryFound) {
            Text(
                stringResource(R.string.calibre_library_detected),
                style = MaterialTheme.typography.titleMedium
            )
            Button(onClick = onUseLibrary, enabled = !isLoading) {
                Text(stringResource(R.string.use_this_library))
            }
        }
        LazyColumn(Modifier.weight(1f)) {
            items(items, key = { it.id }) { item ->
                Row(
                    Modifier.fillMaxWidth()
                        .clickable(enabled = item.isFolder && !isLoading) { onFolderClick(item) }
                        .padding(vertical = 16.dp)
                ) {
                    Text((if (item.isFolder) "📁 " else "📄 ") + item.name)
                }
                HorizontalDivider()
            }
        }
    }
}
