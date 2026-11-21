@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.photos.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.photos.data.Media
import com.example.photos.data.Source
import com.example.photos.ui.theme.LocalPalette
import com.example.photos.ui.theme.NavStyle
import com.example.photos.util.hasMediaPermission
import com.example.photos.util.mediaPermissions
import com.example.photos.util.shareUris
import kotlinx.coroutines.launch

/** Everything that needs an Activity / system dialog, passed down to screens. */
class MediaActions(
    val share: (List<Media>) -> Unit,
    val trash: (List<Media>) -> Unit,
    val restore: (List<Media>) -> Unit,
    val deleteForever: (List<Media>) -> Unit,
)

private data class NavState(val screen: Screen?, val depth: Int)

@Composable
fun GalleryApp(vm: GalleryViewModel = viewModel()) {
    val context = LocalContext.current
    val palette = LocalPalette.current
    var granted by remember { mutableStateOf(hasMediaPermission(context)) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted = hasMediaPermission(context) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        granted = hasMediaPermission(context)
        if (granted) vm.refresh()
    }
    LaunchedEffect(granted) { if (granted) vm.start() }

    val senderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result -> vm.onWriteResult(result.resultCode == Activity.RESULT_OK) }

    val actions = remember(vm, context, senderLauncher) {
        MediaActions(
            share = { vm.share(it) },
            trash = {
                if (it.isNotEmpty()) senderLauncher.launch(IntentSenderRequest.Builder(vm.trashIntent(it, true)).build())
            },
            restore = {
                if (it.isNotEmpty()) senderLauncher.launch(IntentSenderRequest.Builder(vm.trashIntent(it, false)).build())
            },
            deleteForever = {
                if (it.isNotEmpty()) senderLauncher.launch(IntentSenderRequest.Builder(vm.deleteIntent(it)).build())
            },
        )
    }

    LaunchedEffect(vm) {
        vm.senderRequests.collect { senderLauncher.launch(IntentSenderRequest.Builder(it).build()) }
    }
    LaunchedEffect(vm) {
        vm.shareRequests.collect { shareUris(context, it.uris, it.mime) }
    }
    LaunchedEffect(vm) {
        vm.messages.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }
    // The locked folder re-locks whenever the app leaves the foreground.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { vm.vault.lock() }

    Surface(Modifier.fillMaxSize(), color = Color.Transparent, contentColor = palette.text) {
        Box(Modifier.fillMaxSize().background(palette.backgroundBrush)) {
            if (!granted) {
                PermissionScreen(
                    onAllow = { permissionLauncher.launch(mediaPermissions()) },
                    onOpenSettings = {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                        )
                    },
                )
            } else {
                val stack = vm.stack
                val top = stack.lastOrNull()
                // The photo viewer is drawn as an overlay (with its own open/close animation);
                // everything else slides.
                val baseIndex = stack.indexOfLast { it !is Screen.Viewer }
                val base = stack.getOrNull(baseIndex)
                BackHandler(enabled = top != null && top !is Screen.Viewer) { vm.back() }

                AnimatedContent(
                    targetState = NavState(base, baseIndex + 1),
                    transitionSpec = {
                        if (targetState.depth > initialState.depth) {
                            (slideInHorizontally(tween(300)) { it / 3 } + fadeIn(tween(300))) togetherWith
                                (slideOutHorizontally(tween(300)) { -it / 6 } + fadeOut(tween(200)))
                        } else {
                            (slideInHorizontally(tween(300)) { -it / 6 } + fadeIn(tween(300))) togetherWith
                                (slideOutHorizontally(tween(300)) { it / 3 } + fadeOut(tween(200)))
                        }
                    },
                    label = "navigation",
                ) { state ->
                    Box(Modifier.fillMaxSize().background(palette.backgroundBrush)) {
                        ScreenHost(vm, actions, state.screen)
                    }
                }
                if (top is Screen.Viewer) ViewerHost(vm, actions, top)
            }
        }
    }
}

