package com.app.freescanner.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.app.freescanner.data.DocumentStore
import com.app.freescanner.export.DocumentExporter
import com.app.freescanner.export.ExportFormat
import com.app.freescanner.export.ExportPage
import com.app.freescanner.model.Document
import com.app.freescanner.model.ImageData
import com.app.freescanner.model.Page
import com.app.freescanner.model.Quadrilateral
import com.app.freescanner.platform.DocumentScanner
import com.app.freescanner.platform.OcrEngine
import com.app.freescanner.platform.PdfExporter
import com.app.freescanner.platform.appFilesDir
import com.app.freescanner.platform.currentTimeMillis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path.Companion.toPath

/** Página procesada dentro de la sesión de escaneo actual. */
class WorkingPage(
    val id: String,
    val image: ImageData,
    var ocrText: String?,
)

class ScannerViewModel {

    enum class Screen { DOCUMENTS, CAMERA, REVIEW, DOCUMENT_PAGES, CONVERT }

    private val store = DocumentStore()
    private val scanner = DocumentScanner()
    private val ocrEngine = OcrEngine()
    private val pdfExporter = PdfExporter()
    private val docExporter = DocumentExporter()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val documents = mutableStateListOf<Document>()
    val workingPages = mutableStateListOf<WorkingPage>()
    val documentPages = mutableStateListOf<Page>()

    var screen by mutableStateOf(Screen.DOCUMENTS)
        private set
    var status by mutableStateOf("")
        private set
    var isProcessing by mutableStateOf(false)
        private set

    // --- Estado de la pantalla de revisión/ajuste ---
    var reviewSource: ImageData? by mutableStateOf(null)
        private set
    var reviewCorners: Quadrilateral? by mutableStateOf(null)
    var reviewResult: ImageData? by mutableStateOf(null)
        private set
    var reviewOcrText: String? by mutableStateOf(null)
        private set
    var ocrEnabled by mutableStateOf(false)

    /** Formato elegido en "Convertir a" que está esperando páginas de origen. */
    var pendingExportFormat by mutableStateOf<ExportFormat?>(null)
        private set

    var selectedDocument by mutableStateOf<Document?>(null)
        private set

    init {
        refreshDocuments()
    }

    fun startScanning() {
        screen = Screen.CAMERA
    }

    /** Abre la pantalla de elección de origen para convertir a [format]. */
    fun requestConvert(format: ExportFormat) {
        pendingExportFormat = format
        screen = Screen.CONVERT
    }

    fun clearPendingExport() {
        pendingExportFormat = null
    }

    /** Navegación hacia atrás equivalente al botón físico/gesto del sistema. */
    fun navigateBack() {
        when (screen) {
            Screen.DOCUMENTS -> Unit
            Screen.CONVERT -> screen = Screen.DOCUMENTS
            Screen.DOCUMENT_PAGES -> backToDocuments()
            Screen.CAMERA -> screen = Screen.DOCUMENTS
            Screen.REVIEW -> {
                if (reviewResult != null) {
                    backToAdjust()
                } else {
                    reviewSource = null
                    reviewCorners = null
                    screen = Screen.CAMERA
                }
            }
        }
    }

    fun refreshDocuments() {
        scope.launch {
            val list = withContext(Dispatchers.Default) { store.listDocuments() }
            documents.clear()
            documents.addAll(list)
        }
    }

    // ---------- Flujo de escaneo ----------

    /** Imagen proveniente de la cámara o de la galería. */
    fun onImageSelected(image: ImageData) {
        reviewSource = image
        reviewResult = null
        reviewOcrText = null
        reviewCorners = null
        screen = Screen.REVIEW
        isProcessing = true
        scope.launch {
            val quad = runCatching { scanner.detectEdges(image) }.getOrNull()
            reviewCorners = quad ?: Quadrilateral.fullFrame(image.width, image.height)
            isProcessing = false
        }
    }

    fun updateReviewCorner(index: Int, point: com.app.freescanner.model.Point) {
        val q = reviewCorners ?: return
        reviewCorners = q.withPoint(index, point)
    }

    fun detectEdgesAgain() {
        val src = reviewSource ?: return
        isProcessing = true
        scope.launch {
            val quad = runCatching { scanner.detectEdges(src) }.getOrNull()
            if (quad != null) reviewCorners = quad
            isProcessing = false
        }
    }

    /** Aplica perspectiva + filtro (+ OCR si está activado) con los vértices actuales. */
    fun applyReview() {
        val src = reviewSource ?: return
        val corners = reviewCorners ?: Quadrilateral.fullFrame(src.width, src.height)
        isProcessing = true
        reviewResult = null
        reviewOcrText = null
        scope.launch {
            val corrected = runCatching {
                scanner.applyPerspectiveCorrection(src, corners)
            }.getOrNull() ?: src
            val filtered = runCatching { scanner.applyScanFilter(corrected) }.getOrNull() ?: corrected
            val text = if (ocrEnabled) {
                runCatching { ocrEngine.recognizeText(filtered, "spa") }.getOrNull()
            } else null
            reviewResult = filtered
            reviewOcrText = text
            isProcessing = false
        }
    }

    /** Vuelve a ajustar vértices sobre la imagen original. */
    fun backToAdjust() {
        reviewResult = null
        reviewOcrText = null
    }

    fun confirmReview() {
        val result = reviewResult ?: return
        val text = reviewOcrText
        workingPages += WorkingPage(
            id = "wp-${workingPages.size}-${currentTime()}",
            image = result,
            ocrText = text,
        )
        reviewSource = null
        reviewResult = null
        reviewOcrText = null
        reviewCorners = null
        screen = Screen.CAMERA
    }

