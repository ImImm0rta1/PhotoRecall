@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)

package com.example.photos.ui

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.photos.data.Media
import com.example.photos.data.Source
import com.example.photos.ui.theme.CardColor
import com.example.photos.ui.theme.NothingRed
import com.example.photos.util.formatDateTime
import com.example.photos.util.formatDuration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable
fun ViewerScreen(
    vm: GalleryViewModel,
    source: Source,
    startKey: String,
    actions: MediaActions,
    onBack: () -> Unit,
) {
    val initial = remember(source) { vm.snapshot(source) }
    val items by remember(source) { vm.itemsFlow(source) }.collectAsStateWithLifecycle(initialValue = initial)
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val isTrash = source == Source.Trash

    val startIndex = remember { initial.indexOfFirst { it.key == startKey }.coerceAtLeast(0) }
    val pagerState = rememberPagerState(initialPage = startIndex) { items.size }

    var controlsVisible by remember { mutableStateOf(true) }
    var slideshow by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }

    val current = items.getOrNull(pagerState.currentPage)

    LaunchedEffect(items.isEmpty()) { if (items.isEmpty()) onBack() }

    // "Recently viewed" bookkeeping (not for trash, and not while browsing that list itself).
    LaunchedEffect(pagerState.settledPage) {
        val m = items.getOrNull(pagerState.settledPage)
        if (m != null && !isTrash && source != Source.RecentlyViewed) vm.markViewed(m.key)
    }

    // Slideshow: advance every 3 seconds, stop at the last item.
    LaunchedEffect(slideshow) {
        while (slideshow) {
            delay(3000)
            val next = pagerState.currentPage + 1
            if (next >= pagerState.pageCount) slideshow = false
            else pagerState.animateScrollToPage(next)
        }
    }
    val view = LocalView.current
    DisposableEffect(slideshow) {
        view.keepScreenOn = slideshow
        onDispose { view.keepScreenOn = false }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1,
            key = { items.getOrNull(it)?.key ?: it },
        ) { page ->
            val m = items.getOrNull(page)
            if (m != null) {
                ViewerPage(
                    media = m,
                    isCurrent = pagerState.currentPage == page,
                    slideshow = slideshow,
                    onTap = {
                        if (slideshow) slideshow = false else controlsVisible = !controlsVisible
                    },
                )
            }
        }

        AnimatedVisibility(
            visible = controlsVisible && !slideshow,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopStart),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)))
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                Column(Modifier.weight(1f)) {
                    if (current != null) {
                        Text(formatDateTime(current.dateTaken), style = MaterialTheme.typography.titleSmall)
                        Text(
                            current.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                IconButton(onClick = { slideshow = true }) { Icon(Icons.Filled.Slideshow, "Slideshow") }
            }
        }

        AnimatedVisibility(
            visible = controlsVisible && !slideshow && current != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))))
                    .navigationBarsPadding()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                if (current != null) {
                    val list = listOf(current)
                    IconButton(onClick = { actions.share(list) }) { Icon(Icons.Filled.Share, "Share") }
                    if (isTrash) {
                        IconButton(onClick = { actions.restore(list) }) { Icon(Icons.Filled.Restore, "Restore") }
                    } else {
                        val isFav = current.key in favorites
                        IconButton(onClick = { vm.toggleFavorite(listOf(current.key), !isFav) }) {
                            Icon(
                                if (isFav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                "Favourite",
                                tint = if (isFav) NothingRed else Color.White,
                            )
                        }
                    }
                    IconButton(onClick = { showInfo = true }) { Icon(Icons.Outlined.Info, "Details") }
                    if (isTrash) {
                        IconButton(onClick = { actions.deleteForever(list) }) { Icon(Icons.Filled.DeleteForever, "Delete permanently") }
                    } else {
                        IconButton(onClick = { actions.trash(list) }) { Icon(Icons.Filled.Delete, "Move to trash") }
                        SelectionMenu(vm, source, list)
                    }
                }
            }
        }
    }

    if (showInfo && current != null) DetailsSheet(current) { showInfo = false }
}

