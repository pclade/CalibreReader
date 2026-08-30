package ch.sakru.calibrereader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

import androidx.compose.foundation.layout.fillMaxSize

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface

import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier

import java.io.File
import ch.sakru.calibrereader.auth.MicrosoftAuthManager
import ch.sakru.calibrereader.model.StorageProvider

import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import ch.sakru.calibrereader.ui.storage.onedrive.OneDriveScreen
import ch.sakru.calibrereader.ui.login.LoginScreen
import ch.sakru.calibrereader.ui.libraries.LibrarySelectionScreen
import ch.sakru.calibrereader.ui.library.LibraryScreen
import androidx.activity.viewModels
import ch.sakru.calibrereader.viewmodel.CalibreViewModel
import androidx.compose.runtime.collectAsState
import ch.sakru.calibrereader.app.CalibreReaderApp
class MainActivity : ComponentActivity() {
    private val calibreViewModel: CalibreViewModel by viewModels()
    private lateinit var app: CalibreReaderApp
    private lateinit var authManager: MicrosoftAuthManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        app = CalibreReaderApp(applicationContext)
        lifecycleScope.launch {

            try {

                val libraries =
                    app.libraryStorage.loadLibraries()

                val viewMode =
                    app.libraryStorage.loadViewMode()

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

        authManager =
            MicrosoftAuthManager(
                context = applicationContext,
                authSession = app.authSession,

                onReadyChanged = { ready ->

                    runOnUiThread {
                        calibreViewModel.setMsalReady(
                            ready
                        )
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

                        val activeCloudStorage =
                            sessionState.activeProvider?.let { provider ->
                                app.storageFactory.getStorage(
                                    provider
                                )
                            }

                        LibraryScreen(
                            books = uiState.books,
                            rootFolderId =
                                sessionState.selectedLibraryRootId,
                            cloudStorage =
                                activeCloudStorage,
                            coverRepository = app.coverRepository,
                            viewMode = uiState.libraryViewMode,

                            onViewModeChange = { mode ->

                                calibreViewModel.changeViewMode(
                                    mode = mode,
                                    libraryStorage = app.libraryStorage
                                )
                            },
                            onOpenBook = { book, bookFile ->

                                val cloudStorage =
                                    activeCloudStorage

                                if (cloudStorage == null) {

                                    calibreViewModel.setError(
                                        "Kein aktiver Cloud-Speicher verfügbar."
                                    )

                                } else {

                                    calibreViewModel.downloadBook(
                                        book = book,
                                        bookFile = bookFile,
                                        cloudStorage = cloudStorage,
                                        onSuccess = { downloadedBook ->

                                            lifecycleScope.launch {

                                                try {

                                                    app.bookOpener.open(
                                                        downloadedBook
                                                    )

                                                } catch (e: Exception) {

                                                    calibreViewModel.setError(
                                                        e.message
                                                            ?: e.javaClass.simpleName
                                                    )
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                        )

                    } else if (uiState.showLibrarySelection) {

                        LibrarySelectionScreen(
                            libraries = uiState.savedLibraries,

                            onLibraryClick = { library ->

                                val cloudStorage =
                                    app.storageFactory.getStorage(
                                        library.provider
                                    )

                                calibreViewModel.loadSavedLibraryAndBooks(
                                    library = library,
                                    cloudStorage = cloudStorage,
                                    calibreRepository = app.calibreRepository,
                                    databaseFile =
                                        File(
                                            filesDir,
                                            "metadata.db"
                                        )
                                )
                            },
                            onAddLibrary = {

                                calibreViewModel.setShowLibrarySelection(
                                    false
                                )

                                calibreViewModel.setActiveProvider(
                                    StorageProvider.ONEDRIVE
                                )

                                calibreViewModel.loadRootFolder(
                                    cloudStorage = app.oneDriveStorage,
                                    provider = StorageProvider.ONEDRIVE
                                )                            }
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

                                calibreViewModel.openFolder(
                                    item = item,
                                    cloudStorage = app.oneDriveStorage
                                )
                            },

                            onUseLibrary = {

                                calibreViewModel.saveCurrentLibrary(
                                    libraryStorage = app.libraryStorage
                                )
                                calibreViewModel.loadCalibreDatabase(
                                    cloudStorage = app.oneDriveStorage,
                                    calibreRepository = app.calibreRepository,
                                    databaseFile =
                                        File(
                                            filesDir,
                                            "metadata.db"
                                        )
                                )
                            }                        )
                    }
                }
            }
        }
    }

    private fun checkExistingLogin() {

        authManager.restoreSession(

            onSuccess = { user ->

                runOnUiThread {

                    calibreViewModel.setUserName(
                        user.userName
                    )

                    calibreViewModel.setLoggedIn(
                        true
                    )

                    calibreViewModel.loadRootFolder(
                        cloudStorage = app.oneDriveStorage,
                        provider = StorageProvider.ONEDRIVE
                    )                }
            },

            onNoAccount = {

                runOnUiThread {

                    calibreViewModel.setLoggedIn(
                        false
                    )
                }
            },

            onError = { exception ->

                runOnUiThread {

                    calibreViewModel.setLoggedIn(
                        false
                    )

                    calibreViewModel.setError(
                        exception.message
                            ?: exception.javaClass.simpleName
                    )
                }
            }
        )
    }

    private fun startLogin() {

        calibreViewModel.clearError()
        calibreViewModel.setLoading(
            true
        )

        authManager.signIn(

            activity = this,

            onSuccess = { user ->

                runOnUiThread {

                    calibreViewModel.setUserName(
                        user.userName
                    )

                    calibreViewModel.setLoggedIn(
                        true
                    )

                    calibreViewModel.loadRootFolder(
                        cloudStorage = app.oneDriveStorage,
                        provider = StorageProvider.ONEDRIVE
                    )
                }
            },

            onError = { error ->

                runOnUiThread {

                    calibreViewModel.setError(
                        error.message
                            ?: error.javaClass.simpleName
                    )

                    calibreViewModel.setLoading(
                        false
                    )
                }
            },

            onCancel = {

                runOnUiThread {

                    calibreViewModel.setError(
                        "Anmeldung wurde abgebrochen."
                    )

                    calibreViewModel.setLoading(
                        false
                    )
                }
            }
        )
    }}
