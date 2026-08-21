package ch.sakru.calibrereader.model

data class SavedLibrary(
    val id: String,
    val name: String,
    val storageRootId: String,
    val account: String,
    val provider: StorageProvider
)