@Composable
internal fun ViewerPage(media: Media, isCurrent: Boolean, slideshow: Boolean, onTap: () -> Unit) {
    val context = LocalContext.current
    when {
        media.isVideo -> VideoPage(media, active = isCurrent && !slideshow, onTap = onTap)
        media.isRaw -> ZoomableBox(onTap) { RawImage(media) }
        else -> ZoomableBox(onTap) {
            // Default image loader animates GIFs here.
            AsyncImage(
                model = ImageRequest.Builder(context).data(media.uri).size(2048).build(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun RawImage(media: Media) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(null, media.key) { value = loadRawFull(context, media, 2048) }
    val bmp = bitmap
    if (bmp != null) {
        Image(bmp.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
    }
}

/** Pinch / pan / double-tap zoom that leaves single-finger swipes to the pager at 1x. */
@Composable
private fun ZoomableBox(onTap: () -> Unit, content: @Composable () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }

    fun clamp(o: Offset, s: Float): Offset {
        val maxX = size.width * (s - 1f) / 2f
        val maxY = size.height * (s - 1f) / 2f
        return Offset(o.x.coerceIn(-maxX, maxX), o.y.coerceIn(-maxY, maxY))
    }

    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { size = it }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = { tap ->
                        if (scale > 1f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            val target = 2.5f
                            val rel = tap - Offset(size.width / 2f, size.height / 2f)
                            scale = target
                            offset = clamp(rel - rel * target, target)
                        }
                    },
                )
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.count { it.pressed }
                        if (pressed >= 2 || scale > 1f) {
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            val centroid = event.calculateCentroid(useCurrent = false)
                            val newScale = (scale * zoom).coerceIn(1f, 6f)
                            val rel = if (centroid.isSpecified) {
                                centroid - Offset(size.width / 2f, size.height / 2f)
                            } else Offset.Zero
                            val moved = rel - (rel - offset) * (newScale / scale) + pan
                            offset = if (newScale <= 1f) Offset.Zero else clamp(moved, newScale)
                            scale = newScale
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            },
        ) { content() }
    }
}

@Composable
private fun VideoPage(media: Media, active: Boolean, onTap: () -> Unit) {
    var playing by remember(media.key) { mutableStateOf(false) }
    LaunchedEffect(active) { if (!active) playing = false }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (playing) {
            VideoPlayer(media.uri, Modifier.fillMaxSize())
        } else {
            SystemThumbnail(media, 1080, Modifier.fillMaxSize(), ContentScale.Fit)
            Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { onTap() } })
            IconButton(
                onClick = { playing = true },
                modifier = Modifier.size(72.dp).background(Color.Black.copy(alpha = 0.5f), CircleShape),
            ) {
                Icon(Icons.Filled.PlayArrow, "Play", tint = Color.White, modifier = Modifier.size(40.dp))
            }
        }
    }
}

@Composable
private fun VideoPlayer(uri: Uri, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val player = remember(uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { player.pause() }
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                this.player = player
                useController = true
                setBackgroundColor(android.graphics.Color.BLACK)
            }
        },
        modifier = modifier,
    )
}

private data class ExifSummary(val camera: String?, val values: List<String>)

private fun readExif(context: Context, m: Media): ExifSummary? {
    if (m.isVideo) return null
    return runCatching {
        context.contentResolver.openInputStream(m.uri)?.use { stream ->
            val e = ExifInterface(stream)
            val values = buildList {
                e.getAttributeDouble(ExifInterface.TAG_F_NUMBER, 0.0).takeIf { it > 0 }
                    ?.let { add("ƒ/" + (if (it % 1.0 == 0.0) it.toInt().toString() else "%.1f".format(it))) }
                e.getAttributeDouble(ExifInterface.TAG_EXPOSURE_TIME, 0.0).takeIf { it > 0 }
                    ?.let { add(if (it < 1) "1/${(1 / it).roundToInt()}s" else "${it}s") }
                e.getAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY)?.let { add("ISO $it") }
                e.getAttributeDouble(ExifInterface.TAG_FOCAL_LENGTH, 0.0).takeIf { it > 0 }
                    ?.let { add("${it.roundToInt()}mm") }
            }
            val camera = listOfNotNull(e.getAttribute(ExifInterface.TAG_MAKE), e.getAttribute(ExifInterface.TAG_MODEL))
                .joinToString(" ").ifBlank { null }
            ExifSummary(camera, values)
        }
    }.getOrNull()
}

@Composable
private fun DetailsSheet(media: Media, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val exif by produceState<ExifSummary?>(null, media.key) {
        value = withContext(Dispatchers.IO) { readExif(context, media) }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = CardColor) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(formatDateTime(media.dateTaken), style = MaterialTheme.typography.titleMedium)
            exif?.let { e ->
                if (e.values.isNotEmpty()) Text(e.values.joinToString("   ·   "), color = MaterialTheme.colorScheme.onSurfaceVariant)
                e.camera?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            DetailRow("Name", media.name)
            DetailRow("Path", media.relativePath + media.name)
            DetailRow("Size", Formatter.formatShortFileSize(context, media.size))
            if (media.width > 0 && media.height > 0) {
                val mp = media.width.toDouble() * media.height / 1_000_000.0
                DetailRow("Resolution", "${media.width} × ${media.height}  ·  ${"%.1f".format(mp)} MP")
            }
            if (media.isVideo) DetailRow("Duration", formatDuration(media.durationMs))
            DetailRow("Type", media.mime)
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(96.dp))
        Text(value, modifier = Modifier.weight(1f))
    }
}
