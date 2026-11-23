package com.app.freescanner.platform

import com.app.freescanner.android.toBitmap
import com.app.freescanner.model.ImageData
import kotlin.math.min
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory

/**
 * Exportación a PDF con PDFBox-Android. Cada página se incrusta como imagen
 * ajustada a una página A4 sin deformar.
 */
actual class PdfExporter actual constructor() {

    actual fun exportToPdf(pages: List<ImageData>, outputPath: String): Boolean {
        if (pages.isEmpty()) return false
        return try {
            PDFBoxResourceLoader.init(requireContext())
            val doc = PDDocument()
            try {
                for (image in pages) {
                    val bitmap = image.toBitmap()
                    val pdImage = LosslessFactory.createFromImage(doc, bitmap)

                    val page = PDPage()
                    doc.addPage(page)

                    PDPageContentStream(doc, page).use { cs ->
                        val pw = page.mediaBox.width
                        val ph = page.mediaBox.height
                        val scale = min(pw / pdImage.width, ph / pdImage.height)
                        val dw = pdImage.width * scale
                        val dh = pdImage.height * scale
                        cs.drawImage(pdImage, (pw - dw) / 2f, (ph - dh) / 2f, dw, dh)
                    }
                }
                doc.save(outputPath)
            } finally {
                doc.close()
            }
            true
        } catch (_: Exception) {
            false
        }
    }
}
