package com.app.freescanner.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.unit.Dp
import com.app.freescanner.export.ExportFormat
import com.app.freescanner.model.ImageData
import com.app.freescanner.model.Page
import com.app.freescanner.model.Point
import com.app.freescanner.model.Quadrilateral
import com.app.freescanner.platform.imageDataToBitmap
import kotlin.math.min
import kotlin.math.roundToInt

// ---------------------------------------------------------------------------
// Pantalla de documentos
// ---------------------------------------------------------------------------

@Composable
fun DocumentsScreen(vm: ScannerViewModel, onPickImage: (() -> Unit)?) {
    var exportFormat by remember { mutableStateOf<ExportFormat?>(null) }

    // Si se eligió un formato en "Convertir a" y se volvió aquí, abrir el diálogo.
    LaunchedEffect(vm.pendingExportFormat) {
        val pending = vm.pendingExportFormat
        if (pending != null) {
            exportFormat = pending
            vm.clearPendingExport()
        }
    }

    Box(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp),
            ) {
                Text("Mis documentos", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Escanea, importa o exporta tus documentos",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))

                QuickAction(
                    glyph = MenuGlyph.PHOTO,
                    label = "Importar imágenes",
                    container = MaterialTheme.colorScheme.secondaryContainer,
                    content = MaterialTheme.colorScheme.onSecondaryContainer,
                    onClick = { onPickImage?.invoke() },
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(20.dp))
                Text(
                    "Convertir a",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    QuickAction(
                        glyph = MenuGlyph.DOCUMENT,
                        label = "Word",
                        container = Color(0xFF2B579A),
                        content = Color.White,
                        onClick = { vm.requestConvert(ExportFormat.WORD) },
                        modifier = Modifier.weight(1f),
                    )
                    QuickAction(
                        glyph = MenuGlyph.TABLE,
                        label = "Excel",
                        container = Color(0xFF1E6B34),
                        content = Color.White,
                        onClick = { vm.requestConvert(ExportFormat.EXCEL) },
                        modifier = Modifier.weight(1f),
                    )
                    QuickAction(
                        glyph = MenuGlyph.SLIDES,
                        label = "PPTX",
                        container = Color(0xFFD24726),
                        content = Color.White,
                        onClick = { vm.requestConvert(ExportFormat.PPTX) },
                        modifier = Modifier.weight(1f),
                    )
                }

                Spacer(Modifier.height(20.dp))
                Text(
                    "Documentos",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))

                if (vm.documents.isEmpty()) {
                    Text(
                        "No hay documentos todavía.\nToca el botón de cámara para empezar.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(vm.documents, key = { it.id }) { doc ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { vm.openDocument(doc) },
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(doc.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(
                                            "${doc.pageCount} página(s)",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    TextButton(onClick = { vm.deleteDocument(doc) }) { Text("Eliminar") }
                                }
                            }
                        }
                    }
                }
            }

        }

        // Botón flotante de cámara (fijo abajo a la derecha)
        FloatingActionButton(
            onClick = { vm.startScanning() },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(16.dp),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ) {
            GlyphIcon(
                glyph = MenuGlyph.CAMERA,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(26.dp),
            )
        }
    }

    // Diálogo para elegir qué exportar
    exportFormat?.let { format ->
        ExportTargetDialog(
            vm = vm,
            format = format,
            onDismiss = { exportFormat = null },
        )
    }
}

/** Glifos disponibles para los botones del menú y el FAB. */
private enum class MenuGlyph { CAMERA, PHOTO, TABLE, DOCUMENT, SLIDES, FOLDER }

