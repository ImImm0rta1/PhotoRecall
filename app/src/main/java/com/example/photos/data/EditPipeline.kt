package com.example.photos.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.Shader
import android.provider.MediaStore
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

enum class EditFilter(val label: String) {
    NONE("Original"), VIVID("Vivid"), WARM("Warm"), COOL("Cool"),
    MONO("Mono"), NOIR("Noir"), SEPIA("Sepia"), FADE("Fade"), DRAMA("Drama"),
}

/** Normalised rectangle (0..1) inside the pre-crop "frame". */
data class NRect(val l: Float, val t: Float, val r: Float, val b: Float) {
    val w get() = r - l
    val h get() = b - t
}

/** Points are normalised to the pre-crop frame; width is a fraction of the frame's shorter side. */
data class Stroke(val points: List<Offset>, val color: Int, val width: Float)

/** Centre (x, y) normalised to the pre-crop frame; size is a fraction of the frame height. */
data class TextItem(val id: Long, val text: String, val x: Float, val y: Float, val color: Int, val size: Float)

/**
 * Non-destructive edit recipe. The original file is never touched: the result is always saved as a copy.
 * Geometry order is: mirror (if [flip]) -> rotate [turns] x 90 deg clockwise -> straighten -> crop.
 */
data class EditState(
    val turns: Int = 0,
    val flip: Boolean = false,
    val straighten: Float = 0f,
    val crop: NRect = NRect(0f, 0f, 1f, 1f),
    val filter: EditFilter = EditFilter.NONE,
    val brightness: Float = 0f,
    val contrast: Float = 0f,
    val saturation: Float = 0f,
    val warmth: Float = 0f,
    val vignette: Float = 0f,
    val strokes: List<Stroke> = emptyList(),
    val texts: List<TextItem> = emptyList(),
    val resizePercent: Int = 100,
) {
    val isModified: Boolean
        get() = this != EditState().copy(resizePercent = resizePercent)

    // ---- visual operations that also keep markup + crop aligned ----

    private fun mapped(f: (Offset) -> Offset, newTurns: Int, newFlip: Boolean): EditState {
        val a = f(Offset(crop.l, crop.t))
        val b = f(Offset(crop.r, crop.b))
        return copy(
            turns = ((newTurns % 4) + 4) % 4,
            flip = newFlip,
            crop = NRect(min(a.x, b.x), min(a.y, b.y), max(a.x, b.x), max(a.y, b.y)),
            strokes = strokes.map { s -> s.copy(points = s.points.map(f)) },
            texts = texts.map { t -> f(Offset(t.x, t.y)).let { t.copy(x = it.x, y = it.y) } },
        )
    }

    fun rotate(clockwise: Boolean): EditState =
        if (clockwise) mapped({ Offset(1f - it.y, it.x) }, turns + 1, flip)
        else mapped({ Offset(it.y, 1f - it.x) }, turns - 1, flip)

    /** Mirror left-right as the user sees it. */
    fun mirrorHorizontal(): EditState = mapped({ Offset(1f - it.x, it.y) }, -turns, !flip)

    /** Mirror top-bottom as the user sees it. */
    fun mirrorVertical(): EditState = mapped({ Offset(it.x, 1f - it.y) }, 2 - turns, !flip)
}

object EditPipeline {

    fun loadBitmap(context: Context, media: Media, maxSide: Int): Bitmap? = runCatching {
        val src = ImageDecoder.createSource(context.contentResolver, media.uri)
        ImageDecoder.decodeBitmap(src) { dec, info, _ ->
            dec.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val longest = max(info.size.width, info.size.height)
            if (longest > maxSide) {
                val s = maxSide.toFloat() / longest
                dec.setTargetSize((info.size.width * s).roundToInt(), (info.size.height * s).roundToInt())
            }
        }
    }.getOrNull()

    private fun geometry(src: Bitmap, st: EditState): Bitmap {
        if (st.turns == 0 && !st.flip) return src
        val m = Matrix()
        if (st.flip) m.postScale(-1f, 1f)
        m.postRotate(90f * st.turns)
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
    }

