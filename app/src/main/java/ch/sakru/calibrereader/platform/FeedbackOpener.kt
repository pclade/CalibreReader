package ch.sakru.calibrereader.platform

import android.content.Context
import android.content.Intent
import android.net.Uri
import ch.sakru.calibrereader.BuildConfig

/**
 * Opens the public feedback form in the user's browser.
 *
 * The feedback endpoint is configured at build time. CAPTCHA verification and
 * rate limiting happen on the server so no secrets are embedded in the app.
 */
class FeedbackOpener(
    private val context: Context
) {
    val isConfigured: Boolean
        get() = BuildConfig.FEEDBACK_URL.isNotBlank()

    fun open(languageTag: String) {
        if (!isConfigured) return

        val uri = Uri.parse(BuildConfig.FEEDBACK_URL)
            .buildUpon()
            .appendQueryParameter("lang", languageTag)
            .appendQueryParameter("version", BuildConfig.VERSION_NAME)
            .build()

        context.startActivity(
            Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }
}
