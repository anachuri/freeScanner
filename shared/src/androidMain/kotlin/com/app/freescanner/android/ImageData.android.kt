package com.app.freescanner.android

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import com.app.freescanner.model.ImageData
import java.io.ByteArrayOutputStream

/** ImageData (RGBA8888) -> android.graphics.Bitmap ARGB_8888. */
fun ImageData.toBitmap(): Bitmap {
    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val pixels = IntArray(width * height)
    var i = 0
    var o = 0
    while (o < data.size) {
        val r = data[o].toInt() and 0xFF
        val g = data[o + 1].toInt() and 0xFF
        val b = data[o + 2].toInt() and 0xFF
        pixels[i++] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        o += 4
    }
    bmp.setPixels(pixels, 0, width, 0, 0, width, height)
    return bmp
}

/** android.graphics.Bitmap -> ImageData (RGBA8888). */
fun Bitmap.toImageData(): ImageData {
    val w = width
    val h = height
    val argb = IntArray(w * h)
    getPixels(argb, 0, w, 0, 0, w, h)
    val out = ByteArray(w * h * 4)
    var o = 0
    for (px in argb) {
        out[o++] = ((px shr 16) and 0xFF).toByte() // R
        out[o++] = ((px shr 8) and 0xFF).toByte()  // G
        out[o++] = (px and 0xFF).toByte()          // B
        out[o++] = ((px shr 24) and 0xFF).toByte() // A
    }
    return ImageData(w, h, out)
}

/** Decodifica una imagen desde un content:// Uri, limitando la resolución máxima. */
fun loadImageData(context: Context, uri: Uri, maxDim: Int = 2560): ImageData? {
    return try {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxDim) sample *= 2

        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            ?: return null
        bmp.toImageData()
    } catch (e: Exception) {
        null
    }
}

fun Bitmap.compressToPng(): ByteArray {
    val out = ByteArrayOutputStream()
    compress(Bitmap.CompressFormat.PNG, 100, out)
    return out.toByteArray()
}

/** Rota un bitmap según los grados indicados por CameraX. */
fun Bitmap.rotated(degrees: Int): Bitmap {
    if (degrees == 0) return this
    val m = Matrix().apply { postRotate(degrees.toFloat()) }
    return Bitmap.createBitmap(this, 0, 0, width, height, m, true)
}
