@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.photos.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.photos.data.Source
import com.example.photos.ui.theme.CardColor

/** Searches file name, folder, album names, date words ("april 2024", "monday"), type and EXIF place. */
@Composable
fun SearchScreen(vm: GalleryViewModel, actions: MediaActions, onBack: () -> Unit) {
    val st by vm.searchState.collectAsStateWithLifecycle()
    val clusters by vm.locationClusters.collectAsStateWithLifecycle()
    val progress by vm.indexProgress.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }
    val hiddenFolders by vm.hiddenFolders.collectAsStateWithLifecycle()
    val hiddenMode = st.query.trim().equals(":h", ignoreCase = true)

    // ":l" jumps straight to the locked folder (it asks for its own password).
    LaunchedEffect(st.query) {
        if (st.query.trim().equals(":l", ignoreCase = true)) {
            vm.setSearchQuery("")
            vm.open(Screen.Vault)
        }
    }

    LaunchedEffect(Unit) { focus.requestFocus() }
    DisposableEffect(Unit) { onDispose { vm.resetSearch() } }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            TextField(
                value = st.query,
                onValueChange = vm::setSearchQuery,
                placeholder = { Text("Name, date, album, type…") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                shape = RoundedCornerShape(28.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = CardColor,
                    unfocusedContainerColor = CardColor,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                trailingIcon = {
                    if (st.query.isNotEmpty()) {
                        IconButton(onClick = { vm.setSearchQuery("") }) { Icon(Icons.Filled.Close, "Clear") }
                    }
                },
                modifier = Modifier.weight(1f).focusRequester(focus),
            )
        }

        if (!hiddenMode && clusters.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                clusters.take(30).forEach { c ->
                    AppChip(st.locationCell == c.cell, "${c.label} · ${c.count}") {
                        vm.setSearchCell(if (st.locationCell == c.cell) null else c.cell)
                    }
                }
            }
        }
        if (!hiddenMode) progress?.let { (done, total) ->
            Text(
                "Indexing locations and duplicates… $done / $total",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
            )
        }

        if (hiddenMode) {
            Text(
                "Hidden",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
            )
            hiddenFolders.sorted().forEach { key ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(key, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    TextButton(onClick = { vm.unhideFolder(key) }) { Text("Unhide folder") }
                }
            }
        }

        MediaGridScreen(
            vm = vm,
            source = if (hiddenMode) Source.HiddenItems else Source.SearchResults,
            title = "Search",
            gridId = if (hiddenMode) "hidden" else "search",
            actions = actions,
            embedded = true,
        )
    }
}
