package ch.sakru.calibrereader.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import ch.sakru.calibrereader.R

/** Secondary information links, opened outside the app. */
@Composable
fun PrivacyPolicyMenu() {
    var expanded by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current

    Box {
        TextButton(onClick = { expanded = true }) {
            Text(stringResource(R.string.more_options))
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.privacy_policy)) },
                onClick = {
                    expanded = false
                    uriHandler.openUri("https://sakru.ch/calibre-to-go/privacy/")
                }
            )
        }
    }
}
