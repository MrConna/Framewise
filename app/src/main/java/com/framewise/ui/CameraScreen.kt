package com.framewise.ui

import android.graphics.Paint
import android.net.Uri
import android.view.ViewGroup
import android.widget.Toast
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import coil.compose.rememberAsyncImagePainter
import com.framewise.R
import com.framewise.SettingsState
import com.framewise.camera.CameraCompositionPipeline
import com.framewise.camera.CameraController
import com.framewise.camera.FrameAnalyzer
import com.framewise.engine.PhotoCompositionEngine
import com.framewise.engine.rules.ALL_RULES
import com.framewise.engine.types.Suggestion
import com.framewise.engine.types.SuggestionType
import com.framewise.engine.types.PhotoAnalysis
import com.framewise.engine.types.Scene
import com.framewise.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.min

@Composable
fun CameraScreen(
    onNavigateToSettings: () -> Unit,
    onNavigateToGallery: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val composableScope = rememberCoroutineScope()

    // Localized strings (resolved in composition; reused inside callbacks).
    val pointHint = stringResource(R.string.point_hint)
    val photoSavedMsg = stringResource(R.string.photo_saved)
    val cameraNotReadyMsg = stringResource(R.string.camera_not_ready)
    val retryLabel = stringResource(R.string.retry)
    val shutterHint = stringResource(R.string.shutter_hint)
    val scoreLabel = stringResource(R.string.score)
    val galleryLabel = stringResource(R.string.gallery_title)
    val settingsLabel = stringResource(R.string.settings_title)

    // Real composition pipeline: FrameAnalyzer → PhotoCompositionEngine (13 rules) → result.
    // Scope is passed for demo-mode timeout fallback.
    val frameAnalyzer = remember { FrameAnalyzer() }
    val pipeline = remember {
        CameraCompositionPipeline(
            frameAnalyzer = frameAnalyzer,
            compositionEngine = PhotoCompositionEngine(ALL_RULES),
            scope = composableScope,
        ).also { it.attach() }
    }

    // Observe the engine output and the raw analysis (used by the overlay).
    val photoAnalysis by pipeline.photoAnalysis.collectAsState()
    val compositionResult by pipeline.compositionResult.collectAsState()

    val previewView = remember {
        PreviewView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
    }

    val cameraController = remember {
        CameraController(
            lifecycleOwner = lifecycleOwner,
            previewView = previewView,
            frameAnalyzer = frameAnalyzer
        )
    }

    val cameraState by cameraController.state.collectAsState()
    var gridVisible by remember { mutableStateOf(true) }
    var showExampleOverlay by remember { mutableStateOf(false) }

    // Shutter flash: white overlay fades in then out over ~200ms on capture.
    val coroutineScope = rememberCoroutineScope()
    val flashAlpha = remember { Animatable(0f) }

    // Most recent captured photo — shown as a thumbnail in the bottom-left corner.
    var lastPhotoUri by remember { mutableStateOf<Uri?>(null) }

    // Lifecycle management runs ONCE per composition entry. We must NOT release
    // the analyzer on dispose — CameraX unbinds via the lifecycle automatically.
    DisposableEffect(Unit) {
        cameraController.bindToLifecycle()
        onDispose {
            cameraController.unbindFromLifecycle()
        }
    }

    // Camera-level status. If no PhotoAnalysis has arrived yet, surface a
    // progressive message after 500ms, then after 3s.
    var cameraStatus by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(photoAnalysis != null) {
        if (photoAnalysis == null) {
            cameraStatus = null
            delay(500)
            if (photoAnalysis == null) cameraStatus = "相机启动中…"
            delay(2500)
            if (photoAnalysis == null) cameraStatus = "相机可能需要重启"
        } else {
            cameraStatus = null
        }
    }

    // Scene-level fallback: frames flowing but nothing detected for >2s.
    val sceneEmpty = photoAnalysis.let { a ->
        a != null && a.subjects.isEmpty() && !a.horizon.detected && a.lines.isEmpty()
    }
    var showEmptyHint by remember { mutableStateOf(false) }
    LaunchedEffect(sceneEmpty) {
        if (sceneEmpty) {
            delay(2000)
            showEmptyHint = true
        } else {
            showEmptyHint = false
        }
    }

    val overlayMessage = cameraStatus ?: if (showEmptyHint) pointHint else null

    // Score-derived accent color shared by the gauge and the suggestion chips.
    val scoreColor = compositionResult?.let { result ->
        when {
            result.overallScore >= 85 -> ScorePerfect
            result.overallScore >= 70 -> ScoreGood
            else -> ScoreBad
        }
    } ?: AccentBlue
    val guidanceCues = remember(photoAnalysis) { buildGuidanceCues(photoAnalysis) }

    Box(modifier = Modifier.fillMaxSize().background(BackgroundDark)) {
        // 1. Full screen camera preview.
        AndroidView(
            factory = { previewView },
            modifier = Modifier
                .fillMaxSize()
                .zIndex(0f)
                .pointerInput(cameraController) {
                    detectTransformGestures { _, _, zoom, _ ->
                        if (zoom != 1f) cameraController.updateZoom(zoom)
                    }
                }
        )

        FilterPreviewOverlay(filterMode = cameraController.filterMode)

        // 2. Compose Canvas overlay (grid + horizon + subject boxes).
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(0.2f)
        ) {
            val width = size.width
            val height = size.height

            // A. Rule-of-thirds grid with a soft glow (wide low-alpha pass first).
            if (gridVisible) {
                val lines = listOf(
                    Offset(width / 3f, 0f) to Offset(width / 3f, height),
                    Offset(width * 2f / 3f, 0f) to Offset(width * 2f / 3f, height),
                    Offset(0f, height / 3f) to Offset(width, height / 3f),
                    Offset(0f, height * 2f / 3f) to Offset(width, height * 2f / 3f),
                )
                lines.forEach { (start, end) ->
                    // Glow pass.
                    drawLine(
                        color = AccentBlue.copy(alpha = 0.18f),
                        start = start,
                        end = end,
                        strokeWidth = 6.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                    // Crisp line.
                    drawLine(
                        color = TransparentGrid,
                        start = start,
                        end = end,
                        strokeWidth = 1.5.dp.toPx()
                    )
                }
            }

            // B. Horizon level indicator.
            photoAnalysis?.horizon?.let { horizon ->
                if (horizon.detected) {
                    val angle = horizon.angle.toFloat()
                    val horizonY = (horizon.y * height).toFloat()
                    val color = if (abs(angle) < 2f) ScorePerfect else ScoreGood

                    rotate(degrees = angle, pivot = Offset(width / 2f, horizonY)) {
                        drawLine(
                            color = color.copy(alpha = 0.6f),
                            start = Offset(width * 0.1f, horizonY),
                            end = Offset(width * 0.9f, horizonY),
                            strokeWidth = 2.dp.toPx()
                        )
                    }
                }
            }

            // C. Subject framing boxes.
            photoAnalysis?.subjects?.forEach { subject ->
                val box = subject.bounds
                drawRect(
                    color = AccentBlue.copy(alpha = 0.5f),
                    topLeft = Offset((box.x * width).toFloat(), (box.y * height).toFloat()),
                    size = Size((box.width * width).toFloat(), (box.height * height).toFloat()),
                    style = Stroke(width = 2.dp.toPx())
                )
            }

            if (showExampleOverlay) {
                drawExampleOverlay(photoAnalysis?.scene ?: Scene.GENERIC, width, height)
            }

            guidanceCues.forEach { cue ->
                drawGuidanceArrow(cue.direction, cue.x * width, cue.y * height, cue.text)
            }
        }

        // C2. Status / empty-scene fallback message.
        overlayMessage?.let { message ->
            Surface(
                shape = MaterialTheme.shapes.large,
                color = SurfaceDark.copy(alpha = 0.8f),
                contentColor = White,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp)
                    .zIndex(1f)
            ) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyLarge,
                    color = White,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)
                )
            }
        }

        // D. Top-left settings icon.
        IconButton(
            onClick = onNavigateToSettings,
            modifier = Modifier
                .statusBarsPadding()
                .align(Alignment.TopStart)
                .padding(16.dp)
                .zIndex(2f)
                .background(SurfaceDark.copy(alpha = 0.7f), CircleShape)
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = settingsLabel,
                tint = White
            )
        }

        IconButton(
            onClick = { gridVisible = !gridVisible },
            modifier = Modifier
                .statusBarsPadding()
                .align(Alignment.TopStart)
                .padding(start = 76.dp, top = 16.dp)
                .size(48.dp)
                .zIndex(2f)
                .background(
                    if (gridVisible) AccentBlue.copy(alpha = 0.78f) else SurfaceDark.copy(alpha = 0.7f),
                    CircleShape
                )
                .border(
                    1.dp,
                    if (gridVisible) White.copy(alpha = 0.65f) else White.copy(alpha = 0.12f),
                    CircleShape
                )
        ) {
            Text(
                text = "格",
                style = MaterialTheme.typography.labelLarge,
                color = White
            )
        }

        TextButton(
            onClick = { showExampleOverlay = !showExampleOverlay },
            modifier = Modifier
                .statusBarsPadding()
                .align(Alignment.TopStart)
                .padding(start = 132.dp, top = 18.dp)
                .height(44.dp)
                .zIndex(2f)
                .background(
                    if (showExampleOverlay) AccentBlue.copy(alpha = 0.78f) else SurfaceDark.copy(alpha = 0.7f),
                    RoundedCornerShape(22.dp)
                )
                .border(
                    1.dp,
                    if (showExampleOverlay) White.copy(alpha = 0.65f) else White.copy(alpha = 0.12f),
                    RoundedCornerShape(22.dp)
                )
        ) {
            Text(
                text = "示例",
                color = White,
                style = MaterialTheme.typography.labelMedium
            )
        }

        // D2. Top-right camera toggle.
        IconButton(
            onClick = { cameraController.flipCamera() },
            modifier = Modifier
                .statusBarsPadding()
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .zIndex(2f)
                .background(SurfaceDark.copy(alpha = 0.7f), CircleShape)
        ) {
            Icon(
                imageVector = Icons.Default.Cached,
                contentDescription = "切换镜头",
                tint = White
            )
        }

        // E. Redesign #1 — semicircular speedometer-style score gauge, top-center.
        compositionResult?.let { result ->
            Box(
                modifier = Modifier
                    .statusBarsPadding()
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp)
                    .zIndex(2f)
            ) {
                ScoreArcGauge(
                    score = result.overallScore.toInt(),
                    color = scoreColor,
                    label = scoreLabel
                )
            }
        }

        // F. Bottom panel — frosted glass card with suggestions + controls.
        Surface(
            color = SurfaceDark.copy(alpha = 0.55f),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .zIndex(2f)
                .border(
                    width = 1.dp,
                    color = White.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
                )
        ) {
            Column(
                modifier = Modifier
                    .navigationBarsPadding()
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Suggestion chips (real or demo fallback), bordered by score color.
                val suggestions = sceneSpecificSuggestions(photoAnalysis?.scene)
                    .ifEmpty { compositionResult?.bestSuggestions.orEmpty() }
                    .takeIf { it.isNotEmpty() }
                    ?: listOf(
                        Suggestion(SuggestionType.INFO, "对准场景获取建议"),
                        Suggestion(SuggestionType.MOVE_CAMERA, "将主体移到三分线交点"),
                        Suggestion(SuggestionType.CHANGE_ANGLE, "换个角度寻找侧光"),
                    )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
                ) {
                    suggestions.forEach { suggestion ->
                        SuggestionChip(suggestion = suggestion, borderColor = scoreColor)
                    }
                }

                FilterSelector(
                    selectedFilter = cameraController.filterMode,
                    onFilterSelected = cameraController::selectFilterMode,
                    modifier = Modifier.fillMaxWidth()
                )

                // Control bar: recent-photo thumbnail / gallery + torch + shutter.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Bottom-left: recent photo thumbnail (after first capture) or gallery icon.
                    val thumb = lastPhotoUri
                    if (thumb != null) {
                        Image(
                            painter = rememberAsyncImagePainter(thumb),
                            contentDescription = galleryLabel,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .size(54.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .border(2.dp, White.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                                .clickable { onNavigateToGallery() }
                        )
                    } else {
                        IconButton(
                            onClick = onNavigateToGallery,
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .size(54.dp)
                                .background(SurfaceDark.copy(alpha = 0.7f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PhotoLibrary,
                                contentDescription = galleryLabel,
                                tint = White
                            )
                        }
                    }

                    IconButton(
                        onClick = { cameraController.toggleTorch() },
                        enabled = cameraState.isReady,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .offset(x = (-74).dp)
                            .size(54.dp)
                            .background(
                                if (cameraState.isTorchOn) AccentBlue.copy(alpha = 0.85f) else SurfaceDark.copy(alpha = 0.7f),
                                CircleShape,
                            )
                    ) {
                        Text(
                            text = if (cameraState.isTorchOn) "闪" else "灯",
                            style = MaterialTheme.typography.labelLarge,
                            color = White
                        )
                    }

                    IconButton(
                        onClick = { cameraController.cycleTimer() },
                        enabled = cameraState.isReady,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .offset(x = 74.dp)
                            .size(54.dp)
                            .background(
                                if (cameraController.timerSeconds > 0) AccentBlue.copy(alpha = 0.85f) else SurfaceDark.copy(alpha = 0.7f),
                                CircleShape,
                            )
                    ) {
                        Text(
                            text = when (cameraController.timerSeconds) {
                                3 -> "3秒"
                                10 -> "10"
                                else -> "定"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = White
                        )
                    }

                    // Center: shutter with a pulsing focus ring while initializing.
                    Box(contentAlignment = Alignment.Center) {
                        if (!cameraState.isReady) {
                            PulsingFocusRing()
                        }
                        LargeFloatingActionButton(
                            onClick = {
                                if (cameraState.isReady) {
                                    cameraController.captureWithTimer { uri ->
                                        val msg = photoSavedMsg
                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                        SettingsState.capturedCount++
                                        lastPhotoUri = uri
                                        coroutineScope.launch {
                                            flashAlpha.animateTo(1f, tween(100))
                                            flashAlpha.animateTo(0f, tween(100))
                                        }
                                    }
                                } else {
                                    Toast.makeText(context, cameraNotReadyMsg, Toast.LENGTH_SHORT).show()
                                    cameraController.requestBinding()
                                }
                            },
                            modifier = Modifier.size(76.dp),
                            shape = CircleShape,
                            containerColor = White,
                            contentColor = SurfaceDark
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(60.dp)
                                    .border(3.dp, SurfaceDark, CircleShape)
                                    .background(Color.Transparent, CircleShape)
                            )
                        }
                    }
                }

                Text(
                    text = if (cameraController.timerSeconds > 0) "${cameraController.timerSeconds}秒定时" else shutterHint,
                    style = MaterialTheme.typography.labelSmall,
                    color = White.copy(alpha = 0.6f)
                )
            }
        }

        ZoomSlider(
            zoomRatio = cameraState.zoomRatio,
            maxZoom = cameraController.maxZoom,
            enabled = cameraState.isReady,
            onZoomChange = cameraController::setZoom,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 10.dp)
                .zIndex(2f)
        )

        if (cameraController.countdownSeconds > 0) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(3f)
                    .background(Color.Black.copy(alpha = 0.24f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = cameraController.countdownSeconds.toString(),
                    style = MaterialTheme.typography.displayLarge,
                    color = White
                )
            }
        }

        // Shutter flash overlay (above the controls).
        if (flashAlpha.value > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(4f)
                    .background(Color.White.copy(alpha = flashAlpha.value))
            )
        }

        // Recoverable camera-binding error banner (drawn last, top of screen).
        cameraState.errorMessage?.let { error ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .zIndex(5f),
                color = Color(0xFFD32F2F)
            ) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "相机错误：$error",
                        color = White,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { cameraController.bindToLifecycle() }) {
                        Text(retryLabel, color = White)
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterPreviewOverlay(filterMode: String) {
    val color = when (filterMode) {
        "warm" -> Color(0xFFFFB36B).copy(alpha = 0.16f)
        "cool" -> Color(0xFF6BB7FF).copy(alpha = 0.16f)
        "vintage" -> Color(0xFFC49A63).copy(alpha = 0.18f)
        "bw" -> Color.Black.copy(alpha = 0.18f)
        else -> Color.Transparent
    }
    if (color.alpha > 0f) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(0.1f)
                .background(color)
        )
    }
}

