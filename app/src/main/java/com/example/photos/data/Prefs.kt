package com.example.photos.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.galleryStore by preferencesDataStore(name = "gallery_prefs")

/** Local-only settings: favorites, hidden folders, recents, view options. */
class Prefs(context: Context) {
    private val ds = context.applicationContext.galleryStore

    private object K {
        val FAVORITES = stringSetPreferencesKey("favorites")
        val HIDDEN = stringSetPreferencesKey("hidden_folders")
        val RECENT = stringPreferencesKey("recently_viewed")
        val COLUMNS = intPreferencesKey("columns")
        val SORT = stringPreferencesKey("sort_field")
        val DESC = booleanPreferencesKey("descending")
        val FILTER = stringPreferencesKey("filter")
        val GROUPING = stringPreferencesKey("grouping")
        val THEME = stringPreferencesKey("theme")
        val HIDDEN_ITEMS = stringSetPreferencesKey("hidden_items")
        val STRIP_SHARE = booleanPreferencesKey("strip_on_share")
    }

    val favorites: Flow<Set<String>> = ds.data.map { it[K.FAVORITES] ?: emptySet() }
    val hiddenFolders: Flow<Set<String>> = ds.data.map { it[K.HIDDEN] ?: emptySet() }
    val recent: Flow<List<String>> = ds.data.map { p ->
        p[K.RECENT]?.split(',')?.filter { it.isNotEmpty() } ?: emptyList()
    }

    val settings: Flow<ViewSettings> = ds.data.map { p ->
        ViewSettings(
            columns = (p[K.COLUMNS] ?: 4).coerceIn(MIN_COLUMNS, MAX_COLUMNS),
            sortField = p[K.SORT]?.let { runCatching { SortField.valueOf(it) }.getOrNull() } ?: SortField.DATE_TAKEN,
            descending = p[K.DESC] ?: true,
            filter = p[K.FILTER]?.let { runCatching { MediaFilter.valueOf(it) }.getOrNull() } ?: MediaFilter.ALL,
            grouping = p[K.GROUPING]?.let { runCatching { Grouping.valueOf(it) }.getOrNull() } ?: Grouping.DAY,
        )
    }

    val hiddenItems: Flow<Set<String>> = ds.data.map { it[K.HIDDEN_ITEMS] ?: emptySet() }
    val stripOnShare: Flow<Boolean> = ds.data.map { it[K.STRIP_SHARE] ?: false }

    suspend fun setItemsHidden(keys: Collection<String>, hidden: Boolean) {
        ds.edit { p ->
            val cur = p[K.HIDDEN_ITEMS] ?: emptySet()
            p[K.HIDDEN_ITEMS] = if (hidden) cur + keys else cur - keys.toSet()
        }
    }
    suspend fun setStripOnShare(v: Boolean) { ds.edit { it[K.STRIP_SHARE] = v } }

    val theme: Flow<ThemeStyle> = ds.data.map { p ->
        p[K.THEME]?.let { runCatching { ThemeStyle.valueOf(it) }.getOrNull() } ?: ThemeStyle.NOTHING
    }

    suspend fun setTheme(v: ThemeStyle) { ds.edit { it[K.THEME] = v.name } }
    suspend fun setColumns(v: Int) { ds.edit { it[K.COLUMNS] = v } }
    suspend fun setSort(v: SortField) { ds.edit { it[K.SORT] = v.name } }
    suspend fun setDescending(v: Boolean) { ds.edit { it[K.DESC] = v } }
    suspend fun setFilter(v: MediaFilter) { ds.edit { it[K.FILTER] = v.name } }
    suspend fun setGrouping(v: Grouping) { ds.edit { it[K.GROUPING] = v.name } }

    suspend fun setFavorites(keys: Collection<String>, favorite: Boolean) {
        ds.edit { p ->
            val cur = p[K.FAVORITES] ?: emptySet()
            p[K.FAVORITES] = if (favorite) cur + keys else cur - keys.toSet()
        }
    }

    suspend fun setFolderHidden(folderKey: String, hidden: Boolean) {
        ds.edit { p ->
            val cur = p[K.HIDDEN] ?: emptySet()
            p[K.HIDDEN] = if (hidden) cur + folderKey else cur - folderKey
        }
    }

    suspend fun markViewed(key: String) {
        ds.edit { p ->
            val cur = p[K.RECENT]?.split(',')?.filter { it.isNotEmpty() } ?: emptyList()
            val next = (listOf(key) + cur.filterNot { it == key }).take(60)
            p[K.RECENT] = next.joinToString(",")
        }
    }
}
