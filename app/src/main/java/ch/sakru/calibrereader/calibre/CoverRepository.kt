package ch.sakru.calibrereader.calibre

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import ch.sakru.calibrereader.onedrive.GraphClient
import java.util.concurrent.ConcurrentHashMap

class CoverRepository {

    private val memoryCache =
        ConcurrentHashMap<Long, Bitmap>()

    fun getCachedCover(bookId: Long): Bitmap? {
        return memoryCache[bookId]
    }

    fun loadCover(
        book: Book,
        accessToken: String,
        rootFolderId: String
    ): Bitmap? {

        memoryCache[book.id]?.let {
            return it
        }

        return try {

            val coverPath =
                "${book.path}/cover.jpg"

            val bytes =
                GraphClient()
                    .downloadFileByRelativePath(
                        accessToken = accessToken,
                        rootFolderId = rootFolderId,
                        relativePath = coverPath
                    )

            val bitmap =
                BitmapFactory.decodeByteArray(
                    bytes,
                    0,
                    bytes.size
                )

            if (bitmap != null) {
                memoryCache[book.id] = bitmap
            }

            bitmap

        } catch (e: Exception) {

            android.util.Log.e(
                "CalibreReader",
                "Cover nicht geladen: ${book.title}",
                e
            )

            null
        }
    }
}