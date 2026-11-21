package com.example.photos.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.photos.data.Media
import com.example.photos.data.Source

private enum class MenuDialog { ADD, MOVE, COPY, VAULT }

/**
 * Organise actions for the selected items. Used by the grid's selection bar and the viewer.
 * Albums are virtual (add/remove only edits the album); move/copy really change files.
 */
@Composable
fun SelectionMenu(vm: GalleryViewModel, source: Source, selected: List<Media>) {
    var open by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<MenuDialog?>(null) }
    val custom by vm.customAlbums.collectAsStateWithLifecycle()
    val albums by vm.albums.collectAsStateWithLifecycle()
    val keys = selected.map { it.key }

    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Filled.MoreVert, "More") }
        ThemedMenu(expanded = open, onDismiss = { open = false }) {
            MenuRow("Add to album", Icons.Outlined.CreateNewFolder) { open = false; dialog = MenuDialog.ADD }
            if (source is Source.Custom) {
                MenuRow("Remove from this album", Icons.Outlined.RemoveCircleOutline) {
                    open = false
                    vm.removeFromAlbum(source.albumId, keys)
                    vm.clearSelection()
                }
            }
            MenuRow("Move to folder…", Icons.Outlined.FolderOpen) { open = false; dialog = MenuDialog.MOVE }
            MenuRow("Copy to folder…", Icons.Outlined.ContentCopy) { open = false; dialog = MenuDialog.COPY }
            MenuRow("Move to locked folder", Icons.Outlined.Lock) { open = false; dialog = MenuDialog.VAULT }
            if (source == Source.HiddenItems) {
                MenuRow("Unhide", Icons.Outlined.Visibility) { open = false; vm.unhideItems(keys) }
            } else {
                MenuRow("Hide", Icons.Outlined.VisibilityOff) { open = false; vm.hideItems(keys) }
            }
            MenuRow("Share without location", Icons.Outlined.Shield) { open = false; vm.share(selected, forceStrip = true) }
        }
    }

    when (dialog) {
        MenuDialog.ADD -> AlbumPickerDialog(
            albums = custom,
            onDismiss = { dialog = null },
            onPick = { a -> vm.addToAlbum(a.id, keys, a.name); vm.clearSelection(); dialog = null },
            onCreate = { name -> vm.createAlbum(name, keys); vm.clearSelection(); dialog = null },
        )
        MenuDialog.MOVE -> FolderPickerDialog(
            title = "Move to folder",
            folders = albums,
            onDismiss = { dialog = null },
            onPick = { t -> dialog = null; vm.moveTo(selected, t) },
        )
        MenuDialog.COPY -> FolderPickerDialog(
            title = "Copy to folder",
            folders = albums,
            onDismiss = { dialog = null },
            onPick = { t -> dialog = null; vm.copyTo(selected, t) },
        )
        MenuDialog.VAULT -> ConfirmDialog(
            title = "Move to locked folder?",
            text = "The ${selected.size} selected item(s) will be encrypted inside this app and the originals deleted from your gallery. You'll need your locked-folder password to see them.",
            confirmLabel = "Move",
            onDismiss = { dialog = null },
            onConfirm = { dialog = null; vm.moveToVault(selected) },
        )
        null -> {}
    }
}
