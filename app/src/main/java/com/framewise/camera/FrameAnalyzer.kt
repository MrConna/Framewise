package com.framewise.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect as AndroidRect
import android.graphics.YuvImage
import android.media.Image
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.framewise.engine.types.Brightness
import com.framewise.engine.types.DetectedLine
import com.framewise.engine.types.Face
import com.framewise.engine.types.Horizon
import com.framewise.engine.types.PhotoAnalysis
import com.framewise.engine.types.Point
import com.framewise.engine.types.Rect
import com.framewise.engine.types.Scene
import com.framewise.engine.types.Subject
import com.framewise.engine.types.SubjectType
import com.framewise.engine.types.Symmetry
import kotlin.math.abs
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

private fun logD(tag: String, msg: String) = Log.d("Framewise/$tag", msg)
private fun logW(tag: String, msg: String, e: Throwable? = null) {
    if (e != null) Log.w("Framewise/$tag", msg, e) else Log.w("Framewise/$tag", msg)
}
private fun logE(tag: String, msg: String, e: Throwable? = null) {
    if (e != null) Log.e("Framewise/$tag", msg, e) else Log.e("Framewise/$tag", msg)
}

/**
 * Per-frame analyzer implementing [ImageAnalysis.Analyzer].
 */
class FrameAnalyzer(
    private val imageProcessor: ImageProcessor? = null,
) : ImageAnalysis.Analyzer {

    companion object {
        private const val TAG = "FrameAnalyzer"
        private const val TARGET_WIDTH = 512
        private const val HISTOGRAM_BINS = 64
        private const val HORIZON_ANGLE_TOLERANCE = 8.0
        private const val GRID_ROWS = 3
        private const val GRID_COLS = 3
    }

    private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val busy = AtomicBoolean(false)

    var onAnalysisReady: ((PhotoAnalysis) -> Unit)? = null
    private var lastValidAnalysis: PhotoAnalysis? = null
    private var analyzedFrameCount = 0

    override fun analyze(imageProxy: ImageProxy) {
        if (busy.get()) { imageProxy.close(); return }
        busy.set(true)

        val image = imageProxy.image
        if (image == null) {
            logW(TAG, "imageProxy.image is null")
            imageProxy.close(); busy.set(false); return
        }

        val srcW = image.width
        val srcH = image.height
        val rot = imageProxy.imageInfo.rotationDegrees

        analysisExecutor.execute {
            try {
                val startNs = System.nanoTime()
                val analysis = analyzeFrame(image, rot, srcW, srcH)
                val elapsedMs = (System.nanoTime() - startNs) / 1_000_000.0
                if (elapsedMs > 200) logW(TAG, "analyzeFrame took ${String.format("%.1f", elapsedMs)}ms")
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    onAnalysisReady?.invoke(analysis)
                }
            } catch (e: Exception) {
                logE(TAG, "Frame analysis exception", e)
            } finally {
                imageProxy.close()
                busy.set(false)
            }
        }
    }

    private fun analyzeFrame(image: Image, rotationDeg: Int, srcW: Int, srcH: Int): PhotoAnalysis {
        val bitmap = yuvToBitmap(image, srcW, srcH) ?: run {
            logW(TAG, "yuvToBitmap returned null, reusing last valid analysis")
            return lastValidAnalysis ?: emptyAnalysis()
        }
        val w = bitmap.width
        val h = bitmap.height

        val yPlane = image.planes[0]
        val yBuffer = ByteArray(yPlane.buffer.remaining())
        yPlane.buffer.get(yBuffer)
        yPlane.buffer.rewind()
        val yStride = yPlane.rowStride
        val luminance = extractLuminance(yBuffer, yStride, srcW, srcH)

        val subjects = detectSubjects(bitmap, imageProcessor)
        val horizon = detectHorizon(bitmap, luminance, w, h)
        val brightness = analyzeBrightness(luminance, srcW)
        val scene = classifyScene(subjects, bitmap, w, h)
        val face = detectFace(subjects)
        val lines = detectLines(bitmap, w, h)
        val symmetry = detectSymmetry(bitmap, w, h)
        val saliencyGrid = computeSaliencyGrid(bitmap, w, h, subjects)

        analyzedFrameCount++
        if (analyzedFrameCount % 30 == 0) {
            logD(TAG, "Frame #$analyzedFrameCount: scene=$scene, subjects=${subjects.size}, horizon=${horizon.detected}")
        }

        val result = PhotoAnalysis(
            subjects = subjects, horizon = horizon, lines = lines,
            symmetry = symmetry, brightness = brightness, scene = scene,
            face = face, saliencyGrid = saliencyGrid,
        )
        lastValidAnalysis = result
        return result
    }

    // ── Stage a: Subject detection ──────────────────────────────────────────

    private fun detectSubjects(bitmap: Bitmap, processor: ImageProcessor?): List<Subject> {
        processor?.let { return it.detectSubjects(bitmap) }

        val w = bitmap.width; val h = bitmap.height
        val gridRows = 4; val gridCols = 4
        val cellW = w / gridCols; val cellH = h / gridRows

        data class Cell(val row: Int, val col: Int, val score: Double)
        val cells = mutableListOf<Cell>()

        for (r in 0 until gridRows) {
            for (c in 0 until gridCols) {
                var sum = 0.0; var sumSq = 0.0; var count = 0
                for (y in r * cellH until minOf((r + 1) * cellH, h)) {
                    for (x in c * cellW until minOf((c + 1) * cellW, w)) {
                        val lum = ((bitmap.getPixel(x, y) shr 8) and 0xFF).toDouble()
                        sum += lum; sumSq += lum * lum; count++
                    }
                }
                if (count > 0) {
                    val mean = sum / count
                    val contrast = kotlin.math.sqrt((sumSq / count - mean * mean).coerceAtLeast(0.0))
                    val centerDist = ((r - 1.5) * (r - 1.5) + (c - 1.5) * (c - 1.5)) / 4.0
                    cells.add(Cell(r, c, contrast * (0.7 + 0.3 * (1.0 - centerDist))))
                }
            }
        }

        val sorted = cells.sortedByDescending { it.score }
        if (sorted.isEmpty()) return listOf(Subject(Rect(0.3, 0.3, 0.4, 0.4), SubjectType.SALIENT_REGION, 0.5))

        val subjects = mutableListOf<Subject>()
        val best = sorted[0]
        subjects.add(Subject(Rect(best.col * cellW / w.toDouble(), best.row * cellH / h.toDouble(), cellW / w.toDouble(), cellH / h.toDouble()), SubjectType.SALIENT_REGION, (best.score / 60.0).coerceIn(0.3, 1.0)))
        if (sorted.size > 1) {
            val second = sorted[1]
            if (second.score > best.score * 0.5 && (abs(second.row - best.row) > 1 || abs(second.col - best.col) > 1))
                subjects.add(Subject(Rect(second.col * cellW / w.toDouble(), second.row * cellH / h.toDouble(), cellW / w.toDouble(), cellH / h.toDouble()), SubjectType.SALIENT_REGION, (second.score / 60.0).coerceIn(0.2, 0.8)))
        }
        return subjects
    }

    // ── Stage b: Horizon detection ─────────────────────────────────────────

    private data class HorizonCandidate(val y: Double, val angle: Double, val strength: Double)

    private fun detectHorizon(bitmap: Bitmap, luminance: ByteArray, w: Int, h: Int): Horizon {
        val rowAvgs = DoubleArray(h) { row ->
            var sum = 0.0; var count = 0
            val start = row * w; val end = minOf(start + w, luminance.size)
            for (i in start until end) { sum += (luminance[i].toInt() and 0xFF); count++ }
            if (count > 0) sum / count else 0.0
        }

        val candidates = mutableListOf<HorizonCandidate>()
        val edgeThreshold = if (rowAvgs.average() > 100) 40 else 20

        for (row in 1 until h - 1) {
            val diff = abs(rowAvgs[row] - rowAvgs[row - 1])
            if (diff > edgeThreshold) {
                var colDelta = 0; var samples = 0
                for (col in 10..(w - 10) step 20) {
                    val above = luminance.getOrNull((row - 1) * w + col)?.toInt()?.and(0xFF) ?: 0
                    val below = luminance.getOrNull(row * w + col)?.toInt()?.and(0xFF) ?: 0
                    val na = luminance.getOrNull((row - 1) * w + col + 1)?.toInt()?.and(0xFF) ?: 0
                    val nb = luminance.getOrNull(row * w + col + 1)?.toInt()?.and(0xFF) ?: 0
                    if (abs(above - below) > edgeThreshold) {
                        colDelta += (Math.atan2((below - above).toDouble(), (nb - na).toDouble()) * 180.0 / Math.PI).toInt()
                        samples++
                    }
                }
                candidates.add(HorizonCandidate(row.toDouble() / h, if (samples > 0) colDelta.toDouble() / samples else 0.0, diff / 255.0))
            }
        }

        if (candidates.isEmpty()) return Horizon(false, 0.0, 0.5)
        val best = candidates.maxByOrNull { it.strength }!!
        val neighbours = candidates.filter { abs(it.y - best.y) < 0.05 && abs(it.angle - best.angle) < HORIZON_ANGLE_TOLERANCE }
        return Horizon(true, if (neighbours.isNotEmpty()) neighbours.map { it.angle }.average() else best.angle, best.y)
    }

    // ── Stage c: Brightness analysis ───────────────────────────────────────

    private fun analyzeBrightness(luma: ByteArray, imgWidth: Int): Brightness {
        val n = luma.size; var sum = 0.0; var clipped = 0; var crushed = 0
        val hist = IntArray(HISTOGRAM_BINS); val binScale = HISTOGRAM_BINS / 256.0
        for (b in luma) { val v = b.toInt() and 0xFF; sum += v; if (v >= 250) clipped++; if (v <= 5) crushed++; hist[minOf((v * binScale).toInt(), HISTOGRAM_BINS - 1)]++ }
        val mean = sum / (n * 255.0)
        val centreLuma = centreLuma4(luma, imgWidth)
        return Brightness(mean, clipped.toDouble() / n, crushed.toDouble() / n, centreLuma < mean * 255.0 * 0.7, hist.toList())
    }

    private fun centreLuma4(luma: ByteArray, width: Int): Double {
        val h = luma.size / width; val cx = width / 2; val cy = h / 2
        val halfW = width / 4; val halfH = h / 4
        var s = 0.0; var n = 0
        for (row in (cy - halfH) until (cy + halfH)) {
            for (col in (cx - halfW) until (cx + halfW)) {
                val idx = row * width + col
                if (idx in luma.indices) { s += (luma[idx].toInt() and 0xFF); n++ }
            }
        }
        return if (n > 0) s / n else 0.0
    }

    // ── Stage d: Scene classification ───────────────────────────────────────

    private fun classifyScene(subjects: List<Subject>, bitmap: Bitmap, w: Int, h: Int): Scene {
        if (subjects.any { it.type == SubjectType.FACE || it.type == SubjectType.PERSON }) return Scene.PORTRAIT
        var skyPixels = 0; var totalTop = 0
        for (row in 0 until h / 2) for (col in 0 until w) {
            val pixel = bitmap.getPixel(col, row); val b = pixel and 0xFF; val g = (pixel shr 8) and 0xFF; val r = (pixel shr 16) and 0xFF; totalTop++
            if (b > r + 20 && b > g + 10) skyPixels++
        }
        return if (totalTop > 0 && skyPixels.toDouble() / totalTop > 0.35) Scene.LANDSCAPE else Scene.GENERIC
    }

    private fun detectFace(subjects: List<Subject>): Face? {
        val face = subjects.find { it.type == SubjectType.FACE }
        return face?.let { Face(it.bounds, null, 0.0) }
    }

    // ── Stage f: Line detection ────────────────────────────────────────────

    private fun detectLines(bitmap: Bitmap, w: Int, h: Int): List<DetectedLine> {
        val lines = mutableListOf<DetectedLine>()
        val gray = IntArray(w * h) { idx -> (bitmap.getPixel(idx % w, idx / w) shr 8) and 0xFF }

        for (row in 2 until h - 2) {
            var start = -1
            for (col in 1 until w - 1) {
                val diff = abs(gray[(row - 1) * w + col] - gray[(row + 1) * w + col])
                if (diff > 40 && start < 0) start = col
                else if (diff <= 40 && start >= 0) { if (col - start > w * 0.3) lines.add(DetectedLine(Point(start / w.toDouble(), row / h.toDouble()), Point(col / w.toDouble(), row / h.toDouble()), 0.0, diff / 255.0)); start = -1 }
            }
        }
        for (col in 2 until w - 2) {
            var start = -1
            for (row in 1 until h - 1) {
                val diff = abs(gray[row * w + (col - 1)] - gray[row * w + (col + 1)])
                if (diff > 40 && start < 0) start = row
                else if (diff <= 40 && start >= 0) { if (row - start > h * 0.3) lines.add(DetectedLine(Point(col / w.toDouble(), start / h.toDouble()), Point(col / w.toDouble(), row / h.toDouble()), 90.0, diff / 255.0)); start = -1 }
            }
        }
        return lines.take(4)
    }

    private fun detectSymmetry(bitmap: Bitmap, w: Int, h: Int) = Symmetry(0.5, 0.3, 0.05)

    private fun computeSaliencyGrid(bitmap: Bitmap, w: Int, h: Int, subjects: List<Subject>): List<List<Double>> {
        val grid = MutableList(GRID_ROWS) { MutableList(GRID_COLS) { 0.0 } }
        val cellW = w / GRID_COLS; val cellH = h / GRID_ROWS
        for (row in 0 until GRID_ROWS) for (col in 0 until GRID_COLS) {
            var sum = 0.0; var count = 0
            for (y in row * cellH until minOf((row + 1) * cellH, h)) for (x in col * cellW until minOf((col + 1) * cellW, w)) { sum += (bitmap.getPixel(x, y) shr 8) and 0xFF; count++ }
            grid[row][col] = if (count > 0) sum / (count * 255.0) else 0.0
        }
        return grid
    }

    // ── Utility ──────────────────────────────────────────────────────────────

    private fun yuvToBitmap(image: Image, srcW: Int, srcH: Int): Bitmap? {
        if (image.format != ImageFormat.YUV_420_888) { logW(TAG, "Unsupported format: ${image.format}"); return null }
        val targetH = (TARGET_WIDTH * srcH.toDouble() / srcW).toInt()

        try {
            val rgb = yuv420ToRgb888Direct(image, srcW, srcH)
            val bmp = Bitmap.createBitmap(rgb, srcW, srcH, Bitmap.Config.ARGB_8888)
            val scaled = Bitmap.createScaledBitmap(bmp, TARGET_WIDTH, targetH, true)
            if (bmp !== scaled) bmp.recycle()
            return scaled
        } catch (e: Exception) { logW(TAG, "Direct YUV->RGB failed, fallback to NV21", e) }

        try {
            val nv21 = yuv420ToNv21(image, srcW, srcH)
            val yuvImg = YuvImage(nv21, ImageFormat.NV21, srcW, srcH, null)
            val out = ByteArrayOutputStream()
            yuvImg.compressToJpeg(AndroidRect(0, 0, srcW, srcH), 85, out)
            val bmp = BitmapFactory.decodeByteArray(out.toByteArray(), 0, out.size())
            return if (bmp != null) Bitmap.createScaledBitmap(bmp, TARGET_WIDTH, targetH, true) else null
        } catch (e: Exception) { logE(TAG, "NV21->JPEG failed", e) }
        return null
    }

    private fun yuv420ToRgb888Direct(image: Image, w: Int, h: Int): IntArray {
        val yP = image.planes[0]; val uP = image.planes[1]; val vP = image.planes[2]
        val yBuf = ByteArray(yP.buffer.remaining()).also { yP.buffer.get(it); yP.buffer.rewind() }
        val uBuf = ByteArray(uP.buffer.remaining()).also { uP.buffer.get(it); uP.buffer.rewind() }
        val vBuf = ByteArray(vP.buffer.remaining()).also { vP.buffer.get(it); vP.buffer.rewind() }
        val yStride = yP.rowStride; val uStride = uP.rowStride; val vStride = vP.rowStride
        val uPStride = uP.pixelStride; val vPStride = vP.pixelStride

        val rgb = IntArray(w * h)
        for (row in 0 until h) for (col in 0 until w) {
            val y = (yBuf[row * yStride + col].toInt() and 0xFF).coerceIn(0, 255)
            val uvRow = row / 2; val uvCol = col / 2
            val u = (uBuf.getOrNull(uvRow * uStride + uvCol * uPStride)?.toInt()?.and(0xFF) ?: 128).coerceIn(0, 255)
            val v = (vBuf.getOrNull(uvRow * vStride + uvCol * vPStride)?.toInt()?.and(0xFF) ?: 128).coerceIn(0, 255)
            val r = (y + 1.402 * (v - 128)).toInt().coerceIn(0, 255)
            val g = (y - 0.344 * (u - 128) - 0.714 * (v - 128)).toInt().coerceIn(0, 255)
            val b = (y + 1.772 * (u - 128)).toInt().coerceIn(0, 255)
            rgb[row * w + col] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
        return rgb
    }

    private fun yuv420ToNv21(image: Image, w: Int, h: Int): ByteArray {
        val yP = image.planes[0]; val uP = image.planes[1]; val vP = image.planes[2]
        val yBuf = ByteArray(yP.buffer.remaining()).also { yP.buffer.get(it); yP.buffer.rewind() }
        val uBuf = ByteArray(uP.buffer.remaining()).also { uP.buffer.get(it); uP.buffer.rewind() }
        val vBuf = ByteArray(vP.buffer.remaining()).also { vP.buffer.get(it); vP.buffer.rewind() }
        val yStride = yP.rowStride; val uStride = uP.rowStride; val vStride = vP.rowStride
        val uPStride = uP.pixelStride; val vPStride = vP.pixelStride

        val nv21 = ByteArray(w * h * 3 / 2)
        for (row in 0 until h) System.arraycopy(yBuf, row * yStride, nv21, row * w, w)

        var idx = w * h
        for (row in 0 until h / 2) for (col in 0 until w / 2) {
            nv21[idx++] = vBuf.getOrNull(row * vStride + col * vPStride) ?: 0x80.toByte()
            nv21[idx++] = uBuf.getOrNull(row * uStride + col * uPStride) ?: 0x80.toByte()
        }
        return nv21
    }

    private fun extractLuminance(yBuffer: ByteArray, yStride: Int, w: Int, h: Int): ByteArray {
        if (yStride == w) return yBuffer
        val out = ByteArray(w * h)
        for (row in 0 until h) System.arraycopy(yBuffer, row * yStride, out, row * w, w)
        return out
    }

    private fun emptyAnalysis() = PhotoAnalysis(emptyList(), Horizon(false, 0.0, 0.5), emptyList(), Symmetry(0.5, 0.3, 0.05), Brightness(0.5, 0.0, 0.0, false), Scene.GENERIC, null, listOf(listOf(0.0, 0.0, 0.0), listOf(0.0, 0.0, 0.0), listOf(0.0, 0.0, 0.0)))

    fun release() { analysisExecutor.shutdown() }
}
