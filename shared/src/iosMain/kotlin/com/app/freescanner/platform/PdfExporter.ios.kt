package com.app.freescanner.platform

import com.app.freescanner.model.ImageData

// FASE 2 - iOS: evaluar una librería PDF multiplataforma (p.ej. PDF4KMP) o un
// `actual` específico con PDFKit de Apple.
actual class PdfExporter actual constructor() {

    actual fun exportToPdf(pages: List<ImageData>, outputPath: String): Boolean =
        TODO("FASE 2 - iOS: implementar exportación a PDF (PDFKit o librería KMP)")
}
