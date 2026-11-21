@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.photos.ui

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.PhotoFilter
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Tune
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.height
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.photos.ui.theme.EditorStyle
import kotlinx.coroutines.delay
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.photos.data.EditFilter
import com.example.photos.data.EditPipeline
import com.example.photos.data.EditState
import com.example.photos.data.Media
import com.example.photos.data.NRect
import com.example.photos.data.Stroke
import com.example.photos.data.TextItem
import com.example.photos.ui.theme.LocalPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.sp

private enum class EditTab(val label: String, val icon: ImageVector) {
    CROP("Crop", Icons.Filled.Crop),
    ADJUST("Adjust", Icons.Filled.Tune),
    FILTER("Filters", Icons.Filled.PhotoFilter),
    DRAW("Draw", Icons.Filled.Brush),
    TEXT("Text", Icons.Filled.TextFields),
}

private enum class AdjustParam(val label: String) {
    BRIGHTNESS("Brightness"), CONTRAST("Contrast"), SATURATION("Saturation"), WARMTH("Warmth"), VIGNETTE("Vignette"),
}

private enum class AspectPreset(val label: String, val ratio: Float?) {
    FREE("Free", null), ORIGINAL("Original", -1f), SQUARE("1:1", 1f),
    R43("4:3", 4f / 3f), R34("3:4", 3f / 4f), R169("16:9", 16f / 9f), R916("9:16", 9f / 16f),
}

private val markColors = listOf(
    Color.White, Color.Black, Color(0xFFE53935), Color(0xFFFFC107),
    Color(0xFF43A047), Color(0xFF1E88E5), Color(0xFFEC407A),
)

