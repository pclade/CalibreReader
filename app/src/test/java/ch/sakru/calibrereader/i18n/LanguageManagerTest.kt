package ch.sakru.calibrereader.i18n

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LanguageManagerTest {
    @Test fun everyPreviouslyStoredLanguageIsPreserved() {
        AppLanguage.entries.forEach { language ->
            assertEquals(language, LanguageManager.languageToMigrate(false, false, language.languageTag))
        }
    }

    @Test fun anExistingOfficialSelectionTakesPrecedenceOverLegacyPreferences() {
        assertNull(LanguageManager.languageToMigrate(false, true, "tr"))
    }

    @Test fun resettingToSystemAfterMigrationDoesNotRestoreTheLegacyLanguage() {
        assertNull(LanguageManager.languageToMigrate(true, false, "de"))
    }

    @Test fun freshInstallPreservesThePreviousEnglishDefault() {
        assertEquals(AppLanguage.ENGLISH, LanguageManager.languageToMigrate(false, false, null))
    }

    @Test fun unsupportedLegacyValueFallsBackToEnglish() {
        assertEquals(AppLanguage.ENGLISH, LanguageManager.languageToMigrate(false, false, "it"))
    }
}
