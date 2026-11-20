package com.example.photos.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.MediaStore
import android.text.format.Formatter
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import com.example.photos.data.LatLon
import com.example.photos.data.Media
import com.example.photos.ui.theme.LocalPalette
import com.example.photos.util.formatDuration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

class ExifInfo(
    val camera: String?,
    val aperture: String?,
    val shutter: String?,
    val ev: String?,
    val iso: String?,
    val focal: String?,
    val location: LatLon?,
)

private fun trimNumber(d: Double): String =
    if (d % 1.0 == 0.0) d.toInt().toString() else String.format(Locale.US, "%.1f", d)

private fun readExifBlocking(context: Context, m: Media, fallback: LatLon?): ExifInfo {
    val empty = ExifInfo(null, null, null, null, null, null, fallback)
    if (m.isVideo) return empty
    return runCatching {
        val hasLoc = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_MEDIA_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        val uri = if (hasLoc) MediaStore.setRequireOriginal(m.uri) else m.uri
        context.contentResolver.openInputStream(uri)?.use { s ->
            val e = ExifInterface(s)
            val f = e.getAttributeDouble(ExifInterface.TAG_F_NUMBER, 0.0).takeIf { it > 0 }
            val t = e.getAttributeDouble(ExifInterface.TAG_EXPOSURE_TIME, 0.0).takeIf { it > 0 }
            val ev = e.getAttributeDouble(ExifInterface.TAG_EXPOSURE_BIAS_VALUE, Double.NaN)
            val iso = e.getAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY)
            val f35 = e.getAttributeInt(ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM, 0)
            val fl = e.getAttributeDouble(ExifInterface.TAG_FOCAL_LENGTH, 0.0)
            val make = e.getAttribute(ExifInterface.TAG_MAKE)?.trim().orEmpty()
            val model = e.getAttribute(ExifInterface.TAG_MODEL)?.trim().orEmpty()
            val camera = when {
                model.isEmpty() -> make
                make.isNotEmpty() && !model.startsWith(make, ignoreCase = true) -> "$make $model"
                else -> model
            }.ifBlank { null }
            ExifInfo(
                camera = camera,
                aperture = f?.let { "ƒ " + trimNumber(it) },
                shutter = t?.let { if (it < 1) "S 1/${(1 / it).roundToInt()}" else "S ${trimNumber(it)}s" },
                ev = if (ev.isNaN()) null else String.format(Locale.US, "%+.1f EV", ev),
                iso = iso?.let { "ISO $it" },
                focal = when {
                    f35 > 0 -> "${f35}MM"
                    fl > 0 -> "${fl.roundToInt()}MM"
                    else -> null
                },
                location = e.latLong?.let { LatLon(it[0], it[1]) } ?: fallback,
            )
        }
    }.getOrNull() ?: empty
}

private fun typeLabel(m: Media): String = when {
    m.isVideo -> "VIDEO"
    m.isRaw -> "RAW"
    m.isGif -> "GIF"
    m.isScreenshot -> "SCREENSHOT"
    else -> "PHOTO"
}

private fun coordinate(ll: LatLon): String = String.format(
    Locale.US, "%.3f°%s, %.3f°%s",
    abs(ll.lat), if (ll.lat >= 0) "N" else "S",
    abs(ll.lon), if (ll.lon >= 0) "E" else "W",
)