/** Photo editor. Everything runs on-device; the original is never modified (saves a copy). */
@Composable
fun EditorScreen(vm: GalleryViewModel, media: Media, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val p = LocalPalette.current

    var st by remember { mutableStateOf(EditState()) }
    var base by remember { mutableStateOf<Bitmap?>(null) }
    var failed by remember { mutableStateOf(false) }
    var frame by remember { mutableStateOf<Bitmap?>(null) }
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var tab by remember { mutableStateOf(EditTab.CROP) }
    var aspect by remember { mutableStateOf(AspectPreset.FREE) }
    var adjust by remember { mutableStateOf(AdjustParam.BRIGHTNESS) }
    var drawColor by remember { mutableStateOf(Color(0xFFE53935)) }
    var drawWidth by remember { mutableStateOf(0.012f) }
    var selectedText by remember { mutableStateOf<Long?>(null) }
    var textDialog by remember { mutableStateOf<TextItem?>(null) }
    var addingText by remember { mutableStateOf(false) }
    var showSave by remember { mutableStateOf(false) }
    var showDiscard by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var resize by remember { mutableStateOf(100) }

    LaunchedEffect(media) {
        val b = withContext(Dispatchers.IO) { EditPipeline.loadBitmap(context, media, 1600) }
        if (b == null) failed = true else base = b
    }
    LaunchedEffect(base, st.turns, st.flip, st.straighten) {
        val b = base ?: return@LaunchedEffect
        frame = withContext(Dispatchers.Default) { EditPipeline.frame(b, st) }
    }
    val renderKey = st.copy(texts = emptyList(), resizePercent = 100)
    LaunchedEffect(frame, renderKey, tab) {
        val f = frame ?: return@LaunchedEffect
        if (tab == EditTab.CROP) return@LaunchedEffect
        preview = withContext(Dispatchers.Default) { EditPipeline.render(f, st, withTexts = false) }
    }

    // Undo / redo: a change is committed to history once the user pauses for a moment.
    var undo by remember { mutableStateOf<List<EditState>>(emptyList()) }
    var redo by remember { mutableStateOf<List<EditState>>(emptyList()) }
    var committed by remember { mutableStateOf(EditState()) }
    LaunchedEffect(st) {
        delay(400)
        if (st != committed) {
            undo = (undo + committed).takeLast(40)
            committed = st
            redo = emptyList()
        }
    }
    fun doUndo() {
        if (st != committed) {
            redo = redo + st
            st = committed
        } else if (undo.isNotEmpty()) {
            redo = redo + committed
            val prev = undo.last()
            undo = undo.dropLast(1)
            committed = prev
            st = prev
        }
    }
    fun doRedo() {
        if (redo.isNotEmpty()) {
            val next = redo.last()
            redo = redo.dropLast(1)
            undo = undo + committed
            committed = next
            st = next
        }
    }

    BackHandler { if (st.isModified) showDiscard = true else onClose() }

    Column(Modifier.fillMaxSize().background(p.background).statusBarsPadding().navigationBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            RoundIconButton(onClick = { if (st.isModified) showDiscard = true else onClose() }) {
                Icon(Icons.Filled.Close, "Close")
            }
            Box(Modifier.weight(1f))
            Row(Modifier.clip(CircleShape).background(p.card), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { doUndo() }, enabled = st != committed || undo.isNotEmpty()) {
                    Icon(Icons.Filled.Undo, "Undo")
                }
                IconButton(onClick = { doRedo() }, enabled = redo.isNotEmpty()) { Icon(Icons.Filled.Redo, "Redo") }
            }
            Button(
                onClick = { showSave = true },
                enabled = frame != null && !saving,
                colors = ButtonDefaults.buttonColors(containerColor = p.accent, contentColor = p.onAccent),
            ) { Text(if (saving) "Saving…" else "Save") }
        }

        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(8.dp)) {
            val shown = if (tab == EditTab.CROP) frame else (preview ?: frame)
            when {
                failed -> Text("This image can't be edited.", Modifier.align(Alignment.Center), color = p.muted)
                shown == null -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = p.accent)
                else -> {
                    val cw = constraints.maxWidth.toFloat()
                    val ch = constraints.maxHeight.toFloat()
                    val s = min(cw / shown.width, ch / shown.height)
                    val dw = shown.width * s
                    val dh = shown.height * s
                    val left = (cw - dw) / 2f
                    val top = (ch - dh) / 2f
                    val img = Rect(left, top, left + dw, top + dh)
                    Image(
                        bitmap = shown.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier
                            .offset { IntOffset(left.roundToInt(), top.roundToInt()) }
                            .size(with(density) { dw.toDp() }, with(density) { dh.toDp() }),
                    )
                    val f = frame
                    if (tab == EditTab.CROP && f != null) {
                        val lock: Float? = when (aspect) {
                            AspectPreset.FREE -> null
                            AspectPreset.ORIGINAL -> f.width.toFloat() / f.height
                            else -> aspect.ratio
                        }
                        CropOverlay(img, f.width, f.height, st.crop, lock) { st = st.copy(crop = it) }
                    } else if (tab == EditTab.DRAW && f != null) {
                        DrawOverlay(img, f.width, f.height, st.crop, drawColor, drawWidth) { st = st.copy(strokes = st.strokes + it) }
                    }
                    if (tab != EditTab.CROP) {
                        TextOverlays(
                            img = img,
                            st = st,
                            selectedId = selectedText,
                            interactive = tab == EditTab.TEXT,
                            onSelect = { selectedText = it },
                            onMove = { id, dx, dy ->
                                st = st.copy(texts = st.texts.map { if (it.id == id) it.copy(x = it.x + dx, y = it.y + dy) else it })
                            },
                        )
                    }
                }
            }
        }

        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (tab) {
                EditTab.CROP -> {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AspectPreset.entries.forEach { a ->
                            AppChip(aspect == a, a.label) {
                                aspect = a
                                val f = frame
                                if (f != null && a != AspectPreset.FREE) {
                                    val ratio = if (a == AspectPreset.ORIGINAL) f.width.toFloat() / f.height else a.ratio!!
                                    st = st.copy(crop = presetCrop(ratio, f.width, f.height))
                                }
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        IconButton(onClick = { st = st.rotate(false) }) { Icon(Icons.Filled.RotateLeft, "Rotate left") }
                        IconButton(onClick = { st = st.rotate(true) }) { Icon(Icons.Filled.RotateRight, "Rotate right") }
                        IconButton(onClick = { st = st.mirrorHorizontal() }) { Icon(Icons.Filled.Flip, "Flip horizontally") }
                        IconButton(onClick = { st = st.mirrorVertical() }) {
                            Icon(Icons.Filled.Flip, "Flip vertically", modifier = Modifier.rotate(90f))
                        }
                    }
                    DialSlider(st.straighten, -30f..30f, "Straighten", "${st.straighten.roundToInt()}°") { st = st.copy(straighten = it) }
                }
                EditTab.ADJUST -> {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AdjustParam.entries.forEach { a -> AppChip(adjust == a, a.label) { adjust = a } }
                    }
                    val value = when (adjust) {
                        AdjustParam.BRIGHTNESS -> st.brightness
                        AdjustParam.CONTRAST -> st.contrast
                        AdjustParam.SATURATION -> st.saturation
                        AdjustParam.WARMTH -> st.warmth
                        AdjustParam.VIGNETTE -> st.vignette
                    }
                    val range = if (adjust == AdjustParam.VIGNETTE) 0f..1f else -1f..1f
                    DialSlider(value, range, adjust.label, "${(value * 100).roundToInt()}") { v ->
                        st = when (adjust) {
                            AdjustParam.BRIGHTNESS -> st.copy(brightness = v)
                            AdjustParam.CONTRAST -> st.copy(contrast = v)
                            AdjustParam.SATURATION -> st.copy(saturation = v)
                            AdjustParam.WARMTH -> st.copy(warmth = v)
                            AdjustParam.VIGNETTE -> st.copy(vignette = v)
                        }
                    }
                }
                EditTab.FILTER -> Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EditFilter.entries.forEach { f -> AppChip(st.filter == f, f.label) { st = st.copy(filter = f) } }
                }
                EditTab.DRAW -> {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        markColors.forEach { c -> ColorDot(c, drawColor == c) { drawColor = c } }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppChip(drawWidth == 0.006f, "Thin") { drawWidth = 0.006f }
                        AppChip(drawWidth == 0.012f, "Medium") { drawWidth = 0.012f }
                        AppChip(drawWidth == 0.024f, "Thick") { drawWidth = 0.024f }
                        Box(Modifier.weight(1f))
                        IconButton(
                            onClick = { st = st.copy(strokes = st.strokes.dropLast(1)) },
                            enabled = st.strokes.isNotEmpty(),
                        ) { Icon(Icons.Filled.Undo, "Undo") }
                    }
                }
                EditTab.TEXT -> {
                    val sel = st.texts.firstOrNull { it.id == selectedText }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppChip(false, "＋ Add text") { addingText = true }
                        if (sel != null) {
                            AppChip(false, "Edit") { textDialog = sel }
                            IconButton(onClick = {
                                st = st.copy(texts = st.texts.filterNot { it.id == sel.id })
                                selectedText = null
                            }) { Icon(Icons.Filled.Delete, "Delete text") }
                        }
                    }
                    if (sel != null) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            markColors.forEach { c ->
                                ColorDot(c, sel.color == c.toArgb()) {
                                    st = st.copy(texts = st.texts.map { if (it.id == sel.id) it.copy(color = c.toArgb()) else it })
                                }
                            }
                        }
                        SliderRow("Size", "${(sel.size * 100).roundToInt()}", sel.size, 0.03f..0.25f) { v ->
                            st = st.copy(texts = st.texts.map { if (it.id == sel.id) it.copy(size = v) else it })
                        }
                    } else {
                        Text("Tap a text to select it, drag to move.", color = p.muted, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }

        EditorTabs(tab) { tab = it }
    }

    if (addingText || textDialog != null) {
        val editing = textDialog
        NameDialog(
            title = if (editing == null) "Add text" else "Edit text",
            initial = editing?.text ?: "",
            confirmLabel = if (editing == null) "Add" else "Save",
            onDismiss = { addingText = false; textDialog = null },
            onConfirm = { value ->
                if (editing == null) {
                    val c = st.crop
                    val item = TextItem(System.nanoTime(), value, (c.l + c.r) / 2f, (c.t + c.b) / 2f, Color.White.toArgb(), 0.08f)
                    st = st.copy(texts = st.texts + item)
                    selectedText = item.id
                } else {
                    st = st.copy(texts = st.texts.map { if (it.id == editing.id) it.copy(text = value) else it })
                }
                addingText = false
                textDialog = null
            },
        )
    }

    if (showDiscard) {
        ConfirmDialog(
            title = "Discard edits?",
            text = "Your changes haven't been saved.",
            confirmLabel = "Discard",
            onDismiss = { showDiscard = false },
            onConfirm = { showDiscard = false; onClose() },
        )
    }

    if (showSave) {
        AlertDialog(
            onDismissRequest = { showSave = false },
            title = { Text("Save a copy") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Your original stays untouched. Size:", color = p.muted)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(100, 75, 50, 25).forEach { v -> AppChip(resize == v, "$v%") { resize = v } }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showSave = false
                    saving = true
                    scope.launch {
                        val ok = EditPipeline.saveCopy(context, media, st.copy(resizePercent = resize))
                        saving = false
                        vm.toast(if (ok) "Saved a copy" else "Couldn't save the edit")
                        if (ok) {
                            vm.refresh()
                            onClose()
                        }
                    }
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showSave = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SliderRow(label: String, valueText: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    val p = LocalPalette.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.padding(end = 12.dp), color = p.muted, style = MaterialTheme.typography.labelLarge)
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            colors = SliderDefaults.colors(thumbColor = p.accent, activeTrackColor = p.accent),
            modifier = Modifier.weight(1f),
        )
        Text(valueText, modifier = Modifier.padding(start = 12.dp), style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun ColorDot(color: Color, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(30.dp).clip(CircleShape).background(color)
            .border(if (selected) 3.dp else 1.dp, if (selected) LocalPalette.current.accent else Color.Gray, CircleShape)
            .clickable(onClick = onClick),
    )
}

private fun presetCrop(ratioPx: Float, fw: Int, fh: Int): NRect {
    val frameRatio = fw.toFloat() / fh
    return if (ratioPx > frameRatio) {
        val h = frameRatio / ratioPx
        NRect(0f, (1f - h) / 2f, 1f, (1f + h) / 2f)
    } else {
        val w = ratioPx / frameRatio
        NRect((1f - w) / 2f, 0f, (1f + w) / 2f, 1f)
    }
}

private fun dragCorner(handle: Int, c: NRect, nx: Float, ny: Float, aspect: Float?, fw: Int, fh: Int): NRect {
    val leftSide = handle == 0 || handle == 2
    val topSide = handle == 0 || handle == 1
    val ax = if (leftSide) c.r else c.l
    val ay = if (topSide) c.b else c.t
    var w = abs(nx - ax)
    var h = abs(ny - ay)
    if (aspect != null) {
        var wp = max(w * fw, h * fh * aspect)
        var hp = wp / aspect
        val maxW = (if (leftSide) ax else 1f - ax) * fw
        val maxH = (if (topSide) ay else 1f - ay) * fh
        if (wp > maxW) { wp = maxW; hp = wp / aspect }
        if (hp > maxH) { hp = maxH; wp = hp * aspect }
        w = wp / fw
        h = hp / fh
    }
    w = max(w, 0.08f)
    h = max(h, 0.08f)
    val l = if (leftSide) ax - w else ax
    val r = if (leftSide) ax else ax + w
    val t = if (topSide) ay - h else ay
    val b = if (topSide) ay else ay + h
    return NRect(l.coerceIn(0f, 1f), t.coerceIn(0f, 1f), r.coerceIn(0f, 1f), b.coerceIn(0f, 1f))
}

@Composable
private fun CropOverlay(img: Rect, frameW: Int, frameH: Int, crop: NRect, aspect: Float?, onChange: (NRect) -> Unit) {
    val current by rememberUpdatedState(crop)
    val aspectNow by rememberUpdatedState(aspect)
    val changeNow by rememberUpdatedState(onChange)
    val grab = with(LocalDensity.current) { 40.dp.toPx() }
    val line = with(LocalDensity.current) { 2.dp.toPx() }

    Canvas(
        Modifier.fillMaxSize().pointerInput(img, frameW, frameH) {
            var handle = -1 // 0 TL, 1 TR, 2 BL, 3 BR, 4 move
            detectDragGestures(
                onDragStart = { pos ->
                    val c = current
                    val l = img.left + c.l * img.width
                    val t = img.top + c.t * img.height
                    val r = img.left + c.r * img.width
                    val b = img.top + c.b * img.height
                    fun near(x: Float, y: Float) = (pos - Offset(x, y)).getDistance() < grab
                    handle = when {
                        near(l, t) -> 0
                        near(r, t) -> 1
                        near(l, b) -> 2
                        near(r, b) -> 3
                        pos.x in l..r && pos.y in t..b -> 4
                        else -> -1
                    }
                },
                onDrag = { change, drag ->
                    change.consume()
                    val c = current
                    if (handle == 4) {
                        val w = c.w
                        val h = c.h
                        val l = (c.l + drag.x / img.width).coerceIn(0f, 1f - w)
                        val t = (c.t + drag.y / img.height).coerceIn(0f, 1f - h)
                        changeNow(NRect(l, t, l + w, t + h))
                    } else if (handle in 0..3) {
                        val nx = ((change.position.x - img.left) / img.width).coerceIn(0f, 1f)
                        val ny = ((change.position.y - img.top) / img.height).coerceIn(0f, 1f)
                        changeNow(dragCorner(handle, c, nx, ny, aspectNow, frameW, frameH))
                    }
                },
            )
        },
    ) {
        val c = crop
        val l = img.left + c.l * img.width
        val t = img.top + c.t * img.height
        val r = img.left + c.r * img.width
        val b = img.top + c.b * img.height
        val dim = Color.Black.copy(alpha = 0.55f)
        drawRect(dim, Offset(img.left, img.top), Size(img.width, t - img.top))
        drawRect(dim, Offset(img.left, b), Size(img.width, img.bottom - b))
        drawRect(dim, Offset(img.left, t), Size(l - img.left, b - t))
        drawRect(dim, Offset(r, t), Size(img.right - r, b - t))
        drawRect(Color.White, Offset(l, t), Size(r - l, b - t), style = DrawStroke(line))
        val third = Color.White.copy(alpha = 0.35f)
        for (i in 1..2) {
            drawLine(third, Offset(l + (r - l) * i / 3f, t), Offset(l + (r - l) * i / 3f, b), 1f)
            drawLine(third, Offset(l, t + (b - t) * i / 3f), Offset(r, t + (b - t) * i / 3f), 1f)
        }
        listOf(Offset(l, t), Offset(r, t), Offset(l, b), Offset(r, b)).forEach {
            drawCircle(Color.White, radius = 9.dp.toPx(), center = it)
        }
    }
}

@Composable
private fun DrawOverlay(
    img: Rect,
    frameW: Int,
    frameH: Int,
    crop: NRect,
    color: Color,
    widthFrac: Float,
    onStroke: (Stroke) -> Unit,
) {
    var live by remember { mutableStateOf<List<Offset>>(emptyList()) }
    val cropNow by rememberUpdatedState(crop)
    val strokeNow by rememberUpdatedState(onStroke)
    val colorNow by rememberUpdatedState(color)
    val widthNow by rememberUpdatedState(widthFrac)
    val widthPx = widthFrac * min(frameW, frameH) * (img.width / (crop.w * frameW))

    Canvas(
        Modifier.fillMaxSize().pointerInput(img) {
            fun commit() {
                val c = cropNow
                if (live.isNotEmpty()) {
                    val pts = live.map {
                        Offset(
                            c.l + ((it.x - img.left) / img.width).coerceIn(0f, 1f) * c.w,
                            c.t + ((it.y - img.top) / img.height).coerceIn(0f, 1f) * c.h,
                        )
                    }
                    strokeNow(Stroke(pts, colorNow.toArgb(), widthNow))
                }
                live = emptyList()
            }
            detectDragGestures(
                onDragStart = { live = listOf(it) },
                onDrag = { change, _ -> change.consume(); live = live + change.position },
                onDragEnd = { commit() },
                onDragCancel = { live = emptyList() },
            )
        },
    ) {
        if (live.size > 1) {
            val path = Path()
            path.moveTo(live.first().x, live.first().y)
            live.drop(1).forEach { path.lineTo(it.x, it.y) }
            drawPath(path, color, style = DrawStroke(widthPx, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

@Composable
private fun TextOverlays(
    img: Rect,
    st: EditState,
    selectedId: Long?,
    interactive: Boolean,
    onSelect: (Long?) -> Unit,
    onMove: (Long, Float, Float) -> Unit,
) {
    val density = LocalDensity.current
    val stNow by rememberUpdatedState(st)
    val moveNow by rememberUpdatedState(onMove)
    val crop = st.crop
    Box(Modifier.fillMaxSize()) {
        st.texts.forEach { t ->
            val nx = (t.x - crop.l) / crop.w
            val ny = (t.y - crop.t) / crop.h
            if (nx in 0f..1f && ny in 0f..1f) {
                val cx = img.left + nx * img.width
                val cy = img.top + ny * img.height
                val fontPx = t.size * img.height / crop.h
                Text(
                    t.text,
                    color = Color(t.color),
                    fontSize = with(density) { fontPx.toSp() },
                    modifier = Modifier
                        .layout { m, c ->
                            val pl = m.measure(c)
                            layout(pl.width, pl.height) {
                                pl.place((cx - pl.width / 2f).roundToInt(), (cy - pl.height / 2f).roundToInt())
                            }
                        }
                        .then(if (interactive && t.id == selectedId) Modifier.border(1.dp, Color.White) else Modifier)
                        .pointerInput(t.id, interactive, img) {
                            if (interactive) {
                                detectDragGestures(onDragStart = { onSelect(t.id) }) { change, drag ->
                                    change.consume()
                                    val cr = stNow.crop
                                    moveNow(t.id, drag.x / img.width * cr.w, drag.y / img.height * cr.h)
                                }
                            }
                        }
                        .clickable(enabled = interactive) { onSelect(t.id) },
                )
            }
        }
    }
}

/** Tool bar below the preview. One design per theme. */
@Composable
private fun EditorTabs(selected: EditTab, onSelect: (EditTab) -> Unit) {
    val p = LocalPalette.current
    when (p.editorStyle) {
        EditorStyle.LABELED -> Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            EditTab.entries.forEach { t ->
                val tint = if (t == selected) p.accent else p.text.copy(alpha = 0.85f)
                Column(
                    Modifier.clip(RoundedCornerShape(14.dp)).clickable { onSelect(t) }.padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(t.icon, t.label, tint = tint)
                    Text(t.label, style = MaterialTheme.typography.labelSmall, color = tint)
                }
            }
        }
        EditorStyle.CIRCLES -> Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)
                .clip(CircleShape).background(p.card).padding(6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            EditTab.entries.forEach { t ->
                val sel = t == selected
                Box(
                    Modifier.size(48.dp).clip(CircleShape)
                        .background(if (sel) p.accent else Color.Transparent).clickable { onSelect(t) },
                    contentAlignment = Alignment.Center,
                ) { Icon(t.icon, t.label, tint = if (sel) p.onAccent else p.text) }
            }
        }
        EditorStyle.PILLS -> Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)
                .clip(RoundedCornerShape(30.dp)).background(p.card).padding(6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            EditTab.entries.forEach { t ->
                val sel = t == selected
                Row(
                    Modifier.clip(CircleShape).background(if (sel) p.accent else Color.Transparent)
                        .clickable { onSelect(t) }
                        .padding(horizontal = if (sel) 14.dp else 10.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(t.icon, t.label, tint = if (sel) Color.White else p.muted, modifier = Modifier.size(20.dp))
                    if (sel) {
                        Text(t.label, color = Color.White, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 6.dp))
                    }
                }
            }
        }
    }
}

/** Ruler-style dial: drag sideways to change the value; shows the value large above the ticks. */
@Composable
private fun DialSlider(
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    label: String,
    display: String,
    onChange: (Float) -> Unit,
) {
    val p = LocalPalette.current
    val valueNow by rememberUpdatedState(value)
    val changeNow by rememberUpdatedState(onChange)
    val span = range.endInclusive - range.start
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(display, style = MaterialTheme.typography.headlineLarge.copy(fontSize = 34.sp))
        Text(label, color = p.muted, style = MaterialTheme.typography.labelLarge)
        Canvas(
            Modifier.fillMaxWidth().height(56.dp).pointerInput(range) {
                detectHorizontalDragGestures { change, dx ->
                    change.consume()
                    changeNow((valueNow + dx / size.width * span * 0.9f).coerceIn(range.start, range.endInclusive))
                }
            },
        ) {
            val w = size.width
            val h = size.height
            val pxPerUnit = w * 0.9f / span
            val cx = w / 2f
            val step = span / 40f
            val first = kotlin.math.ceil(range.start / step).toInt()
            val last = kotlin.math.floor(range.endInclusive / step).toInt()
            for (i in first..last) {
                val v = i * step
                val x = cx + (valueNow - v) * pxPerUnit
                if (x < 0f || x > w) continue
                val major = i % 5 == 0
                val fade = 1f - (kotlin.math.abs(x - cx) / (w / 2f)).coerceIn(0f, 1f) * 0.7f
                drawLine(
                    p.text.copy(alpha = (if (major) 0.8f else 0.4f) * fade),
                    Offset(x, h * (if (major) 0.35f else 0.5f)),
                    Offset(x, h * 0.85f),
                    strokeWidth = if (major) 2.5f else 1.5f,
                )
            }
            drawLine(p.accent, Offset(cx, 0f), Offset(cx, h * 0.9f), strokeWidth = 4f)
        }
    }
}
