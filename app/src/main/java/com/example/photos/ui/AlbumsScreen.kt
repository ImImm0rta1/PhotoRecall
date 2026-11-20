@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)

package com.example.photos.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.photos.data.AlbumInfo
import com.example.photos.data.AlbumSort
import com.example.photos.data.Source
import com.example.photos.data.supportsCover
import com.example.photos.ui.theme.CardColor
import com.example.photos.ui.theme.CardColorHigh

@Composable
fun AlbumsScreen(vm: GalleryViewModel) {
    val albums by vm.albums.collectAsStateWithLifecycle()
    val hidden by vm.hiddenFolders.collectAsStateWithLifecycle()
    val sort by vm.albumSort.collectAsStateWithLifecycle()
    val covers by vm.albumCovers.collectAsStateWithLifecycle()

    var menuOpen by remember { mutableStateOf(false) }
    var showHidden by remember { mutableStateOf(false) }
    var hideTarget by remember { mutableStateOf<AlbumInfo?>(null) }
    var creating by remember { mutableStateOf(false) }
    var menuFor by remember { mutableStateOf<String?>(null) }
    var renameTarget by remember { mutableStateOf<AlbumInfo?>(null) }
    var deleteTarget by remember { mutableStateOf<AlbumInfo?>(null) }
    var coverTarget by remember { mutableStateOf<AlbumInfo?>(null) }
    var showTheme by remember { mutableStateOf(false) }
    val themeNow by vm.themeStyle.collectAsStateWithLifecycle()

    // Drag-and-drop reordering (applies to custom albums + folders; smart albums stay pinned).
    var reorder by remember { mutableStateOf(false) }
    val working = remember { mutableStateListOf<AlbumInfo>() }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    val gridState = rememberLazyGridState()

    val pinned = albums.filter { it.source !is Source.Custom && it.source !is Source.Folder }
    val rest = albums.filter { it.source is Source.Custom || it.source is Source.Folder }
    val shownRest: List<AlbumInfo> = if (reorder) working else rest
    val bottomPad = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 96.dp

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Albums",
                    style = MaterialTheme.typography.headlineLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${albums.size} albums",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            RoundIconButton(onClick = { vm.open(Screen.Search) }) { Icon(Icons.Filled.Search, "Search") }
            RoundIconButton(onClick = { creating = true }) { Icon(Icons.Filled.Add, "New album") }
            Box {
                RoundIconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, "More") }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    AlbumSort.entries.forEach { s ->
                        DropdownMenuItem(
                            text = { Text((if (sort == s) "✓  " else "     ") + "Sort: " + s.label) },
                            onClick = { menuOpen = false; vm.setAlbumSort(s) },
                        )
                    }
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Reorder albums") },
                        onClick = {
                            menuOpen = false
                            working.clear()
                            working.addAll(rest)
                            reorder = true
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Find duplicates") },
                        onClick = { menuOpen = false; vm.open(Screen.Duplicates) },
                    )
                    DropdownMenuItem(
                        text = { Text("Appearance…") },
                        onClick = { menuOpen = false; showTheme = true },
                    )
                }
            }
        }

        if (reorder) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Drag albums to reorder",
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = { reorder = false; draggingId = null }) { Text("Done") }
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottomPad),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(pinned, key = { it.source.id }) { info ->
                AlbumCardWithMenu(
                    info = info,
                    reorder = reorder,
                    menuOpen = menuFor == info.source.id,
                    hasCover = covers.containsKey(info.source.id),
                    onMenu = { menuFor = it },
                    onOpen = { vm.open(Screen.Album(info.source, info.title)) },
                    onRename = { renameTarget = info },
                    onDelete = { deleteTarget = info },
                    onCover = { coverTarget = info },
                    onHide = { hideTarget = info },
                )
            }
            items(shownRest, key = { it.source.id }) { info ->
                val id = info.source.id
                val dragging = draggingId == id
                Box(
                    Modifier
                        .zIndex(if (dragging) 1f else 0f)
                        .graphicsLayer {
                            if (dragging) {
                                translationX = dragOffset.x
                                translationY = dragOffset.y
                                scaleX = 1.05f
                                scaleY = 1.05f
                                alpha = 0.9f
                            }
                        }
                        .then(
                            if (reorder) Modifier.pointerInput(id) {
                                detectDragGestures(
                                    onDragStart = { draggingId = id; dragOffset = Offset.Zero },
                                    onDragEnd = {
                                        draggingId = null
                                        dragOffset = Offset.Zero
                                        vm.setAlbumOrder(working.map { it.source.id })
                                    },
                                    onDragCancel = { draggingId = null; dragOffset = Offset.Zero },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        dragOffset += amount
                                        val visible = gridState.layoutInfo.visibleItemsInfo
                                        val me = visible.firstOrNull { it.key == id } ?: return@detectDragGestures
                                        val cx = me.offset.x + me.size.width / 2f + dragOffset.x
                                        val cy = me.offset.y + me.size.height / 2f + dragOffset.y
                                        val target = visible.firstOrNull { v ->
                                            v.key != id &&
                                                working.any { it.source.id == v.key } &&
                                                cx >= v.offset.x && cx < v.offset.x + v.size.width &&
                                                cy >= v.offset.y && cy < v.offset.y + v.size.height
                                        }
                                        if (target != null) {
                                            val from = working.indexOfFirst { it.source.id == id }
                                            val to = working.indexOfFirst { it.source.id == target.key }
                                            if (from >= 0 && to >= 0 && from != to) {
                                                working.add(to, working.removeAt(from))
                                                dragOffset -= Offset(
                                                    (target.offset.x - me.offset.x).toFloat(),
                                                    (target.offset.y - me.offset.y).toFloat(),
                                                )
                                            }
                                        }
                                    },
                                )
                            } else Modifier,
                        ),
                ) {
                    AlbumCardWithMenu(
                        info = info,
                        reorder = reorder,
                        menuOpen = menuFor == id,
                        hasCover = covers.containsKey(id),
                        onMenu = { menuFor = it },
                        onOpen = { vm.open(Screen.Album(info.source, info.title)) },
                        onRename = { renameTarget = info },
                        onDelete = { deleteTarget = info },
                        onCover = { coverTarget = info },
                        onHide = { hideTarget = info },
                    )
                }
            }
        }
    }

    if (creating) {
        NameDialog("New album", confirmLabel = "Create", onDismiss = { creating = false }) {
            vm.createAlbum(it)
            creating = false
        }
    }
    renameTarget?.let { t ->
        NameDialog("Rename album", initial = t.title, onDismiss = { renameTarget = null }) {
            (t.source as? Source.Custom)?.let { c -> vm.renameAlbum(c.albumId, it) }
            renameTarget = null
        }
    }
    deleteTarget?.let { t ->
        ConfirmDialog(
            title = "Delete album?",
            text = "“${t.title}” will be removed. Your photos and videos are not deleted.",
            confirmLabel = "Delete",
            onDismiss = { deleteTarget = null },
            onConfirm = {
                (t.source as? Source.Custom)?.let { c -> vm.deleteAlbum(c.albumId) }
                deleteTarget = null
            },
        )
    }
    coverTarget?.let { t ->
        val items = remember(t.source) { vm.snapshot(t.source) }
        CoverPickerDialog(
            items = items,
            canReset = covers.containsKey(t.source.id),
            onDismiss = { coverTarget = null },
            onPick = { m -> vm.setCover(t.source.id, m?.key); coverTarget = null },
        )
    }

    if (showTheme) {
        ThemePickerDialog(current = themeNow, onPick = { vm.setTheme(it) }, onDismiss = { showTheme = false })
    }

    hideTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { hideTarget = null },
            title = { Text("Hide folder?") },
            text = {
                Text("“${target.title}” (${target.count} items) won't appear in Photos or albums. Find it again by typing :h in search.")
            },
            confirmButton = {
                TextButton(onClick = {
                    (target.source as? Source.Folder)?.let { vm.hideFolder(it.key) }
                    hideTarget = null
                }) { Text("Hide") }
            },
            dismissButton = { TextButton(onClick = { hideTarget = null }) { Text("Cancel") } },
        )
    }

    if (showHidden) {
        AlertDialog(
            onDismissRequest = { showHidden = false },
            title = { Text("Hidden folders") },
            text = {
                if (hidden.isEmpty()) {
                    Text("No hidden folders. Long-press a folder album to hide it.")
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        hidden.sorted().forEach { key ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(key, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                                TextButton(onClick = { vm.unhideFolder(key) }) { Text("Unhide") }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showHidden = false }) { Text("Done") } },
        )
    }
}

