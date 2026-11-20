@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)

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
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.photos.data.Media
import com.example.photos.data.Source
import com.example.photos.ui.theme.LocalPalette
import com.example.photos.util.hasMediaPermission
import com.example.photos.util.mediaPermissions
import com.example.photos.util.shareMedia

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
            share = { shareMedia(context, it) },
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
                val top = vm.stack.lastOrNull()
                BackHandler(enabled = top != null) { vm.back() }

                SharedTransitionLayout {
                    AnimatedContent(
                        targetState = NavState(top, vm.stack.size),
                        transitionSpec = {
                            val viewerInvolved = targetState.screen is Screen.Viewer || initialState.screen is Screen.Viewer
                            val opening = targetState.depth > initialState.depth
                            when {
                                viewerInvolved -> fadeIn(tween(260)) togetherWith fadeOut(tween(260))
                                opening -> (slideInHorizontally(tween(320)) { it / 3 } + fadeIn(tween(320))) togetherWith
                                    (slideOutHorizontally(tween(320)) { -it / 6 } + fadeOut(tween(220)))
                                else -> (slideInHorizontally(tween(320)) { -it / 6 } + fadeIn(tween(320))) togetherWith
                                    (slideOutHorizontally(tween(320)) { it / 3 } + fadeOut(tween(220)))
                            }
                        },
                        label = "navigation",
                    ) { state ->
                        CompositionLocalProvider(
                            LocalSharedScope provides this@SharedTransitionLayout,
                            LocalAnimScope provides this@AnimatedContent,
                        ) {
                            Box(Modifier.fillMaxSize().background(palette.backgroundBrush)) {
                                ScreenHost(vm, actions, state.screen)
                            }
                        }
                    }
                }
            }
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
        is Screen.Viewer -> ViewerScreen(
            vm = vm,
            source = top.source,
            startKey = top.startKey,
            actions = actions,
            onBack = { vm.back() },
        )
    }
}

@Composable
private fun HomeScreen(vm: GalleryViewModel, actions: MediaActions) {
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
            PillNav(vm.tab, vm::selectTab, onSearch = { vm.open(Screen.Search) })
        }
    }
}

@Composable
private fun PillNav(tab: Tab, onSelect: (Tab) -> Unit, onSearch: () -> Unit, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    Row(
        modifier
            .navigationBarsPadding()
            .padding(bottom = 12.dp)
            .clip(CircleShape)
            .background(p.cardHigh.copy(alpha = 0.94f))
            .border(1.dp, p.text.copy(alpha = 0.08f), CircleShape)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        NavIcon(Icons.Outlined.Photo, "Photos", tab == Tab.PHOTOS) { onSelect(Tab.PHOTOS) }
        NavIcon(Icons.Outlined.PhotoLibrary, "Albums", tab == Tab.ALBUMS) { onSelect(Tab.ALBUMS) }
        NavIcon(Icons.Outlined.Search, "Search", false, onSearch)
    }
}

@Composable
private fun NavIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    Box(
        Modifier.clip(CircleShape).background(if (selected) p.accent.copy(alpha = 0.18f) else Color.Transparent),
    ) {
        IconButton(onClick = onClick) {
            Icon(icon, label, tint = if (selected) p.accent else p.text)
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
        Text("Photos", style = MaterialTheme.typography.headlineLarge, color = p.text)
        Text(
            "Photos needs access to your photos and videos to show them. " +
                "Everything stays on this device — the app has no internet permission.",
            modifier = Modifier.padding(vertical = 20.dp),
            textAlign = TextAlign.Center,
            color = p.muted,
        )
        Button(onClick = onAllow, colors = ButtonDefaults.buttonColors(containerColor = p.accent, contentColor = p.onAccent)) {
            Text("Allow access")
        }
        TextButton(onClick = onOpenSettings) { Text("Open app settings", color = p.text) }
    }
}
