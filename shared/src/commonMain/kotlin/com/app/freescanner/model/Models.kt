package com.app.freescanner.model

/**
 * Imagen en memoria, independiente de plataforma.
 * [data] contiene pixeles RGBA8888 empaquetados por filas (row-major), 4 bytes por pixel.
 */
class ImageData(
    val width: Int,
    val height: Int,
    val data: ByteArray,
) {
    init {
        require(data.size == width * height * 4) {
            "ImageData.size=${data.size} != ${width}x${height}x4"
        }
    }

    override fun equals(other: Any?): Boolean =
        other is ImageData && width == other.width && height == other.height && data.contentEquals(other.data)

    override fun hashCode(): Int = 31 * (31 * width + height) + data.contentHashCode()
}

data class Point(val x: Float, val y: Float)

/**
 * Cuadrilátero de 4 vértices ordenados: superior-izquierdo, superior-derecho,
 * inferior-derecho, inferior-izquierdo.
 */
class Quadrilateral(
    val topLeft: Point,
    val topRight: Point,
    val bottomRight: Point,
    val bottomLeft: Point,
) {
    val points: List<Point>
        get() = listOf(topLeft, topRight, bottomRight, bottomLeft)

    fun withPoint(index: Int, point: Point): Quadrilateral = when (index) {
        0 -> Quadrilateral(point, topRight, bottomRight, bottomLeft)
        1 -> Quadrilateral(topLeft, point, bottomRight, bottomLeft)
        2 -> Quadrilateral(topLeft, topRight, point, bottomLeft)
        else -> Quadrilateral(topLeft, topRight, bottomRight, point)
    }

    companion object {
        /** Cuadrilátero que cubre toda la imagen (fallback cuando no hay detección). */
        fun fullFrame(width: Int, height: Int): Quadrilateral = Quadrilateral(
            topLeft = Point(0f, 0f),
            topRight = Point(width.toFloat(), 0f),
            bottomRight = Point(width.toFloat(), height.toFloat()),
            bottomLeft = Point(0f, height.toFloat()),
        )
    }
}

data class Document(
    val id: String,
    val name: String,
    val createdAt: Long,
    val pageCount: Int,
)

data class Page(
    val id: String,
    val documentId: String,
    val imagePath: String,
    val width: Int,
    val height: Int,
    val ocrText: String?,
)
