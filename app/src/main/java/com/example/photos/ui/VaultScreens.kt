@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)

package com.example.photos.ui

import android.graphics.Bitmap
import android.net.Uri
import android.view.WindowManager
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.photos.data.Media
import com.example.photos.data.Vault
import com.example.photos.data.VaultItem
import com.example.photos.ui.theme.CardColorHigh
import com.example.photos.ui.theme.NothingRed
import com.example.photos.util.findActivity
import com.example.photos.util.formatDuration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Blocks screenshots and hides the screen in the recents list while sensitive content is shown. */
@Composable
fun SecureWindowEffect() {
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val window = context.findActivity()?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
}

private fun biometricAvailable(activity: FragmentActivity?): Boolean =
    activity != null && BiometricManager.from(activity)
        .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS

private fun promptBiometric(activity: FragmentActivity, onSuccess: () -> Unit) {
    val prompt = BiometricPrompt(
        activity,
        ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
        },
    )
    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle("Unlock locked folder")
        .setNegativeButtonText("Use password")
        .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        .build()
    prompt.authenticate(info)
}

@Composable
fun VaultScreen(vm: GalleryViewModel, onBack: () -> Unit) {
    SecureWindowEffect()
    val vault = vm.vault
    val configured by vault.configured.collectAsStateWithLifecycle()
    val unlocked by vault.unlocked.collectAsStateWithLifecycle()

    LaunchedEffect(unlocked) {
        if (unlocked) {
            vault.refreshItems()
            vm.consumePendingVault()
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        when {
            !configured -> PasswordSetup(onBack) { pw -> vault.setPassword(pw); vault.grantAccess() }
            !unlocked -> UnlockGate(vault, onBack)
            else -> VaultContent(vm, onBack)
        }
    }
}

@Composable
private fun GateHeader(title: String, text: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
        Text(title, style = MaterialTheme.typography.headlineLarge)
    }
    Text(
        text,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun PasswordSetup(onBack: () -> Unit, onCreate: (String) -> Unit) {
    var pw by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    val error = when {
        pw.isNotEmpty() && pw.length < 6 -> "Use at least 6 characters"
        confirm.isNotEmpty() && confirm != pw -> "Passwords don't match"
        else -> null
    }
    GateHeader(
        "Locked folder",
        "Choose a password just for this folder. It is separate from your phone's lock screen. " +
            "If you forget it, locked items cannot be recovered.",
        onBack,
    )
    Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = pw, onValueChange = { pw = it }, label = { Text("Password") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = confirm, onValueChange = { confirm = it }, label = { Text("Confirm password") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(),
            isError = error != null, supportingText = { error?.let { Text(it) } },
        )
        Button(
            onClick = { onCreate(pw) },
            enabled = pw.length >= 6 && pw == confirm,
            colors = ButtonDefaults.buttonColors(containerColor = NothingRed),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Create locked folder") }
    }
}

@Composable
private fun UnlockGate(vault: Vault, onBack: () -> Unit) {
    val context = LocalContext.current
    val activity = context.findActivity() as? FragmentActivity
    val scope = rememberCoroutineScope()
    val bioOn by vault.biometricOn.collectAsStateWithLifecycle()
    val bioReady = bioOn && biometricAvailable(activity)
    var pw by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun attempt() {
        if (busy || pw.isEmpty()) return
        busy = true
        scope.launch {
            error = withContext(Dispatchers.Default) { vault.tryUnlock(pw) }
            busy = false
            pw = ""
        }
    }

    LaunchedEffect(bioReady) {
        if (bioReady && activity != null) promptBiometric(activity) { vault.grantAccess() }
    }

    GateHeader("Locked folder", "Enter your locked-folder password.", onBack)
    Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = pw, onValueChange = { pw = it }, label = { Text("Password") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(),
            isError = error != null, supportingText = { error?.let { Text(it) } },
        )
        Button(
            onClick = { attempt() },
            enabled = pw.isNotEmpty() && !busy,
            colors = ButtonDefaults.buttonColors(containerColor = NothingRed),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Unlock") }
        if (bioReady && activity != null) {
            OutlinedButton(
                onClick = { promptBiometric(activity) { vault.grantAccess() } },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Fingerprint, null)
                Text("  Use biometrics")
            }
        }
    }
}

@Composable
private fun VaultThumb(vault: Vault, item: VaultItem, modifier: Modifier = Modifier) {
    val bmp by produceState<Bitmap?>(null, item.id) { value = vault.loadThumb(item.id) }
    val b = bmp
    if (b != null) Image(b.asImageBitmap(), null, modifier, contentScale = ContentScale.Crop)
    else Box(modifier.background(CardColorHigh))
}

@Composable
private fun VaultContent(vm: GalleryViewModel, onBack: () -> Unit) {
    val vault = vm.vault
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val items by vault.items.collectAsStateWithLifecycle()
    val bioOn by vault.biometricOn.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var menu by remember { mutableStateOf(false) }
    var changePw by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val picked = items.filter { it.id in selected }

    Row(Modifier.fillMaxWidth().padding(end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (selected.isEmpty()) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Text("Locked folder", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.weight(1f))
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Lock now") }, onClick = { menu = false; vault.lock() })
                    DropdownMenuItem(text = { Text("Change password") }, onClick = { menu = false; changePw = true })
                    if (biometricAvailable(context.findActivity() as? FragmentActivity)) {
                        DropdownMenuItem(
                            text = { Text(if (bioOn) "Turn off biometric unlock" else "Turn on biometric unlock") },
                            onClick = { menu = false; vault.setBiometric(!bioOn) },
                        )
                    }
                }
            }
        } else {
            IconButton(onClick = { selected = emptySet() }) { Icon(Icons.Filled.Close, "Cancel") }
            Text("${selected.size} selected", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = {
                val list = picked
                selected = emptySet()
                scope.launch {
                    var n = 0
                    for (it in list) if (vault.restore(it)) n++
                    vm.toast("Restored $n of ${list.size} to your gallery")
                    vm.refresh()
                }
            }) { Icon(Icons.Filled.Restore, "Restore to gallery") }
            IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.DeleteForever, "Delete permanently") }
        }
    }

    if (items.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "Nothing locked yet.\nSelect photos in your gallery and choose ⋮ › Move to locked folder.",
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(32.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(1.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            items(items, key = { it.id }) { item ->
                val on = item.id in selected
                Box(
                    Modifier.aspectRatio(1f).combinedClickable(
                        onClick = {
                            if (selected.isNotEmpty()) selected = if (on) selected - item.id else selected + item.id
                            else vm.open(Screen.VaultViewer(item.id))
                        },
                        onLongClick = { selected = if (on) selected - item.id else selected + item.id },
                    ),
                ) {
                    VaultThumb(vault, item, Modifier.fillMaxSize())
                    if (on) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)))
                    if (selected.isNotEmpty()) {
                        Box(
                            Modifier.align(Alignment.TopStart).padding(6.dp).size(22.dp).clip(CircleShape)
                                .background(if (on) NothingRed else Color.Transparent)
                                .border(2.dp, Color.White, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) { if (on) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(14.dp)) }
                    }
                    if (item.isVideo) {
                        Text(
                            "▶ " + formatDuration(item.durationMs),
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.align(Alignment.BottomEnd).padding(5.dp)
                                .background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 4.dp),
                        )
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete permanently?",
            text = "${selected.size} locked item(s) will be erased. This can't be undone.",
            confirmLabel = "Delete",
            onDismiss = { confirmDelete = false },
            onConfirm = {
                val ids = selected
                confirmDelete = false
                selected = emptySet()
                scope.launch { vault.removeAll(ids) }
            },
        )
    }
    if (changePw) ChangePasswordDialog(vault) { changePw = false }
}

