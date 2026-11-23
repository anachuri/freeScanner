package com.app.freescanner.platform

import com.app.freescanner.model.ImageData

// FASE 2 - iOS: implementar con AVFoundation (AVCaptureSession) mostrando
// AVCaptureVideoPreviewLayer en la vista nativa que reciba attachPreview.
actual class CameraController actual constructor() {

    actual fun attachPreview(view: Any) =
        TODO("FASE 2 - iOS: implementar preview de cámara con AVFoundation")

    actual fun capturePhoto(onResult: (ImageData?) -> Unit) =
        TODO("FASE 2 - iOS: implementar captura de foto con AVFoundation")

    actual fun detachPreview() = Unit
}
