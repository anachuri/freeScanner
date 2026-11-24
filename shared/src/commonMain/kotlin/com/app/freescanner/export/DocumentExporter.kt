package com.app.freescanner.export

import com.app.freescanner.model.ImageData
import okio.FileSystem
import okio.Path.Companion.toPath

/** Formatos de exportación soportados (archivos Office/OpenXML reales). */
enum class ExportFormat(val extension: String) {
    EXCEL("xlsx"),
    WORD("docx"),
    PPTX("pptx"),
}

/** Una página a exportar: imagen escaneada + texto OCR opcional. */
data class ExportPage(
    val image: ImageData,
    val text: String?,
)

/**
 * Genera archivos .xlsx / .docx / .pptx reales (paquetes OOXML) sin depender de
 * librerías externas: escribe el ZIP en modo "store" (sin compresión) y las
 * partes XML/relaciones mínimas que Office y LibreOffice aceptan.
 *
 * Las imágenes de página se incrustan como BMP de 32 bits, codificadas en
 * Kotlin puro (sin encoder por plataforma).
 */
class DocumentExporter {

    fun export(pages: List<ExportPage>, format: ExportFormat, outputPath: String): Boolean {
        if (pages.isEmpty()) return false
        return try {
            val entries = when (format) {
                ExportFormat.EXCEL -> buildXlsx(pages)
                ExportFormat.WORD -> buildDocx(pages)
                ExportFormat.PPTX -> buildPptx(pages)
            }
            val zip = ZipBuilder()
            entries.forEach { (name, bytes) -> zip.addEntry(name, bytes) }
            FileSystem.SYSTEM.write(outputPath.toPath()) { write(zip.build()) }
            true
        } catch (_: Exception) {
            false
        }
    }
}

// ---------------------------------------------------------------------------
// ZIP (método store, sin compresión)
// ---------------------------------------------------------------------------

private class ZipBuilder {
    private class Entry(val name: String, val data: ByteArray)

    private val entries = mutableListOf<Entry>()

    fun addEntry(name: String, data: ByteArray) {
        entries += Entry(name, data)
    }

    fun addEntry(name: String, text: String) {
        addEntry(name, text.encodeToByteArray())
    }

    fun build(): ByteArray {
        val local = ByteWriter()
        val central = ByteWriter()
        var offset = 0L

        for (entry in entries) {
            val nameBytes = entry.name.encodeToByteArray()
            val crc = Crc32.of(entry.data)
            val size = entry.data.size.toLong()

            // Local file header
            local.u32le(0x04034b50)
            local.u16le(20)          // version needed
            local.u16le(0)           // flags
            local.u16le(0)           // method: store
            local.u16le(0)           // mod time
            local.u16le(33)          // mod date (1980-01-01)
            local.u32le(crc)
            local.u32le(size)
            local.u32le(size)
            local.u16le(nameBytes.size)
            local.u16le(0)           // extra len
            local.bytes(nameBytes)
            local.bytes(entry.data)

            // Central directory header
            central.u32le(0x02014b50)
            central.u16le(20)        // version made by
            central.u16le(20)        // version needed
            central.u16le(0)
            central.u16le(0)
            central.u16le(0)
            central.u16le(33)
            central.u32le(crc)
            central.u32le(size)
            central.u32le(size)
            central.u16le(nameBytes.size)
            central.u16le(0)         // extra
            central.u16le(0)         // comment
            central.u16le(0)         // disk number
            central.u16le(0)         // internal attrs
            central.u32le(0)         // external attrs
            central.u32le(offset)
            central.bytes(nameBytes)

            offset += 30 + nameBytes.size + entry.data.size
        }

        val cdSize = central.size.toLong()
        val cdOffset = local.size.toLong()

        val out = ByteWriter()
        out.bytes(local.toByteArray())
        out.bytes(central.toByteArray())
        // End of central directory
        out.u32le(0x06054b50)
        out.u16le(0)
        out.u16le(0)
        out.u16le(entries.size)
        out.u16le(entries.size)
        out.u32le(cdSize)
        out.u32le(cdOffset)
        out.u16le(0)
        return out.toByteArray()
    }
}

