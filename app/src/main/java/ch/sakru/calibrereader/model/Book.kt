package ch.sakru.calibrereader.model

data class BookFile(
    val name: String,
    val format: String
)

data class Book(
    val id: Long,
    val title: String,
    val authors: String,
    val path: String,
    val formats: List<String>,
    val files: List<BookFile>
)