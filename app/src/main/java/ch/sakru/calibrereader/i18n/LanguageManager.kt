package ch.sakru.calibrereader.i18n

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/** AppCompat owns the language; legacy preferences are read only during migration. */
object LanguageManager {
    private const val PREFERENCES_NAME = "calibre_language"
    private const val KEY_LANGUAGE = "language"
    private const val KEY_MIGRATED = "appcompat_migrated"

    fun current(context: Context): AppLanguage {
        val locale = AppCompatDelegate.getApplicationLocales()[0]
            ?: context.resources.configuration.locales[0]
        return AppLanguage.fromLanguageTag(locale.language)
    }

    fun setLanguage(language: AppLanguage) {
        AppCompatDelegate.setApplicationLocales(
            LocaleListCompat.forLanguageTags(language.languageTag)
        )
    }

    /** Call after Activity.onCreate, when AppCompat has restored its stored locales. */
    fun migrateLegacyLanguage(context: Context) {
        val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        if (preferences.getBoolean(KEY_MIGRATED, false)) return

        languageToMigrate(
            alreadyMigrated = false,
            hasOfficialLanguage = !AppCompatDelegate.getApplicationLocales().isEmpty,
            legacyTag = preferences.getString(KEY_LANGUAGE, null)
        )?.let(::setLanguage)

        // Never replay a legacy selection after a later reset to the system language.
        check(preferences.edit().remove(KEY_LANGUAGE).putBoolean(KEY_MIGRATED, true).commit()) {
            "The language migration could not be saved."
        }
    }

    internal fun languageToMigrate(
        alreadyMigrated: Boolean,
        hasOfficialLanguage: Boolean,
        legacyTag: String?
    ): AppLanguage? {
        if (alreadyMigrated || hasOfficialLanguage) return null
        // Preserve the previous English default as well as explicit saved selections.
        return AppLanguage.fromLanguageTag(legacyTag)
    }
}
