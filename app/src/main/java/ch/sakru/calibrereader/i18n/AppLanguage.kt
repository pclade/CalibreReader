package ch.sakru.calibrereader.i18n

/**
 * Languages supported by the Calibre To Go user interface.
 */
enum class AppLanguage(
    val languageTag: String,
    val displayName: String
) {
    ENGLISH("en", "English"),
    GERMAN("de", "Deutsch"),
    FRENCH("fr", "Français"),
    TURKISH("tr", "Türkçe");

    companion object {
        fun fromLanguageTag(tag: String?): AppLanguage =
            entries.firstOrNull { it.languageTag.equals(tag, ignoreCase = true) }
                ?: ENGLISH
    }
}