@Composable
private fun ChangePasswordDialog(vault: Vault, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var old by remember { mutableStateOf("") }
    var new by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Change password") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(old, { old = it }, label = { Text("Current password") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
                OutlinedTextField(new, { new = it }, label = { Text("New password (6+ characters)") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
                OutlinedTextField(confirm, { confirm = it }, label = { Text("Confirm new password") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                scope.launch {
                    val ok = withContext(Dispatchers.Default) { vault.checkPassword(old) }
                    when {
                        !ok -> error = "Current password is wrong"
                        new.length < 6 -> error = "Use at least 6 characters"
                        new != confirm -> error = "Passwords don't match"
                        else -> {
                            withContext(Dispatchers.Default) { vault.setPassword(new) }
                            onDismiss()
                        }
                    }
                }
            }) { Text("Change") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Swipe through locked items; each page is decrypted to a temp file and shown with the normal viewer pages. */
@Composable
fun VaultViewerScreen(vm: GalleryViewModel, startId: String, onBack: () -> Unit) {
    SecureWindowEffect()
    val vault = vm.vault
    val scope = rememberCoroutineScope()
    val items by vault.items.collectAsStateWithLifecycle()
    val startIndex = remember { items.indexOfFirst { it.id == startId }.coerceAtLeast(0) }
    val pagerState = rememberPagerState(initialPage = startIndex) { items.size }
    var controls by remember { mutableStateOf(true) }
    var confirmDelete by remember { mutableStateOf(false) }
    val current = items.getOrNull(pagerState.currentPage)

    LaunchedEffect(items.isEmpty()) { if (items.isEmpty()) onBack() }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            key = { items.getOrNull(it)?.id ?: it },
        ) { page ->
            val item = items.getOrNull(page)
            if (item != null) {
                val file by produceState<File?>(null, item.id) { value = vault.decryptToCache(item) }
                val f = file
                if (f == null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = NothingRed)
                    }
                } else {
                    val media = remember(f) { item.toMedia(f) }
                    ViewerPage(media, isCurrent = pagerState.currentPage == page, slideshow = false) { controls = !controls }
                }
            }
        }
        if (controls) {
            Row(
                Modifier.align(Alignment.TopStart).fillMaxWidth().statusBarsPadding().padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                Text(current?.name ?: "", modifier = Modifier.weight(1f), maxLines = 1)
            }
            Row(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                IconButton(onClick = {
                    val it = current ?: return@IconButton
                    scope.launch {
                        val ok = vault.restore(it)
                        vm.toast(if (ok) "Restored to your gallery" else "Couldn't restore")
                        vm.refresh()
                    }
                }) { Icon(Icons.Filled.Restore, "Restore to gallery") }
                IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.DeleteForever, "Delete permanently") }
            }
        }
    }

    if (confirmDelete && current != null) {
        ConfirmDialog(
            title = "Delete permanently?",
            text = "This locked item will be erased. This can't be undone.",
            confirmLabel = "Delete",
            onDismiss = { confirmDelete = false },
            onConfirm = {
                confirmDelete = false
                scope.launch { vault.removeAll(listOf(current.id)) }
            },
        )
    }
}

private fun VaultItem.toMedia(file: File): Media = Media(
    id = id.hashCode().toLong(),
    isVideo = isVideo,
    uri = Uri.fromFile(file),
    name = name,
    mime = mime,
    size = size,
    dateTaken = dateTaken,
    dateModified = dateTaken,
    dateAdded = addedAt,
    dateExpires = 0L,
    width = width,
    height = height,
    durationMs = durationMs,
    relativePath = "",
    folderName = "",
)
