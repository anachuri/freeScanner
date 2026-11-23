package com.app.freescanner.platform

import com.app.freescanner.model.ImageData
import com.app.freescanner.model.Point
import com.app.freescanner.model.Quadrilateral
import kotlin.math.hypot
import kotlin.math.max
import org.opencv.calib3d.Calib3d
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Size
import org.opencv.android.OpenCVLoader
import org.opencv.core.Point as CvPoint
import org.opencv.imgproc.Imgproc

/**
 * Implementación Android del pipeline de escaneo con OpenCV.
 *
 * Trabaja directamente sobre los bytes RGBA de [ImageData] creando Mats
 * CV_8UC4, evitando conversiones intermedias por Bitmap.
 */
actual class DocumentScanner actual constructor() {

    init {
        if (!OpenCVLoader.initDebug()) {
            // OpenCV no pudo cargar las librerías nativas; las llamadas fallarán de forma controlada.
        }
    }

    private fun rgbaMat(image: ImageData): Mat {
        val mat = Mat(image.height, image.width, CvType.CV_8UC4)
        mat.put(0, 0, image.data)
        return mat
    }

    private fun Mat.toImageData(): ImageData {
        val w = cols()
        val h = rows()
        val bytes = ByteArray(w * h * channels())
        get(0, 0, bytes)
        return ImageData(w, h, bytes)
    }

    actual fun detectEdges(image: ImageData): Quadrilateral? {
        val src = rgbaMat(image)
        val gray = Mat()
        Imgproc.cvtColor(src, gray, Imgproc.COLOR_RGBA2GRAY)
        Imgproc.GaussianBlur(gray, gray, Size(5.0, 5.0), 0.0)

        val edges = Mat()
        Imgproc.Canny(gray, edges, 60.0, 160.0)
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0))
        Imgproc.morphologyEx(edges, edges, Imgproc.MORPH_CLOSE, kernel)

        val contours = ArrayList<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(edges, contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)

        val minArea = image.width * image.height * 0.15
        var bestArea = 0.0
        var best: Array<CvPoint>? = null

        for (contour in contours) {
            val contour2f = MatOfPoint2f(*contour.toArray())
            val perimeter = Imgproc.arcLength(contour2f, true)
            val approx = MatOfPoint2f()
            Imgproc.approxPolyDP(contour2f, approx, 0.02 * perimeter, true)
            if (approx.total().toInt() == 4) {
                val area = Imgproc.contourArea(approx)
                if (area > bestArea && area > minArea) {
                    bestArea = area
                    best = approx.toArray()
                }
            }
            approx.release()
            contour2f.release()
        }

        src.release(); gray.release(); edges.release(); hierarchy.release()
        contours.forEach { it.release() }

        val pts = best ?: return null
        return orderQuad(pts)
    }

    /** Ordena 4 puntos como TL, TR, BR, BL. */
    private fun orderQuad(pts: Array<CvPoint>): Quadrilateral {
        val tl = pts.minBy { it.x + it.y }
        val br = pts.maxBy { it.x + it.y }
        val tr = pts.filter { it !== tl && it !== br }.maxBy { it.x - it.y }
        val bl = pts.filter { it !== tl && it !== br && it !== tr }.first()
        return Quadrilateral(
            topLeft = Point(tl.x.toFloat(), tl.y.toFloat()),
            topRight = Point(tr.x.toFloat(), tr.y.toFloat()),
            bottomRight = Point(br.x.toFloat(), br.y.toFloat()),
            bottomLeft = Point(bl.x.toFloat(), bl.y.toFloat()),
        )
    }

    actual fun applyPerspectiveCorrection(image: ImageData, corners: Quadrilateral): ImageData {
        val src = rgbaMat(image)
        val srcPts = MatOfPoint2f(
            *corners.points.map { CvPoint(it.x.toDouble(), it.y.toDouble()) }.toTypedArray(),
        )

        val w = max(
            hypot(
                (corners.topRight.x - corners.topLeft.x).toDouble(),
                (corners.topRight.y - corners.topLeft.y).toDouble(),
            ),
            hypot(
                (corners.bottomRight.x - corners.bottomLeft.x).toDouble(),
                (corners.bottomRight.y - corners.bottomLeft.y).toDouble(),
            ),
        ).toInt().coerceAtLeast(1)
        val h = max(
            hypot(
                (corners.bottomLeft.x - corners.topLeft.x).toDouble(),
                (corners.bottomLeft.y - corners.topLeft.y).toDouble(),
            ),
            hypot(
                (corners.bottomRight.x - corners.topRight.x).toDouble(),
                (corners.bottomRight.y - corners.topRight.y).toDouble(),
            ),
        ).toInt().coerceAtLeast(1)

        val dstPts = MatOfPoint2f(
            CvPoint(0.0, 0.0), CvPoint(w.toDouble(), 0.0),
            CvPoint(w.toDouble(), h.toDouble()), CvPoint(0.0, h.toDouble()),
        )
        val transform = Calib3d.findHomography(srcPts, dstPts)
        val dst = Mat()
        Imgproc.warpPerspective(src, dst, transform, Size(w.toDouble(), h.toDouble()))

        srcPts.release(); dstPts.release(); transform.release(); src.release()
        return dst.toImageData()
    }

    actual fun applyScanFilter(image: ImageData): ImageData {
        val src = rgbaMat(image)
        val gray = Mat()
        Imgproc.cvtColor(src, gray, Imgproc.COLOR_RGBA2GRAY)

        val clahe = Imgproc.createCLAHE(2.0, Size(8.0, 8.0))
        val enhanced = Mat()
        clahe.apply(gray, enhanced)

        val bw = Mat()
        Imgproc.adaptiveThreshold(
            enhanced, bw, 255.0,
            Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, Imgproc.THRESH_BINARY,
            35, 15.0,
        )

        val out = Mat()
        Imgproc.cvtColor(bw, out, Imgproc.COLOR_GRAY2RGBA)

        src.release(); gray.release(); enhanced.release(); bw.release()
        return out.toImageData()
    }
}
