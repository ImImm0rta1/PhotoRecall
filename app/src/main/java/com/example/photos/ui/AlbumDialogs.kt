@file:OptIn(ExperimentalFoundationApi::class)

package com.example.photos.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.photos.data.AlbumInfo
import com.example.photos.data.CustomAlbum
import com.example.photos.data.FolderTarget
import com.example.photos.data.Media
import com.example.photos.data.Source

@Composable
fun NameDialog(
    title: String,
    initial: String = "",
    confirmLabel: String = "Save",
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
        },
        confirmButton = {
            TextButton(onClick = { if (text.isNotBlank()) onConfirm(text.trim()) }) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun ConfirmDialog(title: String, text: String, confirmLabel: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Pick one of the user's virtual albums, or create a new one. */
@Composable
fun AlbumPickerDialog(
    albums: List<CustomAlbum>,
    onDismiss: () -> Unit,
    onPick: (CustomAlbum) -> Unit,
    onCreate: (String) -> Unit,
) {
    var creating by remember { mutableStateOf(false) }
    if (creating) {
        NameDialog("New album", confirmLabel = "Create", onDismiss = onDismiss, onConfirm = onCreate)
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to album") },
        text = {
            LazyColumn(Modifier.heightIn(max = 320.dp)) {
                item { TextButton(onClick = { creating = true }) { Text("＋ New album…") } }
                items(albums, key = { it.id }) { a ->
                    Text(
                        "${a.name}  (${a.itemKeys.size})",
                        modifier = Modifier.fillMaxWidth().clickable { onPick(a) }.padding(vertical = 12.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Pick an existing device folder (relative path) or name a new one. */
@Composable
fun FolderPickerDialog(
    title: String,
    folders: List<AlbumInfo>,
    onDismiss: () -> Unit,
    onPick: (FolderTarget) -> Unit,
) {
    var creating by remember { mutableStateOf(false) }
    if (creating) {
        NameDialog("New folder", confirmLabel = "Create", onDismiss = onDismiss) { onPick(FolderTarget(null, it)) }
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn(Modifier.heightIn(max = 340.dp)) {
                item { TextButton(onClick = { creating = true }) { Text("＋ New folder…") } }
                items(folders.filter { it.source is Source.Folder }, key = { it.source.id }) { f ->
                    val path = (f.source as Source.Folder).key
                    Column(Modifier.fillMaxWidth().clickable { onPick(FolderTarget(path, null)) }.padding(vertical = 10.dp)) {
                        Text(f.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(path, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun CoverPickerDialog(items: List<Media>, canReset: Boolean, onDismiss: () -> Unit, onPick: (Media?) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose cover") },
        text = {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.height(320.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(items.take(300), key = { it.key }) { m ->
                    MediaThumbnail(m, 256, Modifier.aspectRatio(1f).clickable { onPick(m) })
                }
            }
        },
        confirmButton = { if (canReset) TextButton(onClick = { onPick(null) }) { Text("Reset to automatic") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
