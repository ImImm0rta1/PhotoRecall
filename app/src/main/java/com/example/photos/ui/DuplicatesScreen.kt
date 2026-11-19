@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.example.photos.ui

import android.text.format.Formatter
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.photos.data.DuplicateGroup
import com.example.photos.ui.theme.CardColor
import com.example.photos.ui.theme.NothingRed

/**
 * Near-duplicate finder. The best copy of each group (highest resolution, then largest file)
 * is kept; the rest are pre-selected and go to the trash (recoverable).
 */
@Composable
fun DuplicatesScreen(vm: GalleryViewModel, actions: MediaActions, onBack: () -> Unit) {
    val context = LocalContext.current
    var threshold by remember { mutableIntStateOf(3) }
    var groups by remember { mutableStateOf<List<DuplicateGroup>?>(null) }
    val progress by vm.indexProgress.collectAsStateWithLifecycle()
    val indexed by vm.indexedCount.collectAsStateWithLifecycle()
    val mediaCount by vm.mediaCount.collectAsStateWithLifecycle()

    LaunchedEffect(threshold, indexed, mediaCount) { groups = vm.findDuplicates(threshold) }

    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    LaunchedEffect(groups) {
        selected = groups.orEmpty().flatMap { g -> g.items.drop(1).map { it.key } }.toSet()
    }
    val all = remember(groups) { groups.orEmpty().flatMap { it.items } }
    val chosen = remember(all, selected) { all.filter { it.key in selected } }
    val savings = chosen.sumOf { it.size }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Text("Duplicates", style = MaterialTheme.typography.headlineLarge)
        }
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppChip(threshold == 3, "Near-identical") { threshold = 3 }
            AppChip(threshold == 7, "Similar") { threshold = 7 }
        }
        progress?.let { (done, total) ->
            Text(
                "Still analysing photos… $done / $total",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
            )
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            val g = groups
            when {
                g == null -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = NothingRed)
                g.isEmpty() -> Text(
                    "No duplicates found",
                    Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> LazyColumn(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(g, key = { it.items.first().key }) { group ->
                        Column(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(CardColor).padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                "${group.items.size} similar photos",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(group.items, key = { it.key }) { m ->
                                    val on = m.key in selected
                                    Box(
                                        Modifier.size(110.dp).clip(RoundedCornerShape(12.dp))
                                            .clickable { selected = if (on) selected - m.key else selected + m.key },
                                    ) {
                                        MediaThumbnail(m, 320, Modifier.fillMaxSize())
                                        if (on) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)))
                                        Box(
                                            Modifier.align(Alignment.TopEnd).padding(6.dp).size(22.dp)
                                                .clip(CircleShape)
                                                .background(if (on) NothingRed else Color.Transparent)
                                                .border(2.dp, Color.White, CircleShape),
                                            contentAlignment = Alignment.Center,
                                        ) { if (on) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(14.dp)) }
                                        if (m.key == group.items.first().key) {
                                            Text(
                                                "BEST",
                                                color = Color.White,
                                                fontSize = 10.sp,
                                                modifier = Modifier.align(Alignment.BottomStart).padding(6.dp)
                                                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                                                    .padding(horizontal = 4.dp, vertical = 1.dp),
                                            )
                                        }
                                        Text(
                                            "${m.width}×${m.height}",
                                            color = Color.White,
                                            fontSize = 10.sp,
                                            modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp)
                                                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                                                .padding(horizontal = 4.dp, vertical = 1.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (chosen.isNotEmpty()) {
            Button(
                onClick = { actions.trash(chosen) },
                colors = ButtonDefaults.buttonColors(containerColor = NothingRed),
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
            ) {
                Text("Move ${chosen.size} to trash · frees ${Formatter.formatShortFileSize(context, savings)}")
            }
        }
    }
}
