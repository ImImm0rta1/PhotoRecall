package com.example.photos.ui

import android.Manifest
import android.app.Application
import android.content.ContentResolver
import android.content.IntentSender
import android.content.pm.PackageManager
import android.provider.MediaStore
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.photos.data.AlbumInfo
import com.example.photos.data.AlbumSort
import com.example.photos.data.AlbumStore
import com.example.photos.data.CustomAlbum
import com.example.photos.data.Dedupe
import com.example.photos.data.DuplicateGroup
import com.example.photos.data.FileOps
import com.example.photos.data.FolderTarget
import com.example.photos.data.Grouping
import com.example.photos.data.IndexEntry
import com.example.photos.data.LatLon
import com.example.photos.data.MAX_COLUMNS
import com.example.photos.data.MIN_COLUMNS
import com.example.photos.data.Media
import com.example.photos.data.MediaFilter
import com.example.photos.data.MediaIndexer
import com.example.photos.data.MediaRepository
import com.example.photos.data.Prefs
import com.example.photos.data.SortField
import com.example.photos.data.Source
import com.example.photos.data.Vault
import com.example.photos.data.ViewSettings
import com.example.photos.data.cellLabel
import com.example.photos.data.cellOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class Tab { PHOTOS, ALBUMS }

sealed interface Screen {
    data class Album(val source: Source, val title: String) : Screen
    data class Viewer(val source: Source, val startKey: String) : Screen
    data object Vault : Screen
    data class VaultViewer(val startId: String) : Screen
    data object Search : Screen
    data object Duplicates : Screen
}

data class SearchState(val query: String = "", val locationCell: String? = null)
data class LocationCluster(val cell: String, val label: String, val count: Int)

private const val RECENT_ADDED_LIMIT = 200

private class Basics(
    val vis: List<Media>,
    val tr: List<Media>,
    val fav: Set<String>,
    val rec: List<String>,
    val s: ViewSettings,
)

private class Extra(
    val custom: List<CustomAlbum>,
    val search: SearchState,
    val locs: Map<String, LatLon>,
    val texts: Map<String, String>,
)

private val EMPTY_EXTRA = Extra(emptyList(), SearchState(), emptyMap(), emptyMap())

private class AlbumSources(val vis: List<Media>, val tr: List<Media>, val fav: Set<String>, val rec: List<String>)
private class AlbumPrefs(val custom: List<CustomAlbum>, val covers: Map<String, String>, val order: List<String>, val sort: AlbumSort)

class GalleryViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = MediaRepository(app)
    private val prefs = Prefs(app)
    private val albumStore = AlbumStore(app)
    private val indexer = MediaIndexer(app)
    private val resolver: ContentResolver = app.contentResolver
    val vault = Vault(app)

    private val allMedia = MutableStateFlow<List<Media>>(emptyList())
    private val trashed = MutableStateFlow<List<Media>>(emptyList())
    private val loaded = MutableStateFlow(false)
    val isLoaded: StateFlow<Boolean> = loaded.asStateFlow()

    val settings: StateFlow<ViewSettings> =
        prefs.settings.stateIn(viewModelScope, SharingStarted.Eagerly, ViewSettings())
    val favorites: StateFlow<Set<String>> =
        prefs.favorites.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())
    val hiddenFolders: StateFlow<Set<String>> =
        prefs.hiddenFolders.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())
    private val recent: StateFlow<List<String>> =
        prefs.recent.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val customAlbums: StateFlow<List<CustomAlbum>> =
        albumStore.albums.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val albumCovers: StateFlow<Map<String, String>> =
        albumStore.covers.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())
    private val albumOrder: StateFlow<List<String>> =
        albumStore.order.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val albumSort: StateFlow<AlbumSort> =
        albumStore.sort.stateIn(viewModelScope, SharingStarted.Eagerly, AlbumSort.CUSTOM)

    /** Everything not in a hidden folder (trash excluded). */
    private val visible: StateFlow<List<Media>> =
        combine(allMedia, hiddenFolders) { list, hidden ->
            if (hidden.isEmpty()) list else list.filter { it.folderKey !in hidden }
        }.flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val mediaCount: StateFlow<Int> =
        visible.map { it.size }.stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    // ---- on-device index (perceptual hash + EXIF location) ----

    private val indexState = MutableStateFlow<Map<String, IndexEntry>>(emptyMap())
    private val _indexProgress = MutableStateFlow<Pair<Int, Int>?>(null)
    val indexProgress: StateFlow<Pair<Int, Int>?> = _indexProgress.asStateFlow()
    val indexedCount: StateFlow<Int> =
        indexState.map { it.size }.stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    private val locationMap: StateFlow<Map<String, LatLon>> =
        indexState.map { m ->
            HashMap<String, LatLon>().also { out ->
                m.forEach { (k, e) ->
                    val la = e.lat
                    val lo = e.lon
                    if (la != null && lo != null) out[k] = LatLon(la, lo)
                }
            }
        }.flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val locationClusters: StateFlow<List<LocationCluster>> =
        combine(visible, locationMap) { vis, locs ->
            val counts = HashMap<String, Int>()
            vis.forEach { m -> locs[m.key]?.let { counts.merge(cellOf(it), 1) { a, b -> a + b } } }
            counts.map { (cell, n) -> LocationCluster(cell, cellLabel(cell), n) }.sortedByDescending { it.count }
        }.flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val searchTexts: StateFlow<Map<String, String>> =
        visible.map { buildSearchTexts(it) }.flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    private val _searchState = MutableStateFlow(SearchState())
    val searchState: StateFlow<SearchState> = _searchState.asStateFlow()

    private val extra: Flow<Extra> =
        combine(customAlbums, searchState, locationMap, searchTexts) { c, st, l, t -> Extra(c, st, l, t) }

    // ---- albums overview ----

    private val albumSources: Flow<AlbumSources> =
        combine(visible, trashed, favorites, recent) { v, t, f, r -> AlbumSources(v, t, f, r) }
    private val albumPrefs: Flow<AlbumPrefs> =
        combine(customAlbums, albumCovers, albumOrder, albumSort) { c, cv, o, s -> AlbumPrefs(c, cv, o, s) }

    val albums: StateFlow<List<AlbumInfo>> =
        combine(albumSources, albumPrefs) { src, p -> buildAlbums(src, p) }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // ---- navigation / selection (UI state) ----

    var tab by mutableStateOf(Tab.PHOTOS)
        private set
    val stack = mutableStateListOf<Screen>()
    var selection by mutableStateOf<Set<String>>(emptySet())
        private set

    private val gridStates = HashMap<String, LazyGridState>()

    /** Keeps scroll position of each grid when navigating away and back. */
    fun gridState(id: String): LazyGridState = gridStates.getOrPut(id) { LazyGridState() }

    fun selectTab(t: Tab) { selection = emptySet(); tab = t }
    fun open(screen: Screen) { selection = emptySet(); stack.add(screen) }
    fun back() {
        if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex)
        selection = emptySet()
    }
    fun toggleSelect(key: String) { selection = if (key in selection) selection - key else selection + key }
    fun selectAll(keys: Collection<String>) { selection = keys.toSet() }
    fun replaceSelection(keys: Set<String>) { selection = keys }
    fun clearSelection() { selection = emptySet() }

    // ---- one-shot events for the UI ----

    val messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val senderRequests = MutableSharedFlow<IntentSender>(extraBufferCapacity = 1)
    fun toast(msg: String) { messages.tryEmit(msg) }

    /** Runs when the next system confirmation dialog returns (ok = user approved). */
    private var pendingResult: ((Boolean) -> Unit)? = null

    // ---- loading ----

    private var observing: Job? = null

    @OptIn(FlowPreview::class)
    fun start() {
        if (observing != null) return
        observing = viewModelScope.launch {
            indexState.value = withContext(Dispatchers.IO) { indexer.load() }
            reload()
            repo.changes().drop(1).debounce(400).collect { reload() }
        }
    }

    fun refresh() {
        if (observing != null) viewModelScope.launch { reload() }
    }

    private suspend fun reload() {
        val (live, tr) = repo.load()
        allMedia.value = live
        trashed.value = tr
        loaded.value = true
        startIndexing()
    }

    private var indexJob: Job? = null

    private fun startIndexing() {
        val locationOk = ContextCompat.checkSelfPermission(
            getApplication<Application>(), Manifest.permission.ACCESS_MEDIA_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val known = indexState.value
        val todo = allMedia.value.filter { m ->
            if (m.isVideo) false else {
                val e = known[m.key]
                e == null || (!e.locDone && locationOk)
            }
        }
        if (todo.isEmpty()) return
        indexJob?.cancel()
        indexJob = viewModelScope.launch(Dispatchers.IO) {
            var done = 0
            _indexProgress.value = 0 to todo.size
            for (chunk in todo.chunked(200)) {
                val results = chunk.chunked(chunk.size / 4 + 1).map { part ->
                    async { part.map { m -> m.key to indexer.compute(m, known[m.key], locationOk) } }
                }.awaitAll().flatten()
                ensureActive()
                val batch = results.toMap()
                indexer.append(batch)
                indexState.update { it + batch }
                done += chunk.size
                _indexProgress.value = done to todo.size
            }
            _indexProgress.value = null
        }
    }

    // ---- lists ----

    fun itemsFlow(source: Source): Flow<List<Media>> {
        val recentFlow: Flow<List<String>> =
            if (source == Source.RecentlyViewed) recent else flowOf(emptyList())
        val basics = combine(visible, trashed, favorites, recentFlow, settings) { vis, tr, fav, rec, s ->
            Basics(vis, tr, fav, rec, s)
        }
        val extraFlow: Flow<Extra> =
            if (source is Source.Custom || source == Source.SearchResults) extra else flowOf(EMPTY_EXTRA)
        return combine(basics, extraFlow) { b, x -> compute(source, b, x) }.flowOn(Dispatchers.Default)
    }

    fun snapshot(source: Source): List<Media> = compute(
        source,
        Basics(visible.value, trashed.value, favorites.value, recent.value, settings.value),
        Extra(customAlbums.value, searchState.value, locationMap.value, searchTexts.value),
    )

    private fun compute(source: Source, b: Basics, x: Extra): List<Media> {
        val s = b.s
        fun List<Media>.view() = filtered(s.filter).sortedWithSettings(s)
        return when (source) {
            Source.All -> b.vis.view()
            Source.RecentlyAdded ->
                b.vis.filtered(s.filter).sortedByDescending { it.dateAdded }.take(RECENT_ADDED_LIMIT)
            Source.RecentlyViewed -> {
                val byKey = b.vis.associateBy { it.key }
                b.rec.mapNotNull { byKey[it] }.filtered(s.filter)
            }
            Source.Favorites -> b.vis.filter { it.key in b.fav }.view()
            Source.Camera -> b.vis.filter { it.isCamera }.view()
            Source.Videos -> b.vis.filter { it.isVideo }.view()
            Source.Screenshots -> b.vis.filter { it.isScreenshot }.view()
            Source.Downloads -> b.vis.filter { it.isDownload }.view()
            Source.Trash -> b.tr.filtered(s.filter).sortedByDescending { it.dateModified }
            is Source.Folder -> b.vis.filter { it.folderKey == source.key }.view()
            is Source.Custom -> {
                val keys: Set<String> =
                    x.custom.firstOrNull { it.id == source.albumId }?.itemKeys?.toHashSet() ?: emptySet()
                b.vis.filter { it.key in keys }.view()
            }
            Source.SearchResults -> searchItems(b.vis, x).view()
        }
    }

    private fun searchItems(vis: List<Media>, x: Extra): List<Media> {
        val st = x.search
        val tokens = st.query.lowercase().split(' ', ',').filter { it.isNotBlank() }
        if (tokens.isEmpty() && st.locationCell == null) return emptyList()
        val albumNames = HashMap<String, String>()
        x.custom.forEach { a ->
            val n = a.name.lowercase()
            a.itemKeys.forEach { k -> albumNames[k] = (albumNames[k] ?: "") + " " + n }
        }
        return vis.filter { m ->
            val cellOk = st.locationCell == null || x.locs[m.key]?.let { cellOf(it) } == st.locationCell
            cellOk && tokens.all { t ->
                (x.texts[m.key]?.contains(t) == true) || (albumNames[m.key]?.contains(t) == true)
            }
        }
    }

    private fun buildSearchTexts(list: List<Media>): Map<String, String> {
        val zone = ZoneId.systemDefault()
        val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd MMMM MMM yyyy EEEE d", Locale.getDefault())
        val out = HashMap<String, String>(list.size * 2)
        for (m in list) {
            val date = fmt.format(Instant.ofEpochMilli(m.dateTaken).atZone(zone))
            val type = buildString {
                append(if (m.isVideo) "video " else "photo ")
                if (m.isGif) append("gif ")
                if (m.isRaw) append("raw ")
                if (m.isScreenshot) append("screenshot ")
            }
            out[m.key] = "${m.name} ${m.folderName} ${m.relativePath} $date $type".lowercase()
        }
        return out
    }

    private fun List<Media>.filtered(f: MediaFilter): List<Media> = when (f) {
        MediaFilter.ALL -> this
        MediaFilter.PHOTOS -> filter { !it.isVideo }
        MediaFilter.VIDEOS -> filter { it.isVideo }
        MediaFilter.SCREENSHOTS -> filter { it.isScreenshot }
    }

    private fun List<Media>.sortedWithSettings(s: ViewSettings): List<Media> {
        val cmp: Comparator<Media> = when (s.sortField) {
            SortField.DATE_TAKEN -> compareBy<Media> { it.dateTaken }
            SortField.DATE_MODIFIED -> compareBy<Media> { it.dateModified }
            SortField.NAME -> compareBy<Media> { it.name.lowercase() }
            SortField.SIZE -> compareBy<Media> { it.size }
        }
        return sortedWith(if (s.descending) cmp.reversed() else cmp)
    }

    /** Smart albums first (fixed order), then custom albums + folders sorted per user choice. */
    private fun buildAlbums(src: AlbumSources, p: AlbumPrefs): List<AlbumInfo> {
        val vis = src.vis
        val byKey = vis.associateBy { it.key }
        val trashByKey = src.tr.associateBy { it.key }

        fun coverFor(source: Source, default: Media?): Media? {
            val k = p.covers[source.id] ?: return default
            return (if (source == Source.Trash) trashByKey[k] else byKey[k]) ?: default
        }

        val pinned = ArrayList<AlbumInfo>()
        fun pin(source: Source, title: String, list: List<Media>, always: Boolean = false) {
            if (list.isEmpty() && !always) return
            pinned += AlbumInfo(
                source, title, list.size, coverFor(source, list.firstOrNull()),
                list.maxOfOrNull { it.dateTaken } ?: 0L,
            )
        }
        pin(Source.RecentlyAdded, "Recently added", vis.sortedByDescending { it.dateAdded }.take(RECENT_ADDED_LIMIT))
        pin(Source.RecentlyViewed, "Recently viewed", src.rec.mapNotNull { byKey[it] })
        pin(Source.Favorites, "Favourites", vis.filter { it.key in src.fav }.sortedByDescending { it.dateTaken }, always = true)
        pin(Source.Camera, "Camera", vis.filter { it.isCamera }.sortedByDescending { it.dateTaken })
        pin(Source.Videos, "Videos", vis.filter { it.isVideo }.sortedByDescending { it.dateTaken })
        pin(Source.Screenshots, "Screenshots", vis.filter { it.isScreenshot }.sortedByDescending { it.dateTaken })
        pin(Source.Downloads, "Downloads", vis.filter { it.isDownload }.sortedByDescending { it.dateTaken })
        pin(Source.Trash, "Trash", src.tr.sortedByDescending { it.dateModified })

        val rest = ArrayList<AlbumInfo>()
        p.custom.forEach { a ->
            val keys = a.itemKeys.toHashSet()
            val items = vis.filter { it.key in keys }.sortedByDescending { it.dateTaken }
            val s = Source.Custom(a.id)
            rest += AlbumInfo(s, a.name, items.size, coverFor(s, items.firstOrNull()), items.firstOrNull()?.dateTaken ?: 0L)
        }
        vis.groupBy { it.folderKey }.values.sortedByDescending { it.size }.forEach { g ->
            val s = Source.Folder(g.first().folderKey)
            val latest = g.maxByOrNull { it.dateTaken }
            rest += AlbumInfo(s, g.first().folderName.ifEmpty { "Other" }, g.size, coverFor(s, latest), latest?.dateTaken ?: 0L)
        }
        val sorted = when (p.sort) {
            AlbumSort.CUSTOM -> {
                val pos = p.order.withIndex().associate { it.value to it.index }
                rest.sortedBy { pos[it.source.id] ?: Int.MAX_VALUE }
            }
            AlbumSort.NAME -> rest.sortedBy { it.title.lowercase() }
            AlbumSort.COUNT -> rest.sortedByDescending { it.count }
            AlbumSort.RECENT -> rest.sortedByDescending { it.latest }
        }
        return pinned + sorted
    }

    // ---- favorites / hide / view options (unchanged behaviour) ----

    fun toggleFavorite(keys: Collection<String>, favorite: Boolean) {
        viewModelScope.launch { prefs.setFavorites(keys, favorite) }
    }
    fun hideFolder(key: String) { viewModelScope.launch { prefs.setFolderHidden(key, true) } }
    fun unhideFolder(key: String) { viewModelScope.launch { prefs.setFolderHidden(key, false) } }
    fun markViewed(key: String) { viewModelScope.launch { prefs.markViewed(key) } }

    fun setColumns(n: Int) {
        val v = n.coerceIn(MIN_COLUMNS, MAX_COLUMNS)
        if (v != settings.value.columns) viewModelScope.launch { prefs.setColumns(v) }
    }
    fun setSort(v: SortField) { viewModelScope.launch { prefs.setSort(v) } }
    fun setDescending(v: Boolean) { viewModelScope.launch { prefs.setDescending(v) } }
    fun setFilter(v: MediaFilter) { viewModelScope.launch { prefs.setFilter(v) } }
    fun setGrouping(v: Grouping) { viewModelScope.launch { prefs.setGrouping(v) } }

    // ---- custom albums (virtual) ----

    fun createAlbum(name: String, keys: Collection<String> = emptyList()) {
        val n = name.trim()
        if (n.isEmpty()) return
        viewModelScope.launch {
            albumStore.create(n, keys)
            if (keys.isNotEmpty()) toast("Added ${keys.size} to “$n”")
        }
    }
    fun renameAlbum(id: String, name: String) {
        val n = name.trim()
        if (n.isNotEmpty()) viewModelScope.launch { albumStore.rename(id, n) }
    }
    fun deleteAlbum(id: String) { viewModelScope.launch { albumStore.delete(id) } }
    fun addToAlbum(id: String, keys: Collection<String>, albumName: String) {
        viewModelScope.launch {
            albumStore.addItems(id, keys)
            toast("Added ${keys.size} to “$albumName”")
        }
    }
    fun removeFromAlbum(id: String, keys: Collection<String>) {
        viewModelScope.launch { albumStore.removeItems(id, keys) }
    }
    fun setCover(albumSourceId: String, mediaKey: String?) {
        viewModelScope.launch { albumStore.setCover(albumSourceId, mediaKey) }
    }
    fun setAlbumOrder(ids: List<String>) {
        viewModelScope.launch {
            albumStore.setOrder(ids)
            albumStore.setSort(AlbumSort.CUSTOM)
        }
    }
    fun setAlbumSort(s: AlbumSort) { viewModelScope.launch { albumStore.setSort(s) } }

    // ---- trash / delete (system dialogs) ----

    /** System-confirmed move to / restore from trash. */
    fun trashIntent(items: List<Media>, trash: Boolean): IntentSender =
        MediaStore.createTrashRequest(resolver, items.map { it.uri }, trash).intentSender

    /** System-confirmed permanent delete. */
    fun deleteIntent(items: List<Media>): IntentSender =
        MediaStore.createDeleteRequest(resolver, items.map { it.uri }).intentSender

    fun onWriteResult(ok: Boolean) {
        val cb = pendingResult
        pendingResult = null
        if (cb != null) cb(ok) else if (ok) selection = emptySet()
        refresh()
    }

    // ---- real file operations (separate from virtual albums) ----

    fun moveTo(items: List<Media>, target: FolderTarget) {
        if (items.isEmpty()) return
        pendingResult = { ok ->
            if (ok) {
                viewModelScope.launch {
                    val (done, failed) = withContext(Dispatchers.IO) { FileOps.moveAll(resolver, items, target) }
                    selection = emptySet()
                    toast(if (failed == 0) "Moved $done item(s)" else "Moved $done, $failed couldn't be moved")
                    reload()
                }
            }
        }
        senderRequests.tryEmit(MediaStore.createWriteRequest(resolver, items.map { it.uri }).intentSender)
    }

    fun copyTo(items: List<Media>, target: FolderTarget) {
        if (items.isEmpty()) return
        viewModelScope.launch {
            toast("Copying ${items.size} item(s)…")
            val (done, failed) = withContext(Dispatchers.IO) { FileOps.copyAll(resolver, items, target) }
            selection = emptySet()
            toast(if (failed == 0) "Copied $done item(s)" else "Copied $done, $failed failed")
            reload()
        }
    }

    // ---- locked folder ----

    private var pendingVaultItems: List<Media>? = null

    fun moveToVault(items: List<Media>) {
        if (items.isEmpty()) return
        if (!vault.isConfigured || !vault.isUnlocked) {
            pendingVaultItems = items
            open(Screen.Vault)
            return
        }
        importToVault(items)
    }

    /** Called by the vault screen once it is unlocked. */
    fun consumePendingVault() {
        val items = pendingVaultItems ?: return
        pendingVaultItems = null
        importToVault(items)
    }

    private fun importToVault(items: List<Media>) {
        viewModelScope.launch {
            val done = vault.importAll(items)
            if (done.isEmpty()) {
                toast("Couldn't encrypt the selected items")
                return@launch
            }
            val ids = done.map { it.second }
            val moved = done.map { it.first }
            pendingResult = { ok ->
                if (ok) {
                    selection = emptySet()
                    toast("Moved ${moved.size} item(s) to the locked folder")
                } else {
                    // User declined the delete prompt: undo so nothing exists twice.
                    viewModelScope.launch { vault.removeAll(ids) }
                }
            }
            senderRequests.tryEmit(MediaStore.createDeleteRequest(resolver, moved.map { it.uri }).intentSender)
        }
    }

    // ---- duplicates ----

    suspend fun findDuplicates(threshold: Int): List<DuplicateGroup> =
        withContext(Dispatchers.Default) { Dedupe.find(visible.value, indexState.value, threshold) }

    // ---- search ----

    fun setSearchQuery(q: String) { _searchState.update { it.copy(query = q) } }
    fun setSearchCell(cell: String?) { _searchState.update { it.copy(locationCell = cell) } }
    fun resetSearch() { _searchState.value = SearchState() }
}