@Composable
private fun FilterSelector(
    selectedFilter: String,
    onFilterSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val filters = listOf(
        FilterOption("original", "原图", Color.White),
        FilterOption("warm", "暖色", Color(0xFFFFB36B)),
        FilterOption("cool", "冷色", Color(0xFF6BB7FF)),
        FilterOption("vintage", "复古", Color(0xFFC49A63)),
        FilterOption("bw", "黑白", Color(0xFFBDBDBD)),
    )
    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        filters.forEach { filter ->
            val selected = filter.id == selectedFilter
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onFilterSelected(filter.id) }
                    .border(
                        width = if (selected) 2.dp else 1.dp,
                        color = if (selected) AccentBlue else White.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(14.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 7.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(filter.color, CircleShape)
                        .border(1.dp, White.copy(alpha = 0.5f), CircleShape)
                )
                Text(
                    text = filter.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = White.copy(alpha = if (selected) 1f else 0.72f)
                )
            }
        }
    }
}

private data class FilterOption(
    val id: String,
    val label: String,
    val color: Color,
)

private data class GuidanceCue(
    val direction: String,
    val x: Float,
    val y: Float,
    val text: String,
)

private fun buildGuidanceCues(analysis: PhotoAnalysis?): List<GuidanceCue> {
    if (analysis == null) return emptyList()
    val cues = mutableListOf<GuidanceCue>()
    val subject = analysis.subjects.maxByOrNull { it.confidence * it.bounds.width * it.bounds.height }
    if (subject != null) {
        val centerX = (subject.bounds.x + subject.bounds.width / 2.0).toFloat()
        val centerY = (subject.bounds.y + subject.bounds.height / 2.0).toFloat()
        when {
            centerX < 0.42f -> cues += GuidanceCue("right", centerX, centerY, "→ 向右移动")
            centerX > 0.58f -> cues += GuidanceCue("left", centerX, centerY, "← 向左移动")
        }
        when {
            centerY < 0.30f -> cues += GuidanceCue("down", centerX, centerY, "↓ 向下压低")
            centerY > 0.72f -> cues += GuidanceCue("up", centerX, centerY, "↑ 向上抬高")
        }
        val area = subject.bounds.width * subject.bounds.height
        if (area < 0.08) {
            cues += GuidanceCue("zoom_in", centerX.coerceIn(0.2f, 0.8f), (centerY + 0.12f).coerceIn(0.2f, 0.82f), "🔍 拉近镜头")
        }
    }

    if (analysis.horizon.detected && abs(analysis.horizon.angle) > 1.2) {
        val direction = if (analysis.horizon.angle > 0) "rotate_ccw" else "rotate_cw"
        val arrow = if (analysis.horizon.angle > 0) "↺" else "↻"
        val action = if (analysis.horizon.angle > 0) "向左旋转" else "向右旋转"
        cues += GuidanceCue(
            direction = direction,
            x = 0.5f,
            y = analysis.horizon.y.toFloat().coerceIn(0.18f, 0.72f),
            text = "$arrow $action ${abs(analysis.horizon.angle).toInt().coerceAtLeast(1)}°",
        )
    }

    return cues.take(3)
}