private object Crc32 {
    private val table = IntArray(256) { n ->
        var c = n
        repeat(8) { c = if (c and 1 != 0) (c ushr 1) xor 0xEDB88320.toInt() else c ushr 1 }
        c
    }

    fun of(data: ByteArray): Long {
        var crc = 0xFFFFFFFF.toInt()
        for (b in data) {
            crc = table[(crc xor (b.toInt() and 0xFF)) and 0xFF] xor (crc ushr 8)
        }
        return (crc xor 0xFFFFFFFF.toInt()).toLong() and 0xFFFFFFFFL
    }
}

/** Buffer de bytes con escritura little-endian explícita. */
private class ByteWriter {
    private var buf = ByteArray(8192)
    var size = 0
        private set

    private fun ensure(extra: Int) {
        if (size + extra > buf.size) {
            val next = ByteArray(maxOf(buf.size * 2, size + extra))
            buf.copyInto(next, 0, 0, size)
            buf = next
        }
    }

    fun u8(v: Int) { ensure(1); buf[size++] = v.toByte() }
    fun u16le(v: Int) { u8(v); u8(v ushr 8) }
    fun u32le(v: Long) { u8(v.toInt()); u8((v ushr 8).toInt()); u8((v ushr 16).toInt()); u8((v ushr 24).toInt()) }
    fun bytes(b: ByteArray) { ensure(b.size); b.copyInto(buf, size); size += b.size }
    fun ascii(s: String) { bytes(s.encodeToByteArray()) }
    fun toByteArray(): ByteArray = buf.copyOf(size)
}

// ---------------------------------------------------------------------------
// Utilidades comunes
// ---------------------------------------------------------------------------

private fun escapeXml(text: String): String = buildString(text.length + 16) {
    for (c in text) {
        when (c) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            else -> append(c)
        }
    }
}

/** Deja solo caracteres válidos en XML 1.0 (sin controles < 0x20 salvo tab/salto). */
private fun sanitizeXmlText(text: String): String = buildString(text.length) {
    for (c in text) {
        if (c == '\n' || c == '\r' || c == '\t' || c >= ' ') append(c)
    }
}

private fun ocrLines(text: String?): List<String> =
    text?.lines()?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()

/** EMU por píxel a 96 DPI. */
private const val EMU_PER_PX = 9525L
/** Ancho útil de página carta con márgenes de 1" (6.5" = 5943600 EMU). */
private const val MAX_CX = 5943600L
/** Alto útil máximo (~9"). */
private const val MAX_CY = 8229600L

/** Dimensiones EMU de la imagen ajustada dentro del área útil. */
private fun fitEmu(w: Int, h: Int): Pair<Long, Long> {
    val pxW = w.toLong() * EMU_PER_PX
    val pxH = h.toLong() * EMU_PER_PX
    val scale = minOf(MAX_CX.toDouble() / pxW, MAX_CY.toDouble() / pxH, 1.0)
    return (pxW * scale).toLong() to (pxH * scale).toLong()
}

// ---------------------------------------------------------------------------
// Codificación BMP 32bpp (BGRA, bottom-up) - formato soportado por OOXML
// ---------------------------------------------------------------------------

private fun encodeBmp(image: ImageData): ByteArray {
    val w = image.width
    val h = image.height
    val pixelBytes = w * h * 4L
    val fileSize = 54L + pixelBytes
    val out = ByteWriter()

    // BITMAPFILEHEADER
    out.ascii("BM")
    out.u32le(fileSize)
    out.u16le(0)
    out.u16le(0)
    out.u32le(54)

    // BITMAPINFOHEADER (40 bytes)
    out.u32le(40)
    out.u32le(w.toLong())
    out.u32le(h.toLong())   // positivo => bottom-up
    out.u16le(1)            // planes
    out.u16le(32)           // bpp
    out.u32le(0)            // BI_RGB
    out.u32le(pixelBytes)
    out.u32le(2835)         // ppm x
    out.u32le(2835)         // ppm y
    out.u32le(0)            // colores usados
    out.u32le(0)            // colores importantes

    // Píxeles bottom-up en orden B,G,R,X
    val src = image.data
    var row = (h - 1) * w * 4
    for (y in 0 until h) {
        var o = row
        for (x in 0 until w) {
            out.u8(src[o + 2].toInt() and 0xFF)
            out.u8(src[o + 1].toInt() and 0xFF)
            out.u8(src[o].toInt() and 0xFF)
            out.u8(src[o + 3].toInt() and 0xFF)
            o += 4
        }
        row -= w * 4
    }
    return out.toByteArray()
}

