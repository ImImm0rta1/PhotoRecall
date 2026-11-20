@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.example.photos.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.photos.data.Grouping
import com.example.photos.data.MAX_COLUMNS
import com.example.photos.data.MIN_COLUMNS
import com.example.photos.data.Media
import com.example.photos.data.MediaFilter
import com.example.photos.data.SortField
import com.example.photos.data.ThemeStyle
import com.example.photos.data.Source
import com.example.photos.data.ViewSettings
import com.example.photos.ui.theme.CardColor
import com.example.photos.ui.theme.NothingRed
import com.example.photos.util.formatDuration
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

sealed interface GridEntry {
    data class Header(val label: String) : GridEntry
    data class Cell(val media: Media) : GridEntry
}

/** Used for the Photos tab and for every album / category screen. */
@Composable
fun MediaGridScreen(
    vm: GalleryViewModel,
    source: Source,
    title: String,
    gridId: String,
    actions: MediaActions,
    onBack: (() -> Unit)? = null,
    embedded: Boolean = false,
) {
    val initial = remember(source) { vm.snapshot(source) }
    val items by remember(source) { vm.itemsFlow(source) }.collectAsStateWithLifecycle(initialValue = initial)
    val settings by vm.settings.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val loaded by vm.isLoaded.collectAsStateWithLifecycle()

    val selection = vm.selection
    val inSelection = selection.isNotEmpty()
    val isTrash = source == Source.Trash
    var showOptions by remember { mutableStateOf(false) }

    BackHandler(enabled = inSelection) { vm.clearSelection() }

    val grouped = source.groupable &&
        (settings.sortField == SortField.DATE_TAKEN || settings.sortField == SortField.DATE_MODIFIED)
    val entries = remember(items, grouped, settings.grouping, settings.sortField) {
        buildEntries(items, grouped, settings.grouping, settings.sortField)
    }

    val screenWidthPx = with(LocalDensity.current) { LocalConfiguration.current.screenWidthDp.dp.roundToPx() }
    val thumbPx = ((screenWidthPx / settings.columns) / 64 + 1) * 64
    val bottomPad = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
        (if (onBack == null) 96.dp else 16.dp)

    val selected = remember(items, selection) { items.filter { it.key in selection } }
    val gridState = vm.gridState(gridId)
    val suppressClick = remember { booleanArrayOf(false) }

    Column(if (embedded) Modifier.fillMaxSize() else Modifier.fillMaxSize().statusBarsPadding()) {
        if (inSelection) {
            SelectionBar(
                count = selection.size,
                isTrash = isTrash,
                allFavorite = selected.isNotEmpty() && selected.all { it.key in favorites },
                onClose = { vm.clearSelection() },
                onSelectAll = { vm.selectAll(items.map { it.key }) },
                onShare = { actions.share(selected) },
                onFavorite = { fav ->
                    vm.toggleFavorite(selected.map { it.key }, fav)
                    vm.clearSelection()
                },
                onTrash = { actions.trash(selected) },
                onRestore = { actions.restore(selected) },
                onDeleteForever = { actions.deleteForever(selected) },
                more = { SelectionMenu(vm, source, selected) },
            )
        } else if (!embedded) {
            Row(
                Modifier.fillMaxWidth().padding(start = if (onBack == null) 20.dp else 12.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (onBack != null) {
                    RoundIconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.headlineLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${items.size} items",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                RoundIconButton(onClick = { vm.open(Screen.Search) }) { Icon(Icons.Filled.Search, "Search") }
                RoundIconButton(onClick = { showOptions = true }) { Icon(Icons.Filled.Tune, "View options") }
            }
        }

        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MediaFilter.entries.forEach { f ->
                AppChip(settings.filter == f, f.label) { vm.setFilter(f) }
            }
        }

        if (isTrash && items.isNotEmpty()) {
            Text(
                "Items are deleted automatically after about 30 days.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
            )
        }

        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (!loaded) CircularProgressIndicator(color = NothingRed)
                else Text("Nothing here yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(settings.columns),
                state = gridState,
                modifier = Modifier.fillMaxSize()
                    .pinchToChangeColumns { d -> vm.setColumns(settings.columns + d) }
                    .dragSelect(
                        gridState = gridState,
                        entries = entries,
                        currentSelection = { vm.selection },
                        setSelection = { vm.replaceSelection(it) },
                        suppressClick = suppressClick,
                    ),
                contentPadding = PaddingValues(bottom = bottomPad),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                itemsIndexed(
                    entries,
                    key = { i, e -> if (e is GridEntry.Header) "h$i" else (e as GridEntry.Cell).media.key },
                    span = { _, e -> if (e is GridEntry.Header) GridItemSpan(maxLineSpan) else GridItemSpan(1) },
                    contentType = { _, e -> if (e is GridEntry.Header) 0 else 1 },
                ) { _, e ->
                    when (e) {
                        is GridEntry.Header -> Text(
                            e.label.uppercase(),
                            style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        )
                        is GridEntry.Cell -> {
                            val m = e.media
                            MediaCell(
                                media = m,
                                thumbPx = thumbPx,
                                selected = m.key in selection,
                                selectionMode = inSelection,
                                favorite = m.key in favorites,
                                showExpiry = isTrash,
                                onClick = {
                                    if (!suppressClick[0]) {
                                        if (inSelection) vm.toggleSelect(m.key)
                                        else vm.open(Screen.Viewer(source, m.key))
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showOptions) {
        OptionsSheet(settings, showGrouping = grouped, vm = vm, onDismiss = { showOptions = false })
    }
}

private fun buildEntries(items: List<Media>, grouped: Boolean, grouping: Grouping, sortField: SortField): List<GridEntry> {
    if (!grouped) return items.map { GridEntry.Cell(it) }
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val locale = Locale.getDefault()
    val out = ArrayList<GridEntry>(items.size + 64)
    var lastKey: Any? = null
    for (m in items) {
        val ms = if (sortField == SortField.DATE_MODIFIED) m.dateModified else m.dateTaken
        val date = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
        val key: Any = when (grouping) {
            Grouping.DAY -> date
            Grouping.MONTH -> YearMonth.from(date)
            Grouping.YEAR -> date.year
        }
        if (key != lastKey) {
            out += GridEntry.Header(groupLabel(date, grouping, today, locale))
            lastKey = key
        }
        out += GridEntry.Cell(m)
    }
    return out
}

private fun groupLabel(date: LocalDate, g: Grouping, today: LocalDate, locale: Locale): String = when (g) {
    Grouping.DAY -> when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> date.format(
            DateTimeFormatter.ofPattern(if (date.year == today.year) "EEE, d MMM" else "EEE, d MMM yyyy", locale),
        )
    }
    Grouping.MONTH -> date.format(DateTimeFormatter.ofPattern("MMMM yyyy", locale))
    Grouping.YEAR -> date.year.toString()
}

/** Two-finger pinch on the grid: spread = bigger thumbnails (fewer columns). */
@Composable
private fun Modifier.pinchToChangeColumns(onStep: (Int) -> Unit): Modifier {
    val current by rememberUpdatedState(onStep)
    return pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var zoom = 1f
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.changes.count { it.pressed } >= 2) {
                    zoom *= event.calculateZoom()
                    if (zoom > 1.35f) { current(-1); zoom = 1f }
                    else if (zoom < 0.75f) { current(1); zoom = 1f }
                    event.changes.forEach { it.consume() }
                }
            } while (event.changes.any { it.pressed })
        }
    }
}

@Composable
private fun MediaCell(
    media: Media,
    thumbPx: Int,
    selected: Boolean,
    selectionMode: Boolean,
    favorite: Boolean,
    showExpiry: Boolean,
    onClick: () -> Unit,
) {
    val scale by animateFloatAsState(if (selected) 0.86f else 1f, label = "cellScale")
    Box(
        Modifier
            .aspectRatio(1f)
            .mediaSharedBounds(media.key)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(if (selected) 12.dp else 4.dp))
            .clickable(onClick = onClick),
    ) {
        MediaThumbnail(media, thumbPx, Modifier.fillMaxSize())
        if (selected) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)))
        if (selectionMode) SelectionDot(selected, Modifier.align(Alignment.TopStart).padding(6.dp))
        if (favorite && !selectionMode) {
            Icon(
                Icons.Filled.Favorite, null, tint = Color.White,
                modifier = Modifier.align(Alignment.BottomStart).padding(5.dp).size(14.dp),
            )
        }
        Row(
            Modifier.align(Alignment.BottomEnd).padding(5.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (media.isRaw) Badge("RAW")
            if (media.isGif) Badge("GIF")
            if (media.isVideo) Badge("▶ " + formatDuration(media.durationMs))
            if (showExpiry && media.dateExpires > 0) {
                val days = ((media.dateExpires - System.currentTimeMillis()) / 86_400_000L).coerceAtLeast(0)
                Badge("${days}d")
            }
        }
    }
}

