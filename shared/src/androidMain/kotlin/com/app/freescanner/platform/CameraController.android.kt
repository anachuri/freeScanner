package com.app.freescanner.platform

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.ProcessLifecycleOwner
import com.app.freescanner.android.rotated
import com.app.freescanner.android.toImageData

import com.app.freescanner.model.ImageData

/**
 * Implementación Android de la cámara con CameraX.
 * Se vincula al ciclo de vida del proceso (ProcessLifecycleOwner) para no
 * depender de una Activity concreta.
 */
actual class CameraController actual constructor() {

    private val context: Context = requireContext()
    private val mainExecutor = ContextCompat.getMainExecutor(context)
    private var provider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null

    actual fun attachPreview(view: Any) {
        val previewView = view as? PreviewView ?: return
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            try {
                val cameraProvider = future.get()
                provider = cameraProvider

                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }
                val capture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                    .build()
                imageCapture = capture

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    ProcessLifecycleOwner.get(),
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    capture,
                )
            } catch (_: Exception) {
                // Sin cámara disponible o permiso denegado: capturePhoto devolverá null.
            }
        }, mainExecutor)
    }

    actual fun capturePhoto(onResult: (ImageData?) -> Unit) {
        val capture = imageCapture
        if (capture == null) {
            onResult(null)
            return
        }
        capture.takePicture(mainExecutor, object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                try {
                    val bitmap = image.toBitmap()
                    val rotated = bitmap.rotated(image.imageInfo.rotationDegrees)
                    onResult(rotated.toImageData())
                } catch (_: Exception) {
                    onResult(null)
                } finally {
                    image.close()
                }
            }

            override fun onError(exception: ImageCaptureException) {
                onResult(null)
            }
        })
    }

    actual fun detachPreview() {
        provider?.unbindAll()
    }
}