// ---------------------------------------------------------------------------
// DOCX (Word)
// ---------------------------------------------------------------------------

private fun buildDocx(pages: List<ExportPage>): List<Pair<String, ByteArray>> {
    val body = StringBuilder()
    val rels = StringBuilder()
    val media = mutableListOf<Pair<String, ByteArray>>()
    var rId = 2 // rId1 reservado para styles.xml

    pages.forEachIndexed { index, page ->
        body.append(
            """<w:p><w:pPr><w:pStyle w:val="Heading1"/></w:pPr>""" +
                """<w:r><w:t xml:space="preserve">Página ${index + 1}</w:t></w:r></w:p>""",
        )
        for (line in ocrLines(page.text)) {
            body.append(
                """<w:p><w:r><w:t xml:space="preserve">${escapeXml(sanitizeXmlText(line))}</w:t></w:r></w:p>""",
            )
        }

        val imgName = "image${index + 1}.bmp"
        media += "word/media/$imgName" to encodeBmp(page.image)
        rels.append(
            """<Relationship Id="rId$rId" """ +
                """Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" """ +
                """Target="media/$imgName"/>""",
        )
        val (cx, cy) = fitEmu(page.image.width, page.image.height)
        body.append(docxPicXml(rId, "img${index + 1}", cx, cy))
        body.append("<w:p/>")
        rId++
    }

    val documentXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>$body<w:sectPr><w:pgSz w:w="12240" w:h="15840"/><w:pgMar w:top="1440" w:right="1440" w:bottom="1440" w:left="1440" w:header="720" w:footer="720" w:gutter="0"/></w:sectPr></w:body></w:document>"""

    val documentRels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>$rels</Relationships>"""

    val contentTypes = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Default Extension="bmp" ContentType="image/bmp"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/><Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/></Types>"""

    val rootRels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>"""

    val stylesXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:style w:type="paragraph" w:default="1" w:styleId="Normal"><w:name w:val="Normal"/><w:rPr><w:sz w:val="22"/></w:rPr></w:style><w:style w:type="paragraph" w:styleId="Heading1"><w:name w:val="heading 1"/><w:basedOn w:val="Normal"/><w:pPr><w:outlineLvl w:val="0"/></w:pPr><w:rPr><w:b/><w:sz w:val="32"/></w:rPr></w:style></w:styles>"""

    return buildList {
        add("[Content_Types].xml" to contentTypes.encodeToByteArray())
        add("_rels/.rels" to rootRels.encodeToByteArray())
        add("word/document.xml" to documentXml.encodeToByteArray())
        add("word/_rels/document.xml.rels" to documentRels.encodeToByteArray())
        add("word/styles.xml" to stylesXml.encodeToByteArray())
        media.forEach { (name, bytes) -> add(name to bytes) }
    }
}

private fun docxPicXml(rId: Int, name: String, cx: Long, cy: Long): String =
    """<w:p><w:r><w:drawing><wp:inline distT="0" distB="0" distL="0" distR="0" xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing"><wp:extent cx="$cx" cy="$cy"/><wp:docPr id="${rId * 10}" name="$name"/><a:graphic xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"><a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/picture"><pic:pic xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture"><pic:nvPicPr><pic:cNvPr id="0" name="$name"/><pic:cNvPicPr/></pic:nvPicPr><pic:blipFill><a:blip xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" r:embed="rId$rId"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill><pic:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="$cx" cy="$cy"/></a:xfrm><a:prstGeom prst="rect"><a:avLst/></a:prstGeom></pic:spPr></pic:pic></a:graphicData></a:graphic></wp:inline></w:drawing></w:r></w:p>"""

// ---------------------------------------------------------------------------
// PPTX (PowerPoint) - 16:9, una diapositiva por página
// ---------------------------------------------------------------------------

private const val SLIDE_W = 12192000L
private const val SLIDE_H = 6858000L

private fun buildPptx(pages: List<ExportPage>): List<Pair<String, ByteArray>> {
    val slideOverrides = StringBuilder()
    val slideIds = StringBuilder()
    val media = mutableListOf<Pair<String, ByteArray>>()
    val slides = mutableListOf<Triple<Int, String, String>>() // n, slideXml, slideRelsXml

    pages.forEachIndexed { index, page ->
        val n = index + 1
        val relId = index + 2 // rId1 = slideLayout

        slideOverrides.append(
            """<Override PartName="/ppt/slides/slide$n.xml" """ +
                """ContentType="application/vnd.openxmlformats-officedocument.presentationml.slide+xml"/>""",
        )
        slideIds.append("""<p:sldId id="${255 + n}" r:id="rId${index + 2}"/>""")

        val slideRels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideLayout" Target="../slideLayouts/slideLayout1.xml"/><Relationship Id="rId$relId" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="../media/image$n.bmp"/></Relationships>"""

        slides += Triple(n, pptxSlideXml(page, relId), slideRels)
        media += "ppt/media/image$n.bmp" to encodeBmp(page.image)
    }

    val presentationXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<p:presentation xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"><p:sldMasterIdLst><p:sldMasterId id="2147483648" r:id="rId1"/></p:sldMasterIdLst><p:sldIdLst>$slideIds</p:sldIdLst><p:sldSz cx="$SLIDE_W" cy="$SLIDE_H" type="screen16x9"/><p:notesSz cx="6858000" cy="9144000"/></p:presentation>"""

    val presentationRels = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
        append("""<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideMaster" Target="slideMasters/slideMaster1.xml"/>""")
        pages.forEachIndexed { index, _ ->
            append("""<Relationship Id="rId${index + 2}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide" Target="slides/slide${index + 1}.xml"/>""")
        }
        val next = pages.size + 2
        append("""<Relationship Id="rId$next" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/presProps" Target="presProps.xml"/>""")
        append("""<Relationship Id="rId${next + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/viewProps" Target="viewProps.xml"/>""")
        append("</Relationships>")
    }

    val contentTypes = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Default Extension="bmp" ContentType="image/bmp"/><Override PartName="/ppt/presentation.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml"/><Override PartName="/ppt/presProps.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.presProps+xml"/><Override PartName="/ppt/viewProps.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.viewProps+xml"/><Override PartName="/ppt/theme/theme1.xml" ContentType="application/vnd.openxmlformats-officedocument.theme+xml"/><Override PartName="/ppt/slideMasters/slideMaster1.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slideMaster+xml"/><Override PartName="/ppt/slideLayouts/slideLayout1.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slideLayout+xml"/>$slideOverrides</Types>"""

    val rootRels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="ppt/presentation.xml"/></Relationships>"""

    val presProps = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<p:presentationPr xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"/>"""

    val viewProps = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<p:viewPr xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"><p:normalViewPr><p:restoredLeft sz="15620"/><p:restoredTop sz="94660"/></p:normalViewPr><p:slideViewPr><p:cSldViewPr var="1"><p:cViewPr><p:scale><a:sx n="100" d="100"/><a:sy n="100" d="100"/></p:scale><p:origin x="0" y="0"/></p:cViewPr><p:guideLst/></p:cSldViewPr></p:slideViewPr><p:gridSpacing cx="72063" cy="72063"/><p:notesTextGridSpacing cx="254000" cy="254000"/></p:viewPr>"""

    val slideMaster = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<p:sldMaster xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"><p:cSld><p:bg><p:bgPr><a:solidFill><a:schemeClr val="bg1"/></a:solidFill><a:effectLst/></p:bgPr></p:bg><p:spTree><p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr><p:grpSpPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="0" cy="0"/><a:chOff x="0" y="0"/><a:chExt cx="0" cy="0"/></a:xfrm></p:grpSpPr></p:spTree></p:cSld><p:clrMap bg1="lt1" tx1="dk1" bg2="lt2" tx2="dk2" accent1="accent1" accent2="accent2" accent3="accent3" accent4="accent4" accent5="accent5" accent6="accent6" hlink="hlink" folHlink="folHlink"/><p:sldLayoutIdLst><p:sldLayoutId id="2147483649" r:id="rId1"/></p:sldLayoutIdLst><p:txStyles><p:titleStyle><a:lvl1pPr algn="ctr"><a:defRPr sz="4400" b="1"><a:solidFill><a:schemeClr val="tx1"/></a:solidFill><a:latin typeface="+mj-lt"/></a:defRPr></a:lvl1pPr></p:titleStyle><p:bodyStyle><a:lvl1pPr><a:defRPr sz="3200"><a:solidFill><a:schemeClr val="tx1"/></a:solidFill><a:latin typeface="+mn-lt"/></a:defRPr></a:lvl1pPr></p:bodyStyle><p:otherStyle><a:lvl1pPr><a:defRPr sz="1800"><a:solidFill><a:schemeClr val="tx1"/></a:solidFill><a:latin typeface="+mn-lt"/></a:defRPr></a:lvl1pPr></p:otherStyle></p:txStyles></p:sldMaster>"""

    val slideMasterRels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideLayout" Target="../slideLayouts/slideLayout1.xml"/><Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/theme" Target="../theme/theme1.xml"/></Relationships>"""

    val slideLayout = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<p:sldLayout xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main" type="blank" preserve="1"><p:cSld name="Blank"><p:spTree><p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr><p:grpSpPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="0" cy="0"/><a:chOff x="0" y="0"/><a:chExt cx="0" cy="0"/></a:xfrm></p:grpSpPr></p:spTree></p:cSld><p:clrMapOvr><a:masterClrMapping/></p:clrMapOvr></p:sldLayout>"""

    val slideLayoutRels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideMaster" Target="../slideMasters/slideMaster1.xml"/></Relationships>"""

    val theme = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<a:theme xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" name="Office"><a:themeElements><a:clrScheme name="Office"><a:dk1><a:sysClr val="windowText" lastClr="000000"/></a:dk1><a:lt1><a:sysClr val="window" lastClr="FFFFFF"/></a:lt1><a:dk2><a:srgbClr val="1F497D"/></a:dk2><a:lt2><a:srgbClr val="EEECE1"/></a:lt2><a:accent1><a:srgbClr val="4F81BD"/></a:accent1><a:accent2><a:srgbClr val="C0504D"/></a:accent2><a:accent3><a:srgbClr val="9BBB59"/></a:accent3><a:accent4><a:srgbClr val="8064A2"/></a:accent4><a:accent5><a:srgbClr val="4BACC6"/></a:accent5><a:accent6><a:srgbClr val="F79646"/></a:accent6><a:hlink><a:srgbClr val="0000FF"/></a:hlink><a:folHlink><a:srgbClr val="800080"/></a:folHlink></a:clrScheme><a:fontScheme name="Office"><a:majorFont><a:latin typeface="Calibri"/><a:ea typeface=""/><a:cs typeface=""/></a:majorFont><a:minorFont><a:latin typeface="Calibri"/><a:ea typeface=""/><a:cs typeface=""/></a:minorFont></a:fontScheme><a:fmtScheme name="Office"><a:fillStyleLst><a:solidFill><a:schemeClr val="phClr"/></a:solidFill><a:gradFill rotWithShape="1"><a:gsLst><a:gs pos="0"><a:schemeClr val="phClr"><a:tint val="50000"/><a:satMod val="300000"/></a:schemeClr></a:gs><a:gs pos="35000"><a:schemeClr val="phClr"><a:tint val="37000"/><a:satMod val="300000"/></a:schemeClr></a:gs><a:gs pos="100000"><a:schemeClr val="phClr"><a:tint val="15000"/><a:satMod val="350000"/></a:schemeClr></a:gs></a:gsLst><a:lin ang="16200000" scaled="1"/></a:gradFill><a:gradFill rotWithShape="1"><a:gsLst><a:gs pos="0"><a:schemeClr val="phClr"><a:tint val="100000"/><a:satMod val="130000"/><a:shade val="100000"/><a:satMod val="130000"/></a:schemeClr></a:gs><a:gs pos="100000"><a:schemeClr val="phClr"><a:tint val="50000"/><a:satMod val="350000"/></a:schemeClr></a:gs></a:gsLst><a:lin ang="16200000" scaled="0"/></a:gradFill></a:fillStyleLst><a:lnStyleLst><a:ln w="9525" cap="flat" cmpd="sng" algn="ctr"><a:solidFill><a:schemeClr val="phClr"/></a:solidFill><a:prstDash val="solid"/></a:ln><a:ln w="25400" cap="flat" cmpd="sng" algn="ctr"><a:solidFill><a:schemeClr val="phClr"/></a:solidFill><a:prstDash val="solid"/></a:ln><a:ln w="38100" cap="flat" cmpd="sng" algn="ctr"><a:solidFill><a:schemeClr val="phClr"/></a:solidFill><a:prstDash val="solid"/></a:ln></a:lnStyleLst><a:effectStyleLst><a:effectStyle><a:effectLst/></a:effectStyle><a:effectStyle><a:effectLst/></a:effectStyle><a:effectStyle><a:effectLst/></a:effectStyle></a:effectStyleLst><a:bgFillStyleLst><a:solidFill><a:schemeClr val="phClr"/></a:solidFill><a:gradFill rotWithShape="1"><a:gsLst><a:gs pos="0"><a:schemeClr val="phClr"><a:tint val="40000"/><a:satMod val="350000"/></a:schemeClr></a:gs><a:gs pos="40000"><a:schemeClr val="phClr"><a:tint val="45000"/><a:shade val="99000"/><a:satMod val="350000"/></a:schemeClr></a:gs><a:gs pos="100000"><a:schemeClr val="phClr"><a:shade val="20000"/><a:satMod val="255000"/></a:schemeClr></a:gs></a:gsLst><a:path path="circle"><a:fillToRect l="50000" t="-80000" r="50000" b="180000"/></a:path></a:gradFill><a:gradFill rotWithShape="1"><a:gsLst><a:gs pos="0"><a:schemeClr val="phClr"><a:tint val="80000"/><a:satMod val="300000"/></a:schemeClr></a:gs><a:gs pos="100000"><a:schemeClr val="phClr"><a:shade val="30000"/><a:satMod val="200000"/></a:schemeClr></a:gs></a:gsLst><a:path path="circle"><a:fillToRect l="50000" t="50000" r="50000" b="50000"/></a:path></a:gradFill></a:bgFillStyleLst></a:fmtScheme></a:themeElements></a:theme>"""

    return buildList {
        add("[Content_Types].xml" to contentTypes.encodeToByteArray())
        add("_rels/.rels" to rootRels.encodeToByteArray())
        add("ppt/presentation.xml" to presentationXml.encodeToByteArray())
        add("ppt/_rels/presentation.xml.rels" to presentationRels.encodeToByteArray())
        add("ppt/presProps.xml" to presProps.encodeToByteArray())
        add("ppt/viewProps.xml" to viewProps.encodeToByteArray())
        add("ppt/theme/theme1.xml" to theme.encodeToByteArray())
        add("ppt/slideMasters/slideMaster1.xml" to slideMaster.encodeToByteArray())
        add("ppt/slideMasters/_rels/slideMaster1.xml.rels" to slideMasterRels.encodeToByteArray())
        add("ppt/slideLayouts/slideLayout1.xml" to slideLayout.encodeToByteArray())
        add("ppt/slideLayouts/_rels/slideLayout1.xml.rels" to slideLayoutRels.encodeToByteArray())
        slides.forEach { (n, slideXml, slideRels) ->
            add("ppt/slides/slide$n.xml" to slideXml.encodeToByteArray())
            add("ppt/slides/_rels/slide$n.xml.rels" to slideRels.encodeToByteArray())
        }
        media.forEach { (name, bytes) -> add(name to bytes) }
    }
}