/** Icono vectorial simple dibujado con Canvas (sin dependencias de iconos). */
@Composable
private fun GlyphIcon(glyph: MenuGlyph, tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(
            width = w * 0.09f,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        )

        fun frame(l: Float, t: Float, r: Float, b: Float, rad: Float = 0.16f) =
            drawRoundRect(
                color = tint,
                topLeft = Offset(w * l, h * t),
                size = Size(w * (r - l), h * (b - t)),
                cornerRadius = CornerRadius(w * rad, h * rad),
                style = stroke,
            )

        when (glyph) {
            MenuGlyph.CAMERA -> {
                frame(0.18f, 0.32f, 0.82f, 0.80f)
                frame(0.38f, 0.20f, 0.62f, 0.36f, rad = 0.08f)
                drawCircle(color = tint, radius = w * 0.16f, center = Offset(w * 0.5f, h * 0.56f), style = stroke)
                drawCircle(color = tint, radius = w * 0.045f, center = Offset(w * 0.5f, h * 0.56f))
            }
            MenuGlyph.PHOTO -> {
                frame(0.14f, 0.18f, 0.86f, 0.82f)
                drawCircle(color = tint, radius = w * 0.07f, center = Offset(w * 0.34f, h * 0.38f))
                val mountain = Path().apply {
                    moveTo(w * 0.18f, h * 0.74f)
                    lineTo(w * 0.42f, h * 0.50f)
                    lineTo(w * 0.55f, h * 0.66f)
                    lineTo(w * 0.68f, h * 0.48f)
                    lineTo(w * 0.84f, h * 0.74f)
                }
                drawPath(mountain, color = tint, style = stroke)
            }
            MenuGlyph.TABLE -> {
                frame(0.16f, 0.20f, 0.84f, 0.80f)
                listOf(0.42f, 0.62f).forEach { x ->
                    drawLine(
                        color = tint,
                        start = Offset(w * x, h * 0.20f),
                        end = Offset(w * x, h * 0.80f),
                        strokeWidth = stroke.width,
                        cap = StrokeCap.Butt,
                    )
                }
                listOf(0.42f, 0.62f).forEach { y ->
                    drawLine(
                        color = tint,
                        start = Offset(w * 0.16f, h * y),
                        end = Offset(w * 0.84f, h * y),
                        strokeWidth = stroke.width,
                        cap = StrokeCap.Butt,
                    )
                }
            }
            MenuGlyph.DOCUMENT -> {
                frame(0.24f, 0.14f, 0.76f, 0.86f)
                drawLine(
                    color = tint,
                    start = Offset(w * 0.62f, h * 0.14f),
                    end = Offset(w * 0.76f, h * 0.28f),
                    strokeWidth = stroke.width,
                )
                drawLine(
                    color = tint,
                    start = Offset(w * 0.76f, h * 0.28f),
                    end = Offset(w * 0.62f, h * 0.28f),
                    strokeWidth = stroke.width,
                )
                listOf(0.38f, 0.52f, 0.66f).forEach { y ->
                    drawLine(
                        color = tint,
                        start = Offset(w * 0.34f, h * y),
                        end = Offset(w * 0.66f, h * y),
                        strokeWidth = stroke.width * 0.8f,
                    )
                }
            }
            MenuGlyph.SLIDES -> {
                frame(0.34f, 0.14f, 0.86f, 0.56f)
                frame(0.14f, 0.36f, 0.66f, 0.82f)
                val play = Path().apply {
                    moveTo(w * 0.38f, h * 0.46f)
                    lineTo(w * 0.38f, h * 0.72f)
                    lineTo(w * 0.58f, h * 0.59f)
                    close()
                }
                drawPath(play, color = tint)
            }
            MenuGlyph.FOLDER -> {
                // Pestaña superior
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(w * 0.22f, h * 0.14f),
                    size = Size(w * 0.34f, h * 0.18f),
                    cornerRadius = CornerRadius(w * 0.05f, h * 0.05f),
                    style = stroke,
                )
                // Cuerpo
                frame(0.16f, 0.30f, 0.84f, 0.82f, rad = 0.10f)
            }
        }
    }
}

/** Botón de la grilla de acciones rápidas. */
@Composable
private fun QuickAction(
    glyph: MenuGlyph,
    label: String,
    container: Color,
    content: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = container),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(content.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                GlyphIcon(glyph = glyph, tint = content, modifier = Modifier.size(20.dp))
            }
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = content,
                maxLines = 2,
            )
        }
    }
}

