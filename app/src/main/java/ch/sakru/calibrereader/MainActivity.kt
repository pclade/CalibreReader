package ch.sakru.calibrereader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import android.database.sqlite.SQLiteDatabase
import java.io.File
import ch.sakru.calibrereader.auth.MicrosoftAuthManager
import ch.sakru.calibrereader.onedrive.DriveItem
import ch.sakru.calibrereader.onedrive.GraphClient
import ch.sakru.calibrereader.calibre.Book
import ch.sakru.calibrereader.calibre.CoverRepository
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import ch.sakru.calibrereader.calibre.BookFile
import ch.sakru.calibrereader.calibre.SavedLibrary
import ch.sakru.calibrereader.calibre.LibraryStorage
import ch.sakru.calibrereader.calibre.LibraryViewMode
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.grid.items

class MainActivity : ComponentActivity() {
    private var libraryViewMode by
    mutableStateOf(LibraryViewMode.LIST)
    private var showLibrarySelection by mutableStateOf(true)
    private var savedLibraries by
    mutableStateOf<List<SavedLibrary>>(emptyList())
    private lateinit var libraryStorage: LibraryStorage
    private var metadataDbItemId by mutableStateOf<String?>(null)
    private var accessToken by mutableStateOf("")

    private var currentItems by
    mutableStateOf<List<DriveItem>>(emptyList())

    private var currentPath by
    mutableStateOf(listOf("OneDrive"))

    private var calibreLibraryFound by
    mutableStateOf(false)

    private var selectedCalibreFolderId by
    mutableStateOf<String?>(null)
    private val coverRepository =
        CoverRepository()
    private lateinit var authManager: MicrosoftAuthManager

    private var msalReady by mutableStateOf(false)
    private var userName by mutableStateOf("")
    private var driveItems by mutableStateOf<List<DriveItem>>(emptyList())
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

