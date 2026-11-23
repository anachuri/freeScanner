package com.app.freescanner.platform

import androidx.compose.ui.graphics.ImageBitmap
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.app.freescanner.db.FreeScannerDatabase
import com.app.freescanner.model.ImageData
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

private var driver: SqlDriver? = null

actual fun platformInit(context: Any?) = Unit

actual fun appFilesDir(): String {
    val dirs = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true)
    return (dirs.firstOrNull() as? String) ?: NSTemporaryDirectoryCompat()
}

private fun NSTemporaryDirectoryCompat(): String =
    platform.Foundation.NSTemporaryDirectory()

actual fun currentTimeMillis(): Long =
    (platform.Foundation.NSDate().timeIntervalSince1970 * 1000).toLong()

actual fun sqlDriver(): SqlDriver {
    return driver ?: synchronized(this) {
        driver ?: NativeSqliteDriver(
            schema = FreeScannerDatabase.Schema,
            name = "freescanner.db",
        ).also { driver = it }
    }
}

// FASE 2 - iOS: convertir ImageData (RGBA) a UIImage/PixelMap para Compose.
actual fun imageDataToBitmap(image: ImageData): ImageBitmap =
    TODO("FASE 2 - iOS: implementar conversión ImageData -> ImageBitmap")
