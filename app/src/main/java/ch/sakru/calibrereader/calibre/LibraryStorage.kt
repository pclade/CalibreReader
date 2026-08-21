package ch.sakru.calibrereader.calibre

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import ch.sakru.calibrereader.model.LibraryViewMode
import ch.sakru.calibrereader.model.SavedLibrary
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import ch.sakru.calibrereader.model.StorageProvider
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

            val storageRootId =
                if (item.has("storageRootId")) {
                    item.getString("storageRootId")
                } else {
                    item.getString("folderId")
                }

            val provider =
                if (item.has("provider")) {
                    StorageProvider.valueOf(
                        item.getString("provider")
                    )
                } else {
                    StorageProvider.ONEDRIVE
                }

            result += SavedLibrary(
                id = item.getString("id"),
                name = item.getString("name"),
                storageRootId = storageRootId,
                account = item.getString("account"),
                provider = provider
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
            item.put("storageRootId", library.storageRootId)
            item.put("account", library.account)
            item.put("provider", library.provider.name)
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