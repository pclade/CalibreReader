package ch.sakru.calibrereader.model

data class LibraryIdentity(
    val provider: StorageProvider,
    val accountId: String,
    val rootFolderId: String
)
