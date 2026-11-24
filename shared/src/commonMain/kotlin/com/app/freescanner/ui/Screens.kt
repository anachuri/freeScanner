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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
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
fun DocumentsScreen(vm: ScannerViewModel) {
    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        Column(modifier = Modifier.weight(1f).fillMaxWidth().padding(16.dp)) {
            Text("Mis documentos", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(12.dp))

        if (vm.documents.isEmpty()) {
            Text(
                "No hay documentos todavía.\nToca \"Nuevo escaneo\" para empezar.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 24.dp),
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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

        BottomActionBar {
            Button(
                onClick = { vm.startScanning() },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("Nuevo escaneo") }
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

        if (vm.workingPages.isNotEmpty()) {
            BottomActionBar {
                Button(
                    onClick = { showSaveDialog = true },
                    modifier = Modifier.weight(1f).height(50.dp),
                ) { Text("Guardar (${vm.workingPages.size})") }
                OutlinedButton(
                    onClick = { vm.cancelWorkingSession() },
                    modifier = Modifier.height(50.dp),
                ) { Text("Descartar") }
            }
        } else {
            BottomActionBar {
                OutlinedButton(
                    onClick = onPickImage ?: {},
                    enabled = onPickImage != null,
                    modifier = Modifier.weight(1f).height(50.dp),
                ) { Text("Importar") }
                Button(
                    onClick = {
                        session?.controller?.capturePhoto { image ->
                            if (image != null) vm.onImageSelected(image)
                        }
                    },
                    enabled = session?.hasPermission == true,
                    modifier = Modifier.weight(1.4f).height(50.dp),
                ) { Text("Capturar") }
            }
        }
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "OCR",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 4.dp),
                )
                Switch(checked = vm.ocrEnabled, onCheckedChange = { vm.ocrEnabled = it })
            }
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
            Button(onClick = { vm.confirmReview() }, modifier = Modifier.height(46.dp)) { Text("Usar página") }
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