private fun sceneSpecificSuggestions(scene: Scene?): List<Suggestion> = when (scene) {
    Scene.PORTRAIT -> listOf(
        Suggestion(SuggestionType.RECOMPOSE, "将人物头部放在上三分线交点"),
        Suggestion(SuggestionType.MOVE_CAMERA, "降低机位10cm，让下巴与下三分线对齐"),
        Suggestion(SuggestionType.CHANGE_ANGLE, "向右转15°，让光线从左侧打过来"),
    )
    Scene.FOOD -> listOf(
        Suggestion(SuggestionType.CHANGE_ANGLE, "将手机与桌面呈45°角俯拍"),
        Suggestion(SuggestionType.RECOMPOSE, "把主体放在中央偏右位置"),
        Suggestion(SuggestionType.ADJUST_EXPOSURE, "侧光拍摄，让阴影在左前方"),
    )
    Scene.LANDSCAPE -> listOf(
        Suggestion(SuggestionType.RECOMPOSE, "将地平线对齐上三分线，突出前景"),
        Suggestion(SuggestionType.MOVE_CAMERA, "向左移动3步，让道路成为引导线"),
        Suggestion(SuggestionType.CHANGE_ANGLE, "降低机位到膝盖高度，增加前景层次"),
    )
    Scene.ARCHITECTURE -> listOf(
        Suggestion(SuggestionType.RECOMPOSE, "站到建筑中轴线上，左右边缘保持对称"),
        Suggestion(SuggestionType.ROTATE, "旋转手机，让竖线保持垂直"),
        Suggestion(SuggestionType.MOVE_CAMERA, "后退半步，保留建筑顶部空间"),
    )
    else -> emptyList()
}

