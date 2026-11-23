package com.app.freescanner.platform

import android.content.Context
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import app.cash.sqldelight.db.SqlDriver
import com.app.freescanner.android.toBitmap
import com.app.freescanner.db.FreeScannerDatabase
import com.app.freescanner.model.ImageData

private var appContext: Context? = null
private val driverLock = Any()
private var driver: SqlDriver? = null

/** Contexto de aplicación registrado por [platformInit] (llamado desde la Activity). */
internal fun requireContext(): Context =
    appContext ?: error("platformInit() no fue llamado antes de usar servicios de plataforma")

actual fun platformInit(context: Any?) {
    appContext = context as? Context
}

actual fun appFilesDir(): String = requireContext().filesDir.absolutePath

actual fun currentTimeMillis(): Long = System.currentTimeMillis()

actual fun sqlDriver(): SqlDriver {
    return driver ?: synchronized(driverLock) {
        driver ?: AndroidSqliteDriver(
            schema = FreeScannerDatabase.Schema,
            context = requireContext(),
            name = "freescanner.db",
        ).also { driver = it }
    }
}

actual fun imageDataToBitmap(image: ImageData): ImageBitmap =
    image.toBitmap().asImageBitmap()
