package ch.sakru.calibrereader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

import androidx.compose.foundation.layout.fillMaxSize

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

import java.io.File
import ch.sakru.calibrereader.auth.MicrosoftAuthManager
import ch.sakru.calibrereader.onedrive.GraphClient
import ch.sakru.calibrereader.model.Book
import ch.sakru.calibrereader.calibre.CoverRepository

import ch.sakru.calibrereader.model.BookFile
import ch.sakru.calibrereader.model.SavedLibrary
import ch.sakru.calibrereader.model.StorageProvider
import ch.sakru.calibrereader.calibre.LibraryStorage
import ch.sakru.calibrereader.model.LibraryViewMode

import androidx.lifecycle.lifecycleScope
import ch.sakru.calibrereader.storage.CloudItem
import ch.sakru.calibrereader.storage.onedrive.OneDriveStorage
import kotlinx.coroutines.launch
import ch.sakru.calibrereader.ui.storage.onedrive.OneDriveScreen
import ch.sakru.calibrereader.ui.login.LoginScreen
import ch.sakru.calibrereader.ui.libraries.LibrarySelectionScreen
import ch.sakru.calibrereader.ui.library.LibraryScreen
import androidx.activity.viewModels
import ch.sakru.calibrereader.viewmodel.CalibreViewModel
import androidx.compose.runtime.collectAsState
import ch.sakru.calibrereader.calibre.CalibreRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private val calibreViewModel: CalibreViewModel by viewModels()
    private lateinit var libraryStorage: LibraryStorage
    private var accessToken by mutableStateOf("")
    private lateinit var coverRepository: CoverRepository
    private lateinit var authManager: MicrosoftAuthManager
    private lateinit var oneDriveStorage: OneDriveStorage

    private val calibreRepository = CalibreRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        libraryStorage =
            LibraryStorage(applicationContext)

        lifecycleScope.launch {

            try {

                val libraries =
                    libraryStorage.loadLibraries()

                val viewMode =
                    libraryStorage.loadViewMode()

                calibreViewModel.setSavedLibraries(
                    libraries
                )

                calibreViewModel.setLibraryViewMode(
                    viewMode
                )

            } catch (e: Exception) {

                calibreViewModel.setError(
                    e.message
                        ?: e.javaClass.simpleName
                )
            }
        }

        authManager = MicrosoftAuthManager(
            context = applicationContext,
            onReadyChanged = { ready ->
                runOnUiThread {
                    calibreViewModel.setMsalReady(ready)
                }
                if (ready) {
                    checkExistingLogin()
                }
            },
            onInitializationError = { error ->

                runOnUiThread {

                    calibreViewModel.setError(
                        "MSAL-Initialisierung fehlgeschlagen:\n${error.message}"
                    )
                }
            }
        )
        oneDriveStorage = OneDriveStorage(graphClient = GraphClient(), accessTokenProvider = { accessToken })
        coverRepository = CoverRepository(cloudStorage = oneDriveStorage)
        setContent {
            val uiState by
            calibreViewModel.uiState.collectAsState()
            val sessionState by
            calibreViewModel.sessionState.collectAsState()
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (!uiState.loggedIn) {
                        LoginScreen(
                            msalReady = uiState.msalReady,
                            isLoading = uiState.isLoading,
                            errorMessage = uiState.errorMessage,
                            onLogin = ::startLogin
                        )
                    } else if (uiState.libraryLoaded) {
                        LibraryScreen(
                            books = uiState.books,
                            rootFolderId =
                                sessionState.selectedLibraryRootId,
                            coverRepository = coverRepository,
                            viewMode = uiState.libraryViewMode,

                            onViewModeChange = { mode ->

                                calibreViewModel.setLibraryViewMode(
                                    mode
                                )

                                changeViewMode(
                                    mode
                                )
                            },

                            onOpenBook = { book, bookFile ->

                                openBook(
                                    book,
                                    bookFile
                                )
                            }
                        )

                    } else if (uiState.showLibrarySelection) {

                        LibrarySelectionScreen(
                            libraries = uiState.savedLibraries,

                            onLibraryClick = { library ->

                                loadSavedLibrary(
                                    library
                                )
                            },

                            onAddLibrary = {

                                calibreViewModel.setShowLibrarySelection(
                                    false
                                )

                                loadRootFolder()
                            }
                        )

                    } else {

                        OneDriveScreen(
                            userName = uiState.userName,
                            currentPath = uiState.currentPath,
                            isLoading = uiState.isLoading,
                            items = uiState.currentItems,
                            errorMessage = uiState.errorMessage,
                            calibreLibraryFound = uiState.calibreLibraryFound,

                            onFolderClick = { item ->

                                openFolder(
                                    item
                                )
                            },

                            onUseLibrary = {

                                saveCurrentLibrary()
                                loadCalibreDatabase()
                            }
                        )
                    }
                }
            }
        }
    }


    private fun loadSavedLibrary(
        library: SavedLibrary
    ) {
        calibreViewModel.setSelectedLibraryRootId(library.storageRootId)
        calibreViewModel.setLoading(true)
        calibreViewModel.clearError()
        lifecycleScope.launch {

            try {

                val children =
                    oneDriveStorage.listChildren(
                        library.storageRootId
                    )

                val metadataItem =
                    children.firstOrNull {
                        it.name.equals(
                            "metadata.db",
                            ignoreCase = true
                        )
                    }

                if (metadataItem == null) {

                    calibreViewModel.setError(
                        "metadata.db wurde nicht gefunden."
                    )

                    calibreViewModel.setLoading(false)

                    return@launch
                }

                calibreViewModel.setMetadataDbItemId(metadataItem.id)

                calibreViewModel.setCurrentPath(
                    listOf(
                        "OneDrive",
                        library.name
                    )
                )

                loadCalibreDatabase()

            } catch (e: Exception) {

                calibreViewModel.setError(
                    e.message
                        ?: e.javaClass.simpleName
                )

                calibreViewModel.setLoading(false)
            }
        }
    }    /**
     * Loads the root folder of the currently authenticated cloud storage.
     */
    private fun loadRootFolder() {
        calibreViewModel.setLoading(true)
        calibreViewModel.clearError()
        lifecycleScope.launch {
            try {
                val items = oneDriveStorage.listChildren()
                calibreViewModel.setCurrentItems(items)
                calibreViewModel.setCurrentPath(listOf("OneDrive"))
                calibreViewModel.setLoading(false)
            } catch (e: Exception) {
                calibreViewModel.setError(
                    e.message ?: e.javaClass.simpleName
                )
                calibreViewModel.setLoading(false)
            }
        }
    }
    /**
     * Saves the currently selected Calibre library.
     *
     * The library is stored persistently so that it can be restored
     * when the application is started again.
     */
    private fun saveCurrentLibrary() {

        val folderId =
            calibreViewModel
                .sessionState
                .value
                .selectedLibraryRootId
                ?: return

        val libraryName =
            calibreViewModel.uiState.value.currentPath
                .lastOrNull()
                ?: "Calibre Library"

        val library =
            SavedLibrary(
                id = folderId,
                name = libraryName,
                storageRootId = folderId,
                account = calibreViewModel.uiState.value.userName,
                provider = StorageProvider.ONEDRIVE
            )

        val newLibraries =
            calibreViewModel.uiState.value.savedLibraries
                .filterNot {
                    it.storageRootId == folderId &&
                            it.provider == StorageProvider.ONEDRIVE
                } + library

        calibreViewModel.setSavedLibraries(
            newLibraries
        )

        lifecycleScope.launch {

            try {

                libraryStorage.saveLibraries(
                    newLibraries
                )

                android.util.Log.d(
                    "CalibreReader",
                    "Library saved: ${library.name}"
                )

            } catch (e: Exception) {

                android.util.Log.e(
                    "CalibreReader",
                    "Failed to save library",
                    e
                )

                calibreViewModel.setError(
                    e.message ?: e.javaClass.simpleName
                )
            }
        }
    }
    /**
     * Persists the selected library view mode.
     */
    private fun changeViewMode(
        mode: LibraryViewMode
    ) {

        lifecycleScope.launch {

            try {

                libraryStorage.saveViewMode(
                    mode
                )

            } catch (e: Exception) {

                calibreViewModel.setError(
                    e.message
                        ?: e.javaClass.simpleName
                )
            }
        }
    }    private fun checkExistingLogin() {

        authManager.getCurrentAccount(

            onAccountFound = { account ->

                if (account == null) {
                    runOnUiThread {
                        calibreViewModel.setLoggedIn(false)
                    }
                    return@getCurrentAccount
                }

                authManager.acquireTokenSilent(

                    account = account,

                    onSuccess = { result ->
                        accessToken = result.accessToken
                        calibreViewModel.setUserName(result.account.username)
                        calibreViewModel.setLoggedIn(true)
                        loadRootFolder()
                    },
                    onError = {
                        runOnUiThread {
                            calibreViewModel.setLoggedIn(false)
                        }
                    }
                )
            },

            onError = { exception ->

                runOnUiThread {
                    calibreViewModel.setError(
                        exception.message
                            ?: exception.javaClass.simpleName
                    )
                }
            }
        )
    }
    /**
     * Opens a cloud folder and checks whether it represents
     * the root of a Calibre library.
     *
     * @param item folder to open.
     */
    /**
     * Opens a cloud folder and checks whether it represents
     * the root of a Calibre library.
     *
     * @param item folder to open.
     */
    private fun openFolder(
        item: CloudItem
    ) {
        if (!item.isFolder) {
            return
        }
        calibreViewModel.setLoading(true)
        calibreViewModel.clearError()
        calibreViewModel.setCalibreLibraryFound(false)
        lifecycleScope.launch {
            try {
                val children =
                    oneDriveStorage.listChildren(
                        item.id
                    )

                val metadataItem =
                    children.firstOrNull {
                        it.name.equals(
                            "metadata.db",
                            ignoreCase = true
                        )
                    }
                calibreViewModel.setCurrentItems(children)
                calibreViewModel.setCurrentPath(calibreViewModel.uiState.value.currentPath + item.name)
                if (metadataItem != null) {
                    calibreViewModel.setCalibreLibraryFound(true)
                    calibreViewModel.setSelectedLibraryRootId(item.id)
                    calibreViewModel.setMetadataDbItemId(metadataItem.id)
                }
                calibreViewModel.setLoading(false)

            } catch (e: Exception) {
                calibreViewModel.setError(
                    e.message ?: e.javaClass.simpleName
                )

                calibreViewModel.setLoading(false)
            }
        }
    }
    private fun loadCalibreDatabase() {
        val itemId = calibreViewModel.sessionState.value.metadataDbItemId?: return
        calibreViewModel.setLoading(true)
        calibreViewModel.clearError()
        lifecycleScope.launch {
            try {

                val data =
                    oneDriveStorage.downloadFile(
                        itemId
                    )

                val dbFile =
                    File(
                        filesDir,
                        "metadata.db"
                    )

                dbFile.writeBytes(
                    data
                )

                val loadedBooks =
                    withContext(Dispatchers.IO) {
                        calibreRepository.loadBooks(
                            dbFile
                        )
                    }

                android.util.Log.d(
                    "CalibreReader",
                    "${loadedBooks.size} Bücher geladen"
                )

                calibreViewModel.setBooks(
                    loadedBooks
                )

                calibreViewModel.setLibraryLoaded(
                    true
                )

                calibreViewModel.setLoading(
                    false
                )

            } catch (e: Exception) {

                android.util.Log.e(
                    "CalibreReader",
                    "Fehler beim Laden der Calibre-Datenbank",
                    e
                )

                calibreViewModel.setError(
                    e.message
                        ?: e.javaClass.simpleName
                )

                calibreViewModel.setLoading(
                    false
                )
            }
        }
    }
    private fun openBook(
        book: Book,
        bookFile: BookFile
    ) {

        android.util.Log.d(
            "CalibreReader",
            "openBook gestartet: ${book.title}"
        )

        android.util.Log.d(
            "CalibreReader",
            "Datei aus metadata.db: ${bookFile.name} / ${bookFile.format}"
        )

        val rootFolderId =
            calibreViewModel
                .sessionState
                .value
                .selectedLibraryRootId

        if (rootFolderId == null) {

            android.util.Log.e(
                "CalibreReader",
                "selectedLibraryRootId ist NULL"
            )

            calibreViewModel.setError(
                "Calibre-Stammverzeichnis nicht gesetzt."
            )

            return
        }

        calibreViewModel.setLoading(true)
        calibreViewModel.clearError()

        lifecycleScope.launch {

            try {

                val extension =
                    bookFile.format.lowercase()

                val relativePath =
                    "${book.path}/${bookFile.name}.$extension"

                android.util.Log.d(
                    "CalibreReader",
                    "Cloud book path: $relativePath"
                )

                val bytes =
                    oneDriveStorage.downloadFileByPath(
                        rootId = rootFolderId,
                        relativePath = relativePath
                    )

                android.util.Log.d(
                    "CalibreReader",
                    "Buch heruntergeladen: ${bytes.size} Bytes"
                )

                val localFile =
                    withContext(Dispatchers.IO) {

                        val booksDirectory =
                            File(
                                filesDir,
                                "books"
                            )

                        booksDirectory.mkdirs()

                        val file =
                            File(
                                booksDirectory,
                                "${book.id}.$extension"
                            )

                        file.writeBytes(
                            bytes
                        )

                        file
                    }

                android.util.Log.d(
                    "CalibreReader",
                    "Lokale Datei: ${localFile.absolutePath}"
                )

                android.util.Log.d(
                    "CalibreReader",
                    "Lokale Dateigrösse: ${localFile.length()} Bytes"
                )

                calibreViewModel.setLoading(
                    false
                )

                openLocalBook(
                    localFile,
                    extension
                )

            } catch (e: Exception) {

                android.util.Log.e(
                    "CalibreReader",
                    "Fehler beim Öffnen des Buches",
                    e
                )

                calibreViewModel.setError(
                    e.message
                        ?: e.javaClass.simpleName
                )

                calibreViewModel.setLoading(
                    false
                )
            }
        }
    }
    private fun openLocalBook(
        file: File,
        extension: String
    ) {
        android.util.Log.d(
            "CalibreReader",
            "openLocalBook: ${file.absolutePath}"
        )

        android.util.Log.d(
            "CalibreReader",
            "Existiert: ${file.exists()}, Grösse: ${file.length()}"
        )

        val uri =
            androidx.core.content.FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                file
            )

        val mimeType =
            when (extension.lowercase()) {

                "pdf" ->
                    "application/pdf"

                "epub" ->
                    "application/epub+zip"

                else ->
                    "application/octet-stream"
            }

        val intent =
            android.content.Intent(
                android.content.Intent.ACTION_VIEW
            ).apply {

                setDataAndType(
                    uri,
                    mimeType
                )

                addFlags(
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }

        try {

            android.util.Log.d(
                "CalibreReader",
                "Starte ACTION_VIEW für .$extension"
            )

            startActivity(
                android.content.Intent.createChooser(
                    intent,
                    "Buch öffnen mit"
                )
            )

        } catch (e: Exception) {

            android.util.Log.e(
                "CalibreReader",
                "Reader konnte nicht gestartet werden",
                e
            )

            calibreViewModel.setError(
                "Keine passende App zum Öffnen von .$extension gefunden."
            )
        }
    }

    private fun startLogin() {

        calibreViewModel.clearError()
        calibreViewModel.setLoading(true)

        authManager.signIn(

            activity = this,

            onSuccess = { result ->
                accessToken = result.accessToken
                calibreViewModel.setUserName(result.account.username)
                calibreViewModel.setLoggedIn(true)
                loadRootFolder()
            },

            onError = { error ->

                runOnUiThread {

                    calibreViewModel.setError(
                        error.message
                            ?: error.javaClass.simpleName
                    )

                    calibreViewModel.setLoading(false)
                }
            },

            onCancel = {

                runOnUiThread {

                    calibreViewModel.setError(
                        "Anmeldung wurde abgebrochen."
                    )

                    calibreViewModel.setLoading(false)
                }
            }
        )
    }

}
