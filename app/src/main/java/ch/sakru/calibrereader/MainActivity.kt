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

import android.database.sqlite.SQLiteDatabase
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

class MainActivity : ComponentActivity() {
    private val calibreViewModel:
            CalibreViewModel by viewModels()
    private var libraryViewMode by
    mutableStateOf(LibraryViewMode.LIST)
    private var showLibrarySelection by mutableStateOf(true)
    private var savedLibraries by
    mutableStateOf<List<SavedLibrary>>(emptyList())
    private lateinit var libraryStorage: LibraryStorage
    private var metadataDbItemId by mutableStateOf<String?>(null)
    private var accessToken by mutableStateOf("")

    private var currentItems by
    mutableStateOf<List<CloudItem>>(emptyList())

    private var currentPath by
    mutableStateOf(listOf("OneDrive"))

    private var calibreLibraryFound by
    mutableStateOf(false)

    private var selectedCalibreFolderId by
    mutableStateOf<String?>(null)
    private val coverRepository =
        CoverRepository()
    private lateinit var authManager: MicrosoftAuthManager
    private lateinit var oneDriveStorage: OneDriveStorage
    private var msalReady by mutableStateOf(false)
    private var userName by mutableStateOf("")
    private var isLoading by mutableStateOf(false)
    private var errorMessage by mutableStateOf<String?>(null)
    private var loggedIn by mutableStateOf(false)

    private var books by mutableStateOf<List<Book>>(emptyList())
    private var libraryLoaded by mutableStateOf(false)



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        libraryStorage =
            LibraryStorage(applicationContext)

        Thread {

            kotlinx.coroutines.runBlocking {

                val libraries =
                    libraryStorage.loadLibraries()

                val viewMode =
                    libraryStorage.loadViewMode()

                runOnUiThread {

                    savedLibraries =
                        libraries

                    libraryViewMode =
                        viewMode
                }
            }

        }.start()

        authManager = MicrosoftAuthManager(
            context = applicationContext,
            onReadyChanged = { ready ->

                runOnUiThread {
                    msalReady = ready
                }

                if (ready) {
                    checkExistingLogin()
                }
            },
            onInitializationError = { error ->
                runOnUiThread {
                    errorMessage = "MSAL-Initialisierung fehlgeschlagen:\n${error.message}"
                }
            }
        )
        oneDriveStorage = OneDriveStorage(
            graphClient = GraphClient(),
            accessTokenProvider = {
                accessToken
            }
        )

