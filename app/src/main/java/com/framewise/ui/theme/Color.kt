package com.framewise.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ── 颜色定义 ─────────────────────────────────────────────────────────────

val BackgroundDark = Color(0xFF0D1117)
val SurfaceDark = Color(0xFF161B22)
val BackgroundLight = Color(0xFFF6F8FA)
val SurfaceLight = Color(0xFFFFFFFF)

val AccentBlue = Color(0xFF58A6FF)
val AccentBlueLight = Color(0xFF0969DA)

val ScorePerfect = Color(0xFF3FB950)
val ScoreGood = Color(0xFFD29922)
val ScoreBad = Color(0xFFF85149)

val ScorePerfectLight = Color(0xFF1A7F37)
val ScoreGoodLight = Color(0xFF9A6700)
val ScoreBadLight = Color(0xFFCF222E)

val White = Color(0xFFFFFFFF)
val Black = Color(0xFF000000)
val Gray = Color(0xFF8B949E)
val GrayLight = Color(0xFF656D76)
val LightGray = Color(0xFFC9D1D9)
val TransparentGrid = Color(0x33FFFFFF)
val TransparentGridLight = Color(0x33000000)

// ── 配色方案 ──────────────────────────────────────────────────────────────

private val DarkColorScheme = darkColorScheme(
    primary = AccentBlue,
    onPrimary = Color.White,
    background = BackgroundDark,
    surface = SurfaceDark,
    surfaceVariant = Color(0xFF21262D),
    onBackground = Color(0xFFE6EDF3),
    onSurface = Color(0xFFE6EDF3),
    onSurfaceVariant = Color(0xFF8B949E),
    primaryContainer = Color(0xFF1F2937),
    onPrimaryContainer = AccentBlue,
    error = ScoreBad,
    onError = Color.White,
    outline = Color(0xFF30363D),
)

private val LightColorScheme = lightColorScheme(
    primary = AccentBlueLight,
    onPrimary = Color.White,
    background = BackgroundLight,
    surface = SurfaceLight,
    surfaceVariant = Color(0xFFE8ECF0),
    onBackground = Color(0xFF1F2328),
    onSurface = Color(0xFF1F2328),
    onSurfaceVariant = Color(0xFF656D76),
    primaryContainer = Color(0xFFDDF4FF),
    onPrimaryContainer = AccentBlueLight,
    error = ScoreBadLight,
    onError = Color.White,
    outline = Color(0xFFD0D7DE),
)

// ── 主题入口 ──────────────────────────────────────────────────────────────

/**
 * Framewise 主题入口。
 *
 * @param darkTheme true=深色模式，false=浅色模式。默认跟随系统设置。
 */
@Composable
fun FramewiseTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
