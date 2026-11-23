package com.app.freescanner

import androidx.compose.ui.window.ComposeUIViewController
import com.app.freescanner.ui.App
import com.app.freescanner.ui.ScannerViewModel

fun MainViewController() = ComposeUIViewController {
    // FASE 2 - iOS: cameraSession = null hasta implementar AVFoundation.
    App(viewModel = ScannerViewModel(), cameraSession = null, onPickImage = null)
}
