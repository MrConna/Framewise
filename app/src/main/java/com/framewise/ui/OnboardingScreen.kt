package com.framewise.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.framewise.SettingsState
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

/**
 * 首次使用引导。3 页可滑动的介绍，每页配一段 Canvas 绘制的循环动画
 * （无需外部 Lottie json 资源）。看完后点击「开始拍照」会把
 * [SettingsState.onboardingCompleted] 置 true 并持久化，从此不再出现。
 *
 * @param onFinished 引导完成（或跳过）后的回调，宿主据此切换到主流程。
 */
@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
    val pageCount = 3
    val pagerState = rememberPagerState(pageCount = { pageCount })
    val scope = rememberCoroutineScope()

    fun complete() {
        SettingsState.onboardingCompleted = true
        onFinished()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // ── 顶部「跳过」 ─────────────────────────────────────────────────
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            TextButton(onClick = { complete() }) {
                Text("跳过")
            }
        }

        // ── 滑动页 ───────────────────────────────────────────────────────
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) { page ->
            when (page) {
                0 -> OnboardingPage(
                    title = "构图三分法",
                    body = "把画面横竖各三等分，将主体放在交叉点或分割线上，照片会更耐看、更有张力。",
                    illustration = { RuleOfThirdsAnimation(it) },
                )
                1 -> OnboardingPage(
                    title = "实时评分",
                    body = "取景时 Framewise 在本地实时分析画面，给出 0–100 的构图评分与改进提示，移动手机就能看到分数变化。",
                    illustration = { LiveScoreAnimation(it) },
                )
                else -> OnboardingPage(
                    title = "开始拍照",
                    body = "一切准备就绪。跟着引导线移动，等评分变高时按下快门，记录最好的瞬间。",
                    illustration = { ShutterAnimation(it) },
                )
            }
        }

        // ── 页码指示点 ─────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            repeat(pageCount) { index ->
                val selected = pagerState.currentPage == index
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .size(if (selected) 10.dp else 8.dp)
                        .clip(CircleShape)
                        .background(if (selected) primary else inactive),
                )
            }
        }

        // ── 底部操作区 ─────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            contentAlignment = Alignment.Center,
        ) {
            val isLast = pagerState.currentPage == pageCount - 1
            if (isLast) {
                Button(
                    onClick = { complete() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                ) {
                    Text("开始拍照", style = MaterialTheme.typography.titleMedium)
                }
            } else {
                Button(
                    onClick = {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                ) {
                    Text("下一步", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

// 指示点颜色（Canvas 内无法直接读 MaterialTheme，故在此提供常量近似色）。
private val primary = Color(0xFF4C6FFF)
private val inactive = Color(0x33888888)

/** 单页布局：上方动画插画，下方标题与说明文字。 */
@Composable
private fun OnboardingPage(
    title: String,
    body: String,
    illustration: @Composable (Modifier) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        illustration(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
        )
        Spacer(Modifier.height(40.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

// ── Canvas 动画插画 ───────────────────────────────────────────────────────

/**
 * 第 1 页：三分法网格 + 一个沿四个交叉点循环移动的主体圆点。
 */
@Composable
private fun RuleOfThirdsAnimation(modifier: Modifier) {
    val transition = rememberInfiniteTransition(label = "thirds")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "subject",
    )
    val grid = MaterialTheme.colorScheme.onSurfaceVariant
    val frame = MaterialTheme.colorScheme.outline
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val inset = size.minDimension * 0.08f
        val w = size.width - inset * 2
        val h = size.height - inset * 2
        val left = inset
        val top = inset

        // 外框
        drawRect(
            color = frame,
            topLeft = Offset(left, top),
            size = Size(w, h),
            style = Stroke(width = 4f),
        )
        // 三分线
        for (i in 1..2) {
            val x = left + w * i / 3f
            val y = top + h * i / 3f
            drawLine(grid, Offset(x, top), Offset(x, top + h), strokeWidth = 2f)
            drawLine(grid, Offset(left, y), Offset(left + w, y), strokeWidth = 2f)
        }

        // 四个交叉点
        val points = listOf(
            Offset(left + w / 3f, top + h / 3f),
            Offset(left + w * 2 / 3f, top + h / 3f),
            Offset(left + w * 2 / 3f, top + h * 2 / 3f),
            Offset(left + w / 3f, top + h * 2 / 3f),
        )
        // 在相邻两个交叉点之间线性插值，得到平滑移动的主体
        val seg = t.toInt() % 4
        val frac = t - t.toInt()
        val from = points[seg]
        val to = points[(seg + 1) % 4]
        val subject = Offset(
            from.x + (to.x - from.x) * frac,
            from.y + (to.y - from.y) * frac,
        )
        drawCircle(color = primary.copy(alpha = 0.25f), radius = size.minDimension * 0.10f, center = subject)
        drawCircle(color = primary, radius = size.minDimension * 0.05f, center = subject)
    }
}

/**
 * 第 2 页：环形评分表，分数从低到高循环扫动，并在中心显示当前分值的弧。
 */
@Composable
private fun LiveScoreAnimation(modifier: Modifier) {
    val transition = rememberInfiniteTransition(label = "score")
    val sweep by transition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "sweep",
    )
    val track = MaterialTheme.colorScheme.surfaceVariant
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val stroke = size.minDimension * 0.10f
        val pad = stroke
        val arcSize = Size(size.width - pad * 2, size.height - pad * 2)
        val topLeft = Offset(pad, pad)
        // 底环
        drawArc(
            color = track,
            startAngle = 135f,
            sweepAngle = 270f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
        // 分数弧：绿色越满分数越高
        val color = when {
            sweep > 0.75f -> Color(0xFF2ECC71)
            sweep > 0.5f -> Color(0xFFF1C40F)
            else -> Color(0xFFE67E22)
        }
        drawArc(
            color = color,
            startAngle = 135f,
            sweepAngle = 270f * sweep,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
        // 指针端点小圆
        val angleRad = Math.toRadians((135f + 270f * sweep).toDouble())
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r = (size.minDimension - pad * 2) / 2f
        val tip = Offset(
            cx + (r * cos(angleRad)).toFloat(),
            cy + (r * sin(angleRad)).toFloat(),
        )
        drawCircle(color = color, radius = stroke * 0.55f, center = tip)
    }
}

/**
 * 第 3 页：快门按钮，外环呼吸式放大缩小，提示用户「按下拍照」。
 */
@Composable
private fun ShutterAnimation(modifier: Modifier) {
    val transition = rememberInfiniteTransition(label = "shutter")
    val pulse by transition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )
    val ring = MaterialTheme.colorScheme.outline
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val base = size.minDimension * 0.28f
        // 呼吸外环
        drawCircle(
            color = ring.copy(alpha = 0.4f),
            radius = base * pulse * 1.25f,
            center = center,
            style = Stroke(width = 6f),
        )
        // 快门外圈
        drawCircle(color = Color.White, radius = base * pulse, center = center, style = Stroke(width = 10f))
        // 快门内圆
        drawCircle(color = primary, radius = base * pulse * 0.78f, center = center)
    }
}