@Composable
private fun Badge(text: String) {
    Text(
        text,
        color = Color.White,
        fontSize = 10.sp,
        modifier = Modifier
            .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(4.dp))
            .padding(horizontal = 4.dp, vertical = 1.dp),
    )
}

@Composable
private fun SelectionDot(selected: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(if (selected) NothingRed else Color.Transparent)
            .border(2.dp, Color.White, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
    }
}

@Composable
private fun SelectionBar(
    count: Int,
    isTrash: Boolean,
    allFavorite: Boolean,
    onClose: () -> Unit,
    onSelectAll: () -> Unit,
    onShare: () -> Unit,
    onFavorite: (Boolean) -> Unit,
    onTrash: () -> Unit,
    onRestore: () -> Unit,
    onDeleteForever: () -> Unit,
    more: @Composable () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)
            .clip(RoundedCornerShape(26.dp)).background(CardColor).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Cancel selection") }
        Text("$count selected", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        IconButton(onClick = onSelectAll) { Icon(Icons.Filled.SelectAll, "Select all") }
        IconButton(onClick = onShare) { Icon(Icons.Filled.Share, "Share") }
        if (isTrash) {
            IconButton(onClick = onRestore) { Icon(Icons.Filled.Restore, "Restore") }
            IconButton(onClick = onDeleteForever) { Icon(Icons.Filled.DeleteForever, "Delete permanently") }
        } else {
            IconButton(onClick = { onFavorite(!allFavorite) }) {
                Icon(if (allFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, "Favourite")
            }
            IconButton(onClick = onTrash) { Icon(Icons.Filled.Delete, "Move to trash") }
            more()
        }
    }
}