private fun DrawScope.drawGuidanceArrow(direction: String, subjectX: Float, subjectY: Float, label: String) {
    val arrowColor = Color(0xFF44D17A)
    val arrowStrokeWidth = 8f
    val length = min(size.width, size.height) * 0.12f
    val x = subjectX.coerceIn(64f, size.width - 64f)
    val y = subjectY.coerceIn(96f, size.height - 220f)

    when (direction) {
        "left" -> {
            drawLine(arrowColor, Offset(x + length / 2, y), Offset(x - length / 2, y), strokeWidth = arrowStrokeWidth, cap = StrokeCap.Round)
            drawArrowHead(Offset(x - length / 2, y), "left", arrowColor)
        }
        "right" -> {
            drawLine(arrowColor, Offset(x - length / 2, y), Offset(x + length / 2, y), strokeWidth = arrowStrokeWidth, cap = StrokeCap.Round)
            drawArrowHead(Offset(x + length / 2, y), "right", arrowColor)
        }
        "up" -> {
            drawLine(arrowColor, Offset(x, y + length / 2), Offset(x, y - length / 2), strokeWidth = arrowStrokeWidth, cap = StrokeCap.Round)
            drawArrowHead(Offset(x, y - length / 2), "up", arrowColor)
        }
        "down" -> {
            drawLine(arrowColor, Offset(x, y - length / 2), Offset(x, y + length / 2), strokeWidth = arrowStrokeWidth, cap = StrokeCap.Round)
            drawArrowHead(Offset(x, y + length / 2), "down", arrowColor)
        }
        "rotate_cw", "rotate_ccw" -> {
            val sweep = if (direction == "rotate_cw") 230f else -230f
            drawArc(
                color = arrowColor,
                startAngle = if (direction == "rotate_cw") -140f else -40f,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = Offset(x - length / 2, y - length / 2),
                size = Size(length, length),
                style = Stroke(width = arrowStrokeWidth, cap = StrokeCap.Round),
            )
        }
        "zoom_in" -> {
            drawCircle(arrowColor, radius = length * 0.28f, center = Offset(x, y), style = Stroke(width = 7f))
            drawLine(arrowColor, Offset(x + length * 0.18f, y + length * 0.18f), Offset(x + length * 0.48f, y + length * 0.48f), strokeWidth = arrowStrokeWidth, cap = StrokeCap.Round)
            drawLine(arrowColor, Offset(x - length * 0.16f, y), Offset(x + length * 0.16f, y), strokeWidth = 5f, cap = StrokeCap.Round)
            drawLine(arrowColor, Offset(x, y - length * 0.16f), Offset(x, y + length * 0.16f), strokeWidth = 5f, cap = StrokeCap.Round)
        }
    }
    drawGuidanceLabel(label, x, y - length * 0.68f)
}

