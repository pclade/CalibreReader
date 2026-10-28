package ch.sakru.calibrereader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import ch.sakru.calibrereader.app.CalibreReaderApp
import ch.sakru.calibrereader.app.CalibreReaderApplication
import ch.sakru.calibrereader.model.StorageProvider
import ch.sakru.calibrereader.ui.libraries.LibrarySelectionScreen
import ch.sakru.calibrereader.ui.library.LibraryScreen
import ch.sakru.calibrereader.ui.login.ProviderSelectionScreen
import ch.sakru.calibrereader.ui.storage.CloudFolderScreen
import ch.sakru.calibrereader.viewmodel.CalibreScreen
import ch.sakru.calibrereader.viewmodel.CalibreViewModel
import ch.sakru.calibrereader.viewmodel.InteractiveLoginRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private val calibreViewModel: CalibreViewModel by viewModels()
    private lateinit var app: CalibreReaderApp

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        app = (application as CalibreReaderApplication).services
        lifecycleScope.launch {
            try {
                calibreViewModel.setSavedLibraries(app.libraryStorage.loadLibraries())
                calibreViewModel.setLibraryViewMode(app.libraryStorage.loadViewMode())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                calibreViewModel.setError("Die gespeicherten Bibliotheken konnten nicht geladen werden.")
            }
        }
        lifecycleScope.launch {
            app.microsoftAuthManager.ready.collect(calibreViewModel::setMsalReady)
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                launch {
                    calibreViewModel.interactiveLoginRequests.collect(::startInteractiveLogin)
                }
                launch {
                    calibreViewModel.booksToOpen.collect { request ->
                        if (calibreViewModel.sessionState.value.libraryIdentity == request.libraryIdentity &&
                            calibreViewModel.uiState.value.screen == CalibreScreen.BOOKS) {
                            try {
                                app.bookOpener.open(request.book, request.libraryIdentity)
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (error: Exception) {
                                calibreViewModel.setError(error.message ?: "Das Buch konnte nicht geöffnet werden.")
                            }
                        }
                    }
                }
            }
        }
        setContent {
            val ui by calibreViewModel.uiState.collectAsState()
            val session by calibreViewModel.sessionState.collectAsState()
            val microsoftError by app.microsoftAuthManager.initializationError.collectAsState()
            val storage = session.activeProvider?.let { app.storageFactory.getStorage(it) }
            val identity = session.libraryIdentity

            BackHandler(enabled = ui.screen != CalibreScreen.PROVIDERS || ui.isLoading) {
                when (ui.screen) {
                    CalibreScreen.BOOKS -> calibreViewModel.showLibraries()
                    CalibreScreen.FOLDERS ->
                        storage?.let(calibreViewModel::navigateUp) ?: cancelConnection()
                    CalibreScreen.LIBRARIES, CalibreScreen.PROVIDERS -> cancelConnection()
                }
            }
            MaterialTheme {
                Surface(Modifier.fillMaxSize().safeDrawingPadding()) {
                    if (ui.screen == CalibreScreen.PROVIDERS || storage == null ||
                        (ui.screen == CalibreScreen.BOOKS && identity == null)) {
                        ProviderSelectionScreen(
                            msalReady = ui.msalReady,
                            microsoftError = microsoftError,
                            dropboxConfigured = app.dropboxAuthManager.isConfigured,
                            isLoading = ui.isLoading,
                            errorMessage = ui.errorMessage,
                            onSelect = ::selectProvider,
                            onCancel = ::cancelConnection
                        )
                    } else {
                        when (ui.screen) {
                            CalibreScreen.LIBRARIES -> LibrarySelectionScreen(
                                providerName = session.activeProvider!!.displayName,
                                userName = ui.userName,
                                libraries = calibreViewModel.availableLibraries,
                                isLoading = ui.isLoading,
                                errorMessage = ui.errorMessage,
                                onLibraryClick = {
                                    calibreViewModel.loadSavedLibraryAndBooks(
                                        it, storage, cacheDir, app.calibreRepository::loadBooks
                                    )
                                },
                                onAddLibrary = { calibreViewModel.browseRoot(storage) },
                                onChangeProvider = ::cancelConnection,
                                onSignOut = ::signOut
                            )
                            CalibreScreen.FOLDERS -> CloudFolderScreen(
                                providerName = session.activeProvider!!.displayName,
                                userName = ui.userName,
                                currentPath = session.folderPath.map { it.name },
                                isLoading = ui.isLoading,
                                items = ui.currentItems,
                                errorMessage = ui.errorMessage,
                                calibreLibraryFound = ui.calibreLibraryFound,
                                onFolderClick = { calibreViewModel.openFolder(it, storage) },
                                onUseLibrary = {
                                    calibreViewModel.useCurrentLibrary(
                                        storage, cacheDir, app.calibreRepository::loadBooks,
                                        app.libraryStorage::saveLibraries
                                    )
                                },
                                onBack = { calibreViewModel.navigateUp(storage) },
                                onRetry = { calibreViewModel.retryFolder(storage) }
                            )
                            CalibreScreen.BOOKS -> key(identity) {
                                val books = calibreViewModel.filteredBooks
                                LibraryScreen(
                                    books = books,
                                    totalBookCount = ui.books.size,
                                    visibleBookCount = books.size,
                                    searchQuery = ui.searchQuery,
                                    onSearchQueryChange = calibreViewModel::setSearchQuery,
                                    sortMode = ui.sortMode,
                                    onSortModeChange = calibreViewModel::setSortMode,
                                    libraryIdentity = identity!!,
                                    cloudStorage = storage,
                                    coverRepository = app.coverRepository,
                                    viewMode = ui.libraryViewMode,
                                    onViewModeChange = {
                                        calibreViewModel.changeViewMode(it, app.libraryStorage::saveViewMode)
                                    },
                                    onOpenBook = { book, file -> calibreViewModel.downloadBook(book, file, storage) },
                                    isLoading = ui.isLoading,
                                    errorMessage = ui.errorMessage,
                                    onBack = calibreViewModel::showLibraries
                                )
                            }
                            CalibreScreen.PROVIDERS -> Unit
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::app.isInitialized && app.dropboxAuthManager.awaitingOAuth) {
            val generation = if (calibreViewModel.sessionState.value.activeProvider == StorageProvider.DROPBOX) {
                calibreViewModel.currentAuthenticationGeneration
            } else {
                calibreViewModel.beginAuthentication(StorageProvider.DROPBOX)
            }
            calibreViewModel.finishPendingAuthentication(generation, StorageProvider.DROPBOX) {
                app.dropboxAuthManager.finishSignIn()
            }
        }
    }

    private fun selectProvider(provider: StorageProvider) {
        if (provider != StorageProvider.DROPBOX) {
            app.dropboxAuthManager.cancelPendingSignIn()
        }
        when (provider) {
            StorageProvider.ONEDRIVE ->
                calibreViewModel.connect(provider, app.microsoftAuthManager::restoreSessionAsync)
            StorageProvider.DROPBOX ->
                calibreViewModel.connect(provider, app.dropboxAuthManager::restoreSession)
            StorageProvider.GOOGLE_DRIVE -> Unit
        }
    }

    private fun startInteractiveLogin(request: InteractiveLoginRequest) {
        if (!calibreViewModel.isCurrentAuthentication(request.generation, request.provider)) return
        when (request.provider) {
            StorageProvider.ONEDRIVE -> app.microsoftAuthManager.signIn(
                activity = this,
                onSuccess = { user ->
                    runOnUiThread {
                        calibreViewModel.completeAuthentication(request.generation, request.provider, user)
                    }
                },
                onError = {
                    runOnUiThread {
                        calibreViewModel.authenticationFailed(request.generation, request.provider,
                            "Die Microsoft-Anmeldung ist fehlgeschlagen. Bitte erneut versuchen.")
                    }
                },
                onCancel = {
                    runOnUiThread {
                        calibreViewModel.authenticationFailed(request.generation, request.provider,
                            "Anmeldung abgebrochen.")
                    }
                }
            )
            StorageProvider.DROPBOX -> try {
                app.dropboxAuthManager.startSignIn(this)
            } catch (_: Exception) {
                calibreViewModel.authenticationFailed(request.generation, request.provider,
                    "Die Dropbox-Anmeldung konnte nicht gestartet werden.")
            }
            StorageProvider.GOOGLE_DRIVE -> Unit
        }
    }

    private fun cancelConnection() {
        app.dropboxAuthManager.cancelPendingSignIn()
        calibreViewModel.showProviders()
    }

    private fun signOut() {
        val provider = calibreViewModel.sessionState.value.activeProvider ?: return
        app.coverRepository.clear()
        calibreViewModel.signOut {
            when (provider) {
                StorageProvider.ONEDRIVE -> app.microsoftAuthManager.signOutAsync()
                StorageProvider.DROPBOX -> withContext(Dispatchers.IO) { app.dropboxAuthManager.signOut() }
                StorageProvider.GOOGLE_DRIVE -> Unit
            }
        }
    }
}
