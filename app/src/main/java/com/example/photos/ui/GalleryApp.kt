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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.photos.data.Media
import com.example.photos.data.Source
import com.example.photos.ui.theme.CardColorHigh
import com.example.photos.ui.theme.NothingRed
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

@Composable
fun GalleryApp(vm: GalleryViewModel = viewModel()) {
    val context = LocalContext.current
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

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
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
    }
}

@Composable
private fun HomeScreen(vm: GalleryViewModel, actions: MediaActions) {
    Box(Modifier.fillMaxSize()) {
        when (vm.tab) {
            Tab.PHOTOS -> MediaGridScreen(vm, Source.All, "Photos", "tab_photos", actions)
            Tab.ALBUMS -> AlbumsScreen(vm)
        }
        if (vm.selection.isEmpty()) {
            PillNav(vm.tab, vm::selectTab, Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
private fun PillNav(tab: Tab, onSelect: (Tab) -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.navigationBarsPadding().padding(bottom = 12.dp),
        shape = CircleShape,
        color = CardColorHigh,
        shadowElevation = 8.dp,
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            IconButton(onClick = { onSelect(Tab.PHOTOS) }) {
                Icon(Icons.Outlined.Photo, "Photos", tint = if (tab == Tab.PHOTOS) NothingRed else Color.White)
            }
            IconButton(onClick = { onSelect(Tab.ALBUMS) }) {
                Icon(Icons.Outlined.PhotoLibrary, "Albums", tint = if (tab == Tab.ALBUMS) NothingRed else Color.White)
            }
        }
    }
}

@Composable
private fun PermissionScreen(onAllow: () -> Unit, onOpenSettings: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Photos", style = MaterialTheme.typography.headlineLarge)
        Text(
            "Photos needs access to your photos and videos to show them. " +
                "Everything stays on this device — the app has no internet permission.",
            modifier = Modifier.padding(vertical = 20.dp),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onAllow, colors = ButtonDefaults.buttonColors(containerColor = NothingRed)) {
            Text("Allow access")
        }
        TextButton(onClick = onOpenSettings) { Text("Open app settings") }
    }
}
