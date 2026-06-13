package com.framewise.ui.theme

import androidx.compose.ui.graphics.Color

val BackgroundDark = Color(0xFF0D1117)
val SurfaceDark = Color(0xFF161B22)
val AccentBlue = Color(0xFF58A6FF)

val ScorePerfect = Color(0xFF3FB950)
val ScoreGood = Color(0xFFD29922)
val ScoreBad = Color(0xFFF85149)

val White = Color(0xFFFFFFFF)
val Gray = Color(0xFF8B949E)
val LightGray = Color(0xFFC9D1D9)
val TransparentGrid = Color(0x33FFFFFF)

private val DarkColorScheme = androidx.compose.material3.darkColorScheme(
    primary = AccentBlue,
    background = BackgroundDark,
    surface = SurfaceDark,
    onBackground = White,
    onSurface = White
)

@androidx.compose.runtime.Composable
fun FramewiseTheme(content: @androidx.compose.runtime.Composable () -> Unit) {
    androidx.compose.material3.MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}

