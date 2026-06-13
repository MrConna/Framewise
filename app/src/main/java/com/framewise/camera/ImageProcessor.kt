package com.framewise.camera

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import com.framewise.engine.types.Subject

/**
 * Interface for ML-based or heuristic image processors that detect subjects
 * and faces in a frame. The default [FrameAnalyzer] fallback uses a simple
 * centre-weighted heuristic when no [ImageProcessor] is supplied.
 *
 * In a production app this would delegate to ML Kit object detection,
 * a custom TFLite model, or MediaPipe face detection.
 */
interface ImageProcessor {

    /**
     * Detect all subjects (people, faces, salient objects) in the given bitmap.
     * The bitmap is always 512 px wide (maintaining aspect ratio) — the
     * [FrameAnalyzer] handles the downscale.
     */
    fun detectSubjects(bitmap: Bitmap): List<Subject>
}

/**
 * 滤镜工具函数：使用 [ColorMatrix] 对 Bitmap 应用滤镜效果。
 *
 * 支持的滤镜模式:
 * - "original": 不处理
 * - "warm": 暖色调 — 增强红/黄通道，减弱蓝通道
 * - "cool": 冷色调 — 增强蓝通道，减弱红/黄通道
 * - "vintage": 复古棕褐色调
 * - "bw": 黑白灰度
 *
 * @param bitmap   原始 Bitmap（不会被修改）
 * @param filter   滤镜模式字符串
 * @return         应用滤镜后的新 Bitmap，或 filter="original" 时返回原图
 */
fun applyColorFilterToBitmap(bitmap: Bitmap, filter: String): Bitmap {
    if (filter == "original") return bitmap

    val matrix = when (filter) {
        // 暖色: 增加红通道 20%，增加绿通道 10%，减少蓝通道 20%
        "warm" -> ColorMatrix(
            floatArrayOf(
                1.2f, 0.1f,  0f,   0f, 0f,
                0.1f, 1.05f, 0f,   0f, 0f,
                0f,   0f,    0.8f, 0f, 0f,
                0f,   0f,    0f,   1f, 0f,
            ),
        )
        // 冷色: 减少红通道 20%，增加蓝通道 20%
        "cool" -> ColorMatrix(
            floatArrayOf(
                0.8f, 0f,   0.1f, 0f, 0f,
                0f,   0.9f, 0.1f, 0f, 0f,
                0.1f, 0f,   1.2f, 0f, 0f,
                0f,   0f,   0f,   1f, 0f,
            ),
        )
        // 复古: 棕褐色调矩阵（经典 sepia 转换）
        "vintage" -> ColorMatrix(
            floatArrayOf(
                0.393f, 0.769f, 0.189f, 0f, 0f,
                0.349f, 0.686f, 0.168f, 0f, 0f,
                0.272f, 0.534f, 0.131f, 0f, 0f,
                0f,     0f,     0f,     1f, 0f,
            ),
        )
        // 黑白: 标准灰度转换 (ITU-R BT.601 亮度权重)
        "bw" -> ColorMatrix(
            floatArrayOf(
                0.299f, 0.587f, 0.114f, 0f, 0f,
                0.299f, 0.587f, 0.114f, 0f, 0f,
                0.299f, 0.587f, 0.114f, 0f, 0f,
                0f,     0f,     0f,     1f, 0f,
            ),
        )
        else -> ColorMatrix()
    }

    val output = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(output)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        colorFilter = ColorMatrixColorFilter(matrix)
    }
    canvas.drawBitmap(bitmap, 0f, 0f, paint)
    return output
}