        setContent {
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

                            viewMode = libraryViewMode,

                            onViewModeChange = { mode ->
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
                                    library.folderId

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
                            isLoading = isLoading,
                            items = currentItems,
                            errorMessage = errorMessage,
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
            library.folderId

        isLoading = true
        errorMessage = null

        Thread {

            try {

                val children =
                    GraphClient().getChildren(
                        accessToken = accessToken,
                        itemId = library.folderId
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
        private fun loadRootFolder() {

        isLoading = true

        Thread {

            try {

                val items =
                    GraphClient()
                        .getRootItems(accessToken)

                runOnUiThread {

                    currentItems = items

                    currentPath =
                        listOf("OneDrive")

                    isLoading = false
                }

            } catch (e: Exception) {

                runOnUiThread {

                    errorMessage =
                        e.message

                    isLoading = false
                }
            }

        }.start()
    }

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
                folderId = folderId,
                account = userName
            )

        val newLibraries =
            savedLibraries
                .filterNot {
                    it.folderId == folderId
                } + library

        savedLibraries =
            newLibraries

        Thread {

            kotlinx.coroutines.runBlocking {
                libraryStorage.saveLibraries(
                    newLibraries
                )
            }

        }.start()
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
    private fun openFolder(item: DriveItem) {

        if (!item.isFolder) {
            return
        }

        isLoading = true
        errorMessage = null
        calibreLibraryFound = false

        Thread {

            try {

                val children =
                    GraphClient().getChildren(
                        accessToken = accessToken,
                        itemId = item.id
                    )

                val metadataItem = children.firstOrNull {
                    it.name.equals(
                        "metadata.db",
                        ignoreCase = true
                    )
                }

                val metadataDbFound = metadataItem != null

                runOnUiThread {

                    currentItems = children

                    currentPath =
                        currentPath + item.name

                    if (metadataDbFound) {
                        calibreLibraryFound = true
                        selectedCalibreFolderId = item.id
                        metadataDbItemId = metadataItem?.id
                    }

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

    private fun loadCalibreDatabase() {

        val itemId = metadataDbItemId ?: return

        isLoading = true
        errorMessage = null

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
                    isLoading = false
                }

            } catch (e: Exception) {

                android.util.Log.e(
                    "CalibreReader",
                    "Fehler beim Laden der Calibre-Datenbank",
                    e
                )

                runOnUiThread {
                    errorMessage =
                        e.message ?: e.javaClass.simpleName
                    isLoading = false
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
        errorMessage = null
        isLoading = true

        authManager.signIn(
            activity = this,
            onSuccess = { result ->
                userName = result.account.username
                loggedIn = true

                Thread {
                    try {
                        val items = GraphClient().getRootItems(result.accessToken)
                        runOnUiThread {
                            accessToken = result.accessToken
                            currentItems = items
                            isLoading = false
                        }
                    } catch (e: Exception) {
                        runOnUiThread {
                            errorMessage = e.message ?: e.javaClass.simpleName
                            isLoading = false
                        }
                    }
                }.start()
            },
            onError = { error ->
                runOnUiThread {
                    errorMessage = error.message ?: error.javaClass.simpleName
                    isLoading = false
                }
            },
            onCancel = {
                runOnUiThread {
                    errorMessage = "Anmeldung wurde abgebrochen."
                    isLoading = false
                }
            }
        )
    }
}

@Composable
private fun LoginScreen(
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

@Composable
private fun OneDriveScreen(
    userName: String,
    currentPath: List<String>,
    isLoading: Boolean,
    items: List<DriveItem>,
    errorMessage: String?,
    calibreLibraryFound: Boolean,
    onFolderClick: (DriveItem) -> Unit,
    onUseLibrary: () -> Unit
) {

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {

        Text(
            text = "Calibre Reader",
            style =
                MaterialTheme.typography.headlineMedium
        )

        Text(text = userName)

        Spacer(
            modifier = Modifier.height(12.dp)
        )

        Text(
            text = currentPath.joinToString(" / "),
            style =
                MaterialTheme.typography.bodyMedium
        )

        Spacer(
            modifier = Modifier.height(20.dp)
        )

        if (calibreLibraryFound) {

            Text(
                text =
                    "✓ Calibre-Bibliothek erkannt",
                style =
                    MaterialTheme.typography.titleMedium
            )

            Spacer(
                modifier = Modifier.height(12.dp)
            )

            Button(
                onClick = onUseLibrary
            ) {
                Text(
                    "Diese Bibliothek verwenden"
                )
            }

            Spacer(
                modifier = Modifier.height(20.dp)
            )
        }

        if (isLoading) {

            CircularProgressIndicator()

            Spacer(
                modifier = Modifier.height(16.dp)
            )
        }

        errorMessage?.let {

            Text(text = it)

            Spacer(
                modifier = Modifier.height(16.dp)
            )
        }

        LazyColumn {

            items(items) { item ->

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            enabled = item.isFolder
                        ) {
                            onFolderClick(item)
                        }
                        .padding(
                            vertical = 14.dp
                        )
                ) {

                    Text(
                        text =
                            if (item.isFolder) {
                                "📁  ${item.name}"
                            } else {
                                "📄  ${item.name}"
                            }
                    )
                }

                HorizontalDivider()
            }
        }
    }
}
@Composable
private fun LibraryScreen(
    books: List<Book>,
    accessToken: String,
    rootFolderId: String?,
    coverRepository: CoverRepository,
    viewMode: LibraryViewMode,
    onViewModeChange: (LibraryViewMode) -> Unit,
    onOpenBook: (Book, BookFile) -> Unit
) {

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Column(
                modifier = Modifier.weight(1f)
            ) {

                Text(
                    text = "Meine Bibliothek",
                    style =
                        MaterialTheme.typography.headlineMedium
                )

                Text(
                    text = "${books.size} Bücher"
                )
            }

            Button(
                onClick = {
                    onViewModeChange(
                        LibraryViewMode.LIST
                    )
                }
            ) {
                Text("☷")
            }

            Spacer(
                modifier = Modifier.width(8.dp)
            )

            Button(
                onClick = {
                    onViewModeChange(
                        LibraryViewMode.GRID
                    )
                }
            ) {
                Text("▦")
            }
        }

        Spacer(
            modifier = Modifier.height(16.dp)
        )

        when (viewMode) {

            LibraryViewMode.LIST -> {

                LibraryList(
                    books = books,
                    accessToken = accessToken,
                    rootFolderId = rootFolderId,
                    coverRepository = coverRepository,
                    onOpenBook = onOpenBook
                )
            }

            LibraryViewMode.GRID -> {

                LibraryGrid(
                    books = books,
                    accessToken = accessToken,
                    rootFolderId = rootFolderId,
                    coverRepository = coverRepository,
                    onOpenBook = onOpenBook
                )
            }
        }
    }
}
@Composable
private fun LibraryList(
    books: List<Book>,
    accessToken: String,
    rootFolderId: String?,
    coverRepository: CoverRepository,
    onOpenBook: (Book, BookFile) -> Unit
) {

    LazyColumn {

        items(
            items = books,
            key = { it.id }
        ) { book ->

            BookRow(
                book = book,
                accessToken = accessToken,
                rootFolderId = rootFolderId,
                coverRepository = coverRepository,
                onOpenBook = onOpenBook
            )

            HorizontalDivider()
        }
    }
}
@Composable
private fun BookRow(
    book: Book,
    accessToken: String,
    rootFolderId: String?,
    coverRepository: CoverRepository,
    onOpenBook: (Book, BookFile) -> Unit
) {

    var bitmap by remember(book.id) {
        mutableStateOf(
            coverRepository.getCachedCover(book.id)
        )
    }

    LaunchedEffect(
        book.id,
        rootFolderId
    ) {

        if (
            bitmap == null &&
            rootFolderId != null
        ) {

            bitmap =
                kotlinx.coroutines.withContext(
                    kotlinx.coroutines.Dispatchers.IO
                ) {

                    coverRepository.loadCover(
                        book = book,
                        accessToken = accessToken,
                        rootFolderId = rootFolderId
                    )
                }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {

        if (bitmap != null) {

            Image(
                bitmap =
                    bitmap!!.asImageBitmap(),

                contentDescription =
                    "Cover von ${book.title}",

                modifier = Modifier
                    .width(80.dp)
                    .height(120.dp),

                contentScale =
                    ContentScale.Crop
            )

        } else {

            Box(
                modifier = Modifier
                    .width(80.dp)
                    .height(120.dp),
                contentAlignment =
                    Alignment.Center
            ) {

                Text("📖")
            }
        }

        Spacer(
            modifier = Modifier.width(16.dp)
        )

        Column(
            modifier =
                Modifier.weight(1f)
        ) {

            Text(
                text = book.title,
                style =
                    MaterialTheme.typography.titleMedium
            )

            Spacer(
                modifier = Modifier.height(4.dp)
            )

            Text(
                text = book.authors,
                style =
                    MaterialTheme.typography.bodyMedium
            )

            Spacer(
                modifier = Modifier.height(6.dp)
            )

            Row {

                book.files.forEach { bookFile ->

                    Button(
                        onClick = {

                            android.util.Log.d(
                                "CalibreReader",
                                "FORMAT gedrückt: ${book.title} / ${bookFile.format}"
                            )

                            onOpenBook(
                                book,
                                bookFile
                            )
                        },
                        modifier = Modifier.padding(
                            end = 6.dp
                        )
                    ) {
                        Text(bookFile.format)
                    }                }
            }
        }
    }
}
@Composable
private fun LibrarySelectionScreen(
    libraries: List<SavedLibrary>,
    onLibraryClick: (SavedLibrary) -> Unit,
    onAddLibrary: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {

        Text(
            text = "Meine Bibliotheken",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(
            modifier = Modifier.height(20.dp)
        )

        if (libraries.isEmpty()) {

            Text("Noch keine Bibliothek gespeichert.")

            Spacer(
                modifier = Modifier.height(20.dp)
            )
        }

        LazyColumn(
            modifier = Modifier.weight(1f)
        ) {

            items(libraries) { library ->

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onLibraryClick(library)
                        }
                        .padding(vertical = 16.dp)
                ) {

                    Text(
                        text = "📚 ${library.name}",
                        style = MaterialTheme.typography.titleMedium
                    )

                    Spacer(
                        modifier = Modifier.height(4.dp)
                    )

                    Text(
                        text = library.account,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                HorizontalDivider()
            }
        }

        Button(
            onClick = onAddLibrary,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("+ Bibliothek hinzufügen")
        }
    }
}
@Composable
private fun LibraryGrid(
    books: List<Book>,
    accessToken: String,
    rootFolderId: String?,
    coverRepository: CoverRepository,
    onOpenBook: (Book, BookFile) -> Unit
) {

    LazyVerticalGrid(
        columns = GridCells.Adaptive(
            minSize = 110.dp
        )
    ) {

        items(
            items = books,
            key = { it.id }
        ) { book ->

            BookGridItem(
                book = book,
                accessToken = accessToken,
                rootFolderId = rootFolderId,
                coverRepository = coverRepository,
                onOpenBook = onOpenBook
            )
        }
    }
}
@Composable
private fun BookGridItem(
    book: Book,
    accessToken: String,
    rootFolderId: String?,
    coverRepository: CoverRepository,
    onOpenBook: (Book, BookFile) -> Unit
) {

    var bitmap by remember(book.id) {
        mutableStateOf(
            coverRepository
                .getCachedCover(book.id)
        )
    }

    LaunchedEffect(
        book.id,
        rootFolderId
    ) {

        if (
            bitmap == null &&
            rootFolderId != null
        ) {

            bitmap =
                kotlinx.coroutines.withContext(
                    kotlinx.coroutines.Dispatchers.IO
                ) {

                    coverRepository.loadCover(
                        book = book,
                        accessToken = accessToken,
                        rootFolderId = rootFolderId
                    )
                }
        }
    }

    Column(
        modifier = Modifier
            .padding(5.dp)
            .clickable {

                val file =
                    book.files.firstOrNull()

                if (file != null) {
                    onOpenBook(
                        book,
                        file
                    )
                }
            }
    ) {

        if (bitmap != null) {

            Image(
                bitmap =
                    bitmap!!.asImageBitmap(),

                contentDescription =
                    book.title,

                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.67f),

                contentScale =
                    ContentScale.Crop
            )

        } else {

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.67f),

                contentAlignment =
                    Alignment.Center
            ) {

                Text("📖")
            }
        }
    }
}