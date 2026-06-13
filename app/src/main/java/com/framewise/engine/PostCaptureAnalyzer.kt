package com.framewise.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.framewise.engine.types.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * 拍后构图分析结果（对标可颂 AI 辅助调整构图）。
 *
 * @param overallScore    最终评分 0-100
 * @param improvements    改进建议列表（中文）
 * @param suggestedCrop   推荐的裁剪区域
 * @param suggestedRotation 推荐的旋转角度
 * @param suggestedFilter 推荐的滤镜模式
 * @param comparisonWithLive 与实时分析的对比描述
 */
data class PostCaptureResult(
    val overallScore: Double,
    val improvements: List<ImprovementSuggestion>,
    val suggestedCrop: Rect? = null,
    val suggestedRotation: Double? = null,
    val suggestedFilter: String? = null,
    val comparisonWithLive: String? = null,
)

data class ImprovementSuggestion(
    val type: SuggestionType,
    val text: String,
    val severity: String = "info",  // "critical" | "warning" | "info"
)

/**
 * 拍后构图分析引擎（对标可颂 AI 辅助调整构图）。
 *
 * 拍照完成后对保存的照片进行二次分析，给出改进建议。
 */
class PostCaptureAnalyzer(
    private val engine: PhotoCompositionEngine,
) {

    companion object {
        private const val TAG = "PostCaptureAnalyzer"
    }

    /**
     * 分析已拍摄的照片，返回构图评分和建议。
     *
     * @param uri     保存到 MediaStore 的照片 URI
     * @param context Android Context
     */
    suspend fun analyzeCapturedPhoto(uri: Uri, context: Context): PostCaptureResult =
        withContext(Dispatchers.IO) {
            try {
                val bitmap = loadBitmap(uri, context) ?: return@withContext fallbackResult()
                val analysis = buildAnalysisFromBitmap(bitmap)
                val results = engine.evaluate(analysis)
                val score = engine.getOverallScore(results) * 100.0
                val improvements = generateImprovements(analysis, results)
                val crop = detectCropSuggestion(analysis)
                val rotation = detectRotationSuggestion(analysis)
                val filter = suggestFilter(analysis)

                PostCaptureResult(
                    overallScore = score,
                    improvements = improvements,
                    suggestedCrop = crop,
                    suggestedRotation = rotation,
                    suggestedFilter = filter,
                )
            } catch (e: Exception) {
                Log.e(TAG, "Post-capture analysis failed", e)
                fallbackResult()
            }
        }

    // ── 内部实现 ──────────────────────────────────────────────────────────

    private fun loadBitmap(uri: Uri, context: Context): Bitmap? {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return null
        return BitmapFactory.decodeStream(inputStream).also { inputStream.close() }
    }

    /** 从 Bitmap 构建简化的 PhotoAnalysis */
    private fun buildAnalysisFromBitmap(bitmap: Bitmap): PhotoAnalysis {
        val w = bitmap.width; val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        // 亮度分析
        var sum = 0; var clipped = 0; var crushed = 0
        val hist = IntArray(64)
        for (i in pixels.indices) {
            val r = (pixels[i] shr 16) and 0xFF
            val g = (pixels[i] shr 8) and 0xFF
            val b = pixels[i] and 0xFF
            val luma = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
            sum += luma
            if (luma >= 250) clipped++
            if (luma <= 5) crushed++
            hist[minOf(luma * 64 / 256, 63)]++
        }
        val mean = sum.toDouble() / (w * h * 255.0)

        // 中心区域亮度（背光检测）
        val cx = w / 2; val cy = h / 2
        val hw = w / 4; val hh = h / 4
        var centreSum = 0; var centreN = 0
        for (y in (cy - hh) until (cy + hh)) for (x in (cx - hw) until (cx + hw)) {
            if (y in 0 until h && x in 0 until w) {
                val r = (pixels[y * w + x] shr 16) and 0xFF
                val g = (pixels[y * w + x] shr 8) and 0xFF
                val b = pixels[y * w + x] and 0xFF
                centreSum += (0.299 * r + 0.587 * g + 0.114 * b).toInt()
                centreN++
            }
        }
        val centreLuma = if (centreN > 0) centreSum.toDouble() / centreN else 128.0
        val backlit = centreLuma < mean * 255.0 * 0.7

        val brightness = Brightness(mean, clipped.toDouble() / (w * h), crushed.toDouble() / (w * h), backlit, hist.toList())

        // 地平线检测（简化边缘扫描）
        val rowAvgs = DoubleArray(h) { row ->
            var s = 0.0
            for (col in 0 until w) {
                val p = pixels[row * w + col]
                s += ((p shr 16) and 0xFF) * 0.299 + ((p shr 8) and 0xFF) * 0.587 + (p and 0xFF) * 0.114
            }
            s / w
        }
        var bestEdge = -1; var bestStrength = 0.0
        for (row in 2 until h - 2) {
            val diff = abs(rowAvgs[row] - rowAvgs[row - 1]) + abs(rowAvgs[row] - rowAvgs[row + 1])
            if (diff > bestStrength) { bestStrength = diff; bestEdge = row }
        }
        val horizon = if (bestEdge >= 0 && bestStrength > 40) {
            // 估算地平线角度
            val leftAvg = (0 until h).filter { abs(rowAvgs[it] - rowAvgs[bestEdge]) < 20 }.average()
            val rightAvg = (0 until h).filter { abs(rowAvgs[it] - rowAvgs[bestEdge]) < 20 }.average()
            val angle = (leftAvg - rightAvg) * 0.5
            Horizon(true, angle, bestEdge.toDouble() / h)
        } else {
            Horizon(false, 0.0, 0.5)
        }

        // 主体检测（中心显著区域）
        var subX = 0.35; var subY = 0.35; var subW = 0.30; var subH = 0.30
        // 找最亮/对比度最高的区域作为主体候选
        var maxContrast = 0.0
        val gridSize = 4
        for (gx in 0 until gridSize) for (gy in 0 until gridSize) {
            val sx = gx * w / gridSize; val sy = gy * h / gridSize
            val ex = (gx + 1) * w / gridSize; val ey = (gy + 1) * h / gridSize
            var localSum = 0.0; var localSumSq = 0.0; var n = 0
            for (y in sy until ey) for (x in sx until ex) {
                val p = pixels[y * w + x]
                val l = ((p shr 16) and 0xFF) * 0.299 + ((p shr 8) and 0xFF) * 0.587 + (p and 0xFF) * 0.114
                localSum += l; localSumSq += l * l; n++
            }
            val m = if (n > 0) localSum / n else 0.0
            val c = if (n > 0) sqrt(localSumSq / n - m * m) else 0.0
            if (c > maxContrast) {
                maxContrast = c; subX = sx.toDouble() / w; subY = sy.toDouble() / h
                subW = (ex - sx).toDouble() / w; subH = (ey - sy).toDouble() / h
            }
        }

        val subjects = listOf(Subject(Rect(subX, subY, subW, subH), SubjectType.SALIENT_REGION, (maxContrast / 60.0).coerceIn(0.3, 1.0)))

        return PhotoAnalysis(subjects, horizon, emptyList(), Symmetry(0.5, 0.3, 0.05), brightness, Scene.GENERIC, null,
            listOf(listOf(0.0, 0.0, 0.0), listOf(0.0, 0.0, 0.0), listOf(0.0, 0.0, 0.0)))
    }

    /** 生成改进建议 */
    private fun generateImprovements(analysis: PhotoAnalysis, results: List<RuleResult>): List<ImprovementSuggestion> {
        val list = mutableListOf<ImprovementSuggestion>()

        // 检查主体位置
        val subject = analysis.subjects.maxByOrNull { it.confidence }
        if (subject != null) {
            val cx = subject.bounds.x + subject.bounds.width / 2
            val cy = subject.bounds.y + subject.bounds.height / 2
            val area = subject.bounds.width * subject.bounds.height
            if (cx < 0.25) list.add(ImprovementSuggestion(SuggestionType.MOVE_CAMERA, "主体偏左，下次可向右移动取景", "warning"))
            else if (cx > 0.75) list.add(ImprovementSuggestion(SuggestionType.MOVE_CAMERA, "主体偏右，下次可向左移动取景", "warning"))
            if (area < 0.06) list.add(ImprovementSuggestion(SuggestionType.ADJUST_ZOOM, "主体占画面比例偏小，建议拉近镜头", "warning"))
            if (area > 0.6) list.add(ImprovementSuggestion(SuggestionType.RECOMPOSE, "主体占画面比例过大，建议后退或广角拍摄", "info"))
        }

        // 检查水平线
        if (analysis.horizon.detected && abs(analysis.horizon.angle) > 3.0) {
            list.add(ImprovementSuggestion(SuggestionType.ROTATE, "画面倾斜 ${String.format("%.0f", abs(analysis.horizon.angle))}°，建议使用水平仪校准", "critical"))
        }

        // 检查亮度
        val b = analysis.brightness
        if (b.backlit) list.add(ImprovementSuggestion(SuggestionType.ADJUST_EXPOSURE, "背光拍摄，建议调整角度让光源在主体前方", "warning"))
        if (b.underexposed > 0.15) list.add(ImprovementSuggestion(SuggestionType.ADJUST_EXPOSURE, "画面偏暗，建议点亮场景或使用闪光灯", "warning"))
        if (b.overexposed > 0.1) list.add(ImprovementSuggestion(SuggestionType.ADJUST_EXPOSURE, "画面过曝，建议降低曝光补偿", "warning"))

        // 检查线条
        if (analysis.lines.isEmpty()) list.add(ImprovementSuggestion(SuggestionType.CHANGE_ANGLE, "画面缺少引导线，可尝试利用道路/栏杆增强纵深感", "info"))

        if (list.isEmpty()) list.add(ImprovementSuggestion(SuggestionType.INFO, "构图不错！可以尝试不同角度获得更多创意", "info"))

        return list
    }

    private fun detectCropSuggestion(analysis: PhotoAnalysis): Rect? {
        val subject = analysis.subjects.maxByOrNull { it.confidence } ?: return null
        val cx = subject.bounds.x + subject.bounds.width / 2
        val cy = subject.bounds.y + subject.bounds.height / 2
        if (abs(cx - 0.33) < 0.08 && abs(cy - 0.33) < 0.08) return null  // 已经在三分线交点
        // 推荐裁剪到三分线位置
        val cropX = (0.33 - subject.bounds.width / 2).coerceIn(0.0, 0.5)
        val cropY = (0.33 - subject.bounds.height / 2).coerceIn(0.0, 0.5)
        return Rect(cropX, cropY, subject.bounds.width * 1.3, subject.bounds.height * 1.3)
    }

    private fun detectRotationSuggestion(analysis: PhotoAnalysis): Double? {
        if (!analysis.horizon.detected || abs(analysis.horizon.angle) < 2.0) return null
        return -analysis.horizon.angle
    }

    private fun suggestFilter(analysis: PhotoAnalysis): String? {
        val b = analysis.brightness
        return when {
            b.backlit || b.mean < 0.35 -> "warm"
            b.mean > 0.75 -> "cool"
            analysis.subjects.any { it.type == SubjectType.FACE || it.type == SubjectType.PERSON } -> "warm"
            else -> null
        }
    }

    private fun fallbackResult() = PostCaptureResult(
        overallScore = 50.0,
        improvements = listOf(ImprovementSuggestion(SuggestionType.INFO, "无法分析此照片", "info")),
    )
}
