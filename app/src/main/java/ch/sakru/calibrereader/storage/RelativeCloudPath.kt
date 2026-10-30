package ch.sakru.calibrereader.storage

/** Keeps metadata-derived file paths inside the selected library. */
fun requireRelativeCloudPath(path: String): String {
    require(path.isNotBlank() && !path.startsWith("/") && !path.contains('\\') &&
        !path.contains('\u0000') &&
        path.split('/').none { it.isEmpty() || it == "." || it == ".." }) {
        "Ungültiger Dateipfad innerhalb der Calibre-Bibliothek."
    }
    return path
}
