package com.app.freescanner.platform

import com.app.freescanner.model.ImageData
import com.app.freescanner.model.Quadrilateral

// FASE 2 - iOS: implementar con Vision framework (VNDetectRectanglesRequest /
// VNDetectDocumentSegmentationRequest) o reutilizar OpenCV2.framework para
// mantener paridad con el pipeline de Android.
actual class DocumentScanner actual constructor() {

    actual fun detectEdges(image: ImageData): Quadrilateral? =
        TODO("FASE 2 - iOS: implementar detección de bordes con Vision framework u OpenCV2.framework")

    actual fun applyPerspectiveCorrection(image: ImageData, corners: Quadrilateral): ImageData =
        TODO("FASE 2 - iOS: implementar corrección de perspectiva con Vision framework u OpenCV2.framework")

    actual fun applyScanFilter(image: ImageData): ImageData =
        TODO("FASE 2 - iOS: implementar filtro de escaneo con Vision framework u OpenCV2.framework")
}