private fun orderLabels(f: SortField): Pair<String, String> = when (f) {
    SortField.DATE_TAKEN, SortField.DATE_MODIFIED -> "Newest first" to "Oldest first"
    SortField.NAME -> "Z → A" to "A → Z"
    SortField.SIZE -> "Largest first" to "Smallest first"
}

@Composable
private fun OptionsSheet(settings: ViewSettings, showGrouping: Boolean, vm: GalleryViewModel, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = CardColor) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            OptionGroup("Sort by") {
                SortField.entries.forEach { f -> AppChip(settings.sortField == f, f.label) { vm.setSort(f) } }
            }
            val (descLabel, ascLabel) = orderLabels(settings.sortField)
            OptionGroup("Order") {
                AppChip(settings.descending, descLabel) { vm.setDescending(true) }
                AppChip(!settings.descending, ascLabel) { vm.setDescending(false) }
            }
            if (showGrouping) {
                OptionGroup("Group by") {
                    Grouping.entries.forEach { g -> AppChip(settings.grouping == g, g.label) { vm.setGrouping(g) } }
                }
            }
            OptionGroup("Show") {
                MediaFilter.entries.forEach { f -> AppChip(settings.filter == f, f.label) { vm.setFilter(f) } }
            }
            val themeNow by vm.themeStyle.collectAsStateWithLifecycle()
            OptionGroup("Theme") {
                ThemeStyle.entries.forEach { t -> AppChip(themeNow == t, t.label) { vm.setTheme(t) } }
            }
            Column {
                Text("Columns: ${settings.columns}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Slider(
                    value = settings.columns.toFloat(),
                    onValueChange = { vm.setColumns(it.roundToInt()) },
                    valueRange = MIN_COLUMNS.toFloat()..MAX_COLUMNS.toFloat(),
                    steps = MAX_COLUMNS - MIN_COLUMNS - 1,
                    colors = SliderDefaults.colors(thumbColor = NothingRed, activeTrackColor = NothingRed),
                )
            }
        }
    }
}

