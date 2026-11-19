package com.example.photos.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

private val Context.albumStore by preferencesDataStore(name = "albums")

/** A virtual album: just a name and a list of media keys. Files are never touched. */
data class CustomAlbum(
    val id: String,
    val name: String,
    val itemKeys: List<String>,
    val createdAt: Long,
)

enum class AlbumSort(val label: String) {
    CUSTOM("Custom order"),
    NAME("Name"),
    COUNT("Item count"),
    RECENT("Recently updated"),
}

class AlbumStore(context: Context) {
    private val ds = context.applicationContext.albumStore

    private object K {
        val ALBUMS = stringPreferencesKey("albums_json")
        val COVERS = stringPreferencesKey("covers_json")
        val ORDER = stringPreferencesKey("order_json")
        val SORT = stringPreferencesKey("album_sort")
    }

    val albums: Flow<List<CustomAlbum>> = ds.data.map { parseAlbums(it[K.ALBUMS]) }
    val covers: Flow<Map<String, String>> = ds.data.map { parseMap(it[K.COVERS]) }
    val order: Flow<List<String>> = ds.data.map { parseList(it[K.ORDER]) }
    val sort: Flow<AlbumSort> = ds.data.map { p ->
        p[K.SORT]?.let { runCatching { AlbumSort.valueOf(it) }.getOrNull() } ?: AlbumSort.CUSTOM
    }

    private suspend fun editAlbums(block: (List<CustomAlbum>) -> List<CustomAlbum>) {
        ds.edit { p -> p[K.ALBUMS] = serializeAlbums(block(parseAlbums(p[K.ALBUMS]))) }
    }

    suspend fun create(name: String, keys: Collection<String>): String {
        val id = UUID.randomUUID().toString().take(8)
        editAlbums { it + CustomAlbum(id, name, keys.distinct(), System.currentTimeMillis()) }
        return id
    }

    suspend fun rename(id: String, name: String) =
        editAlbums { list -> list.map { if (it.id == id) it.copy(name = name) else it } }

    suspend fun delete(id: String) {
        editAlbums { list -> list.filterNot { it.id == id } }
        ds.edit { p ->
            val covers = parseMap(p[K.COVERS]).toMutableMap()
            covers.remove("custom:$id")
            p[K.COVERS] = JSONObject(covers as Map<*, *>).toString()
            p[K.ORDER] = JSONArray(parseList(p[K.ORDER]).filterNot { it == "custom:$id" }).toString()
        }
    }

    suspend fun addItems(id: String, keys: Collection<String>) =
        editAlbums { list -> list.map { if (it.id == id) it.copy(itemKeys = (it.itemKeys + keys).distinct()) else it } }

    suspend fun removeItems(id: String, keys: Collection<String>) {
        val drop = keys.toSet()
        editAlbums { list -> list.map { if (it.id == id) it.copy(itemKeys = it.itemKeys.filterNot { k -> k in drop }) else it } }
    }

    /** [mediaKey] == null resets to the automatic cover. Works for every kind of album. */
    suspend fun setCover(albumSourceId: String, mediaKey: String?) {
        ds.edit { p ->
            val covers = parseMap(p[K.COVERS]).toMutableMap()
            if (mediaKey == null) covers.remove(albumSourceId) else covers[albumSourceId] = mediaKey
            p[K.COVERS] = JSONObject(covers as Map<*, *>).toString()
        }
    }

    suspend fun setOrder(ids: List<String>) { ds.edit { it[K.ORDER] = JSONArray(ids).toString() } }
    suspend fun setSort(s: AlbumSort) { ds.edit { it[K.SORT] = s.name } }

    private fun parseAlbums(s: String?): List<CustomAlbum> {
        if (s.isNullOrEmpty()) return emptyList()
        return runCatching {
            val arr = JSONArray(s)
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                val items = o.getJSONArray("items")
                CustomAlbum(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    itemKeys = List(items.length()) { items.getString(it) },
                    createdAt = o.optLong("created"),
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun serializeAlbums(list: List<CustomAlbum>): String {
        val arr = JSONArray()
        list.forEach { a ->
            arr.put(
                JSONObject()
                    .put("id", a.id)
                    .put("name", a.name)
                    .put("created", a.createdAt)
                    .put("items", JSONArray(a.itemKeys)),
            )
        }
        return arr.toString()
    }

    private fun parseMap(s: String?): Map<String, String> {
        if (s.isNullOrEmpty()) return emptyMap()
        return runCatching {
            val o = JSONObject(s)
            o.keys().asSequence().associateWith { o.getString(it) }
        }.getOrDefault(emptyMap())
    }

    private fun parseList(s: String?): List<String> {
        if (s.isNullOrEmpty()) return emptyList()
        return runCatching {
            val a = JSONArray(s)
            List(a.length()) { a.getString(it) }
        }.getOrDefault(emptyList())
    }
}
