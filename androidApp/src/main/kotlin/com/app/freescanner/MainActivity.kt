package com.app.freescanner

import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.app.freescanner.android.loadImageData
import com.app.freescanner.platform.CameraController
import com.app.freescanner.platform.platformInit
import com.app.freescanner.ui.App
import com.app.freescanner.ui.CameraPreview
import com.app.freescanner.ui.CameraSession
import com.app.freescanner.ui.ScannerViewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        platformInit(this)

        setContent {
            val context = LocalContext.current
            val viewModel = remember { ScannerViewModel() }
            val cameraController = remember { CameraController() }

            var hasCameraPermission by remember {
                mutableStateOf(
                    ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) ==
                        PackageManager.PERMISSION_GRANTED,
                )
            }
            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission(),
            ) { granted -> hasCameraPermission = granted }

            val pickImageLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.GetContent(),
            ) { uri ->
                if (uri != null) {
                    loadImageData(context, uri)?.let { viewModel.onImageSelected(it) }
                }
            }

            App(
                viewModel = viewModel,
                cameraSession = CameraSession(
                    controller = cameraController,
                    hasPermission = hasCameraPermission,
                    onRequestPermission = {
                        permissionLauncher.launch(android.Manifest.permission.CAMERA)
                    },
                    preview = { modifier -> CameraPreview(cameraController, modifier) },
                ),
                onPickImage = { pickImageLauncher.launch("image/*") },
            )
        }
    }
}