/** Diálogo para elegir el documento (o la sesión actual) a exportar. */
@Composable
private fun ExportTargetDialog(
    vm: ScannerViewModel,
    format: ExportFormat,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Exportar a ${format.extension.uppercase()}") },
        text = {
            LazyColumn {
                if (vm.workingPages.isNotEmpty()) {
                    item {
                        ExportTargetItem(
                            title = "Sesión actual",
                            subtitle = "${vm.workingPages.size} página(s) sin guardar",
                        ) {
                            vm.exportWorkingSessionAs(format)
                            onDismiss()
                        }
                    }
                }
                items(vm.documents, key = { it.id }) { doc ->
                    ExportTargetItem(
                        title = doc.name,
                        subtitle = "${doc.pageCount} página(s)",
                    ) {
                        vm.exportDocumentAs(doc, format)
                        onDismiss()
                    }
                }
                if (vm.documents.isEmpty() && vm.workingPages.isEmpty()) {
                    item {
                        Text(
                            "No hay documentos para exportar.\nEscanea o importa imágenes primero.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 12.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun ExportTargetItem(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Pantalla "Convertir a": elegir origen de las páginas
// ---------------------------------------------------------------------------

@Composable
fun ConvertScreen(
    vm: ScannerViewModel,
    onPickImage: (() -> Unit)?,
    onPickDocument: (() -> Unit)?,
) {
    val format = vm.pendingExportFormat
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(16.dp),
    ) {
        TextButton(onClick = { vm.navigateBack() }) { Text("←") }
        Spacer(Modifier.height(8.dp))

        Text(
            "Convertir a ${format?.extension?.uppercase() ?: ""}",
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Elige el origen de las páginas que quieres convertir.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ConvertSourceCard(
                glyph = MenuGlyph.CAMERA,
                title = "Escanear",
                subtitle = "Capturar páginas con la cámara",
                onClick = { vm.startScanning() },
            )
            ConvertSourceCard(
                glyph = MenuGlyph.PHOTO,
                title = "Galería",
                subtitle = "Elegir una imagen de la galería",
                enabled = onPickImage != null,
                onClick = { onPickImage?.invoke() },
            )
            ConvertSourceCard(
                glyph = MenuGlyph.FOLDER,
                title = "Dispositivo",
                subtitle = "Elegir un archivo del dispositivo",
                enabled = onPickDocument != null,
                onClick = { onPickDocument?.invoke() },
            )
        }
    }
}

@Composable
private fun ConvertSourceCard(
    glyph: MenuGlyph,
    title: String,
    subtitle: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                GlyphIcon(
                    glyph = glyph,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text("›", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ---------------------------------------------------------------------------
// Pantalla de cámara / captura
// ---------------------------------------------------------------------------

@Composable
fun CameraScreen(
    vm: ScannerViewModel,
    session: CameraSession?,
    onPickImage: (() -> Unit)?,
) {
    var showSaveDialog by remember { mutableStateOf(false) }
    var showFinishDialog by remember { mutableStateOf(false) }
    var docName by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth().background(Color.Black)) {
            when {
                session == null -> Text(
                    "Cámara no disponible en esta plataforma",
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                )
                !session.hasPermission -> Column(
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("Se necesita permiso de cámara", color = Color.White)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = session.onRequestPermission) { Text("Permitir cámara") }
                }
                else -> session.preview(Modifier.fillMaxSize())
            }

            // Botón de retroceso (iOS no tiene gesto de "atrás")
            Surface(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(12.dp),
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.45f),
            ) {
                TextButton(onClick = { vm.navigateBack() }) {
                    Text("←", color = Color.White)
                }
            }
        }

        // Páginas de la sesión actual
        if (vm.workingPages.isNotEmpty()) {
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(vm.workingPages) { index, page ->
                    val bmp = remember(page.id) { imageDataToBitmap(page.image) }
                    Box {
                        Image(
                            bitmap = bmp,
                            contentDescription = null,
                            modifier = Modifier
                                .size(96.dp, 128.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop,
                        )
                        TextButton(
                            onClick = { vm.removeWorkingPage(index) },
                            modifier = Modifier.align(Alignment.TopEnd),
                        ) { Text("✕", color = Color.White) }
                    }
                }
            }
        }

        BottomActionBar {
            OutlinedButton(
                onClick = onPickImage ?: {},
                enabled = onPickImage != null,
                modifier = Modifier.height(50.dp),
            ) { Text("Importar") }
            Button(
                onClick = {
                    session?.controller?.capturePhoto { image ->
                        if (image != null) vm.onImageSelected(image)
                    }
                },
                enabled = session?.hasPermission == true,
                modifier = Modifier.weight(1f).height(50.dp),
                contentPadding = PaddingValues(0.dp),
            ) {
                GlyphIcon(
                    glyph = MenuGlyph.CAMERA,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(26.dp),
                )
            }
            if (vm.workingPages.isNotEmpty()) {
                Button(
                    onClick = { showFinishDialog = true },
                    modifier = Modifier.height(50.dp),
                ) { Text("Finalizar") }
            }
        }
    }

    // Diálogo de finalización: opción de aplicar OCR antes de guardar
    if (showFinishDialog) {
        AlertDialog(
            onDismissRequest = { showFinishDialog = false },
            title = { Text("Finalizar escaneo") },
            text = {
                Text(
                    "¿Deseas aplicar OCR a las ${vm.workingPages.size} página(s) capturadas?",
                )
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = {
                        showFinishDialog = false
                        vm.cancelWorkingSession()
                    }) { Text("Descartar") }
                    TextButton(onClick = {
                        showFinishDialog = false
                        showSaveDialog = true
                    }) { Text("Sin OCR") }
                    TextButton(onClick = {
                        showFinishDialog = false
                        vm.applyOcrToWorkingPages { showSaveDialog = true }
                    }) { Text("Aplicar OCR") }
                }
            },
        )
    }

    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("Guardar documento") },
            text = {
                OutlinedTextField(
                    value = docName,
                    onValueChange = { docName = it },
                    label = { Text("Nombre") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.saveWorkingDocument(docName)
                    showSaveDialog = false
                    docName = ""
                }) { Text("Guardar") }
            },
            dismissButton = { TextButton(onClick = { showSaveDialog = false }) { Text("Cancelar") } },
        )
    }
}

