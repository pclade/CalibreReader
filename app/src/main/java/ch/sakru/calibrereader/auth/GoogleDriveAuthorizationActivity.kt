package ch.sakru.calibrereader.auth

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.IntentCompat

/** Correlates every consent result, including after rotation or process recreation. */
class GoogleDriveAuthorizationActivity : ComponentActivity() {
    private val launcher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        finishWithResult(result.resultCode, result.data)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) return
        val pending = IntentCompat.getParcelableExtra(intent, PENDING_INTENT, PendingIntent::class.java)
        if (pending == null) {
            finishWithResult(RESULT_CANCELED, null)
            return
        }
        try {
            launcher.launch(IntentSenderRequest.Builder(pending.intentSender).build())
        } catch (_: Exception) {
            finishWithResult(RESULT_CANCELED, null)
        }
    }

    private fun finishWithResult(code: Int, data: Intent?) {
        setResult(code, (data ?: Intent()).putExtra(REQUEST_ID, intent.getStringExtra(REQUEST_ID)))
        finish()
    }

    companion object {
        const val REQUEST_ID = "ch.sakru.calibrereader.google_drive.request_id"
        private const val PENDING_INTENT = "google_drive_pending_intent"
        fun request(context: Context, id: String, pending: PendingIntent): Intent =
            Intent(context, GoogleDriveAuthorizationActivity::class.java)
                .putExtra(REQUEST_ID, id).putExtra(PENDING_INTENT, pending)
    }
}
