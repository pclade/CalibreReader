package ch.sakru.calibrereader.ui.login
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class LoginScreen {
}
/**
 * Displays the Microsoft account login screen.
 *
 * The screen contains presentation logic only. Authentication is delegated
 * to the caller through [onLoginClick].
 *
 * @param isLoading indicates whether an authentication operation is running.
 * @param errorMessage optional authentication error message.
 * @param onLoginClick invoked when the user requests authentication.
 */
@Composable
fun LoginScreen(
    msalReady: Boolean,
    isLoading: Boolean,
    errorMessage: String?,
    onLogin: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Calibre Reader",
            style = MaterialTheme.typography.headlineLarge
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Nur-Lese-Zugriff auf das OneDrive des angemeldeten M365-Benutzers."
        )

        Spacer(modifier = Modifier.height(28.dp))

        Button(
            enabled = msalReady && !isLoading,
            onClick = onLogin
        ) {
            Text(
                if (msalReady) "Mit Microsoft anmelden"
                else "Microsoft-Anmeldung wird vorbereitet …"
            )
        }

        if (isLoading) {
            Spacer(modifier = Modifier.height(20.dp))
            CircularProgressIndicator()
        }

        errorMessage?.let {
            Spacer(modifier = Modifier.height(20.dp))
            Text(text = it)
        }
    }
}
