package ch.sakru.calibrereader

import android.app.Activity
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import ch.sakru.calibrereader.auth.GoogleDriveAuthorizationActivity
import ch.sakru.calibrereader.auth.GoogleDriveConsent
import ch.sakru.calibrereader.i18n.AppLanguage
import ch.sakru.calibrereader.i18n.LanguageManager
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

    private val googleAuthorizationLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
            ::finishGoogleAuthorization
        )

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(
            LanguageManager.wrap(newBase)
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        app =
            (application as CalibreReaderApplication).services

        lifecycleScope.launch {
            try {
                calibreViewModel.setSavedLibraries(
                    app.libraryStorage.loadLibraries()
                )
                calibreViewModel.setLibraryViewMode(
                    app.libraryStorage.loadViewMode()
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                calibreViewModel.setError(
                    getString(R.string.saved_libraries_load_error)
                )
            }
        }

        lifecycleScope.launch {
            app.microsoftAuthManager.ready.collect(
                calibreViewModel::setMsalReady
            )
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                launch {
                    calibreViewModel.interactiveLoginRequests.collect(
                        ::startInteractiveLogin
                    )
                }

                launch {
                    app.googleDriveAuthManager.consent.collect { consent ->
                        if (
                            consent != null &&
                            calibreViewModel.isCurrentAuthentication(
                                consent.generation,
                                StorageProvider.GOOGLE_DRIVE
                            ) &&
                            app.googleDriveAuthManager.markConsentLaunched(
                                consent.revision
                            )
                        ) {
                            launchGoogleAuthorization(
                                consent
                            )
                        }
                    }
                }

                launch {
                    calibreViewModel.booksToOpen.collect { request ->
                        if (
                            calibreViewModel.sessionState.value.libraryIdentity ==
                            request.libraryIdentity &&
                            calibreViewModel.uiState.value.screen ==
                            CalibreScreen.BOOKS
                        ) {
                            try {
                                app.bookOpener.open(
                                    request.book,
                                    request.libraryIdentity
                                )
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (_: Exception) {
                                calibreViewModel.setError(
                                    getString(R.string.book_open_error)
                                )
                            }
                        }
                    }
                }
            }
        }

        setContent {
            val ui by
                calibreViewModel.uiState.collectAsState()

            val session by
                calibreViewModel.sessionState.collectAsState()

            val microsoftError by
                app.microsoftAuthManager.initializationError.collectAsState()

            val storage =
                session.activeProvider?.let {
                    app.storageFactory.getStorage(it)
                }

            val identity =
                session.libraryIdentity

            val currentLanguage =
                LanguageManager.current(this)

            BackHandler(
                enabled =
                    ui.screen != CalibreScreen.PROVIDERS ||
                        ui.isLoading
            ) {
                when (ui.screen) {
                    CalibreScreen.BOOKS ->
                        calibreViewModel.showLibraries()

                    CalibreScreen.FOLDERS ->
                        storage?.let(
                            calibreViewModel::navigateUp
                        ) ?: cancelConnection()

                    CalibreScreen.LIBRARIES,
                    CalibreScreen.PROVIDERS ->
                        cancelConnection()
                }
            }

            MaterialTheme {
                Surface(
                    Modifier
                        .fillMaxSize()
                        .safeDrawingPadding()
                ) {
                    if (
                        ui.screen == CalibreScreen.PROVIDERS ||
                        storage == null ||
                        (
                            ui.screen == CalibreScreen.BOOKS &&
                                identity == null
                            )
                    ) {
                        ProviderSelectionScreen(
                            msalReady = ui.msalReady,
                            microsoftError = microsoftError,
                            dropboxConfigured =
                                app.dropboxAuthManager.isConfigured,
                            isLoading = ui.isLoading,
                            errorMessage = ui.errorMessage,
                            currentLanguage = currentLanguage,
                            feedbackEnabled =
                                app.feedbackOpener.isConfigured,
                            onSelect = ::selectProvider,
                            onCancel = ::cancelConnection,
                            onLanguageChange = ::changeLanguage,
                            onFeedback = {
                                app.feedbackOpener.open(
                                    currentLanguage.languageTag
                                )
                            }
                        )
                    } else {
                        when (ui.screen) {
                            CalibreScreen.LIBRARIES ->
                                LibrarySelectionScreen(
                                    providerName =
                                        session.activeProvider!!.displayName,
                                    userName = ui.userName,
                                    libraries =
                                        calibreViewModel.availableLibraries,
                                    isLoading = ui.isLoading,
                                    errorMessage = ui.errorMessage,
                                    currentLanguage = currentLanguage,
                                    feedbackEnabled =
                                        app.feedbackOpener.isConfigured,
                                    onLibraryClick = {
                                        calibreViewModel.loadSavedLibraryAndBooks(
                                            it,
                                            storage,
                                            cacheDir,
                                            app.calibreRepository::loadBooks
                                        )
                                    },
                                    onAddLibrary = {
                                        calibreViewModel.browseRoot(
                                            storage
                                        )
                                    },
                                    onChangeProvider =
                                        ::cancelConnection,
                                    onSignOut =
                                        ::signOut,
                                    onLanguageChange =
                                        ::changeLanguage,
                                    onFeedback = {
                                        app.feedbackOpener.open(
                                            currentLanguage.languageTag
                                        )
                                    }
                                )

                            CalibreScreen.FOLDERS ->
                                CloudFolderScreen(
                                    providerName =
                                        session.activeProvider!!.displayName,
                                    userName = ui.userName,
                                    currentPath =
                                        session.folderPath.map {
                                            it.name
                                        },
                                    isLoading = ui.isLoading,
                                    items = ui.currentItems,
                                    errorMessage = ui.errorMessage,
                                    calibreLibraryFound =
                                        ui.calibreLibraryFound,
                                    onFolderClick = {
                                        calibreViewModel.openFolder(
                                            it,
                                            storage
                                        )
                                    },
                                    onUseLibrary = {
                                        calibreViewModel.useCurrentLibrary(
                                            storage,
                                            cacheDir,
                                            app.calibreRepository::loadBooks,
                                            app.libraryStorage::saveLibraries
                                        )
                                    },
                                    onBack = {
                                        calibreViewModel.navigateUp(
                                            storage
                                        )
                                    },
                                    onRetry = {
                                        calibreViewModel.retryFolder(
                                            storage
                                        )
                                    }
                                )

                            CalibreScreen.BOOKS ->
                                key(identity) {
                                    val books =
                                        calibreViewModel.filteredBooks

                                    LibraryScreen(
                                        books = books,
                                        totalBookCount =
                                            ui.books.size,
                                        visibleBookCount =
                                            books.size,
                                        searchQuery =
                                            ui.searchQuery,
                                        onSearchQueryChange =
                                            calibreViewModel::setSearchQuery,
                                        sortMode =
                                            ui.sortMode,
                                        onSortModeChange =
                                            calibreViewModel::setSortMode,
                                        libraryIdentity =
                                            identity!!,
                                        cloudStorage =
                                            storage,
                                        coverRepository =
                                            app.coverRepository,
                                        viewMode =
                                            ui.libraryViewMode,
                                        onViewModeChange = {
                                            calibreViewModel.changeViewMode(
                                                it,
                                                app.libraryStorage::saveViewMode
                                            )
                                        },
                                        onOpenBook = { book, file ->
                                            calibreViewModel.downloadBook(
                                                book,
                                                file,
                                                storage
                                            )
                                        },
                                        isLoading =
                                            ui.isLoading,
                                        errorMessage =
                                            ui.errorMessage,
                                        onBack =
                                            calibreViewModel::showLibraries
                                    )
                                }

                            CalibreScreen.PROVIDERS ->
                                Unit
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()

        if (
            ::app.isInitialized &&
            app.dropboxAuthManager.awaitingOAuth
        ) {
            val generation =
                if (
                    calibreViewModel.sessionState.value.activeProvider ==
                    StorageProvider.DROPBOX
                ) {
                    calibreViewModel.currentAuthenticationGeneration
                } else {
                    calibreViewModel.beginAuthentication(
                        StorageProvider.DROPBOX
                    )
                }

            calibreViewModel.finishPendingAuthentication(
                generation,
                StorageProvider.DROPBOX
            ) {
                app.dropboxAuthManager.finishSignIn()
            }
        }
    }

    private fun changeLanguage(
        language: AppLanguage
    ) {
        if (
            LanguageManager.current(this) ==
            language
        ) {
            return
        }

        LanguageManager.setLanguage(
            this,
            language
        )

        recreate()
    }

    private fun selectProvider(
        provider: StorageProvider
    ) {
        if (
            provider !=
            StorageProvider.DROPBOX
        ) {
            app.dropboxAuthManager.cancelPendingSignIn()
        }

        app.googleDriveAuthManager.cancelPendingSignIn()

        when (provider) {
            StorageProvider.ONEDRIVE ->
                calibreViewModel.connect(
                    provider,
                    app.microsoftAuthManager::restoreSessionAsync
                )

            StorageProvider.DROPBOX ->
                calibreViewModel.connect(
                    provider,
                    app.dropboxAuthManager::restoreSession
                )

            StorageProvider.GOOGLE_DRIVE ->
                calibreViewModel.connect(
                    provider,
                    app.googleDriveAuthManager::restoreSession
                )
        }
    }

    private fun startInteractiveLogin(
        request: InteractiveLoginRequest
    ) {
        if (
            !calibreViewModel.isCurrentAuthentication(
                request.generation,
                request.provider
            )
        ) {
            return
        }

        when (request.provider) {
            StorageProvider.ONEDRIVE ->
                app.microsoftAuthManager.signIn(
                    activity = this,
                    onSuccess = { user ->
                        runOnUiThread {
                            calibreViewModel.completeAuthentication(
                                request.generation,
                                request.provider,
                                user
                            )
                        }
                    },
                    onError = {
                        runOnUiThread {
                            calibreViewModel.authenticationFailed(
                                request.generation,
                                request.provider,
                                getString(
                                    R.string.microsoft_signin_failed
                                )
                            )
                        }
                    },
                    onCancel = {
                        runOnUiThread {
                            calibreViewModel.authenticationFailed(
                                request.generation,
                                request.provider,
                                getString(
                                    R.string.signin_cancelled
                                )
                            )
                        }
                    }
                )

            StorageProvider.DROPBOX ->
                try {
                    app.dropboxAuthManager.startSignIn(
                        this
                    )
                } catch (_: Exception) {
                    calibreViewModel.authenticationFailed(
                        request.generation,
                        request.provider,
                        getString(
                            R.string.dropbox_signin_start_failed
                        )
                    )
                }

            StorageProvider.GOOGLE_DRIVE ->
                calibreViewModel.finishPendingAuthentication(
                    request.generation,
                    request.provider,
                    keepPending = true
                ) {
                    app.googleDriveAuthManager.startSignIn(
                        request.generation
                    )
                }
        }
    }

    private fun launchGoogleAuthorization(
        consent: GoogleDriveConsent
    ) {
        try {
            googleAuthorizationLauncher.launch(
                GoogleDriveAuthorizationActivity.request(
                    this,
                    consent.id,
                    consent.intent
                )
            )
        } catch (_: Exception) {
            app.googleDriveAuthManager.cancelPendingSignIn()

            calibreViewModel.authenticationFailed(
                consent.generation,
                StorageProvider.GOOGLE_DRIVE,
                getString(
                    R.string.google_signin_open_failed
                )
            )
        }
    }

    private fun finishGoogleAuthorization(
        result: ActivityResult
    ) {
        val pending =
            app.googleDriveAuthManager.consent.value
                ?: return

        if (
            result.data?.getStringExtra(
                GoogleDriveAuthorizationActivity.REQUEST_ID
            ) != pending.id ||
            !calibreViewModel.isCurrentAuthentication(
                pending.generation,
                StorageProvider.GOOGLE_DRIVE
            )
        ) {
            return
        }

        if (
            result.resultCode !=
            Activity.RESULT_OK ||
            result.data == null
        ) {
            app.googleDriveAuthManager.cancelPendingSignIn()

            calibreViewModel.authenticationFailed(
                pending.generation,
                StorageProvider.GOOGLE_DRIVE,
                getString(
                    R.string.google_signin_cancelled
                )
            )

            return
        }

        calibreViewModel.finishPendingAuthentication(
            pending.generation,
            StorageProvider.GOOGLE_DRIVE
        ) {
            app.googleDriveAuthManager.finishSignIn(
                pending.revision,
                app.googleDriveAuthorization.readResult(
                    result.data!!
                )
            )
        }
    }

    private fun cancelConnection() {
        app.googleDriveAuthManager.cancelPendingSignIn()
        app.dropboxAuthManager.cancelPendingSignIn()
        calibreViewModel.showProviders()
    }

    private fun signOut() {
        val provider =
            calibreViewModel.sessionState.value.activeProvider
                ?: return

        app.coverRepository.clear()

        calibreViewModel.signOut {
            when (provider) {
                StorageProvider.ONEDRIVE ->
                    app.microsoftAuthManager.signOutAsync()

                StorageProvider.DROPBOX ->
                    withContext(Dispatchers.IO) {
                        app.dropboxAuthManager.signOut()
                    }

                StorageProvider.GOOGLE_DRIVE ->
                    app.googleDriveAuthManager.signOut()
            }
        }
    }
}