/** Clips the viewer to a rectangle that grows from the tapped thumbnail to full screen. */
private class RevealShape(val f: Float, val startHeight: Float, val startRadius: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val vh = lerp(startHeight, size.height, f)
        val top = (size.height - vh) / 2f
        return Outline.Rounded(RoundRect(0f, top, size.width, top + vh, CornerRadius(lerp(startRadius, 0f, f))))
    }
}

@Composable
private fun ViewerHost(vm: GalleryViewModel, actions: MediaActions, screen: Screen.Viewer) {
    val progress = remember { Animatable(if (vm.viewerEntered) 1f else 0f) }
    val scope = rememberCoroutineScope()
    var currentKey by remember { mutableStateOf(screen.startKey) }
    var closing by remember { mutableStateOf(false) }
    val origin = remember { vm.openOrigin }

    LaunchedEffect(Unit) {
        if (progress.value < 1f) progress.animateTo(1f, tween(340, easing = FastOutSlowInEasing))
        vm.viewerEntered = true
    }
    fun close() {
        if (closing) return
        closing = true
        scope.launch {
            progress.animateTo(0f, tween(260, easing = FastOutSlowInEasing))
            vm.viewerEntered = false
            vm.back()
        }
    }
    BackHandler { close() }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        // Only morph to/from the thumbnail while the user is still on the photo they tapped.
        val useOrigin = origin != null && origin.width > 1f && currentKey == screen.startKey
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                val f = progress.value
                if (useOrigin && origin != null) {
                    val u0 = (origin.width / w).coerceIn(0.05f, 1f)
                    val u = lerp(u0, 1f, f)
                    scaleX = u
                    scaleY = u
                    translationX = lerp(origin.center.x - w / 2f, 0f, f)
                    translationY = lerp(origin.center.y - h / 2f, 0f, f)
                    alpha = (0.25f + 0.75f * f).coerceIn(0f, 1f)
                    clip = true
                    shape = RevealShape(f, (origin.height / u0).coerceAtMost(h), 14.dp.toPx() / u0)
                } else {
                    val u = lerp(0.92f, 1f, f)
                    scaleX = u
                    scaleY = u
                    alpha = f
                }
            },
        ) {
            ViewerScreen(
                vm = vm,
                source = screen.source,
                startKey = screen.startKey,
                actions = actions,
                onBack = { close() },
                onPageChanged = { currentKey = it },
            )
        }
    }
}

@Composable
private fun ScreenHost(vm: GalleryViewModel, actions: MediaActions, top: Screen?) {
    when (top) {
        null -> HomeScreen(vm, actions)
        is Screen.Album -> MediaGridScreen(
            vm = vm,
            source = top.source,
            title = top.title,
            gridId = "album_${top.source.id}",
            actions = actions,
            onBack = { vm.back() },
        )
        Screen.Vault -> VaultScreen(vm) { vm.back() }
        is Screen.VaultViewer -> VaultViewerScreen(vm, top.startId) { vm.back() }
        Screen.Search -> SearchScreen(vm, actions) { vm.back() }
        Screen.Duplicates -> DuplicatesScreen(vm, actions) { vm.back() }
        is Screen.Editor -> EditorScreen(vm, top.media) { vm.back() }
        is Screen.VideoTrim -> VideoTrimScreen(vm, top.media) { vm.back() }
        is Screen.Viewer -> Unit
    }
}

@Composable
private fun HomeScreen(vm: GalleryViewModel, actions: MediaActions) {
    val p = LocalPalette.current
    Box(Modifier.fillMaxSize()) {
        Crossfade(targetState = vm.tab, animationSpec = tween(220), label = "tab") { tab ->
            when (tab) {
                Tab.PHOTOS -> MediaGridScreen(vm, Source.All, "Photos", "tab_photos", actions)
                Tab.ALBUMS -> AlbumsScreen(vm)
            }
        }
        AnimatedVisibility(
            visible = vm.selection.isEmpty(),
            enter = fadeIn() + slideInVertically(tween(220)) { it },
            exit = fadeOut() + slideOutVertically(tween(180)) { it },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            BottomNav(vm.tab, vm::selectTab, onSearch = { vm.open(Screen.Search) })
        }
    }
}

