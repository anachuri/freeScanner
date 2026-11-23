package com.app.freescanner.ui

import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.app.freescanner.platform.CameraController

/**
 * Vista previa de cámara Android (CameraX PreviewView).
 * Solo existe en androidMain; la UI compartida la recibe como slot.
 */
@Composable
fun CameraPreview(controller: CameraController, modifier: Modifier = Modifier) {
    DisposableEffect(controller) {
        onDispose { controller.detachPreview() }
    }
    AndroidView(
        factory = { context ->
            PreviewView(context).also { controller.attachPreview(it) }
        },
        modifier = modifier,
    )
}
