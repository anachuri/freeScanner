package com.app.freescanner.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.app.freescanner.platform.CameraController

/**
 * Sesión de cámara provista por la plataforma (Android: CameraX + PreviewView).
 * En iOS será null hasta implementar AVFoundation - FASE 2.
 */
class CameraSession(
    val controller: CameraController,
    val hasPermission: Boolean,
    val onRequestPermission: () -> Unit,
    val preview: @Composable (Modifier) -> Unit,
)

@Composable
fun App(
    viewModel: ScannerViewModel,
    cameraSession: CameraSession?,
    onPickImage: (() -> Unit)?,
) {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize()) {
                when (viewModel.screen) {
                    ScannerViewModel.Screen.DOCUMENTS -> DocumentsScreen(viewModel)
                    ScannerViewModel.Screen.CAMERA -> CameraScreen(viewModel, cameraSession, onPickImage)
                    ScannerViewModel.Screen.REVIEW -> ReviewScreen(viewModel)
                    ScannerViewModel.Screen.DOCUMENT_PAGES -> DocumentPagesScreen(viewModel)
                }

                if (viewModel.isProcessing) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxSize())
                }

                if (viewModel.status.isNotEmpty()) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .statusBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.inverseSurface,
                        shadowElevation = 4.dp,
                    ) {
                        Text(
                            text = viewModel.status,
                            color = MaterialTheme.colorScheme.inverseOnSurface,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        )
                    }
                }
            }
        }
    }
}
