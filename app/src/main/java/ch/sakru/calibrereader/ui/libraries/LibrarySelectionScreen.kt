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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ch.sakru.calibrereader.R
import ch.sakru.calibrereader.i18n.AppLanguage
import ch.sakru.calibrereader.model.SavedLibrary
import ch.sakru.calibrereader.ui.common.LanguageMenu

@Composable
fun LibrarySelectionScreen(
    providerName: String,
    userName: String,
    libraries: List<SavedLibrary>,
    isLoading: Boolean,
    errorMessage: String?,
    currentLanguage: AppLanguage,
    feedbackEnabled: Boolean,
    onLibraryClick: (SavedLibrary) -> Unit,
    onAddLibrary: () -> Unit,
    onChangeProvider: () -> Unit,
    onSignOut: () -> Unit,
    onLanguageChange: (AppLanguage) -> Unit,
    onFeedback: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onChangeProvider) { Text(stringResource(R.string.change_storage)) }
            TextButton(onClick = onSignOut, enabled = !isLoading) { Text(stringResource(R.string.sign_out)) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            LanguageMenu(
                currentLanguage = currentLanguage,
                onLanguageChange = onLanguageChange
            )
            if (feedbackEnabled) {
                TextButton(onClick = onFeedback) {
                    Text(stringResource(R.string.feedback))
                }
            }
        }
        Text(stringResource(R.string.my_libraries), style = MaterialTheme.typography.headlineMedium)
        Text(providerName + " · " + userName, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(16.dp))
        if (isLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
        errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (libraries.isEmpty()) Text(stringResource(R.string.no_saved_library))
        LazyColumn(Modifier.weight(1f)) {
            items(libraries, key = { it.id }) { library ->
                Column(
                    Modifier.fillMaxWidth()
                        .clickable(enabled = !isLoading) { onLibraryClick(library) }
                        .padding(vertical = 16.dp)
                ) {
                    Text("📚 " + library.name, style = MaterialTheme.typography.titleMedium)
                }
                HorizontalDivider()
            }
        }
        Button(
            onClick = onAddLibrary,
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.add_library))
        }
    }
}