// ---------------------------------------------------------------------------
// Pantalla de revisión / ajuste de vértices
// ---------------------------------------------------------------------------

@Composable
fun ReviewScreen(vm: ScannerViewModel) {
    val result = vm.reviewResult
    if (result != null) {
        ReviewResultView(vm, result)
    } else {
        ReviewAdjustView(vm)
    }
}

@Composable
private fun ReviewAdjustView(vm: ScannerViewModel) {
    val source = vm.reviewSource ?: return
    val density = LocalDensity.current
    var boxSize by remember { mutableStateOf(IntSize.Zero) }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color.DarkGray)
                .onSizeChanged { boxSize = it }
        ) {
            val bmp = remember(source) { imageDataToBitmap(source) }
            Image(
                bitmap = bmp,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )

            val quad = vm.reviewCorners
            if (quad != null && boxSize != IntSize.Zero) {
                val scale = fitScale(source.width, source.height, boxSize.width, boxSize.height)
                val offX = (boxSize.width - source.width * scale) / 2f
                val offY = (boxSize.height - source.height * scale) / 2f

                fun toScreen(p: Point) = Offset(offX + p.x * scale, offY + p.y * scale)
                fun toImage(x: Float, y: Float) = Point(
                    ((x - offX) / scale).coerceIn(0f, source.width.toFloat()),
                    ((y - offY) / scale).coerceIn(0f, source.height.toFloat()),
                )

                Canvas(modifier = Modifier.fillMaxSize()) {
                    val pts = quad.points.map { toScreen(it) }
                    for (i in pts.indices) {
                        val a = pts[i]
                        val b = pts[(i + 1) % pts.size]
                        drawLine(Color(0xFF00E5FF), a, b, strokeWidth = 6f)
                    }
                }

                quad.points.forEachIndexed { index, p ->
                    val s = toScreen(p)
                    val handleSize = with(density) { 28.dp.toPx() }
                    Box(
                        modifier = Modifier
                            .offset { IntOffset((s.x - handleSize / 2).roundToInt(), (s.y - handleSize / 2).roundToInt()) }
                            .size(with(density) { 28.dp })
                            .clip(CircleShape)
                            .background(Color(0xFF00E5FF))
                            .border(2.dp, Color.White, CircleShape)
                            .pointerInput(index, scale, offX, offY, boxSize) {
                                detectDragGestures { change, dragAmount ->
                                    val cur = vm.reviewCorners?.points?.get(index) ?: return@detectDragGestures
                                    vm.updateReviewCorner(
                                        index,
                                        Point(
                                            (cur.x + dragAmount.x / scale).coerceIn(0f, source.width.toFloat()),
                                            (cur.y + dragAmount.y / scale).coerceIn(0f, source.height.toFloat()),
                                        ),
                                    )
                                    change.consume()
                                }
                            }
                    )
                }
            }
        }

        BottomActionBar {
            TextButton(onClick = { vm.detectEdgesAgain() }) { Text("Detectar") }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { vm.retake() }) { Text("Repetir") }
            Button(onClick = { vm.applyReview() }, modifier = Modifier.height(46.dp)) { Text("Aplicar") }
        }
    }
}

