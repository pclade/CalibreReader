package ch.sakru.calibrereader.storage.googledrive

import java.io.IOException

const val GOOGLE_DRIVE_READONLY_SCOPE = "https://www.googleapis.com/auth/drive.readonly"
const val GOOGLE_DRIVE_FOLDER = "application/vnd.google-apps.folder"
const val GOOGLE_DRIVE_SHORTCUT = "application/vnd.google-apps.shortcut"

data class GoogleDriveAccount(val email: String, val permissionId: String) {
    init { require(email.isNotBlank() && permissionId.isNotBlank()) }
}

/** Resource keys travel with saved references, including targets of shared shortcuts. */
data class GoogleDriveReference(val id: String, val resourceKey: String? = null) {
    init {
        require(id.matches(Regex("[A-Za-z0-9_-]+")))
        require(resourceKey == null || (resourceKey.matches(Regex("[A-Za-z0-9_-]+"))))
    }
    fun encode(): String = if (resourceKey == null) id else id + "|" + resourceKey
    companion object {
        fun decode(value: String): GoogleDriveReference {
            val parts = value.split('|', limit = 2)
            return GoogleDriveReference(parts[0], parts.getOrNull(1))
        }
    }
}

data class GoogleDriveItem(
    val reference: GoogleDriveReference,
    val name: String,
    val mimeType: String,
    val shortcutTarget: GoogleDriveReference? = null,
    val shortcutTargetMimeType: String? = null
) {
    val isFolder: Boolean get() = mimeType == GOOGLE_DRIVE_FOLDER ||
        (mimeType == GOOGLE_DRIVE_SHORTCUT && shortcutTargetMimeType == GOOGLE_DRIVE_FOLDER)
    val resolvedReference: GoogleDriveReference get() =
        if (mimeType == GOOGLE_DRIVE_SHORTCUT) shortcutTarget
            ?: throw IOException("Die Google-Drive-Verknüpfung hat kein erreichbares Ziel.")
        else reference
}

data class GoogleDrivePage(val items: List<GoogleDriveItem>, val nextPageToken: String?)

interface GoogleDriveTokens {
    suspend fun accessToken(): String
    suspend fun invalidate(token: String)
}

interface GoogleDriveApi {
    suspend fun currentAccount(): GoogleDriveAccount
    suspend fun listChildren(folder: GoogleDriveReference, pageToken: String? = null): GoogleDrivePage
    suspend fun getItem(reference: GoogleDriveReference): GoogleDriveItem
    suspend fun download(reference: GoogleDriveReference): ByteArray
}

fun interface GoogleDriveApiFactory {
    fun create(tokens: GoogleDriveTokens): GoogleDriveApi
}