private fun DrawScope.drawArrowHead(tip: Offset, direction: String, color: Color) {
    val size = 22f
    val path = Path()
    when (direction) {
        "left" -> {
            path.moveTo(tip.x, tip.y)
            path.lineTo(tip.x + size, tip.y - size * 0.7f)
            path.lineTo(tip.x + size, tip.y + size * 0.7f)
        }
        "right" -> {
            path.moveTo(tip.x, tip.y)
            path.lineTo(tip.x - size, tip.y - size * 0.7f)
            path.lineTo(tip.x - size, tip.y + size * 0.7f)
        }
        "up" -> {
            path.moveTo(tip.x, tip.y)
            path.lineTo(tip.x - size * 0.7f, tip.y + size)
            path.lineTo(tip.x + size * 0.7f, tip.y + size)
        }
        "down" -> {
            path.moveTo(tip.x, tip.y)
            path.lineTo(tip.x - size * 0.7f, tip.y - size)
            path.lineTo(tip.x + size * 0.7f, tip.y - size)
        }
    }
    path.close()
    drawPath(path, color)
}

private fun DrawScope.drawGuidanceLabel(text: String, x: Float, y: Float) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textSize = 38f
        textAlign = Paint.Align.CENTER
    }
    val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.argb(178, 20, 28, 24)
    }
    val width = paint.measureText(text) + 34f
    val left = (x - width / 2).coerceIn(12f, size.width - width - 12f)
    val top = y.coerceIn(80f, size.height - 280f)
    drawContext.canvas.nativeCanvas.drawRoundRect(left, top, left + width, top + 52f, 26f, 26f, bgPaint)
    drawContext.canvas.nativeCanvas.drawText(text, left + width / 2, top + 37f, paint)
}

