@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.photos.ui

import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore
import android.view.LayoutInflater
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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.photos.R
import com.example.photos.data.Media
import com.example.photos.data.VideoTrimmer
import com.example.photos.ui.theme.LocalPalette
import com.example.photos.util.formatDuration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Lossless video trim: pick start/end, preview, save as a new file. */
@Composable
fun VideoTrimScreen(vm: GalleryViewModel, media: Media, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val p = LocalPalette.current
    val duration = media.durationMs.coerceAtLeast(1L)
    var range by remember { mutableStateOf(0f..duration.toFloat()) }
    var playing by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    val player = remember(media.uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(media.uri))
            prepare()
            playWhenReady = false
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { player.pause() }
    DisposableEffect(player) {
        val l = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
        }
        player.addListener(l)
        onDispose { player.removeListener(l) }
    }
    // Loop the selected part while previewing.
    LaunchedEffect(player, range) {
        while (true) {
            if (player.isPlaying && player.currentPosition >= range.endInclusive) {
                player.pause()
                player.seekTo(range.start.toLong())
            }
            delay(100)
        }
    }

    Column(Modifier.fillMaxSize().background(p.background).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            RoundIconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Close") }
            Text("Trim video", modifier = Modifier.weight(1f).padding(start = 14.dp), style = MaterialTheme.typography.titleLarge)
            Button(
                enabled = !saving,
                onClick = {
                    saving = true
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) {
                            saveTrimmed(context, media, range.start.toLong(), range.endInclusive.toLong())
                        }
                        saving = false
                        vm.toast(if (ok) "Saved trimmed copy" else "Couldn't trim this video format")
                        if (ok) { vm.refresh(); onClose() }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = p.accent, contentColor = p.onAccent),
            ) { Text(if (saving) "Saving…" else "Save") }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(
                factory = { ctx ->
                    (LayoutInflater.from(ctx).inflate(R.layout.photos_player_view, null) as PlayerView)
                        .also { it.player = player }
                },
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                Modifier.align(Alignment.Center).size(64.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    .clickable {
                        if (player.isPlaying) player.pause() else {
                            if (player.currentPosition < range.start || player.currentPosition >= range.endInclusive) {
                                player.seekTo(range.start.toLong())
                            }
                            player.play()
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (playing) "Pause" else "Play",
                    tint = Color.White,
                    modifier = Modifier.size(36.dp),
                )
            }
        }

        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            RangeSlider(
                value = range,
                onValueChange = { new ->
                    if (new.start != range.start) player.seekTo(new.start.toLong()) else player.seekTo(new.endInclusive.toLong())
                    range = new
                },
                valueRange = 0f..duration.toFloat(),
                colors = SliderDefaults.colors(thumbColor = p.accent, activeTrackColor = p.accent),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Start ${formatDuration(range.start.toLong())}", style = MaterialTheme.typography.labelLarge)
                Text("Length ${formatDuration((range.endInclusive - range.start).toLong())}", style = MaterialTheme.typography.labelLarge)
                Text("End ${formatDuration(range.endInclusive.toLong())}", style = MaterialTheme.typography.labelLarge)
            }
            Text(
                "Saved as a new file with no re-encoding, so quality is unchanged. The cut snaps to the nearest earlier keyframe.",
                color = p.muted,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

private fun saveTrimmed(context: Context, media: Media, startMs: Long, endMs: Long): Boolean {
    val resolver = context.contentResolver
    val path = if (media.relativePath.startsWith("DCIM/") || media.relativePath.startsWith("Movies/")) {
        media.relativePath
    } else "Movies/Edited/"
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, media.name.substringBeforeLast('.', media.name) + "_trim.mp4")
        put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
        put(MediaStore.MediaColumns.RELATIVE_PATH, path)
        if (media.dateTaken > 0) put(MediaStore.MediaColumns.DATE_TAKEN, media.dateTaken)
        put(MediaStore.MediaColumns.IS_PENDING, 1)
    }
    val dest = runCatching {
        resolver.insert(MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)
    }.getOrNull() ?: return false
    return try {
        val ok = resolver.openFileDescriptor(dest, "rw")?.use { pfd ->
            VideoTrimmer.toDescriptor(context, media.uri, startMs * 1000L, endMs * 1000L, pfd.fileDescriptor)
        } ?: false
        if (ok) {
            resolver.update(dest, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            true
        } else {
            resolver.delete(dest, null, null)
            false
        }
    } catch (e: Exception) {
        runCatching { resolver.delete(dest, null, null) }
        false
    }
}