/** Photo info screen in the Nothing-phone layout: hero photo, serif weekday, info card, path + name cards. */
@Composable
fun DetailsScreen(media: Media, indexedLocation: LatLon?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val p = LocalPalette.current
    val exif by produceState<ExifInfo?>(null, media.key) {
        value = withContext(Dispatchers.IO) { readExifBlocking(context, media, indexedLocation) }
    }
    val locale = Locale.getDefault()
    val zdt = remember(media.key) { Instant.ofEpochMilli(media.dateTaken).atZone(ZoneId.systemDefault()) }
    val info = exif
    val location = info?.location

    CompositionLocalProvider(LocalContentColor provides p.text) {
        Column(
            Modifier.fillMaxSize().background(p.background).verticalScroll(rememberScrollState()),
        ) {
            Box(Modifier.fillMaxWidth().height(400.dp)) {
                MediaThumbnail(media, 1080, Modifier.fillMaxSize())
                Box(
                    Modifier.fillMaxWidth().height(170.dp).align(Alignment.BottomCenter)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, p.background))),
                )
                RoundIconButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(12.dp),
                ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Close") }
            }

            Column(
                Modifier.padding(horizontal = 16.dp).navigationBarsPadding().padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    zdt.format(DateTimeFormatter.ofPattern("EEEE", locale)),
                    fontFamily = if (p.titleFont == FontFamily.Serif) FontFamily.Serif else p.titleFont,
                    fontSize = 26.sp,
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(zdt.format(DateTimeFormatter.ofPattern("d MMMM yyyy", locale)), fontSize = 13.sp)
                    Box(Modifier.width(1.dp).height(12.dp).background(p.muted))
                    Text(zdt.format(DateTimeFormatter.ofPattern("HH:mm", locale)), fontSize = 13.sp)
                }

                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(p.card)) {
                    if (location != null) {
                        MapBlock(location)
                        HorizontalDivider(color = p.text.copy(alpha = 0.08f))
                    }
                    val shots = listOfNotNull(info?.aperture, info?.shutter, info?.ev, info?.iso, info?.focal)
                    if (shots.isNotEmpty()) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) { shots.forEach { Text(it, fontSize = 11.sp, color = p.text) } }
                        HorizontalDivider(color = p.text.copy(alpha = 0.08f))
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(typeLabel(media), fontSize = 11.sp, color = p.muted)
                        if (media.width > 0 && media.height > 0) {
                            Text("${media.width} × ${media.height}", fontSize = 11.sp)
                        } else if (media.isVideo) {
                            Text(formatDuration(media.durationMs), fontSize = 11.sp)
                        }
                        Text(Formatter.formatShortFileSize(context, media.size), fontSize = 11.sp)
                    }
                    HorizontalDivider(color = p.text.copy(alpha = 0.08f))
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            (info?.camera ?: media.folderName.ifEmpty { "DEVICE" }).uppercase(),
                            fontSize = 11.sp, color = p.muted, modifier = Modifier.weight(1f), maxLines = 1,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Chip(media.name.substringAfterLast('.', "").uppercase().ifEmpty { "FILE" })
                            if (media.width > 0 && media.height > 0) {
                                Chip(String.format(Locale.US, "%.1fMP", media.width.toDouble() * media.height / 1_000_000.0))
                            }
                        }
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    InfoCard("Storage path", "Phone/" + media.relativePath + media.name, Modifier.weight(1f))
                    InfoCard("Name", media.name, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun Chip(text: String) {
    val p = LocalPalette.current
    Text(
        text, fontSize = 10.sp, color = p.text,
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(p.text.copy(alpha = 0.14f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
private fun InfoCard(label: String, value: String, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    Column(
        modifier.clip(RoundedCornerShape(18.dp)).background(p.card).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(label, fontSize = 11.sp, color = p.text)
        Text(value, fontSize = 11.sp, color = p.muted)
    }
}

/** Stylised map card (no map tiles are downloaded; coordinates come from the photo's EXIF). */
@Composable
private fun MapBlock(ll: LatLon) {
    val p = LocalPalette.current
    val road = p.text.copy(alpha = 0.10f)
    Box(
        Modifier.fillMaxWidth().height(116.dp).background(p.cardHigh),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            drawLine(road, Offset(0f, h * 0.78f), Offset(w, h * 0.18f), strokeWidth = 14f)
            drawLine(road, Offset(w * 0.15f, 0f), Offset(w * 0.55f, h), strokeWidth = 9f)
            drawLine(road, Offset(w * 0.62f, 0f), Offset(w * 0.95f, h), strokeWidth = 12f)
            drawLine(road, Offset(0f, h * 0.3f), Offset(w, h * 0.55f), strokeWidth = 7f)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Filled.LocationOn, null, tint = p.accent, modifier = Modifier.height(30.dp))
            Text(coordinate(ll), fontSize = 11.sp, color = p.text)
        }
    }
}
