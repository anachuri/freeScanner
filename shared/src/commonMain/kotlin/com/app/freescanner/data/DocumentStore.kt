package com.app.freescanner.data

import com.app.freescanner.db.FreeScannerDatabase
import com.app.freescanner.model.Document
import com.app.freescanner.model.ImageData
import com.app.freescanner.model.Page
import com.app.freescanner.platform.appFilesDir
import com.app.freescanner.platform.currentTimeMillis
import com.app.freescanner.platform.sqlDriver
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import okio.FileSystem
import okio.Path.Companion.toPath

/**
 * Persistencia de documentos y páginas.
 *
 * Los metadatos viven en SQLDelight; las imágenes de página se guardan como
 * archivos RGBA crudos dentro del directorio privado de la app.
 */
class DocumentStore {

    private val database = FreeScannerDatabase(sqlDriver())
    private val documentsQueries = database.documentsQueries
    private val pagesQueries = database.pagesQueries

    fun listDocuments(): List<Document> =
        documentsQueries.selectAllDocuments().executeAsList().map {
            Document(
                id = it.id,
                name = it.name,
                createdAt = it.created_at,
                pageCount = it.page_count.toInt(),
            )
        }

    @OptIn(ExperimentalUuidApi::class)
    fun createDocument(name: String): String {
        val id = Uuid.random().toString()
        documentsQueries.insertDocument(id, name, currentTimeMillis())
        return id
    }

    fun deleteDocument(documentId: String) {
        pagesForDocument(documentId).forEach { deletePageFile(it.imagePath) }
        pagesQueries.deletePagesForDocument(documentId)
        documentsQueries.deleteDocument(documentId)
    }

    fun pagesForDocument(documentId: String): List<Page> =
        pagesQueries.selectPagesForDocument(documentId).executeAsList().map {
            Page(
                id = it.id,
                documentId = it.document_id,
                imagePath = it.image_path,
                width = it.width.toInt(),
                height = it.height.toInt(),
                ocrText = it.ocr_text,
            )
        }

    /** Guarda la imagen de una página en disco y registra la fila en la BD. */
    @OptIn(ExperimentalUuidApi::class)
    fun addPage(documentId: String, image: ImageData, ocrText: String?): Page {
        val id = Uuid.random().toString()
        val path = pageFilePath(documentId, id)
        savePageImage(path, image)
        pagesQueries.insertPage(
            id, documentId, path,
            image.width.toLong(), image.height.toLong(), ocrText,
        )
        val count = pagesForDocument(documentId).size
        documentsQueries.updatePageCount(count.toLong(), documentId)
        return Page(id, documentId, path, image.width, image.height, ocrText)
    }

    fun loadPageImage(page: Page): ImageData? {
        val path = page.imagePath.toPath()
        if (!FileSystem.SYSTEM.exists(path)) return null
        return try {
            val bytes = FileSystem.SYSTEM.read(path) { readByteArray() }
            ImageData(page.width, page.height, bytes)
        } catch (e: Exception) {
            null
        }
    }

    fun pageFilePath(documentId: String, pageId: String): String =
        "${appFilesDir()}/scans/$documentId/$pageId.rgba"

    private fun savePageImage(pathString: String, image: ImageData) {
        val path = pathString.toPath()
        FileSystem.SYSTEM.createDirectories(path.parent!!)
        FileSystem.SYSTEM.write(path) { write(image.data) }
    }

    private fun deletePageFile(pathString: String) {
        try {
            FileSystem.SYSTEM.delete(pathString.toPath())
        } catch (_: Exception) {
        }
    }
}