    fun retake() {
        reviewSource = null
        reviewResult = null
        reviewOcrText = null
        reviewCorners = null
        screen = Screen.CAMERA
    }

    fun removeWorkingPage(index: Int) {
        if (index in workingPages.indices) workingPages.removeAt(index)
    }

    /**
     * Aplica OCR a todas las páginas de la sesión que aún no tengan texto.
     * [onDone] se invoca al terminar (en el hilo principal).
     */
    fun applyOcrToWorkingPages(onDone: () -> Unit = {}) {
        if (workingPages.isEmpty()) {
            onDone()
            return
        }
        isProcessing = true
        scope.launch {
            workingPages.forEach { page ->
                if (page.ocrText.isNullOrBlank()) {
                    val text = runCatching {
                        ocrEngine.recognizeText(page.image, "spa")
                    }.getOrNull()
                    if (!text.isNullOrBlank()) page.ocrText = text
                }
            }
            isProcessing = false
            withContext(Dispatchers.Main) { onDone() }
        }
    }

    fun saveWorkingDocument(name: String) {
        if (workingPages.isEmpty()) return
        scope.launch {
            val docId = store.createDocument(name.ifBlank { "Documento ${currentTime()}" })
            workingPages.forEach { store.addPage(docId, it.image, it.ocrText) }
            workingPages.clear()
            reviewSource = null
            reviewResult = null
            reviewCorners = null
            screen = Screen.DOCUMENTS
            status = "Documento guardado"
            refreshDocuments()
        }
    }

    fun cancelWorkingSession() {
        workingPages.clear()
        reviewSource = null
        reviewResult = null
        reviewCorners = null
        screen = Screen.DOCUMENTS
    }

    // ---------- Documentos guardados ----------

    fun openDocument(document: Document) {
        selectedDocument = document
        documentPages.clear()
        screen = Screen.DOCUMENT_PAGES
        scope.launch {
            val pages = withContext(Dispatchers.Default) { store.pagesForDocument(document.id) }
            documentPages.addAll(pages)
        }
    }

    fun backToDocuments() {
        selectedDocument = null
        documentPages.clear()
        screen = Screen.DOCUMENTS
    }

    fun deleteDocument(document: Document) {
        scope.launch {
            withContext(Dispatchers.Default) { store.deleteDocument(document.id) }
            if (selectedDocument?.id == document.id) backToDocuments()
            refreshDocuments()
        }
    }

    suspend fun loadPageImage(page: Page): ImageData? =
        withContext(Dispatchers.Default) { store.loadPageImage(page) }

    /** Exporta un documento guardado a PDF (PDFBox-Android). */
    fun exportDocument(document: Document) {
        scope.launch {
            isProcessing = true
            val result = withContext(Dispatchers.Default) {
                val pages = store.pagesForDocument(document.id)
                    .mapNotNull { store.loadPageImage(it) }
                if (pages.isEmpty()) return@withContext null
                val dir = "${appFilesDir()}/exports".toPath()
                FileSystem.SYSTEM.createDirectories(dir)
                val out = "${appFilesDir()}/exports/${sanitizeFileName(document.name)}.pdf"
                if (pdfExporter.exportToPdf(pages, out)) out else null
            }
            isProcessing = false
            status = if (result != null) "Exportado: $result" else "Error al exportar PDF"
        }
    }

    // ---------- Exportación a Office (xlsx / docx / pptx) ----------

    /** Exporta un documento guardado al formato Office indicado. */
    fun exportDocumentAs(document: Document, format: ExportFormat) {
        scope.launch {
            isProcessing = true
            val result = withContext(Dispatchers.Default) {
                val pages = store.pagesForDocument(document.id)
                    .mapNotNull { page ->
                        store.loadPageImage(page)?.let { ExportPage(it, page.ocrText) }
                    }
                exportPagesTo(pages, format, sanitizeFileName(document.name))
            }
            isProcessing = false
            status = if (result) {
                "Exportado: ${sanitizeFileName(document.name)}.${format.extension}"
            } else {
                "Error al exportar ${format.extension.uppercase()}"
            }
        }
    }

    /** Exporta las páginas de la sesión de escaneo actual (sin guardar). */
    fun exportWorkingSessionAs(format: ExportFormat) {
        val pages = workingPages.map { ExportPage(it.image, it.ocrText) }
        if (pages.isEmpty()) {
            status = "No hay páginas en la sesión actual"
            return
        }
        scope.launch {
            isProcessing = true
            val name = "escaneo-actual"
            val result = withContext(Dispatchers.Default) {
                exportPagesTo(pages, format, name)
            }
            isProcessing = false
            status = if (result) {
                "Exportado: $name.${format.extension}"
            } else {
                "Error al exportar ${format.extension.uppercase()}"
            }
        }
    }

    private fun exportPagesTo(pages: List<ExportPage>, format: ExportFormat, baseName: String): Boolean {
        if (pages.isEmpty()) return false
        val dir = "${appFilesDir()}/exports".toPath()
        FileSystem.SYSTEM.createDirectories(dir)
        return docExporter.export(pages, format, "${dir}/${baseName}.${format.extension}")
    }

    private fun sanitizeFileName(name: String): String =
        name.replace(Regex("[^A-Za-z0-9ÁÉÍÓÚáéíóúÑñ _-]"), "_").ifBlank { "documento" }

    private fun currentTime(): String = currentTimeMillis().toString()
}
