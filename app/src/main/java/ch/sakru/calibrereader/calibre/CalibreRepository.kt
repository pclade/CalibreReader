package ch.sakru.calibrereader.calibre

import android.database.sqlite.SQLiteDatabase
import ch.sakru.calibrereader.model.Book
import ch.sakru.calibrereader.model.BookFile
import java.io.File

/**
 * Reads Calibre library metadata from a local metadata.db file.
 *
 * This repository understands the Calibre database schema but has no
 * knowledge of OneDrive, Google Drive, or any other cloud provider.
 */
class CalibreRepository {

    /**
     * Loads all books and their available files from a Calibre metadata.db file.
     *
     * @param databaseFile local Calibre metadata.db file.
     * @return books contained in the Calibre library.
     */
    fun loadBooks(
        databaseFile: File
    ): List<Book> {

        val db =
            SQLiteDatabase.openDatabase(
                databaseFile.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY
            )

        try {

            val filesByBook =
                loadBookFiles(
                    db
                )

            return loadBooks(
                db = db,
                filesByBook = filesByBook
            )

        } finally {

            db.close()
        }
    }

    /**
     * Loads all physical book files from the Calibre data table.
     */
    private fun loadBookFiles(
        db: SQLiteDatabase
    ): Map<Long, List<BookFile>> {

        val filesByBook =
            mutableMapOf<Long, MutableList<BookFile>>()

        val cursor =
            db.rawQuery(
                """
                SELECT
                    book,
                    name,
                    format
                FROM data
                ORDER BY book
                """.trimIndent(),
                null
            )

        try {

            while (cursor.moveToNext()) {

                val bookId =
                    cursor.getLong(
                        cursor.getColumnIndexOrThrow(
                            "book"
                        )
                    )

                val fileName =
                    cursor.getString(
                        cursor.getColumnIndexOrThrow(
                            "name"
                        )
                    )

                val format =
                    cursor.getString(
                        cursor.getColumnIndexOrThrow(
                            "format"
                        )
                    )

                filesByBook
                    .getOrPut(bookId) {
                        mutableListOf()
                    }
                    .add(
                        BookFile(
                            name = fileName,
                            format = format
                        )
                    )
            }

        } finally {

            cursor.close()
        }

        return filesByBook
    }

    /**
     * Loads books and authors from the Calibre database.
     */
    private fun loadBooks(
        db: SQLiteDatabase,
        filesByBook: Map<Long, List<BookFile>>
    ): List<Book> {

        val cursor =
            db.rawQuery(
                """
                SELECT
                    b.id,
                    b.title,
                    b.path,
                    GROUP_CONCAT(DISTINCT a.name) AS authors
                FROM books b
                LEFT JOIN books_authors_link bal
                    ON bal.book = b.id
                LEFT JOIN authors a
                    ON a.id = bal.author
                GROUP BY
                    b.id,
                    b.title,
                    b.path
                ORDER BY
                    b.title COLLATE NOCASE
                """.trimIndent(),
                null
            )

        val books =
            mutableListOf<Book>()

        try {

            while (cursor.moveToNext()) {

                val id =
                    cursor.getLong(
                        cursor.getColumnIndexOrThrow(
                            "id"
                        )
                    )

                val title =
                    cursor.getString(
                        cursor.getColumnIndexOrThrow(
                            "title"
                        )
                    )

                val path =
                    cursor.getString(
                        cursor.getColumnIndexOrThrow(
                            "path"
                        )
                    )

                val authors =
                    cursor.getString(
                        cursor.getColumnIndexOrThrow(
                            "authors"
                        )
                    ) ?: "Unbekannter Autor"

                val bookFiles =
                    filesByBook[id]
                        ?: emptyList()

                val formats =
                    bookFiles
                        .map {
                            it.format
                        }
                        .distinct()

                books +=
                    Book(
                        id = id,
                        title = title,
                        authors = authors,
                        path = path,
                        formats = formats,
                        files = bookFiles
                    )
            }

        } finally {

            cursor.close()
        }

        return books
    }
}