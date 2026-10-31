package com.catdaily.app.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Base64
import android.util.LruCache
import com.catdaily.app.data.Region
import com.catdaily.app.data.Visual
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Draws questions exactly as printed by cropping horizontal strips out of the PDF pages.
 * This keeps tables, charts, exponents and fractions intact (plain text extraction loses them).
 */
class PdfCropRenderer(private val ctx: Context) {
    private val renderers = HashMap<String, Pair<ParcelFileDescriptor, PdfRenderer>>()
    private val cache = object : LruCache<String, Bitmap>(48 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    private fun fileFor(pdf: String): File {
        if (pdf.startsWith("file:")) return File(pdf.removePrefix("file:"))
        val out = File(ctx.filesDir, pdf)            // copy bundled asset once
        if (!out.exists()) {
            out.parentFile?.mkdirs()
            ctx.assets.open(pdf).use { i -> out.outputStream().use { i.copyTo(it) } }
        }
        return out
    }

    fun canRender(v: Visual) = v.pdf != null && (!v.pdf.startsWith("file:") || File(v.pdf.removePrefix("file:")).exists())

    @Synchronized
    private fun renderer(pdf: String): PdfRenderer = renderers.getOrPut(pdf) {
        val fd = ParcelFileDescriptor.open(fileFor(pdf), ParcelFileDescriptor.MODE_READ_ONLY)
        fd to PdfRenderer(fd)
    }.second

    /** Renders one strip at [widthPx] wide. */
    @Synchronized
    fun render(pdf: String, r: Region, widthPx: Int): Bitmap {
        val key = "$pdf|${r.page}|${r.y0}|${r.y1}|$widthPx"
        cache.get(key)?.let { return it }
        val pr = renderer(pdf)
        val page = pr.openPage(r.page)
        try {
            val scale = widthPx / page.width.toFloat()
            val h = ((r.y1 - r.y0) * scale).toInt().coerceAtLeast(1)
            val bmp = Bitmap.createBitmap(widthPx, h, Bitmap.Config.ARGB_8888)
            bmp.eraseColor(Color.WHITE)
            val m = Matrix().apply { postScale(scale, scale); postTranslate(0f, -r.y0 * scale) }
            page.render(bmp, null, m, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            cache.put(key, bmp)
            return bmp
        } finally {
            page.close()
        }
    }

    /** All strips of a visual, from the PDF when present, else from synced images. */
    fun strips(v: Visual, widthPx: Int): List<Bitmap> =
        if (canRender(v)) v.regions.map { render(v.pdf!!, it, widthPx) }
        else v.images.map { decode(it) }

    private fun decode(b64: String): Bitmap {
        cache.get(b64.hashCode().toString())?.let { return it }
        val bytes = Base64.decode(b64, Base64.DEFAULT)
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size).also { cache.put(b64.hashCode().toString(), it) }
    }

    /** For cloud upload: strips encoded as compact WEBP base64 (fits inside a Firestore document). */
    fun encodeStrips(v: Visual, widthPx: Int = 900, quality: Int = 55): List<String> =
        v.regions.map { r ->
            val bmp = render(v.pdf!!, r, widthPx)
            val bos = ByteArrayOutputStream()
            @Suppress("DEPRECATION")
            val fmt = if (android.os.Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP
            bmp.compress(fmt, quality, bos)
            Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP)
        }
}