private fun DrawScope.drawExampleOverlay(scene: Scene, width: Float, height: Float) {
    val guide = Color(0xFF44D17A).copy(alpha = 0.36f)
    val fill = Color.White.copy(alpha = 0.12f)
    drawLine(guide, Offset(width / 3f, 0f), Offset(width / 3f, height), strokeWidth = 3f)
    drawLine(guide, Offset(width * 2f / 3f, 0f), Offset(width * 2f / 3f, height), strokeWidth = 3f)
    drawLine(guide, Offset(0f, height / 3f), Offset(width, height / 3f), strokeWidth = 3f)
    drawLine(guide, Offset(0f, height * 2f / 3f), Offset(width, height * 2f / 3f), strokeWidth = 3f)
    when (scene) {
        Scene.PORTRAIT -> {
            drawOval(fill, topLeft = Offset(width * 0.28f, height * 0.19f), size = Size(width * 0.18f, height * 0.22f))
            drawRect(fill, topLeft = Offset(width * 0.24f, height * 0.42f), size = Size(width * 0.26f, height * 0.34f))
        }
        Scene.FOOD -> {
            drawCircle(fill, radius = width * 0.16f, center = Offset(width * 0.58f, height * 0.50f))
            drawLine(guide, Offset(width * 0.22f, height * 0.82f), Offset(width * 0.78f, height * 0.22f), strokeWidth = 5f)
        }
        Scene.LANDSCAPE -> {
            drawLine(guide, Offset(0f, height / 3f), Offset(width, height / 3f), strokeWidth = 7f)
            drawRect(fill, topLeft = Offset(width * 0.08f, height * 0.62f), size = Size(width * 0.84f, height * 0.18f))
        }
        else -> {
            drawRect(fill, topLeft = Offset(width * 0.33f, height * 0.22f), size = Size(width * 0.34f, height * 0.56f))
        }
    }
}