@Composable
private fun AlbumCardWithMenu(
    info: AlbumInfo,
    reorder: Boolean,
    menuOpen: Boolean,
    hasCover: Boolean,
    onMenu: (String?) -> Unit,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onCover: () -> Unit,
    onHide: () -> Unit,
) {
    val src = info.source
    val hasMenu = src is Source.Custom || src is Source.Folder || src.supportsCover
    Box {
        AlbumCard(
            info = info,
            onClick = { if (!reorder) onOpen() },
            onLongClick = { if (!reorder && hasMenu) onMenu(src.id) },
        )
        DropdownMenu(expanded = menuOpen, onDismissRequest = { onMenu(null) }) {
            if (src is Source.Custom) {
                DropdownMenuItem(text = { Text("Rename") }, onClick = { onMenu(null); onRename() })
                DropdownMenuItem(text = { Text("Delete album") }, onClick = { onMenu(null); onDelete() })
            }
            if (src.supportsCover) {
                DropdownMenuItem(
                    text = { Text(if (hasCover) "Change cover" else "Set cover") },
                    onClick = { onMenu(null); onCover() },
                )
            }
            if (src is Source.Folder) {
                DropdownMenuItem(text = { Text("Hide folder") }, onClick = { onMenu(null); onHide() })
            }
        }
    }
}

@Composable
private fun AlbumCard(info: AlbumInfo, onClick: () -> Unit, onLongClick: () -> Unit) {
    Column(
        Modifier
            .clip(RoundedCornerShape(26.dp))
            .background(CardColor)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(CardColorHigh),
            contentAlignment = Alignment.Center,
        ) {
            val cover = info.cover
            if (cover != null) {
                MediaThumbnail(cover, 400, Modifier.fillMaxSize())
            } else {
                Icon(Icons.Outlined.PhotoLibrary, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(
            info.title,
            modifier = Modifier.padding(top = 10.dp),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            "${info.count}",
            modifier = Modifier.padding(bottom = 6.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun VaultCard(onClick: () -> Unit) {
    Column(
        Modifier
            .clip(RoundedCornerShape(22.dp))
            .background(CardColor)
            .combinedClickable(onClick = onClick)
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(CardColorHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Lock, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("Locked folder", modifier = Modifier.padding(top = 10.dp), style = MaterialTheme.typography.bodyMedium)
        Text(
            "Password protected",
            modifier = Modifier.padding(bottom = 6.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