        setContent {
            val uiState by
            calibreViewModel.uiState.collectAsState()

            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    if (!loggedIn) {

                        LoginScreen(
                            msalReady = msalReady,
                            isLoading = isLoading,
                            errorMessage = errorMessage,
                            onLogin = ::startLogin
                        )

                    } else if (libraryLoaded) {

                        LibraryScreen(
                            books = books,
                            accessToken = accessToken,
                            rootFolderId = selectedCalibreFolderId,
                            coverRepository = coverRepository,

                            viewMode = uiState.libraryViewMode,

                            onViewModeChange = { mode ->
                                calibreViewModel.setLibraryViewMode(mode)
                                changeViewMode(mode)
                            },

                            onOpenBook = { book, bookFile ->
                                openBook(
                                    book,
                                    bookFile
                                )
                            }
                        )
                    } else if (showLibrarySelection) {

                        LibrarySelectionScreen(
                            libraries = savedLibraries,

                            onLibraryClick = { library ->

                                selectedCalibreFolderId =
                                    library.storageRootId

                                loadSavedLibrary(
                                    library
                                )
                            },

                            onAddLibrary = {
                                showLibrarySelection = false
                                loadRootFolder()
                            }
                        )

                    } else {

                        OneDriveScreen(
                            userName = userName,
                            currentPath = currentPath,
                            isLoading = uiState.isLoading,
                            items = currentItems,
                            errorMessage = uiState.errorMessage,
                            calibreLibraryFound = calibreLibraryFound,
                            onFolderClick = { item ->
                                openFolder(item)
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

        selectedCalibreFolderId =
            library.storageRootId

        isLoading = true
        errorMessage = null

        Thread {

            try {

                val children =
                    GraphClient().getChildren(
                        accessToken = accessToken,
                        itemId = library.storageRootId
                    )

                val metadataItem =
                    children.firstOrNull {
                        it.name.equals(
                            "metadata.db",
                            ignoreCase = true
                        )
                    }

                if (metadataItem == null) {

                    runOnUiThread {
                        errorMessage =
                            "metadata.db wurde nicht gefunden."
                        isLoading = false
                    }

                    return@Thread
                }

                metadataDbItemId =
                    metadataItem.id

                runOnUiThread {

                    currentPath =
                        listOf(
                            "OneDrive",
                            library.name
                        )

                    loadCalibreDatabase()
                }

            } catch (e: Exception) {

                runOnUiThread {

                    errorMessage =
                        e.message ?: e.javaClass.simpleName

                    isLoading = false
                }
            }

        }.start()
    }

    /**
     * Loads the root folder of the currently authenticated cloud storage.
     */
    private fun loadRootFolder() {
        calibreViewModel.setLoading(true)
        calibreViewModel.clearError()
        lifecycleScope.launch {
            try {
                val items = oneDriveStorage.listChildren()
                currentItems = items
                currentPath = listOf("OneDrive")
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
            selectedCalibreFolderId
                ?: return

        val libraryName =
            currentPath.lastOrNull()
                ?: "Calibre Library"

        val library =
            SavedLibrary(
                id = folderId,
                name = libraryName,
                storageRootId = folderId,
                account = userName,
                provider = StorageProvider.ONEDRIVE
            )

        val newLibraries =
            savedLibraries
                .filterNot {
                    it.storageRootId == folderId &&
                            it.provider == StorageProvider.ONEDRIVE
                } + library

        savedLibraries = newLibraries

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
    private fun changeViewMode(
        mode: LibraryViewMode
    ) {

        libraryViewMode = mode

        Thread {

            kotlinx.coroutines.runBlocking {

                libraryStorage.saveViewMode(
                    mode
                )
            }

        }.start()
    }
    private fun checkExistingLogin() {

        authManager.getCurrentAccount(

            onAccountFound = { account ->

                if (account == null) {
                    runOnUiThread {
                        loggedIn = false
                    }
                    return@getCurrentAccount
                }

                authManager.acquireTokenSilent(

                    account = account,

                    onSuccess = { result ->

                        accessToken =
                            result.accessToken

                        userName =
                            result.account.username

                        loggedIn = true

                        loadRootFolder()
                    },

                    onError = {
                        runOnUiThread {
                            loggedIn = false
                        }
                    }
                )
            },

            onError = { exception ->

                runOnUiThread {
                    errorMessage =
                        exception.message
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

        calibreLibraryFound = false

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

                currentItems = children
                currentPath = currentPath + item.name

                if (metadataItem != null) {
                    calibreLibraryFound = true
                    selectedCalibreFolderId = item.id
                    metadataDbItemId = metadataItem.id
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

        val itemId = metadataDbItemId ?: return

        calibreViewModel.setLoading(true)
        calibreViewModel.clearError()

        Thread {

            try {

                val data =
                    GraphClient().downloadFile(
                        accessToken,
                        itemId
                    )

                val dbFile =
                    File(
                        filesDir,
                        "metadata.db"
                    )

                dbFile.writeBytes(data)

                val db =
                    SQLiteDatabase.openDatabase(
                        dbFile.absolutePath,
                        null,
                        SQLiteDatabase.OPEN_READONLY
                    )

                // 1. Buchdateien separat aus Tabelle "data" lesen
                val filesByBook =
                    mutableMapOf<Long, MutableList<BookFile>>()

                val fileCursor =
                    db.rawQuery(
                        """
                SELECT
                    book,
                    name,
                    format
                FROM data
                ORDER BY book
                """.trimIndent(),
                        null
                    )

                while (fileCursor.moveToNext()) {

                    val bookId =
                        fileCursor.getLong(
                            fileCursor.getColumnIndexOrThrow("book")
                        )

                    val fileName =
                        fileCursor.getString(
                            fileCursor.getColumnIndexOrThrow("name")
                        )

                    val format =
                        fileCursor.getString(
                            fileCursor.getColumnIndexOrThrow("format")
                        )

                    filesByBook
                        .getOrPut(bookId) {
                            mutableListOf()
                        }
                        .add(
                            BookFile(
                                name = fileName,
                                format = format
                            )
                        )
                }

                fileCursor.close()

                android.util.Log.d(
                    "CalibreReader",
                    "Dateizuordnungen für ${filesByBook.size} Bücher gefunden"
                )

                // 2. Bücher und Autoren lesen
                val cursor =
                    db.rawQuery(
                        """
                SELECT
                    b.id,
                    b.title,
                    b.path,
                    GROUP_CONCAT(DISTINCT a.name) AS authors
                FROM books b
                LEFT JOIN books_authors_link bal
                    ON bal.book = b.id
                LEFT JOIN authors a
                    ON a.id = bal.author
                GROUP BY
                    b.id,
                    b.title,
                    b.path
                ORDER BY
                    b.title COLLATE NOCASE
                """.trimIndent(),
                        null
                    )

                val loadedBooks =
                    mutableListOf<Book>()

                while (cursor.moveToNext()) {

                    val id =
                        cursor.getLong(
                            cursor.getColumnIndexOrThrow("id")
                        )

                    val title =
                        cursor.getString(
                            cursor.getColumnIndexOrThrow("title")
                        )

                    val path =
                        cursor.getString(
                            cursor.getColumnIndexOrThrow("path")
                        )

                    val authors =
                        cursor.getString(
                            cursor.getColumnIndexOrThrow("authors")
                        ) ?: "Unbekannter Autor"

                    val bookFiles =
                        filesByBook[id] ?: emptyList()

                    val formats =
                        bookFiles
                            .map { it.format }
                            .distinct()

                    loadedBooks +=
                        Book(
                            id = id,
                            title = title,
                            authors = authors,
                            path = path,
                            formats = formats,
                            files = bookFiles
                        )
                }

                cursor.close()
                db.close()

                runOnUiThread {
                    books = loadedBooks
                    libraryLoaded = true
                    calibreViewModel.setLoading(false)
                }

            } catch (e: Exception) {

                android.util.Log.e(
                    "CalibreReader",
                    "Fehler beim Laden der Calibre-Datenbank",
                    e
                )

                runOnUiThread {
                    calibreViewModel.setError(
                        e.message ?: e.javaClass.simpleName
                    )
                    calibreViewModel.setLoading(false)
                }
            }

        }.start()
    }

    private fun loadCover(book: Book): ByteArray? {

        val rootFolderId = selectedCalibreFolderId ?: return null

        val coverPath =
            "${book.path}/cover.jpg"

        return GraphClient().downloadFileByRelativePath(
            accessToken = accessToken,
            rootFolderId = rootFolderId,
            relativePath = coverPath
        )
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
            selectedCalibreFolderId

        if (rootFolderId == null) {

            android.util.Log.e(
                "CalibreReader",
                "selectedCalibreFolderId ist NULL"
            )

            errorMessage =
                "Calibre-Stammverzeichnis nicht gesetzt."

            return
        }

        // bisheriger Code folgt...

        Thread {

            try {

                val extension =
                    bookFile.format.lowercase()

                val relativePath =
                    "${book.path}/${bookFile.name}.$extension"

                android.util.Log.d(
                    "CalibreReader",
                    "OneDrive Buchpfad: $relativePath"
                )

                val bytes =
                    GraphClient()
                        .downloadFileByRelativePath(
                            accessToken = accessToken,
                            rootFolderId = rootFolderId,
                            relativePath = relativePath
                        )

                android.util.Log.d(
                    "CalibreReader",
                    "Buch heruntergeladen: ${bytes.size} Bytes"
                )

                val booksDirectory =
                    File(
                        filesDir,
                        "books"
                    )

                booksDirectory.mkdirs()

                val localFile =
                    File(
                        booksDirectory,
                        "${book.id}.$extension"
                    )

                localFile.writeBytes(bytes)

                android.util.Log.d(
                    "CalibreReader",
                    "Lokale Datei: ${localFile.absolutePath}"
                )

                android.util.Log.d(
                    "CalibreReader",
                    "Lokale Dateigrösse: ${localFile.length()} Bytes"
                )

                runOnUiThread {

                    openLocalBook(
                        localFile,
                        extension
                    )

                    isLoading = false
                }

            } catch (e: Exception) {

                runOnUiThread {

                    errorMessage =
                        e.message ?: e.javaClass.simpleName

                    isLoading = false
                }
            }

        }.start()
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

            errorMessage =
                "Keine passende App zum Öffnen von .$extension gefunden."
        }
    }
    private fun startLogin() {

        calibreViewModel.clearError()
        calibreViewModel.setLoading(true)

        authManager.signIn(

            activity = this,

            onSuccess = { result ->

                accessToken =
                    result.accessToken

                userName =
                    result.account.username

                loggedIn = true

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