@Composable
private fun ZoomSlider(
    zoomRatio: Float,
    maxZoom: Float,
    enabled: Boolean,
    onZoomChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val upper = maxZoom.coerceAtLeast(1f)
    if (upper <= 1.01f) return

    Surface(
        modifier = modifier
            .height(240.dp)
            .width(48.dp),
        shape = RoundedCornerShape(24.dp),
        color = SurfaceDark.copy(alpha = 0.38f),
        contentColor = White,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Slider(
                value = zoomRatio.coerceIn(1f, upper),
                onValueChange = onZoomChange,
                valueRange = 1f..upper,
                enabled = enabled,
                modifier = Modifier
                    .width(218.dp)
                    .rotate(-90f),
                colors = SliderDefaults.colors(
                    thumbColor = White.copy(alpha = 0.92f),
                    activeTrackColor = AccentBlue.copy(alpha = 0.85f),
                    inactiveTrackColor = White.copy(alpha = 0.22f),
                    disabledThumbColor = White.copy(alpha = 0.35f),
                    disabledActiveTrackColor = White.copy(alpha = 0.25f),
                    disabledInactiveTrackColor = White.copy(alpha = 0.12f),
                )
            )
        }
    }
}

/** Redesign #1 — semicircular score gauge that fills like a speedometer. */
@Composable
private fun ScoreArcGauge(score: Int, color: Color, label: String) {
    val clamped = score.coerceIn(0, 100)
    Box(
        modifier = Modifier.size(width = 130.dp, height = 78.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 9.dp.toPx()
            val d = size.width - stroke
            val topLeft = Offset(stroke / 2f, size.height - stroke / 2f - d / 2f)
            val arcSize = Size(d, d)
            // Background track (top semicircle).
            drawArc(
                color = Color(0x33FFFFFF),
                startAngle = 180f,
                sweepAngle = -180f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
            // Progress.
            drawArc(
                color = color,
                startAngle = 180f,
                sweepAngle = -(180f * clamped / 100f),
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(bottom = 2.dp)
        ) {
            Text(
                text = clamped.toString(),
                style = MaterialTheme.typography.titleLarge,
                color = White
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = White.copy(alpha = 0.7f)
            )
        }
    }
}

/** Redesign #6 — pulsing ring shown around the shutter while the camera initializes. */
@Composable
private fun PulsingFocusRing() {
    val transition = rememberInfiniteTransition(label = "focus")
    val scale by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart),
        label = "scale"
    )
    val alpha by transition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart),
        label = "alpha"
    )
    Box(
        modifier = Modifier
            .size((90 * scale).dp)
            .border(2.dp, White.copy(alpha = alpha), CircleShape)
    )
}