private fun pptxSlideXml(page: ExportPage, relId: Int): String {
    val text = ocrLines(page.text).joinToString("\n").take(600)
    val pic = """<p:pic><p:nvPicPr><p:cNvPr id="2" name="Escaneo"/><p:cNvPicPr><a:picLocks noChangeAspect="1"/></p:cNvPicPr><p:nvPr/></p:nvPicPr><p:blipFill><a:blip xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" r:embed="rId$relId"/><a:stretch><a:fillRect/></a:stretch></p:blipFill><p:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="$SLIDE_W" cy="$SLIDE_H"/></a:xfrm><a:prstGeom prst="rect"><a:avLst/></a:prstGeom></p:spPr></p:pic>"""

    val spTreeContent = buildString {
        append("""<p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr>""")
        append("""<p:grpSpPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="0" cy="0"/><a:chOff x="0" y="0"/><a:chExt cx="0" cy="0"/></a:xfrm></p:grpSpPr>""")
        append(pic)
        if (text.isNotBlank()) {
            val clean = escapeXml(sanitizeXmlText(text))
            append("""<p:sp><p:nvSpPr><p:cNvPr id="3" name="Texto OCR"/><p:cNvSpPr txBox="1"/><p:nvPr/></p:nvSpPr><p:spPr><a:xfrm><a:off x="274320" y="5650000"/><a:ext cx="11612880" cy="900000"/></a:xfrm><a:prstGeom prst="rect"><a:avLst/></a:prstGeom><a:noFill/></p:spPr><p:txBody><a:bodyPr wrap="square" rtlCol="0" anchor="b"><a:normAutofit/></a:bodyPr><a:lstStyle/><a:p><a:pPr algn="ctr"/><a:r><a:rPr lang="es-ES" sz="1400"><a:solidFill><a:srgbClr val="FFFFFF"/></a:solidFill></a:rPr><a:t>$clean</a:t></a:r></a:p></p:txBody></p:sp>""")
        }
    }

    return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<p:sld xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"><p:cSld><p:spTree>$spTreeContent</p:spTree></p:cSld><p:clrMapOvr><a:masterClrMapping/></p:clrMapOvr></p:sld>"""
}

