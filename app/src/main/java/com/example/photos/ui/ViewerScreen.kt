@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)

package com.example.photos.ui

import android.graphics.Bitmap
import android.view.LayoutInflater
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.geometry.lerp as lerpOffset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.photos.R
import com.example.photos.data.Media
import com.example.photos.data.Source
import com.example.photos.ui.theme.NothingRed
import com.example.photos.util.formatDateTime
import com.example.photos.util.formatDuration
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

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
    var dragY by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val dismissPx = with(density) { 110.dp.toPx() }
    val infoPx = with(density) { 90.dp.toPx() }

    val current = items.getOrNull(pagerState.currentPage)

    LaunchedEffect(items.isEmpty()) { if (items.isEmpty()) onBack() }
    BackHandler(enabled = showInfo) { showInfo = false }

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

    fun settle() {
        scope.launch { animate(initialValue = dragY, targetValue = 0f, animationSpec = tween(220)) { v, _ -> dragY = v } }
    }

    CompositionLocalProvider(LocalContentColor provides Color.White) {
        Box(
            Modifier.fillMaxSize()
                .background(Color.Black.copy(alpha = (1f - abs(dragY) / 900f).coerceIn(0.15f, 1f))),
        ) {
            Box(
                Modifier.fillMaxSize()
                    // Swipe down to close, swipe up for photo info (only when the photo isn't zoomed).
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragEnd = {
                                val y = dragY
                                when {
                                    y > dismissPx -> onBack()
                                    y < -infoPx -> { showInfo = true; settle() }
                                    else -> settle()
                                }
                            },
                            onDragCancel = { settle() },
                            onVerticalDrag = { change, amount ->
                                change.consume()
                                dragY += amount
                            },
                        )
                    }
                    .graphicsLayer {
                        translationY = dragY
                        val s = 1f - (abs(dragY) / 2500f).coerceAtMost(0.15f)
                        scaleX = s
                        scaleY = s
                    },
            ) {
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
                            controlsVisible = controlsVisible,
                        ) {
                            if (slideshow) slideshow = false else controlsVisible = !controlsVisible
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = controlsVisible && !slideshow && dragY == 0f,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopStart),
            ) {
                Row(
                    Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    GlassButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                    Column(Modifier.weight(1f)) {
                        if (current != null) {
                            Text(formatDateTime(current.dateTaken), style = MaterialTheme.typography.titleSmall)
                            Text(
                                current.name,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.7f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    GlassButton({ slideshow = true }) { Icon(Icons.Filled.Slideshow, "Slideshow") }
                }
            }

            AnimatedVisibility(
                visible = controlsVisible && !slideshow && current != null && dragY == 0f,
                enter = fadeIn() + slideInVertically(tween(220)) { it / 2 },
                exit = fadeOut() + slideOutVertically(tween(180)) { it / 2 },
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 14.dp)) {
                    Row(
                        Modifier.align(Alignment.Center)
                            .clip(RoundedCornerShape(30.dp))
                            .background(Color.Black.copy(alpha = 0.55f))
                            .padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
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
                                IconButton(onClick = { actions.deleteForever(list) }) {
                                    Icon(Icons.Filled.DeleteForever, "Delete permanently")
                                }
                            } else {
                                IconButton(onClick = { actions.trash(list) }) { Icon(Icons.Filled.Delete, "Move to trash") }
                                SelectionMenu(vm, source, list)
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = showInfo && current != null,
                enter = slideInVertically(tween(320)) { it } + fadeIn(tween(200)),
                exit = slideOutVertically(tween(260)) { it } + fadeOut(tween(200)),
            ) {
                current?.let { DetailsScreen(it, vm.locationOf(it.key)) { showInfo = false } }
            }
        }
    }
}

