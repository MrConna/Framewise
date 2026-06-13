package com.framewise.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.framewise.engine.types.Brightness

/**
 * 实时曝光直方图（参考剪映实时直方图）。
 *
 * 显示亮度分布的 64-bin 柱状图，悬浮在取景器角落。
 * - 左暗右亮，红色 warning 表示欠曝/过曝
 * - 带 spring 动画平滑过渡
 */
@Composable
fun HistogramView(
    brightnessData: Brightness?,
    modifier: Modifier = Modifier,
) {
    if (brightnessData == null) return

    val hist = brightnessData.histogram
    if (hist.isEmpty()) return

    // spring 动画平滑过渡柱状图高度
    val animatedHeights = remember { hist.map { Animatable(0f) }.toMutableList() }
    LaunchedEffect(hist) {
        hist.forEachIndexed { i, v ->
            animatedHeights.getOrNull(i)?.animateTo(
                targetValue = (v.toFloat() / (hist.maxOrNull()?.toFloat() ?: 1f).coerceAtLeast(1f)),
                animationSpec = spring(dampingRatio = 0.7f, stiffness = 200f),
            )
        }
    }

    Column(
        modifier = modifier
            .width(140.dp)
            .height(96.dp)
            .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 直方图主体
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
        ) {
            val barWidth = size.width / hist.size
            val maxH = size.height

            // 绘制每个 bin 的柱状条
            hist.forEachIndexed { i, value ->
                val height = animatedHeights.getOrNull(i)?.value?.coerceIn(0.01f, 1f) ?: 0.01f
                val barH = maxH * height

                // 颜色：暗部红色，中间绿色，亮部蓝色
                val t = i.toFloat() / hist.size
                val color = when {
                    t < 0.15f -> Color(0xFFE74C3C).copy(alpha = 0.7f)      // 暗部警告红
                    t > 0.85f -> Color(0xFFE74C3C).copy(alpha = 0.7f)      // 亮部警告红
                    t < 0.4f -> Color(0xFF2ECC71).copy(alpha = 0.6f)       // 中间绿
                    t < 0.7f -> Color(0xFFF1C40F).copy(alpha = 0.7f)       // 中高黄
                    else -> Color(0xFF3498DB).copy(alpha = 0.7f)            // 高光蓝
                }

                drawRect(
                    color = color,
                    topLeft = Offset(i * barWidth, maxH - barH),
                    size = Size(barWidth * 0.85f, barH),
                )
            }

            // 过曝/欠曝标记线
            val overExposed = brightnessData.overexposed > 0.05
            val underExposed = brightnessData.underexposed > 0.02
            if (overExposed) {
                drawLine(Color.Red, Offset(size.width * 0.92f, 0f), Offset(size.width * 0.92f, size.height), strokeWidth = 1.dp.toPx())
            }
            if (underExposed) {
                drawLine(Color(0xFF3498DB), Offset(size.width * 0.08f, 0f), Offset(size.width * 0.08f, size.height), strokeWidth = 1.dp.toPx())
            }
        }

        // 标签：暗 / 亮
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("暗", fontSize = 9.sp, color = Color.White.copy(alpha = 0.5f))
            Text("亮", fontSize = 9.sp, color = Color.White.copy(alpha = 0.5f))
        }
    }
}