private class NavItem(val label: String, val icon: ImageVector, val iconSelected: ImageVector, val tab: Tab?)

/** Three bottom bars, one per theme: outlined + labelled, floating icons with a dark dot, dark bar with a raised centre button. */
@Composable
private fun BottomNav(tab: Tab, onSelect: (Tab) -> Unit, onSearch: () -> Unit) {
    val p = LocalPalette.current
    val items = remember {
        listOf(
            NavItem("Photos", Icons.Outlined.Photo, Icons.Filled.Photo, Tab.PHOTOS),
            NavItem("Albums", Icons.Outlined.PhotoLibrary, Icons.Filled.PhotoLibrary, Tab.ALBUMS),
            NavItem("Search", Icons.Outlined.Search, Icons.Outlined.Search, null),
        )
    }
    fun pick(item: NavItem) { if (item.tab != null) onSelect(item.tab) else onSearch() }

    when (p.navStyle) {
        NavStyle.OUTLINE_LABELS -> {
            val shape = RoundedCornerShape(36.dp)
            Row(
                Modifier.navigationBarsPadding().padding(bottom = 14.dp)
                    .clip(shape).background(p.background.copy(alpha = 0.94f))
                    .border(1.5.dp, p.accent.copy(alpha = 0.8f), shape)
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(28.dp),
            ) {
                items.forEach { item ->
                    val sel = item.tab == tab
                    Column(
                        Modifier.clip(RoundedCornerShape(16.dp)).clickable { pick(item) }.padding(horizontal = 6.dp, vertical = 2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(if (sel) item.iconSelected else item.icon, item.label, tint = if (sel) p.accent else p.text, modifier = Modifier.size(24.dp))
                        Text(item.label, style = MaterialTheme.typography.labelSmall, color = if (sel) p.accent else p.text)
                    }
                }
            }
        }
        NavStyle.FLOATING_DOT -> {
            Row(
                Modifier.navigationBarsPadding().padding(bottom = 14.dp)
                    .shadow(14.dp, CircleShape, ambientColor = Color(0x33000000), spotColor = Color(0x33000000))
                    .clip(CircleShape).background(p.cardHigh.copy(alpha = 0.82f))
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items.forEach { item ->
                    val sel = item.tab == tab
                    Box(
                        Modifier.size(52.dp).clip(CircleShape)
                            .background(if (sel) p.accent else Color.Transparent).clickable { pick(item) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(item.icon, item.label, tint = if (sel) p.onAccent else p.text, modifier = Modifier.size(24.dp))
                    }
                }
            }
        }
        NavStyle.CENTER_BUTTON -> {
            Row(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                    .background(Color(0xFF080305))
                    .navigationBarsPadding().padding(top = 14.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items.forEach { item ->
                    val sel = item.tab == tab
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Box(
                            Modifier
                                .offset(y = if (sel) (-10).dp else 0.dp)
                                .size(if (sel) 58.dp else 48.dp)
                                .clip(CircleShape)
                                .background(if (sel) p.accent else Color.Transparent)
                                .clickable { pick(item) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(item.icon, item.label, tint = if (sel) Color.White else p.muted, modifier = Modifier.size(if (sel) 26.dp else 24.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionScreen(onAllow: () -> Unit, onOpenSettings: () -> Unit) {
    val p = LocalPalette.current
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        EmptyState("Photos", "Needs access to your photos and videos. Everything stays on this device — the app has no internet permission.")
        Button(
            onClick = onAllow,
            colors = ButtonDefaults.buttonColors(containerColor = p.accent, contentColor = p.onAccent),
            modifier = Modifier.padding(top = 8.dp),
        ) { Text("Allow access") }
        TextButton(onClick = onOpenSettings) { Text("Open app settings", color = p.text) }
    }
}
