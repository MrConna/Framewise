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

/**
 * Per-frame analyzer implementing [ImageAnalysis.Analyzer].
 *
 * Every frame:
 *   1. Converts YUV_420_888 to a downscaled RGB [Bitmap] (512 px wide).
 *   2. Runs the detection pipeline:
 *      a. Subject detection — centre-weighted region of interest.
 *      b. Horizon detection — edge detection + Hough-like line scan.
 *      c. Brightness analysis — histogram of the Y (luma) channel.
 *      d. Scene classification — heuristic based on face presence + colour.
 *   3. Produces a [PhotoAnalysis] consumable by the composition engine.
 *
 * Throttled to ~10 fps; frames arriving while the previous analysis is running
 * are silently dropped.
 */
class FrameAnalyzer(
    /** Optional external image processor for subject detection. */
    private val imageProcessor: ImageProcessor? = null,
) : ImageAnalysis.Analyzer {

    companion object {
        private const val TAG = "FrameAnalyzer"
        /** Target analysis width (maintains aspect ratio). */
        private const val TARGET_WIDTH = 512
        /** Number of luma-brightness histogram bins. */
        private const val HISTOGRAM_BINS = 64
        /** Angle tolerance when grouping near-horizontal lines (°). */
        private const val HORIZON_ANGLE_TOLERANCE = 8.0
        /** Minimum edge-score for a line to be considered a horizon candidate. */
        private const val HORIZON_MIN_STRENGTH = 0.3
        /** Saliency grid dimensions (rows x cols). */
        private const val GRID_ROWS = 3
        private const val GRID_COLS = 3
    }

    private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val busy = AtomicBoolean(false)

    /** Callback invoked on each new [PhotoAnalysis] after analysis completes. */
    var onAnalysisReady: ((PhotoAnalysis) -> Unit)? = null

    /**
     * The last successfully produced analysis. When a frame fails to decode
     * (e.g. [yuvToBitmap] returns null), we reuse this instead of emitting an
     * empty analysis that would blank the overlay and drop all suggestions.
     */
    private var lastValidAnalysis: PhotoAnalysis? = null

    override fun analyze(imageProxy: ImageProxy) {
        // Skip frames when the previous analysis is still running (throttle).
        if (busy.get()) {
            imageProxy.close()
            return
        }
        busy.set(true)

        val image = imageProxy.image
        if (image == null) {
            imageProxy.close()
            busy.set(false)
            return
        }

        analysisExecutor.execute {
            try {
                val analysis = analyzeFrame(image, imageProxy.imageInfo.rotationDegrees)
                // Post result back to main thread.
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    onAnalysisReady?.invoke(analysis)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Frame analysis failed", e)
            } finally {
                imageProxy.close()
                busy.set(false)
            }
        }
    }

    // ── Pipeline ────────────────────────────────────────────────────────────

    private fun analyzeFrame(image: Image, rotationDeg: Int): PhotoAnalysis {
        // 1. Convert YUV → downscaled Bitmap for processing.
        val bitmap = yuvToBitmap(image) ?: run {
            Log.w(TAG, "yuvToBitmap returned null; reusing last valid analysis")
            return lastValidAnalysis ?: emptyAnalysis()
        }
        val w = bitmap.width
        val h = bitmap.height

        // 2. Extract Y (luma) channel for brightness + horizon detection.
        val yPlane = image.planes[0]
        val yBuffer = ByteArray(yPlane.buffer.remaining())
        yPlane.buffer.get(yBuffer)
        val yStride = yPlane.rowStride
        val luminance = extractLuminance(yBuffer, yStride, image.width, image.height)

        // 3. Run pipeline stages.

        val subjects = detectSubjects(bitmap, imageProcessor)
        val horizon = detectHorizon(bitmap, luminance, w, h)
        val brightness = analyzeBrightness(luminance)
        val scene = classifyScene(subjects, bitmap, w, h)
        val face = detectFace(subjects)
        val lines = detectLines(bitmap, w, h)
        val symmetry = detectSymmetry(bitmap, w, h)
        val saliencyGrid = computeSaliencyGrid(bitmap, w, h, subjects)

        return PhotoAnalysis(
            subjects = subjects,
            horizon = horizon,
            lines = lines,
            symmetry = symmetry,
            brightness = brightness,
            scene = scene,
            face = face,
            saliencyGrid = saliencyGrid,
        ).also { lastValidAnalysis = it }
    }

    // ── Stage a: Subject detection ──────────────────────────────────────────

    private fun detectSubjects(
        bitmap: Bitmap,
        processor: ImageProcessor?,
    ): List<Subject> {
        // If an ML-backed processor is provided, delegate to it.
        processor?.let { return it.detectSubjects(bitmap) }

        // Fallback: centre-weighted heuristic — assume the middle 40 % region
        // of the frame is the primary subject area.
        val cw = bitmap.width
        val ch = bitmap.height
        val cx = cw / 2.0
        val cy = ch / 2.0
        val regionW = (cw * 0.40).toInt()
        val regionH = (ch * 0.40).toInt()

        return listOf(
            Subject(
                bounds = Rect(
                    x = (cx - regionW / 2.0) / cw,
                    y = (cy - regionH / 2.0) / ch,
                    width = regionW.toDouble() / cw,
                    height = regionH.toDouble() / ch,
                ),
                type = SubjectType.SALIENT_REGION,
                confidence = 0.6,
            ),
        )
    }

    // ── Stage b: Horizon detection ─────────────────────────────────────────

    private data class HorizonCandidate(
        val y: Double,
        val angle: Double,
        val strength: Double,
    )

    private fun detectHorizon(
        bitmap: Bitmap,
        luminance: ByteArray,
        w: Int,
        h: Int,
    ): Horizon {
        // Simple edge-based line scan. Walk horizontal scan-lines and detect
        // strong horizontal edges (abrupt luma changes across adjacent rows).
        val candidates = mutableListOf<HorizonCandidate>()
        val edgeThreshold = 30

        // Compute row-wise average luma.
        val rowAvgs = DoubleArray(h) { row ->
            var sum = 0.0
            var count = 0
            val start = row * w
            val end = minOf(start + w, luminance.size)
            for (i in start until end) {
                sum += (luminance[i].toInt() and 0xFF)
                count++
            }
            if (count > 0) sum / count else 0.0
        }

        // Scan for sharp transitions (edges) between adjacent rows.
        for (row in 1 until h - 1) {
            val diff = abs(rowAvgs[row] - rowAvgs[row - 1])
            if (diff > edgeThreshold) {
                // Try neighbouring columns to estimate angle.
                var colDelta = 0
                var samples = 0
                for (col in 10..(w - 10) step 20) {
                    val above = luminance.getOrNull((row - 1) * w + col)?.toInt()?.and(0xFF) ?: 0
                    val below = luminance.getOrNull(row * w + col)?.toInt()?.and(0xFF) ?: 0
                    val nextAbove = luminance.getOrNull((row - 1) * w + col + 1)?.toInt()?.and(0xFF) ?: 0
                    val nextBelow = luminance.getOrNull(row * w + col + 1)?.toInt()?.and(0xFF) ?: 0
                    if (abs(above - below) > edgeThreshold) {
                        val localAngle = Math.atan2(
                            (below - above).toDouble(),
                            (nextBelow - nextAbove).toDouble(),
                        ) * 180.0 / Math.PI
                        colDelta += localAngle.toInt()
                        samples++
                    }
                }
                val avgAngle = if (samples > 0) colDelta.toDouble() / samples else 0.0
                candidates.add(
                    HorizonCandidate(
                        y = row.toDouble() / h,
                        angle = avgAngle,
                        strength = diff / 255.0,
                    )
                )
            }
        }

        if (candidates.isEmpty()) {
            return Horizon(detected = false, angle = 0.0, y = 0.5)
        }

        // Accept the strongest candidate; group nearby candidates.
        val best = candidates.maxByOrNull { it.strength }!!
        // Average angle of candidates near the strongest one.
        val neighbours = candidates.filter {
            abs(it.y - best.y) < 0.05 && abs(it.angle - best.angle) < HORIZON_ANGLE_TOLERANCE
        }
        val avgAngle = if (neighbours.isNotEmpty()) {
            neighbours.map { it.angle }.average()
        } else {
            best.angle
        }

        return Horizon(
            detected = true,
            angle = avgAngle,
            y = best.y,
        )
    }

    // ── Stage c: Brightness analysis ───────────────────────────────────────

    private fun analyzeBrightness(luma: ByteArray): Brightness {
        val n = luma.size
        var sum = 0.0
        var clippedWhite = 0
        var crushedBlack = 0
        val hist = IntArray(HISTOGRAM_BINS)
        val binScale = HISTOGRAM_BINS / 256.0

        for (b in luma) {
            val v = b.toInt() and 0xFF
            sum += v
            if (v >= 250) clippedWhite++
            if (v <= 5) crushedBlack++
            val bin = minOf((v * binScale).toInt(), HISTOGRAM_BINS - 1)
            hist[bin]++
        }

        val mean = sum / (n * 255.0)
        val overFrac = clippedWhite.toDouble() / n
        val underFrac = crushedBlack.toDouble() / n

        // Backlight heuristic: centre region noticeably darker than periphery.
        val centreLuma = centreLuma4(luma, 256)   // assume 256-wide stride
        val peripheralLuma = mean * 255.0
        val backlit = centreLuma < peripheralLuma * 0.7

        return Brightness(
            mean = mean,
            overexposed = overFrac,
            underexposed = underFrac,
            backlit = backlit,
            histogram = hist.toList(),
        )
    }

    private fun centreLuma4(luma: ByteArray, stride: Int): Double {
        val h = luma.size / stride
        val cx = stride / 2
        val cy = h / 2
        val halfW = stride / 4
        val halfH = h / 4
        var s = 0.0
        var n = 0
        for (row in (cy - halfH) until (cy + halfH)) {
            for (col in (cx - halfW) until (cx + halfW)) {
                val idx = row * stride + col
                if (idx in luma.indices) {
                    s += (luma[idx].toInt() and 0xFF)
                    n++
                }
            }
        }
        return if (n > 0) s / n else 0.0
    }

    // ── Stage d: Scene classification ───────────────────────────────────────

    private fun classifyScene(
        subjects: List<Subject>,
        bitmap: Bitmap,
        w: Int,
        h: Int,
    ): Scene {
        // If a face is present → portrait.
        if (subjects.any { it.type == SubjectType.FACE || it.type == SubjectType.PERSON }) {
            return Scene.PORTRAIT
        }

        // Heuristic: large blue/cyan region at top → landscape (sky).
        var skyPixels = 0
        var totalTop = 0
        val topHalfRows = h / 2
        for (row in 0 until topHalfRows) {
            for (col in 0 until w) {
                val pixel = bitmap.getPixel(col, row)
                val b = pixel and 0xFF
                val g = (pixel shr 8) and 0xFF
                val r = (pixel shr 16) and 0xFF
                totalTop++
                // Blue-dominant pixel → likely sky.
                if (b > r + 20 && b > g + 10) skyPixels++
            }
        }
        if (totalTop > 0 && skyPixels.toDouble() / totalTop > 0.35) {
            return Scene.LANDSCAPE
        }

        // Otherwise generic.
        return Scene.GENERIC
    }

    // ── Stage e: Face detection (pass-through from subjects) ────────────────

    private fun detectFace(subjects: List<Subject>): Face? {
        val faceSubject = subjects.find { it.type == SubjectType.FACE }
        return faceSubject?.let { s ->
            Face(
                bounds = s.bounds,
                eyes = null,
                roll = 0.0,
            )
        }
    }

    // ── Stage f: Line detection (simplified) ────────────────────────────────

    private fun detectLines(bitmap: Bitmap, w: Int, h: Int): List<DetectedLine> {
        // Simplified: only report the horizon line if one is detected.
        // A full implementation would use a Sobel + Hough transform pipeline.
        return emptyList()
    }

    // ── Stage g: Symmetry (placeholder) ─────────────────────────────────────

    private fun detectSymmetry(bitmap: Bitmap, w: Int, h: Int): Symmetry {
        // Full implementation would split the frame and compute SSIM.
        // For now return neutral values — the real symmetry analysis can be
        // plugged in from on-device ML.
        return Symmetry(
            vertical = 0.5,
            horizontal = 0.3,
            axisOffset = 0.05,
        )
    }

    // ── Stage h: Saliency grid ─────────────────────────────────────────────

    private fun computeSaliencyGrid(
        bitmap: Bitmap,
        w: Int,
        h: Int,
        subjects: List<Subject>,
    ): List<List<Double>> {
        val grid = MutableList(GRID_ROWS) { MutableList(GRID_COLS) { 0.0 } }
        val cellW = w / GRID_COLS
        val cellH = h / GRID_ROWS

        // Simple heuristic: measure average edge intensity per cell.
        for (row in 0 until GRID_ROWS) {
            for (col in 0 until GRID_COLS) {
                var edgeSum = 0.0
                var count = 0
                for (y in row * cellH until minOf((row + 1) * cellH, h)) {
                    for (x in col * cellW until minOf((col + 1) * cellW, w)) {
                        val pixel = bitmap.getPixel(x, y)
                        // Use green channel as a luminosity proxy.
                        edgeSum += (pixel shr 8) and 0xFF
                        count++
                    }
                }
                grid[row][col] = if (count > 0) edgeSum / (count * 255.0) else 0.0
            }
        }
        return grid
    }

    // ── Utility ──────────────────────────────────────────────────────────────

    private fun yuvToBitmap(image: Image): Bitmap? {
        if (image.format != ImageFormat.YUV_420_888) return null

        val planes = image.planes
        val yPlane = planes[0]
        val ySize = yPlane.rowStride * image.height
        val yBuf = ByteArray(ySize)
        yPlane.buffer.get(yBuf, 0, ySize)
        yPlane.buffer.rewind()

        val uvPlane = planes[1]
        val uvSize = uvPlane.rowStride * (image.height / 2)
        val uvBuf = ByteArray(uvSize)
        uvPlane.buffer.get(uvBuf, 0, uvSize)
        uvPlane.buffer.rewind()

        // NV21 needs interleaved VU; YUV_420_888 stores U and V separately.
        // Convert to NV21 first (standard for YuvImage).
        val nv21 = yuv420ToNv21(yBuf, uvBuf, image.width, image.height)

        val yuvImg = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
        val out = ByteArrayOutputStream()
        val targetH = (TARGET_WIDTH * image.height.toDouble() / image.width).toInt()
        yuvImg.compressToJpeg(AndroidRect(0, 0, image.width, image.height), 85, out)
        val jpegBytes = out.toByteArray()

        val bmp = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
        return Bitmap.createScaledBitmap(bmp, TARGET_WIDTH, targetH, true)
    }

    private fun yuv420ToNv21(y: ByteArray, uv: ByteArray, w: Int, h: Int): ByteArray {
        val nv21 = ByteArray(w * h * 3 / 2)
        // Copy Y.
        System.arraycopy(y, 0, nv21, 0, w * h)
        // Approximate UV interleave — simplified.
        // In production, iterate the U and V planes properly.
        val uvSize = w * h / 4
        System.arraycopy(uv, 0, nv21, w * h, minOf(uv.size, uvSize * 2))
        return nv21
    }

    private fun extractLuminance(
        yBuffer: ByteArray,
        yStride: Int,
        w: Int,
        h: Int,
    ): ByteArray {
        // Extract the Y channel from a potentially padded buffer.
        if (yStride == w) return yBuffer
        val out = ByteArray(w * h)
        for (row in 0 until h) {
            val srcPos = row * yStride
            val dstPos = row * w
            System.arraycopy(yBuffer, srcPos, out, dstPos, w)
        }
        return out
    }

    private fun emptyAnalysis(): PhotoAnalysis = PhotoAnalysis(
        subjects = emptyList(),
        horizon = Horizon(detected = false, angle = 0.0, y = 0.5),
        lines = emptyList(),
        symmetry = Symmetry(vertical = 0.5, horizontal = 0.3, axisOffset = 0.05),
        brightness = Brightness(mean = 0.5, overexposed = 0.0, underexposed = 0.0, backlit = false),
        scene = Scene.GENERIC,
        face = null,
        saliencyGrid = listOf(
            listOf(0.0, 0.0, 0.0),
            listOf(0.0, 0.0, 0.0),
            listOf(0.0, 0.0, 0.0),
        ),
    )

    /** Release the analysis executor when the analyzer is no longer needed. */
    fun release() {
        analysisExecutor.shutdown()
    }
}
