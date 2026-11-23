package com.app.freescanner.platform

import android.content.Context
import com.app.freescanner.android.toBitmap

import com.app.freescanner.model.ImageData
import com.googlecode.tesseract.android.TessBaseAPI
import java.io.File
import java.io.FileOutputStream

/**
 * OCR on-device con Tesseract (tess-two / Tesseract4Android).
 * Los archivos `*.traineddata` se copian desde assets/tessdata al almacenamiento
 * interno la primera vez que se usa el motor.
 */
actual class OcrEngine actual constructor() {

    private var api: TessBaseAPI? = null
    private var initFailed = false

    private fun ensureInit(language: String): Boolean {
        api?.let { return true }
        if (initFailed) return false
        return try {
            val ctx = requireContext()
            copyTrainedData(ctx)
            val t = TessBaseAPI()
            if (t.init(ctx.filesDir.absolutePath, language)) {
                api = t
                true
            } else {
                t.recycle()
                initFailed = true
                false
            }
        } catch (_: Exception) {
            initFailed = true
            false
        }
    }

    private fun copyTrainedData(ctx: Context) {
        val outDir = File(ctx.filesDir, "tessdata")
        outDir.mkdirs()
        ctx.assets.list("tessdata")?.forEach { name ->
            val out = File(outDir, name)
            if (!out.exists()) {
                ctx.assets.open("tessdata/$name").use { input ->
                    FileOutputStream(out).use { output -> input.copyTo(output) }
                }
            }
        }
    }

    actual fun recognizeText(image: ImageData, language: String): String {
        if (!ensureInit(language)) return ""
        val engine = api ?: return ""
        return try {
            engine.setImage(image.toBitmap())
            engine.getUTF8Text() ?: ""
        } catch (_: Exception) {
            ""
        }
    }

    actual fun release() {
        api?.recycle()
        api = null
    }
}
