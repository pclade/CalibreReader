package ch.sakru.calibrereader.calibre

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

private val Context.dataStore by preferencesDataStore(
    name = "calibre_settings"
)

class LibraryStorage(
    private val context: Context
) {

    private val librariesKey =
        stringPreferencesKey("libraries")

    private val viewModeKey =
        stringPreferencesKey("library_view_mode")

    suspend fun loadLibraries(): List<SavedLibrary> {

        val preferences =
            context.dataStore.data.first()

        val json =
            preferences[librariesKey]
                ?: return emptyList()

        val array =
            JSONArray(json)

        val result =
            mutableListOf<SavedLibrary>()

        for (i in 0 until array.length()) {

            val item =
                array.getJSONObject(i)

            result += SavedLibrary(
                id = item.getString("id"),
                name = item.getString("name"),
                folderId = item.getString("folderId"),
                account = item.getString("account")
            )
        }

        return result
    }

    suspend fun saveLibraries(
        libraries: List<SavedLibrary>
    ) {

        val array =
            JSONArray()

        libraries.forEach { library ->

            val item =
                JSONObject()

            item.put("id", library.id)
            item.put("name", library.name)
            item.put("folderId", library.folderId)
            item.put("account", library.account)

            array.put(item)
        }

        context.dataStore.edit {
            it[librariesKey] = array.toString()
        }
    }
    suspend fun loadViewMode(): LibraryViewMode {

        val preferences =
            context.dataStore.data.first()

        val value =
            preferences[viewModeKey]
                ?: LibraryViewMode.LIST.name

        return try {
            LibraryViewMode.valueOf(value)
        } catch (e: Exception) {
            LibraryViewMode.LIST
        }
    }
    suspend fun saveViewMode(
        mode: LibraryViewMode
    ) {

        context.dataStore.edit {
            it[viewModeKey] = mode.name
        }
    }
}