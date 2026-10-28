package ch.sakru.calibrereader.model

/**
 * Defines the available sort orders for the Calibre library.
 */
enum class LibrarySortMode(
    val displayName: String
) {
    TITLE_ASC("Title A–Z"),
    TITLE_DESC("Title Z–A"),
    AUTHOR_ASC("Author A–Z"),
    AUTHOR_DESC("Author Z–A")
}