@Composable
private fun ReviewResultView(vm: ScannerViewModel, result: ImageData) {
    Column(modifier = Modifier.fillMaxSize()) {
        val bmp = remember(result) { imageDataToBitmap(result) }
        Box(modifier = Modifier.weight(1f).fillMaxWidth().background(Color.Black)) {
            Image(
                bitmap = bmp,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }

        vm.reviewOcrText?.let { text ->
            if (text.isNotBlank()) {
                Text(
                    text.take(400),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .height(80.dp),
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        BottomActionBar {
            TextButton(onClick = { vm.backToAdjust() }) { Text("Ajustar") }
            Spacer(Modifier.weight(1f))
            Button(onClick = { vm.confirmReview() }, modifier = Modifier.height(46.dp)) { Text("Continuar") }
        }
    }
}

// ---------------------------------------------------------------------------
// Pantalla de páginas de un documento guardado
// ---------------------------------------------------------------------------

@Composable
fun DocumentPagesScreen(vm: ScannerViewModel) {
    val doc = vm.selectedDocument ?: return
    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { vm.backToDocuments() }) { Text("←") }
            Text(doc.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = { vm.exportDocument(doc) }) { Text("Exportar PDF") }
        }
        Spacer(Modifier.height(8.dp))

        if (vm.documentPages.isEmpty()) {
            Text(
                "Cargando páginas…",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        } else {
            LazyColumn(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(vm.documentPages, key = { it.id }) { page ->
                    SavedPageItem(vm, page)
                }
            }
        }
    }
}

@Composable
private fun SavedPageItem(vm: ScannerViewModel, page: Page) {
    val image by produceState<ImageData?>(null, page.id) {
        value = vm.loadPageImage(page)
    }
    val bmp = image?.let { remember(it) { imageDataToBitmap(it) } }
    Column {
        if (bmp != null) {
            Image(
                bitmap = bmp,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(bmp.width.toFloat() / bmp.height.toFloat()),
                contentScale = ContentScale.Fit,
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) { Text("No se pudo cargar la página") }
        }
        page.ocrText?.takeIf { it.isNotBlank() }?.let {
            Text(
                it.take(200),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

/** Escala de ContentScale.Fit para centrar [srcW]x[srcH] dentro de [dstW]x[dstH]. */
private fun fitScale(srcW: Int, srcH: Int, dstW: Int, dstH: Int): Float =
    min(dstW.toFloat() / srcW, dstH.toFloat() / srcH)

/**
 * Barra inferior fija con esquinas redondeadas, elevación y padding seguro
 * para no invadir la zona de gestos/botones de navegación del sistema.
 */
@Composable
private fun BottomActionBar(content: @Composable RowScope.() -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 12.dp,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            ),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}
