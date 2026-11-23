package com.app.freescanner.platform

import androidx.compose.ui.graphics.ImageBitmap
import app.cash.sqldelight.db.SqlDriver
import com.app.freescanner.model.ImageData
import com.app.freescanner.model.Quadrilateral

/**
 * Pipeline de escaneo de documentos. La lógica pesada vive en las
 * implementaciones `actual` de cada plataforma (OpenCV en Android,
 * Vision framework u OpenCV2 en iOS - FASE 2).
 */
expect class DocumentScanner() {
    /**
     * Detecta los bordes del documento y devuelve el cuadrilátero de 4 vértices
     * en coordenadas de píxel de [image], o null si no se encuentra.
     */
    fun detectEdges(image: ImageData): Quadrilateral?

    /**
     * Aplica corrección de perspectiva: endereza el documento según [corners]
     * y devuelve la imagen rectangular resultante.
     */
    fun applyPerspectiveCorrection(image: ImageData, corners: Quadrilateral): ImageData

    /**
     * Aplica el filtro "escaneado" (blanco y negro / mejora de contraste).
     */
    fun applyScanFilter(image: ImageData): ImageData
}

/**
 * Controlador de cámara. En Android usa CameraX; en iOS se implementará con
 * AVFoundation - FASE 2.
 *
 * [attachPreview] recibe una vista nativa de preview (en Android, un
 * `androidx.camera.view.PreviewView`), tipada como `Any` para mantener la
 * firma multiplataforma.
 */
expect class CameraController() {
    fun attachPreview(view: Any)
    fun capturePhoto(onResult: (ImageData?) -> Unit)
    fun detachPreview()
}

/**
 * Motor de OCR on-device. En Android usa Tesseract (tess-two); en iOS se
 * evaluará TesseractOCR vía CocoaPods - FASE 2.
 */
expect class OcrEngine() {
    fun recognizeText(image: ImageData, language: String = "spa"): String
    fun release()
}

/**
 * Exportador a PDF. En Android usa PDFBox-Android; en iOS se evaluará una
 * alternativa multiplataforma o `actual` específico - FASE 2.
 */
expect class PdfExporter() {
    fun exportToPdf(pages: List<ImageData>, outputPath: String): Boolean
}

/** Directorio de archivos privados de la app, para persistir páginas en crudo. */
expect fun appFilesDir(): String

/** Driver SQLDelight de la plataforma. */
expect fun sqlDriver(): SqlDriver

/** Convierte [ImageData] a un bitmap componible para mostrar en la UI. */
expect fun imageDataToBitmap(image: ImageData): ImageBitmap

/** Tiempo actual en milisegundos desde epoch. */
expect fun currentTimeMillis(): Long

/**
 * Inicializa el contexto de plataforma. En Android se llama una vez desde la
 * Activity con `this`; en iOS no requiere acción.
 */
expect fun platformInit(context: Any?)
