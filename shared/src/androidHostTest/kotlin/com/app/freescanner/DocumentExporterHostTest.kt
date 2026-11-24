package com.app.freescanner

import com.app.freescanner.export.DocumentExporter
import com.app.freescanner.export.ExportFormat
import com.app.freescanner.export.ExportPage
import com.app.freescanner.model.ImageData
import java.io.File
import java.util.zip.ZipFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DocumentExporterHostTest {

    private fun samplePages(): List<ExportPage> {
        // Imagen 8x8 RGBA con algo de variación
        val w = 8
        val h = 8
        val data = ByteArray(w * h * 4) { i ->
            when ((i / 4) % 3) {
                0 -> 200.toByte()
                1 -> 100.toByte()
                else -> 50.toByte()
            }
        }
        return listOf(
            ExportPage(ImageData(w, h, data), "Hola mundo\nSegunda línea <con> & símbolos"),
            ExportPage(ImageData(w, h, data), null),
        )
    }

    private fun exportAndOpen(format: ExportFormat): ZipFile {
        val dir = File(System.getProperty("java.io.tmpdir"), "exp-test-${format.extension}")
        dir.mkdirs()
        val out = File(dir, "test.${format.extension}")
        out.delete()
        val ok = DocumentExporter().export(samplePages(), format, out.absolutePath)
        assertTrue(ok, "export falló para ${format.extension}")
        return ZipFile(out)
    }

    private fun entryText(zip: ZipFile, name: String): String =
        zip.getInputStream(zip.getEntry(name)).bufferedReader().readText()

    @Test
    fun docxGeneraEstructuraValida() {
        exportAndOpen(ExportFormat.WORD).use { zip ->
            listOf(
                "[Content_Types].xml",
                "_rels/.rels",
                "word/document.xml",
                "word/_rels/document.xml.rels",
                "word/styles.xml",
                "word/media/image1.bmp",
                "word/media/image2.bmp",
            ).forEach { assertTrue(zip.getEntry(it) != null, "falta $it") }

            val doc = entryText(zip, "word/document.xml")
            assertTrue(doc.contains("Página 1"))
            assertTrue(doc.contains("Segunda línea &lt;con&gt; &amp; símbolos"))
            assertTrue(doc.contains("r:embed=\"rId2\""))
        }
    }

    @Test
    fun xlsxGeneraEstructuraValida() {
        exportAndOpen(ExportFormat.EXCEL).use { zip ->
            listOf(
                "[Content_Types].xml",
                "_rels/.rels",
                "xl/workbook.xml",
                "xl/_rels/workbook.xml.rels",
                "xl/worksheets/sheet1.xml",
            ).forEach { assertTrue(zip.getEntry(it) != null, "falta $it") }

            val sheet = entryText(zip, "xl/worksheets/sheet1.xml")
            assertTrue(sheet.contains("Página 1"))
            assertTrue(sheet.contains("Hola mundo"))
            assertTrue(sheet.contains("(sin texto OCR)"))
        }
    }

    @Test
    fun pptxGeneraEstructuraValida() {
        exportAndOpen(ExportFormat.PPTX).use { zip ->
            listOf(
                "[Content_Types].xml",
                "_rels/.rels",
                "ppt/presentation.xml",
                "ppt/_rels/presentation.xml.rels",
                "ppt/presProps.xml",
                "ppt/viewProps.xml",
                "ppt/theme/theme1.xml",
                "ppt/slideMasters/slideMaster1.xml",
                "ppt/slideMasters/_rels/slideMaster1.xml.rels",
                "ppt/slideLayouts/slideLayout1.xml",
                "ppt/slideLayouts/_rels/slideLayout1.xml.rels",
                "ppt/slides/slide1.xml",
                "ppt/slides/_rels/slide1.xml.rels",
                "ppt/slides/slide2.xml",
                "ppt/slides/_rels/slide2.xml.rels",
                "ppt/media/image1.bmp",
                "ppt/media/image2.bmp",
            ).forEach { assertTrue(zip.getEntry(it) != null, "falta $it") }

            val slide1 = entryText(zip, "ppt/slides/slide1.xml")
            assertTrue(slide1.contains("Segunda línea"))
            assertTrue(slide1.contains("r:embed=\"rId2\""))

            val pres = entryText(zip, "ppt/presentation.xml")
            assertEquals(2, Regex("<p:sldId ").findAll(pres).count())
        }
    }
}