    /** Rotates by [angle] degrees and zooms just enough that no empty corners remain. */
    private fun straightened(geo: Bitmap, angle: Float): Bitmap {
        if (abs(angle) < 0.05f) return geo
        val w = geo.width.toFloat()
        val h = geo.height.toFloat()
        val rad = Math.toRadians(abs(angle).toDouble())
        val c = cos(rad).toFloat()
        val s = sin(rad).toFloat()
        val zoom = max(c + (h / w) * s, c + (w / h) * s)
        val out = Bitmap.createBitmap(geo.width, geo.height, Bitmap.Config.ARGB_8888)
        val m = Matrix()
        m.postTranslate(-w / 2f, -h / 2f)
        m.postRotate(angle)
        m.postScale(zoom, zoom)
        m.postTranslate(w / 2f, h / 2f)
        Canvas(out).drawBitmap(geo, m, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        return out
    }

    /** The pre-crop frame: geometry + straighten applied. May return [src] itself. */
    fun frame(src: Bitmap, st: EditState): Bitmap {
        val geo = geometry(src, st)
        val out = straightened(geo, st.straighten)
        if (geo !== src && out !== geo) geo.recycle()
        return out
    }

    private fun colorMatrix(st: EditState): ColorMatrix {
        val cm = ColorMatrix()
        when (st.filter) {
            EditFilter.NONE -> {}
            EditFilter.VIVID -> { cm.postConcat(ColorMatrix().apply { setSaturation(1.35f) }); cm.postConcat(contrastBrightness(1.1f, 0f)) }
            EditFilter.WARM -> cm.postConcat(scaleRgb(1.08f, 1.0f, 0.92f))
            EditFilter.COOL -> cm.postConcat(scaleRgb(0.92f, 1.0f, 1.1f))
            EditFilter.MONO -> cm.postConcat(ColorMatrix().apply { setSaturation(0f) })
            EditFilter.NOIR -> { cm.postConcat(ColorMatrix().apply { setSaturation(0f) }); cm.postConcat(contrastBrightness(1.35f, -8f)) }
            EditFilter.SEPIA -> cm.postConcat(
                ColorMatrix(floatArrayOf(
                    0.393f, 0.769f, 0.189f, 0f, 0f,
                    0.349f, 0.686f, 0.168f, 0f, 0f,
                    0.272f, 0.534f, 0.131f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f,
                )),
            )
            EditFilter.FADE -> { cm.postConcat(ColorMatrix().apply { setSaturation(0.8f) }); cm.postConcat(contrastBrightness(0.85f, 22f)) }
            EditFilter.DRAMA -> { cm.postConcat(ColorMatrix().apply { setSaturation(1.2f) }); cm.postConcat(contrastBrightness(1.25f, -10f)) }
        }
        cm.postConcat(ColorMatrix().apply { setSaturation(1f + st.saturation) })
        cm.postConcat(scaleRgb(1f + 0.25f * st.warmth, 1f, 1f - 0.25f * st.warmth))
        cm.postConcat(contrastBrightness(1f + st.contrast, st.brightness * 127f))
        return cm
    }

    private fun contrastBrightness(c: Float, brightness: Float): ColorMatrix {
        val t = (1f - c) * 128f + brightness
        return ColorMatrix(floatArrayOf(
            c, 0f, 0f, 0f, t,
            0f, c, 0f, 0f, t,
            0f, 0f, c, 0f, t,
            0f, 0f, 0f, 1f, 0f,
        ))
    }

    private fun scaleRgb(r: Float, g: Float, b: Float) = ColorMatrix(floatArrayOf(
        r, 0f, 0f, 0f, 0f,
        0f, g, 0f, 0f, 0f,
        0f, 0f, b, 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
    ))

    /** Crop + colour + vignette + markup. [withTexts] is false for the live preview (texts are overlays there). */
    fun render(frame: Bitmap, st: EditState, withTexts: Boolean, scale: Float = 1f): Bitmap {
        val fw = frame.width.toFloat()
        val fh = frame.height.toFloat()
        val cl = (st.crop.l * fw).roundToInt().coerceIn(0, frame.width - 1)
        val ct = (st.crop.t * fh).roundToInt().coerceIn(0, frame.height - 1)
        val cw = (st.crop.w * fw).roundToInt().coerceIn(1, frame.width - cl)
        val ch = (st.crop.h * fh).roundToInt().coerceIn(1, frame.height - ct)
        val outW = max(1, (cw * scale).roundToInt())
        val outH = max(1, (ch * scale).roundToInt())
        val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)

        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        paint.colorFilter = ColorMatrixColorFilter(colorMatrix(st))
        canvas.drawBitmap(frame, Rect(cl, ct, cl + cw, ct + ch), Rect(0, 0, outW, outH), paint)

        if (st.vignette > 0f) {
            val radius = hypot(outW.toFloat(), outH.toFloat()) / 2f
            val edge = Color.argb((st.vignette * 210).roundToInt().coerceIn(0, 255), 0, 0, 0)
            val shader = RadialGradient(
                outW / 2f, outH / 2f, radius,
                intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, edge),
                floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP,
            )
            canvas.drawRect(0f, 0f, outW.toFloat(), outH.toFloat(), Paint().apply { this.shader = shader })
        }