@Composable
private fun GlassButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier.size(42.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
internal fun ViewerPage(
    media: Media,
    isCurrent: Boolean,
    slideshow: Boolean,
    controlsVisible: Boolean = true,
    onTap: () -> Unit,
) {
    val context = LocalContext.current
    when {
        media.isVideo -> VideoPage(media, active = isCurrent && !slideshow, controlsVisible = controlsVisible, onTap = onTap)
        media.isRaw -> ZoomableBox(onTap, Modifier.mediaSharedBounds(media.key)) { RawImage(media) }
        else -> ZoomableBox(onTap, Modifier.mediaSharedBounds(media.key)) {
            // The default image loader animates GIFs here.
            AsyncImage(
                model = remember(media.uri) { ImageRequest.Builder(context).data(media.uri).size(1600).build() },
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

/**
 * Pinch / pan / double-tap zoom. A double-tap on a zoomed image always zooms fully back out.
 * Single-finger drags are left to the pager while the image is at 1x.
 */
@Composable
private fun ZoomableBox(
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
    onEdgeDoubleTap: ((forward: Boolean) -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val tapNow by rememberUpdatedState(onTap)
    val edgeNow by rememberUpdatedState(onEdgeDoubleTap)

    fun clamp(o: Offset, s: Float): Offset {
        val maxX = size.width * (s - 1f) / 2f
        val maxY = size.height * (s - 1f) / 2f
        return Offset(o.x.coerceIn(-maxX, maxX), o.y.coerceIn(-maxY, maxY))
    }

    fun animateTo(targetScale: Float, targetOffset: Offset) {
        val s0 = scale
        val o0 = offset
        scope.launch {
            animate(0f, 1f, animationSpec = tween(260)) { f, _ ->
                scale = lerp(s0, targetScale, f)
                offset = lerpOffset(o0, targetOffset, f)
            }
        }
    }

    Box(
        modifier
            .fillMaxSize()
            .onSizeChanged { size = it }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { tapNow() },
                    onDoubleTap = { tap ->
                        val edge = edgeNow
                        when {
                            edge != null && scale <= 1.01f && (tap.x < size.width * 0.3f || tap.x > size.width * 0.7f) ->
                                edge(tap.x > size.width / 2f)
                            scale > 1.01f -> animateTo(1f, Offset.Zero)
                            else -> {
                                val target = 2.5f
                                val rel = tap - Offset(size.width / 2f, size.height / 2f)
                                animateTo(target, clamp(rel - rel * target, target))
                            }
                        }
                    },
                )
            }
            .pointerInput(Unit) {
                val slop = viewConfiguration.touchSlop
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var pastSlop = false
                    var travelled = Offset.Zero
                    do {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.count { it.pressed }
                        val zoom = event.calculateZoom()
                        val pan = event.calculatePan()
                        if (pressed >= 2) {
                            pastSlop = true
                        } else {
                            travelled += pan
                            if (travelled.getDistance() > slop) pastSlop = true
                        }
                        // Don't consume until the finger really moves, so taps / double-taps stay recognisable.
                        if (pastSlop && (pressed >= 2 || scale > 1f)) {
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
private fun VideoPage(media: Media, active: Boolean, controlsVisible: Boolean, onTap: () -> Unit) {
    Box(Modifier.fillMaxSize().mediaSharedBounds(media.key), contentAlignment = Alignment.Center) {
        if (active) {
            VideoSurface(media, controlsVisible, onTap)
        } else {
            SystemThumbnail(media, 1080, Modifier.fillMaxSize(), ContentScale.Fit)
            Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { onTap() } })
            Box(
                Modifier.size(64.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(36.dp)) }
        }
    }
}

/** Autoplaying video with custom controls: seek bar, speed, mute, double-tap to skip, pinch to zoom. */
@Composable
private fun VideoSurface(media: Media, controlsVisible: Boolean, onTap: () -> Unit) {
    val context = LocalContext.current
    val player = remember(media.uri) {
        ExoPlayer.Builder(context)
            .setAudioAttributes(AudioAttributes.DEFAULT, true)
            .setHandleAudioBecomingNoisy(true)
            .build()
            .apply {
                setMediaItem(MediaItem.fromUri(media.uri))
                prepare()
                playWhenReady = true
            }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { player.pause() }

    var isPlaying by remember { mutableStateOf(true) }
    var buffering by remember { mutableStateOf(true) }
    var ended by remember { mutableStateOf(false) }
    var duration by remember { mutableLongStateOf(0L) }
    var position by remember { mutableLongStateOf(0L) }
    var seeking by remember { mutableStateOf(false) }
    var seekValue by remember { mutableFloatStateOf(0f) }
    var muted by remember { mutableStateOf(false) }
    var speedIndex by remember { mutableIntStateOf(0) }
    var hint by remember { mutableStateOf<String?>(null) }
    val speeds = remember { floatArrayOf(1f, 1.5f, 2f, 0.5f) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                ended = state == Player.STATE_ENDED
                if (state == Player.STATE_READY || state == Player.STATE_ENDED) {
                    duration = player.duration.coerceAtLeast(0L)
                }
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    LaunchedEffect(player) {
        while (true) {
            if (!seeking) position = player.currentPosition
            delay(200)
        }
    }
    LaunchedEffect(hint) {
        if (hint != null) {
            delay(700)
            hint = null
        }
    }

    Box(Modifier.fillMaxSize()) {
        ZoomableBox(
            onTap = onTap,
            onEdgeDoubleTap = { forward ->
                val target = (player.currentPosition + if (forward) 10_000L else -10_000L)
                    .coerceIn(0L, maxOf(duration, 0L))
                player.seekTo(target)
                hint = if (forward) "+10s" else "−10s"
            },
        ) {
            AndroidView(
                factory = { ctx ->
                    (LayoutInflater.from(ctx).inflate(R.layout.photos_player_view, null) as PlayerView)
                        .also { it.player = player }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (buffering) CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
        hint?.let {
            Text(
                it,
                modifier = Modifier.align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                color = Color.White,
            )
        }

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center),
        ) {
            Box(
                Modifier.size(68.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.5f)).clickable {
                    when {
                        ended -> { player.seekTo(0); player.play() }
                        isPlaying -> player.pause()
                        else -> player.play()
                    }
                },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    when {
                        ended -> Icons.Filled.Replay
                        isPlaying -> Icons.Filled.Pause
                        else -> Icons.Filled.PlayArrow
                    },
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(38.dp),
                )
            }
        }

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 92.dp, start = 20.dp, end = 20.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${speeds[speedIndex]}×".replace(".0×", "×"),
                        color = Color.White,
                        modifier = Modifier.clip(RoundedCornerShape(14.dp))
                            .background(Color.Black.copy(alpha = 0.5f))
                            .clickable {
                                speedIndex = (speedIndex + 1) % speeds.size
                                player.setPlaybackSpeed(speeds[speedIndex])
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                    IconButton(onClick = {
                        muted = !muted
                        player.volume = if (muted) 0f else 1f
                    }) {
                        Icon(if (muted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp, "Mute", tint = Color.White)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(formatDuration(if (seeking) seekValue.toLong() else position), color = Color.White)
                    Slider(
                        value = if (seeking) seekValue else position.toFloat(),
                        onValueChange = { seeking = true; seekValue = it },
                        onValueChangeFinished = {
                            player.seekTo(seekValue.toLong())
                            position = seekValue.toLong()
                            seeking = false
                        },
                        valueRange = 0f..maxOf(duration, 1L).toFloat(),
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = NothingRed,
                            inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                        ),
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    )
                    Text(formatDuration(duration), color = Color.White)
                }
            }
        }
    }
}