@Composable
private fun OptionGroup(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}

/**
 * Long-press a thumbnail, then drag across the grid to select a range (auto-scrolls near the edges).
 * [suppressClick] stops the finger-lift after the long press from toggling the cell again.
 */
@Composable
private fun Modifier.dragSelect(
    gridState: LazyGridState,
    entries: List<GridEntry>,
    currentSelection: () -> Set<String>,
    setSelection: (Set<String>) -> Unit,
    suppressClick: BooleanArray,
): Modifier {
    val scope = rememberCoroutineScope()
    val entriesNow by rememberUpdatedState(entries)
    val selectionNow by rememberUpdatedState(currentSelection)
    val setNow by rememberUpdatedState(setSelection)
    return pointerInput(gridState) {
        var anchor = -1
        var base: Set<String> = emptySet()
        var lastX = 0f
        var lastY = 0f
        var scroller: Job? = null

        fun indexAt(x: Float, y: Float): Int {
            val hit = gridState.layoutInfo.visibleItemsInfo.firstOrNull {
                x >= it.offset.x && x < it.offset.x + it.size.width &&
                    y >= it.offset.y && y < it.offset.y + it.size.height
            }
            return hit?.index ?: -1
        }

        fun applyRange(to: Int) {
            if (anchor < 0 || to < 0 || to >= entriesNow.size) return
            val lo = minOf(anchor, to)
            val hi = maxOf(anchor, to)
            val keys = entriesNow.subList(lo, hi + 1).mapNotNull { (it as? GridEntry.Cell)?.media?.key }
            setNow(base + keys)
        }

        fun finish() {
            scroller?.cancel()
            scroller = null
            anchor = -1
            scope.launch { delay(150); suppressClick[0] = false }
        }

        detectDragGesturesAfterLongPress(
            onDragStart = { offset ->
                suppressClick[0] = true
                lastX = offset.x
                lastY = offset.y
                val i = indexAt(offset.x, offset.y)
                val entry = entriesNow.getOrNull(i)
                if (entry is GridEntry.Cell) {
                    anchor = i
                    base = selectionNow()
                    setNow(base + entry.media.key)
                    scroller = scope.launch {
                        while (isActive) {
                            val h = gridState.layoutInfo.viewportSize.height
                            val zone = 140f
                            val dy = when {
                                lastY < zone -> -(zone - lastY) / 5f
                                lastY > h - zone -> (lastY - (h - zone)) / 5f
                                else -> 0f
                            }
                            if (dy != 0f) {
                                gridState.scrollBy(dy)
                                applyRange(indexAt(lastX, lastY))
                            }
                            delay(16)
                        }
                    }
                } else {
                    anchor = -1
                }
            },
            onDrag = { change, _ ->
                lastX = change.position.x
                lastY = change.position.y
                applyRange(indexAt(lastX, lastY))
            },
            onDragEnd = { finish() },
            onDragCancel = { finish() },
        )
    }
}