// ---------------------------------------------------------------------------
// XLSX (Excel) - una hoja, texto OCR por filas
// ---------------------------------------------------------------------------

private fun buildXlsx(pages: List<ExportPage>): List<Pair<String, ByteArray>> {
    val sheet = StringBuilder()
    var rowNum = 1

    fun row(cells: List<String>) {
        sheet.append("""<row r="$rowNum">""")
        cells.forEachIndexed { col, value ->
            val ref = "${'A' + col}$rowNum"
            sheet.append("""<c r="$ref" t="inlineStr"><is><t xml:space="preserve">${escapeXml(sanitizeXmlText(value))}</t></is></c>""")
        }
        sheet.append("</row>")
        rowNum++
    }

    pages.forEachIndexed { index, page ->
        if (index > 0) row(listOf(""))
        row(listOf("Página ${index + 1}"))
        val lines = ocrLines(page.text)
        if (lines.isEmpty()) {
            row(listOf("(sin texto OCR)"))
        } else {
            lines.forEach { row(listOf(it)) }
        }
    }

    val sheetXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>$sheet</sheetData></worksheet>"""

    val contentTypes = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/></Types>"""

    val rootRels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>"""

    val workbookXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Escaneo" sheetId="1" r:id="rId1"/></sheets></workbook>"""

    val workbookRels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/></Relationships>"""

    return listOf(
        "[Content_Types].xml" to contentTypes.encodeToByteArray(),
        "_rels/.rels" to rootRels.encodeToByteArray(),
        "xl/workbook.xml" to workbookXml.encodeToByteArray(),
        "xl/_rels/workbook.xml.rels" to workbookRels.encodeToByteArray(),
        "xl/worksheets/sheet1.xml" to sheetXml.encodeToByteArray(),
    )
}
