package ch.sakru.calibrereader.onedrive

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class GraphClient {

    fun getRootItems(accessToken: String): List<DriveItem> {
        return getItems(
            accessToken,
            "https://graph.microsoft.com/v1.0/me/drive/root/children" +
                    "?%24select=id,name,folder,file"
        )
    }

    fun getChildren(
        accessToken: String,
        itemId: String
    ): List<DriveItem> {

        val url =
            "https://graph.microsoft.com/v1.0/me/drive/items/$itemId/children" +
                    "?%24select=id,name,folder,file"

        return getItems(accessToken, url)
    }

    private fun getItems(
        accessToken: String,
        urlString: String
    ): List<DriveItem> {

        val result = mutableListOf<DriveItem>()
        var nextUrl: String? = urlString

        while (nextUrl != null) {

            val connection =
                URL(nextUrl).openConnection() as HttpURLConnection

            try {
                connection.requestMethod = "GET"
                connection.connectTimeout = 15_000
                connection.readTimeout = 30_000

                connection.setRequestProperty(
                    "Authorization",
                    "Bearer $accessToken"
                )

                connection.setRequestProperty(
                    "Accept",
                    "application/json"
                )

                val responseCode = connection.responseCode

                if (responseCode !in 200..299) {

                    val errorBody =
                        connection.errorStream
                            ?.bufferedReader()
                            ?.use { it.readText() }
                            .orEmpty()

                    throw IllegalStateException(
                        "Microsoft Graph: HTTP $responseCode\n$errorBody"
                    )
                }

                val response =
                    connection.inputStream
                        .bufferedReader()
                        .use { it.readText() }

                val json = JSONObject(response)

                val values =
                    json.getJSONArray("value")

                for (i in 0 until values.length()) {

                    val item =
                        values.getJSONObject(i)

                    result += DriveItem(
                        id = item.getString("id"),
                        name = item.getString("name"),
                        isFolder = item.has("folder")
                    )
                }

                nextUrl =
                    if (json.has("@odata.nextLink")) {
                        json.getString("@odata.nextLink")
                    } else {
                        null
                    }

            } finally {
                connection.disconnect()
            }
        }

        return result.sortedWith(
            compareByDescending<DriveItem> {
                it.isFolder
            }.thenBy(
                String.CASE_INSENSITIVE_ORDER
            ) {
                it.name
            }
        )
    }
    fun downloadFile(
        accessToken: String,
        itemId: String
    ): ByteArray {

        val url =
            "https://graph.microsoft.com/v1.0/me/drive/items/$itemId/content"

        val connection =
            URL(url).openConnection() as HttpURLConnection

        try {
            connection.requestMethod = "GET"

            connection.setRequestProperty(
                "Authorization",
                "Bearer $accessToken"
            )

            connection.connectTimeout = 15_000
            connection.readTimeout = 60_000

            val responseCode = connection.responseCode

            if (responseCode !in 200..299) {
                throw IllegalStateException(
                    "Download fehlgeschlagen: HTTP $responseCode"
                )
            }

            return connection.inputStream.use {
                it.readBytes()
            }

        } finally {
            connection.disconnect()
        }
    }
    fun downloadFileByRelativePath(
        accessToken: String,
        rootFolderId: String,
        relativePath: String
    ): ByteArray {

        val encodedPath =
            relativePath
                .split("/")
                .joinToString("/") {
                    java.net.URLEncoder.encode(
                        it,
                        Charsets.UTF_8.name()
                    ).replace("+", "%20")
                }

        val url =
            "https://graph.microsoft.com/v1.0/me/drive/items/" +
                    "$rootFolderId:/$encodedPath:/content"

        val connection =
            URL(url).openConnection() as HttpURLConnection

        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000

            connection.setRequestProperty(
                "Authorization",
                "Bearer $accessToken"
            )

            val responseCode = connection.responseCode

            if (responseCode !in 200..299) {
                throw IllegalStateException(
                    "Download fehlgeschlagen: HTTP $responseCode"
                )
            }

            return connection.inputStream.use {
                it.readBytes()
            }

        } finally {
            connection.disconnect()
        }
    }
}