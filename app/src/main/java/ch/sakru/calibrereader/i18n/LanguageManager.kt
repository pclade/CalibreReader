package ch.sakru.calibrereader.i18n

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * Persists and applies the explicitly selected application language.
 *
 * English is used for new installations. The selected language is independent
 * of the Android system language so users can change it inside the app.
 */
object LanguageManager {
    private const val PREFERENCES_NAME = "calibre_language"
    private const val KEY_LANGUAGE = "language"

    fun current(context: Context): AppLanguage {
        val tag = context
            .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LANGUAGE, AppLanguage.ENGLISH.languageTag)

        return AppLanguage.fromLanguageTag(tag)
    }

    fun setLanguage(
        context: Context,
        language: AppLanguage
    ) {
        context
            .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANGUAGE, language.languageTag)
            .apply()
    }

    fun wrap(baseContext: Context): Context {
        val language = current(baseContext)
        val locale = Locale.forLanguageTag(language.languageTag)

        Locale.setDefault(locale)

        val configuration =
            Configuration(baseContext.resources.configuration).apply {
                setLocale(locale)
                setLayoutDirection(locale)
            }

        return baseContext.createConfigurationContext(configuration)
    }
}
