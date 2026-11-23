package com.app.freescanner.platform

import com.app.freescanner.model.ImageData

// FASE 2 - iOS: evaluar TesseractOCR vía CocoaPods/SPM o Vision framework
// (VNRecognizeTextRequest) que es on-device y no requiere binarios extra.
actual class OcrEngine actual constructor() {

    actual fun recognizeText(image: ImageData, language: String): String =
        TODO("FASE 2 - iOS: implementar OCR con TesseractOCR (CocoaPods) o Vision framework")

    actual fun release() = Unit
}
