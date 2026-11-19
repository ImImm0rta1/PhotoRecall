package com.example.photos.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
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
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("Add to album") }, onClick = { open = false; dialog = MenuDialog.ADD })
            if (source is Source.Custom) {
                DropdownMenuItem(
                    text = { Text("Remove from this album") },
                    onClick = {
                        open = false
                        vm.removeFromAlbum(source.albumId, keys)
                        vm.clearSelection()
                    },
                )
            }
            DropdownMenuItem(text = { Text("Move to folder…") }, onClick = { open = false; dialog = MenuDialog.MOVE })
            DropdownMenuItem(text = { Text("Copy to folder…") }, onClick = { open = false; dialog = MenuDialog.COPY })
            DropdownMenuItem(text = { Text("Move to locked folder") }, onClick = { open = false; dialog = MenuDialog.VAULT })
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