        val kx = outW / cw.toFloat()
        val ky = outH / ch.toFloat()
        fun mx(x: Float) = (x * fw - cl) * kx
        fun my(y: Float) = (y * fh - ct) * ky

        for (s in st.strokes) {
            if (s.points.isEmpty()) continue
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                color = s.color
                strokeWidth = s.width * min(fw, fh) * kx
            }
            val path = Path()
            val first = s.points.first()
            path.moveTo(mx(first.x), my(first.y))
            if (s.points.size == 1) path.lineTo(mx(first.x) + 0.1f, my(first.y))
            s.points.drop(1).forEach { path.lineTo(mx(it.x), my(it.y)) }
            canvas.drawPath(path, p)
        }

        if (withTexts) {
            for (t in st.texts) {
                val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = t.color
                    textAlign = Paint.Align.CENTER
                    textSize = t.size * fh * ky
                    setShadowLayer(textSize * 0.06f, 0f, 0f, Color.argb(120, 0, 0, 0))
                }
                val fm = p.fontMetrics
                canvas.drawText(t.text, mx(t.x), my(t.y) - (fm.ascent + fm.descent) / 2f, p)
            }
        }
        return out
    }

    /** Full-resolution render, saved as a NEW file next to the original. */
    suspend fun saveCopy(context: Context, media: Media, st: EditState): Boolean = withContext(Dispatchers.IO) {
        val base = loadBitmap(context, media, 4096) ?: return@withContext false
        val frame = frame(base, st)
        if (frame !== base) base.recycle()
        val out = render(frame, st, withTexts = true, scale = st.resizePercent / 100f)
        frame.recycle()

        val png = media.mime.equals("image/png", ignoreCase = true)
        val ext = if (png) "png" else "jpg"
        val path = if (media.relativePath.startsWith("DCIM/") || media.relativePath.startsWith("Pictures/")) {
            media.relativePath
        } else "Pictures/Edited/"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, media.name.substringBeforeLast('.', media.name) + "_edited.$ext")
            put(MediaStore.MediaColumns.MIME_TYPE, if (png) "image/png" else "image/jpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, path)
            if (media.dateTaken > 0) put(MediaStore.MediaColumns.DATE_TAKEN, media.dateTaken)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val dest = runCatching {
            resolver.insert(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)
        }.getOrNull()
        if (dest == null) { out.recycle(); return@withContext false }
        try {
            resolver.openOutputStream(dest)?.use {
                out.compress(if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, 95, it)
            } ?: error("no output")
            resolver.update(dest, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            true
        } catch (e: Exception) {
            runCatching { resolver.delete(dest, null, null) }
            false
        } finally {
            out.recycle()
        }
    }
}