@Composable
fun SuggestionChip(
    suggestion: com.framewise.engine.types.Suggestion,
    borderColor: Color = AccentBlue,
) {
    // Redesign #7 — subtle animated gradient sweeping across the chip background.
    val transition = rememberInfiniteTransition(label = "chip")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3000, easing = LinearEasing), RepeatMode.Reverse),
        label = "phase"
    )
    val brush = Brush.horizontalGradient(
        colors = listOf(
            SurfaceDark.copy(alpha = 0.85f),
            borderColor.copy(alpha = 0.28f),
            SurfaceDark.copy(alpha = 0.85f),
        ),
        startX = -300f + phase * 600f,
        endX = 300f + phase * 600f
    )

    // Redesign #4 — colored border matching the current score.
    Box(
        modifier = Modifier
            .padding(2.dp)
            .clip(CircleShape)
            .background(brush)
            .border(1.dp, borderColor.copy(alpha = 0.8f), CircleShape)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val iconText = when (suggestion.type) {
                SuggestionType.ROTATE -> "🔄"
                SuggestionType.MOVE_CAMERA -> "📱"
                SuggestionType.ADJUST_ZOOM -> "🔍"
                SuggestionType.CHANGE_ANGLE -> "📐"
                SuggestionType.ADJUST_EXPOSURE -> "☀️"
                SuggestionType.RECOMPOSE -> "🖼️"
                SuggestionType.INFO -> "ℹ️"
            }
            Text(text = iconText)
            Text(
                text = chineseSuggestionText(suggestion.text),
                style = MaterialTheme.typography.bodySmall,
                color = White
            )
        }
    }
}

private fun chineseSuggestionText(text: String): String {
    val normalized = text.lowercase()
    return when {
        "leading lines" in normalized || "roads" in normalized || "railings" in normalized -> "寻找引导线"
        "rule of thirds" in normalized || "thirds" in normalized -> "试试三分法"
        "different angle" in normalized || "adjust the camera" in normalized || "reposition" in normalized -> "换个角度"
        "point" in normalized && "scene" in normalized -> "对准场景获取建议"
        "exposure" in normalized || "overexposed" in normalized || "underexposed" in normalized -> "调整曝光"
        "horizon" in normalized || "rotate" in normalized -> "校正水平线"
        "symmetry" in normalized || "center" in normalized -> "调整主体位置"
        "zoom" in normalized || "closer" in normalized -> "调整变焦"
        else -> text
    }
}
