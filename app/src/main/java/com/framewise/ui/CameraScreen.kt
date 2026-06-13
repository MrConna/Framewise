package com.framewise.ui

import android.graphics.Paint
import android.net.Uri
import android.view.HapticFeedbackConstants
import android.view.ViewGroup
import android.widget.Toast
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.ui.draw.scale
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
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
import com.framewise.engine.PoseLibrary
import com.framewise.engine.PoseSuggestion
import com.framewise.engine.rules.ALL_RULES
import com.framewise.engine.types.Suggestion
import com.framewise.engine.types.SuggestionType
import com.framewise.engine.types.PhotoAnalysis
import com.framewise.engine.types.Scene
import com.framewise.engine.SceneTemplate
import com.framewise.engine.SceneTemplateRepository
import com.framewise.ui.components.HistogramView
import com.framewise.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.sin

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
    val currentTemplate by pipeline.currentTemplate.collectAsState()

    LaunchedEffect(context) {
        SceneTemplateRepository.load(context)
    }

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
    var suggestionIndex by remember { mutableStateOf(0) }
    var selectedPose by remember { mutableStateOf<PoseSuggestion?>(null) }

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

    // 根据评分范围计算颜色，使用弹性过渡动画
    val guidanceColor = compositionResult?.let { result ->
        when {
            result.overallScore >= 85 -> ScorePerfect
            result.overallScore >= 70 -> ScoreGood
            else -> ScoreBad
        }
    } ?: ScoreGood
    // 评分值弹性动画过渡
    val animatedScore by animateFloatAsState(
        targetValue = (compositionResult?.overallScore ?: 0f).toFloat(),
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 180f),
        label = "scoreSpring"
    )
    val guidanceCues = remember(photoAnalysis) { buildGuidanceCues(photoAnalysis) }
    val suggestions = sceneSpecificSuggestions(photoAnalysis?.scene)
        .ifEmpty { compositionResult?.bestSuggestions.orEmpty() }
        .takeIf { it.isNotEmpty() }
        ?: listOf(Suggestion(SuggestionType.INFO, "对准场景获取建议"))
    val pulseTransition = rememberInfiniteTransition(label = "guidancePulse")
    val guidanceAlpha by pulseTransition.animateFloat(
        initialValue = 0.42f,
        targetValue = 0.88f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Reverse),
        label = "guidanceAlpha"
    )

    LaunchedEffect(suggestions.size) {
        suggestionIndex = 0
        while (suggestions.size > 1) {
            delay(3000)
            suggestionIndex = (suggestionIndex + 1) % suggestions.size
        }
    }

    LaunchedEffect(photoAnalysis?.scene) {
        if (photoAnalysis?.scene != Scene.PORTRAIT) {
            selectedPose = null
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
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
                    drawLine(
                        color = White.copy(alpha = 0.16f),
                        start = start,
                        end = end,
                        strokeWidth = 1.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
                drawGoldenRatioSpiral(
                    color = ScorePerfect.copy(alpha = 0.38f),
                    width = width,
                    height = height,
                )
            }

            // B. Horizon level indicator.
            photoAnalysis?.horizon?.let { horizon ->
                if (horizon.detected) {
                    val angle = horizon.angle.toFloat()
                    val horizonY = (horizon.y * height).toFloat()
                    val color = if (abs(angle) < 2f) ScorePerfect else ScoreGood

                    rotate(degrees = angle, pivot = Offset(width / 2f, horizonY)) {
                        drawLine(
                            color = color.copy(alpha = 0.54f),
                            start = Offset(width * 0.1f, horizonY),
                            end = Offset(width * 0.9f, horizonY),
                            strokeWidth = 1.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }
                }
            }

            // C. Subject framing boxes.
            photoAnalysis?.subjects?.forEach { subject ->
                val box = subject.bounds
                drawRect(
                    color = guidanceColor.copy(alpha = 0.34f),
                    topLeft = Offset((box.x * width).toFloat(), (box.y * height).toFloat()),
                    size = Size((box.width * width).toFloat(), (box.height * height).toFloat()),
                    style = Stroke(width = 1.dp.toPx())
                )
            }

            if (showExampleOverlay) {
                drawExampleOverlay(photoAnalysis?.scene ?: Scene.GENERIC, width, height)
            }

            guidanceCues.forEach { cue ->
                drawGuidanceArrow(
                    direction = cue.direction,
                    subjectX = cue.x * width,
                    subjectY = cue.y * height,
                    label = cue.text,
                    color = guidanceColor,
                    alpha = guidanceAlpha,
                )
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

        IconButton(
            onClick = { showExampleOverlay = !showExampleOverlay },
            modifier = Modifier
                .statusBarsPadding()
                .align(Alignment.TopStart)
                .padding(start = 132.dp, top = 18.dp)
                .size(44.dp)
                .zIndex(2f)
                .background(
                    if (showExampleOverlay) AccentBlue.copy(alpha = 0.78f) else SurfaceDark.copy(alpha = 0.7f),
                    CircleShape
                )
                .border(
                    1.dp,
                    if (showExampleOverlay) White.copy(alpha = 0.65f) else White.copy(alpha = 0.12f),
                    CircleShape
                )
        ) {
            Icon(
                imageVector = Icons.Default.Visibility,
                contentDescription = "显示参考",
                tint = White.copy(alpha = if (showExampleOverlay) 1f else 0.72f)
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

        // 在 top-right 显示弧形评分仪表盘（带弹性动画）
        if (compositionResult != null) {
            ScoreArcGauge(
                score = animatedScore.toInt().coerceIn(0, 100),
                color = guidanceColor,
                label = "构图",
                modifier = Modifier
                    .statusBarsPadding()
                    .align(Alignment.TopEnd)
                    .padding(top = 22.dp, end = 74.dp)
                    .zIndex(2f)
            )
        }

        HistogramView(
            brightnessData = photoAnalysis?.brightness,
            modifier = Modifier
                .statusBarsPadding()
                .align(Alignment.TopEnd)
                .padding(top = 108.dp, end = 16.dp)
                .zIndex(2f)
        )

        HorizonLevelStatus(
            analysis = photoAnalysis,
            modifier = Modifier
                .statusBarsPadding()
                .align(Alignment.TopCenter)
                .padding(top = 88.dp)
                .zIndex(2.1f)
        )

        currentTemplate?.let { template ->
            SceneTemplateRecommendationBar(
                template = template,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, bottom = 168.dp)
                    .zIndex(2.15f)
            )
        }

        selectedPose?.let { pose ->
            PoseReferenceOverlay(
                pose = pose,
                onDismiss = { selectedPose = null },
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(2.35f)
            )
        }

        // 建议标签：从底部滑入 + 淡入组合动画
        AnimatedContent(
            targetState = suggestions[suggestionIndex.coerceIn(0, suggestions.lastIndex)],
            transitionSpec = {
                ContentTransform(
                    slideInVertically { it + 40 } + fadeIn(),
                    slideOutVertically { -it - 40 } + fadeOut()
                )
            },
            label = "suggestionSlide",
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 122.dp)
                .zIndex(2.2f)
        ) { suggestion ->
            MinimalSuggestionLabel(
                suggestion = suggestion,
                color = guidanceColor
            )
        }

        // 底部控制栏：毛玻璃效果（参考 awesome-android-ui: EtsyBlur）
        Surface(
            color = SurfaceDark.copy(alpha = 0.48f),
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 14.dp, top = 0.dp, end = 14.dp, bottom = 14.dp)
                .zIndex(2f)
                .blur(radius = 2.dp)
                .border(
                    width = 0.8.dp,
                    color = White.copy(alpha = 0.10f),
                    shape = RoundedCornerShape(28.dp)
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, bottom = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FilterSelector(
                    selectedFilter = cameraController.filterMode,
                    onFilterSelected = cameraController::selectFilterMode,
                    modifier = Modifier.fillMaxWidth()
                )

                if (photoAnalysis?.scene == Scene.PORTRAIT) {
                    PoseRecommendationRow(
                        poses = PoseLibrary.recommendedFor(Scene.PORTRAIT),
                        selectedPose = selectedPose,
                        onPoseSelected = { selectedPose = it },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

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
                                // 📳 快门触感反馈
                                previewView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                if (cameraState.isReady) {
                                    cameraController.captureWithTimer { uri ->
                                        val msg = photoSavedMsg
                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                        SettingsState.capturedCount++
                                        lastPhotoUri = uri
                                        // 📳 拍照保存后更强的触感
                                        previewView.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                        coroutineScope.launch {
                                            flashAlpha.animateTo(1f, tween(100))
                                            flashAlpha.animateTo(0f, tween(100))
                                        }
                                    }
                                } else {
                                    // 📳 不可用时轻微震动提醒
                                    previewView.performHapticFeedback(HapticFeedbackConstants.REJECT)
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
                    color = White.copy(alpha = 0.48f)
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

        // 倒计时覆盖层：圆形进度环 + 数字动画 + 取消按钮
        if (cameraController.countdownSeconds > 0) {
            val countdownText = cameraController.countdownSeconds.toString()
            val progress = cameraController.countdownProgress
            val animatedScale by animateFloatAsState(
                targetValue = if (cameraController.countdownSeconds > 0) 1f else 0.8f,
                animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f),
                label = "countdownScale",
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(3f)
                    .background(Color.Black.copy(alpha = 0.24f))
                    .clickable(enabled = cameraController.countdownSeconds > 0) {
                        cameraController.cancelTimer()
                    },
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(
                    targetState = countdownText,
                    transitionSpec = {
                        ContentTransform(
                            fadeIn(tween(100)) + slideInVertically(tween(200)),
                            fadeOut(tween(100)) + slideOutVertically(tween(200))
                        )
                    },
                    label = "countdownNumber",
                ) { text ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // 圆形进度环
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(180.dp * animatedScale)
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val strokeWidth = 8.dp.toPx()
                                val diameter = size.minDimension - strokeWidth
                                // 背景圆环（半透明）
                                drawArc(
                                    color = White.copy(alpha = 0.2f),
                                    startAngle = -90f,
                                    sweepAngle = 360f,
                                    useCenter = false,
                                    topLeft = androidx.compose.ui.geometry.Offset(
                                        (size.width - diameter) / 2f,
                                        (size.height - diameter) / 2f,
                                    ),
                                    size = androidx.compose.ui.geometry.Size(diameter, diameter),
                                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                                )
                                // 进度弧线 (从 -90° 开始，顺时针递减)
                                drawArc(
                                    color = AccentBlue,
                                    startAngle = -90f,
                                    sweepAngle = -(progress * 360f),
                                    useCenter = false,
                                    topLeft = androidx.compose.ui.geometry.Offset(
                                        (size.width - diameter) / 2f,
                                        (size.height - diameter) / 2f,
                                    ),
                                    size = androidx.compose.ui.geometry.Size(diameter, diameter),
                                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                                )
                            }
                            // 中心数字
                            Text(
                                text = text,
                                style = MaterialTheme.typography.displayLarge,
                                color = White,
                            )
                        }
                        // 取消按钮提示文字
                        Text(
                            text = "点击任意位置取消",
                            style = MaterialTheme.typography.bodySmall,
                            color = White.copy(alpha = 0.6f),
                        )
                    }
                }
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
            // 选中时弹性缩放的边框动画
            val animatedBorderWidth by animateFloatAsState(
                targetValue = if (selected) 2f else 1f,
                animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f),
                label = "filterBorder${filter.id}"
            )
            val animatedAlpha by animateFloatAsState(
                targetValue = if (selected) 1f else 0.72f,
                animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f),
                label = "filterAlpha${filter.id}"
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onFilterSelected(filter.id) }
                    .border(
                        width = animatedBorderWidth.dp,
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
                    color = White.copy(alpha = animatedAlpha)
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

@Composable
private fun PoseRecommendationRow(
    poses: List<PoseSuggestion>,
    selectedPose: PoseSuggestion?,
    onPoseSelected: (PoseSuggestion) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "姿势",
            style = MaterialTheme.typography.labelMedium,
            color = White.copy(alpha = 0.72f),
            modifier = Modifier.padding(end = 2.dp)
        )
        poses.forEach { pose ->
            val selected = selectedPose?.id == pose.id
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (selected) AccentBlue.copy(alpha = 0.32f) else SurfaceDark.copy(alpha = 0.38f),
                contentColor = White,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (selected) AccentBlue.copy(alpha = 0.78f) else White.copy(alpha = 0.12f)
                ),
                modifier = Modifier
                    .height(48.dp)
                    .clickable { onPoseSelected(pose) }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Text(
                        text = pose.icon,
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Column(
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.widthIn(min = 68.dp, max = 112.dp)
                    ) {
                        Text(
                            text = pose.name,
                            style = MaterialTheme.typography.labelMedium,
                            color = White.copy(alpha = 0.94f),
                            maxLines = 1,
                        )
                        Text(
                            text = pose.description,
                            style = MaterialTheme.typography.labelSmall,
                            color = White.copy(alpha = 0.54f),
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PoseReferenceOverlay(
    pose: PoseSuggestion,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.36f))
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth(0.72f)
                .fillMaxHeight(0.58f)
        ) {
            drawPoseSilhouette(pose)
        }
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = SurfaceDark.copy(alpha = 0.68f),
            contentColor = White,
            border = androidx.compose.foundation.BorderStroke(1.dp, White.copy(alpha = 0.16f)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(start = 18.dp, end = 18.dp, bottom = 188.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = pose.icon,
                    style = MaterialTheme.typography.titleMedium,
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = pose.name,
                        style = MaterialTheme.typography.labelLarge,
                        color = White.copy(alpha = 0.95f)
                    )
                    Text(
                        text = pose.description,
                        style = MaterialTheme.typography.labelSmall,
                        color = White.copy(alpha = 0.68f),
                        maxLines = 2,
                    )
                }
                Text(
                    text = "参考",
                    style = MaterialTheme.typography.labelSmall,
                    color = AccentBlue.copy(alpha = 0.9f),
                    modifier = Modifier
                        .background(AccentBlue.copy(alpha = 0.14f), RoundedCornerShape(999.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun HorizonLevelStatus(
    analysis: PhotoAnalysis?,
    modifier: Modifier = Modifier,
) {
    val horizon = analysis?.horizon ?: return
    if (!horizon.detected) return

    val angle = horizon.angle.toFloat()
    val level = abs(angle) < 1.2f
    val color = if (level) ScorePerfect else ScoreGood
    val label = when {
        level -> "水平"
        angle > 0 -> "右倾"
        else -> "左倾"
    }

    Surface(
        shape = RoundedCornerShape(999.dp),
        color = SurfaceDark.copy(alpha = 0.58f),
        contentColor = White,
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.52f)),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Canvas(modifier = Modifier.size(width = 34.dp, height = 14.dp)) {
                val centerY = size.height / 2f
                drawLine(
                    color = White.copy(alpha = 0.22f),
                    start = Offset(0f, centerY),
                    end = Offset(size.width, centerY),
                    strokeWidth = 1.dp.toPx(),
                    cap = StrokeCap.Round
                )
                rotate(degrees = angle.coerceIn(-8f, 8f), pivot = Offset(size.width / 2f, centerY)) {
                    drawLine(
                        color = color,
                        start = Offset(2f, centerY),
                        end = Offset(size.width - 2f, centerY),
                        strokeWidth = 2.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }
            Text(
                text = "$label ${abs(angle).toInt().coerceAtLeast(if (level) 0 else 1)}°",
                style = MaterialTheme.typography.labelMedium,
                color = White.copy(alpha = 0.94f)
            )
        }
    }
}

@Composable
private fun SceneTemplateRecommendationBar(
    template: SceneTemplate,
    modifier: Modifier = Modifier,
) {
    val tip = template.poseHint ?: template.tips.firstOrNull() ?: template.description
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = SurfaceDark.copy(alpha = 0.62f),
        contentColor = White,
        border = androidx.compose.foundation.BorderStroke(1.dp, AccentBlue.copy(alpha = 0.34f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = template.icon,
                style = MaterialTheme.typography.titleMedium,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "${template.label}模板 · ${template.description}",
                    style = MaterialTheme.typography.labelMedium,
                    color = White.copy(alpha = 0.94f),
                    maxLines = 1,
                )
                Text(
                    text = tip,
                    style = MaterialTheme.typography.labelSmall,
                    color = White.copy(alpha = 0.66f),
                    maxLines = 1,
                )
            }
            Text(
                text = template.suggestedFilter,
                style = MaterialTheme.typography.labelSmall,
                color = AccentBlue.copy(alpha = 0.92f),
                modifier = Modifier
                    .background(AccentBlue.copy(alpha = 0.14f), RoundedCornerShape(999.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

/**
 * 底部建议标签。外部已用 AnimatedContent 包裹以提供滑入/淡出动画。
 */
@Composable
private fun MinimalSuggestionLabel(
    suggestion: Suggestion,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = Color.Black.copy(alpha = 0.42f),
        contentColor = White,
        modifier = modifier,
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.48f))
    ) {
        Text(
            text = chineseSuggestionText(suggestion.text),
            style = MaterialTheme.typography.bodySmall,
            color = White.copy(alpha = 0.92f),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
        )
    }
}

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

private fun DrawScope.drawPoseSilhouette(pose: PoseSuggestion) {
    val line = White.copy(alpha = 0.62f)
    val glow = AccentBlue.copy(alpha = 0.16f)
    val stroke = 5.dp.toPx()
    val guideStroke = 2.dp.toPx()
    val centerX = size.width / 2f
    val top = size.height * 0.08f
    val headRadius = min(size.width, size.height) * 0.075f
    val head = Offset(centerX, top + headRadius)
    val neck = Offset(centerX, head.y + headRadius + 10.dp.toPx())
    val hip = Offset(centerX, size.height * 0.55f)

    drawRoundRect(
        color = glow,
        topLeft = Offset(size.width * 0.16f, size.height * 0.04f),
        size = Size(size.width * 0.68f, size.height * 0.84f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(32.dp.toPx(), 32.dp.toPx()),
        style = Stroke(width = guideStroke)
    )

    when (pose.id) {
        "wall_lean" -> {
            val wallX = size.width * 0.32f
            drawLine(White.copy(alpha = 0.22f), Offset(wallX, size.height * 0.10f), Offset(wallX, size.height * 0.82f), strokeWidth = guideStroke)
            drawPoseBody(head, neck.copy(x = centerX - 18.dp.toPx()), hip.copy(x = centerX + 14.dp.toPx()), line, stroke)
            drawArms(neck.copy(x = centerX - 18.dp.toPx()), Offset(centerX + 42.dp.toPx(), size.height * 0.35f), Offset(centerX - 44.dp.toPx(), size.height * 0.38f), line, stroke)
            drawLegs(hip.copy(x = centerX + 14.dp.toPx()), Offset(centerX - 34.dp.toPx(), size.height * 0.82f), Offset(centerX + 58.dp.toPx(), size.height * 0.80f), line, stroke)
        }
        "seated_forward" -> {
            val seatY = size.height * 0.62f
            drawLine(White.copy(alpha = 0.22f), Offset(size.width * 0.28f, seatY), Offset(size.width * 0.72f, seatY), strokeWidth = guideStroke, cap = StrokeCap.Round)
            drawPoseBody(head, neck, Offset(centerX + 18.dp.toPx(), seatY - 8.dp.toPx()), line, stroke)
            drawArms(neck, Offset(centerX - 56.dp.toPx(), seatY - 18.dp.toPx()), Offset(centerX + 62.dp.toPx(), seatY - 16.dp.toPx()), line, stroke)
            drawLegs(Offset(centerX + 18.dp.toPx(), seatY - 8.dp.toPx()), Offset(centerX - 78.dp.toPx(), size.height * 0.76f), Offset(centerX + 86.dp.toPx(), size.height * 0.76f), line, stroke)
        }
        "hand_near_face", "hair_adjust", "window_light" -> {
            drawPoseBody(head, neck, hip, line, stroke)
            drawArms(neck, Offset(centerX - 42.dp.toPx(), head.y + 4.dp.toPx()), Offset(centerX + 72.dp.toPx(), size.height * 0.45f), line, stroke)
            drawLegs(hip, Offset(centerX - 42.dp.toPx(), size.height * 0.82f), Offset(centerX + 34.dp.toPx(), size.height * 0.82f), line, stroke)
        }
        "walking_motion" -> {
            drawPoseBody(head.copy(x = centerX + 8.dp.toPx()), neck.copy(x = centerX + 6.dp.toPx()), hip.copy(x = centerX - 12.dp.toPx()), line, stroke)
            drawArms(neck, Offset(centerX - 62.dp.toPx(), size.height * 0.42f), Offset(centerX + 66.dp.toPx(), size.height * 0.32f), line, stroke)
            drawLegs(hip.copy(x = centerX - 12.dp.toPx()), Offset(centerX - 86.dp.toPx(), size.height * 0.82f), Offset(centerX + 78.dp.toPx(), size.height * 0.76f), line, stroke)
        }
        "look_over_shoulder", "side_profile" -> {
            drawPoseBody(head.copy(x = centerX - 10.dp.toPx()), neck.copy(x = centerX + 8.dp.toPx()), hip.copy(x = centerX + 28.dp.toPx()), line, stroke)
            drawArms(neck.copy(x = centerX + 8.dp.toPx()), Offset(centerX - 34.dp.toPx(), size.height * 0.43f), Offset(centerX + 74.dp.toPx(), size.height * 0.43f), line, stroke)
            drawLegs(hip.copy(x = centerX + 28.dp.toPx()), Offset(centerX - 24.dp.toPx(), size.height * 0.82f), Offset(centerX + 66.dp.toPx(), size.height * 0.82f), line, stroke)
        }
        "arms_crossed" -> {
            drawPoseBody(head, neck, hip, line, stroke)
            drawLine(line, Offset(centerX - 58.dp.toPx(), size.height * 0.35f), Offset(centerX + 54.dp.toPx(), size.height * 0.45f), strokeWidth = stroke, cap = StrokeCap.Round)
            drawLine(line, Offset(centerX + 58.dp.toPx(), size.height * 0.35f), Offset(centerX - 52.dp.toPx(), size.height * 0.45f), strokeWidth = stroke, cap = StrokeCap.Round)
            drawLegs(hip, Offset(centerX - 38.dp.toPx(), size.height * 0.82f), Offset(centerX + 38.dp.toPx(), size.height * 0.82f), line, stroke)
        }
        "low_angle_power" -> {
            drawPoseBody(head.copy(y = head.y + 18.dp.toPx()), neck.copy(y = neck.y + 18.dp.toPx()), hip.copy(y = hip.y + 24.dp.toPx()), line, stroke)
            drawArms(neck.copy(y = neck.y + 18.dp.toPx()), Offset(centerX - 66.dp.toPx(), size.height * 0.48f), Offset(centerX + 66.dp.toPx(), size.height * 0.48f), line, stroke)
            drawLegs(hip.copy(y = hip.y + 24.dp.toPx()), Offset(centerX - 82.dp.toPx(), size.height * 0.86f), Offset(centerX + 82.dp.toPx(), size.height * 0.86f), line, stroke)
        }
        else -> {
            drawPoseBody(head, neck, hip, line, stroke)
            drawArms(neck, Offset(centerX - 58.dp.toPx(), size.height * 0.46f), Offset(centerX + 58.dp.toPx(), size.height * 0.40f), line, stroke)
            drawLegs(hip, Offset(centerX - 42.dp.toPx(), size.height * 0.82f), Offset(centerX + 48.dp.toPx(), size.height * 0.80f), line, stroke)
        }
    }
}

private fun DrawScope.drawPoseBody(
    head: Offset,
    neck: Offset,
    hip: Offset,
    color: Color,
    stroke: Float,
) {
    val headRadius = min(size.width, size.height) * 0.075f
    drawCircle(color.copy(alpha = 0.18f), radius = headRadius * 1.18f, center = head)
    drawCircle(color, radius = headRadius, center = head, style = Stroke(width = stroke))
    drawLine(color, neck, hip, strokeWidth = stroke, cap = StrokeCap.Round)
    drawLine(color.copy(alpha = 0.72f), Offset(neck.x - 34.dp.toPx(), neck.y + 12.dp.toPx()), Offset(neck.x + 34.dp.toPx(), neck.y + 12.dp.toPx()), strokeWidth = stroke, cap = StrokeCap.Round)
}

private fun DrawScope.drawArms(
    shoulder: Offset,
    leftHand: Offset,
    rightHand: Offset,
    color: Color,
    stroke: Float,
) {
    drawLine(color, Offset(shoulder.x - 28.dp.toPx(), shoulder.y + 18.dp.toPx()), leftHand, strokeWidth = stroke, cap = StrokeCap.Round)
    drawLine(color, Offset(shoulder.x + 28.dp.toPx(), shoulder.y + 18.dp.toPx()), rightHand, strokeWidth = stroke, cap = StrokeCap.Round)
}

private fun DrawScope.drawLegs(
    hip: Offset,
    leftFoot: Offset,
    rightFoot: Offset,
    color: Color,
    stroke: Float,
) {
    drawLine(color, hip, leftFoot, strokeWidth = stroke, cap = StrokeCap.Round)
    drawLine(color, hip, rightFoot, strokeWidth = stroke, cap = StrokeCap.Round)
}

private fun DrawScope.drawGuidanceArrow(
    direction: String,
    subjectX: Float,
    subjectY: Float,
    label: String,
    color: Color,
    alpha: Float,
) {
    val arrowColor = color.copy(alpha = alpha)
    val arrowStrokeWidth = 2.dp.toPx()
    val length = min(size.width, size.height) * 0.12f
    val x = subjectX.coerceIn(64f, size.width - 64f)
    val y = subjectY.coerceIn(96f, size.height - 220f)

    when (direction) {
        "left" -> {
            drawLine(arrowColor, Offset(x + length / 2, y), Offset(x - length / 2, y), strokeWidth = arrowStrokeWidth, cap = StrokeCap.Round)
            drawArrowHead(Offset(x - length / 2, y), "left", arrowColor, arrowStrokeWidth)
        }
        "right" -> {
            drawLine(arrowColor, Offset(x - length / 2, y), Offset(x + length / 2, y), strokeWidth = arrowStrokeWidth, cap = StrokeCap.Round)
            drawArrowHead(Offset(x + length / 2, y), "right", arrowColor, arrowStrokeWidth)
        }
        "up" -> {
            drawLine(arrowColor, Offset(x, y + length / 2), Offset(x, y - length / 2), strokeWidth = arrowStrokeWidth, cap = StrokeCap.Round)
            drawArrowHead(Offset(x, y - length / 2), "up", arrowColor, arrowStrokeWidth)
        }
        "down" -> {
            drawLine(arrowColor, Offset(x, y - length / 2), Offset(x, y + length / 2), strokeWidth = arrowStrokeWidth, cap = StrokeCap.Round)
            drawArrowHead(Offset(x, y + length / 2), "down", arrowColor, arrowStrokeWidth)
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
            val tipAngle = if (direction == "rotate_cw") 90f else -90f
            val tip = Offset(x + length * 0.36f, y + if (direction == "rotate_cw") length * 0.28f else -length * 0.28f)
            drawRotationTip(tip, tipAngle, arrowColor, arrowStrokeWidth)
        }
        "zoom_in" -> {
            drawCircle(arrowColor, radius = length * 0.28f, center = Offset(x, y), style = Stroke(width = arrowStrokeWidth))
            drawLine(arrowColor, Offset(x + length * 0.18f, y + length * 0.18f), Offset(x + length * 0.48f, y + length * 0.48f), strokeWidth = arrowStrokeWidth, cap = StrokeCap.Round)
            drawLine(arrowColor, Offset(x - length * 0.16f, y), Offset(x + length * 0.16f, y), strokeWidth = arrowStrokeWidth, cap = StrokeCap.Round)
            drawLine(arrowColor, Offset(x, y - length * 0.16f), Offset(x, y + length * 0.16f), strokeWidth = arrowStrokeWidth, cap = StrokeCap.Round)
        }
    }
    drawGuidanceLabel(label, x, y - length * 0.68f, arrowColor)
}

private fun DrawScope.drawGoldenRatioSpiral(
    color: Color,
    width: Float,
    height: Float,
) {
    val thetaMax = (PI * 4.5).toFloat()
    val growth = (ln(1.61803398875) / (PI / 2.0)).toFloat()
    val maxRadius = min(width, height) * 0.48f
    val baseRadius = maxRadius / exp(growth * thetaMax)
    val center = Offset(width * 0.50f, height * 0.47f)
    val path = Path()

    for (i in 0..180) {
        val theta = thetaMax * (i / 180f)
        val radius = baseRadius * exp(growth * theta)
        val x = center.x + radius * cos(theta)
        val y = center.y + radius * sin(theta)
        if (i == 0) {
            path.moveTo(x, y)
        } else {
            path.lineTo(x, y)
        }
    }

    drawPath(
        path = path,
        color = color.copy(alpha = 0.18f),
        style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round)
    )
    drawPath(
        path = path,
        color = color,
        style = Stroke(width = 1.2.dp.toPx(), cap = StrokeCap.Round)
    )
}

private fun DrawScope.drawArrowHead(tip: Offset, direction: String, color: Color, strokeWidth: Float) {
    val size = 10.dp.toPx()
    val p1: Offset
    val p2: Offset
    when (direction) {
        "left" -> {
            p1 = Offset(tip.x + size, tip.y - size * 0.62f)
            p2 = Offset(tip.x + size, tip.y + size * 0.62f)
        }
        "right" -> {
            p1 = Offset(tip.x - size, tip.y - size * 0.62f)
            p2 = Offset(tip.x - size, tip.y + size * 0.62f)
        }
        "up" -> {
            p1 = Offset(tip.x - size * 0.62f, tip.y + size)
            p2 = Offset(tip.x + size * 0.62f, tip.y + size)
        }
        else -> {
            p1 = Offset(tip.x - size * 0.62f, tip.y - size)
            p2 = Offset(tip.x + size * 0.62f, tip.y - size)
        }
    }
    drawLine(color, tip, p1, strokeWidth = strokeWidth, cap = StrokeCap.Round)
    drawLine(color, tip, p2, strokeWidth = strokeWidth, cap = StrokeCap.Round)
}

private fun DrawScope.drawRotationTip(tip: Offset, angle: Float, color: Color, strokeWidth: Float) {
    val size = 10.dp.toPx()
    rotate(angle, pivot = tip) {
        drawLine(color, tip, Offset(tip.x - size, tip.y - size * 0.55f), strokeWidth = strokeWidth, cap = StrokeCap.Round)
        drawLine(color, tip, Offset(tip.x - size, tip.y + size * 0.55f), strokeWidth = strokeWidth, cap = StrokeCap.Round)
    }
}

private fun DrawScope.drawGuidanceLabel(text: String, x: Float, y: Float, color: Color) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = android.graphics.Color.WHITE
        textSize = 28f
        textAlign = Paint.Align.CENTER
    }
    val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = android.graphics.Color.argb((100 * color.alpha).toInt().coerceIn(56, 120), 14, 18, 22)
    }
    val width = paint.measureText(text) + 34f
    val left = (x - width / 2).coerceIn(12f, size.width - width - 12f)
    val top = y.coerceIn(80f, size.height - 280f)
    drawContext.canvas.nativeCanvas.drawRoundRect(left, top, left + width, top + 40f, 20f, 20f, bgPaint)
    drawContext.canvas.nativeCanvas.drawText(text, left + width / 2, top + 28f, paint)
}

private fun DrawScope.drawExampleOverlay(scene: Scene, width: Float, height: Float) {
    val guide = ScorePerfect.copy(alpha = 0.34f)
    val fill = Color.White.copy(alpha = 0.08f)
    val dash = PathEffect.dashPathEffect(floatArrayOf(18f, 14f), 0f)
    drawLine(guide, Offset(width / 3f, 0f), Offset(width / 3f, height), strokeWidth = 1.2.dp.toPx(), pathEffect = dash)
    drawLine(guide, Offset(width * 2f / 3f, 0f), Offset(width * 2f / 3f, height), strokeWidth = 1.2.dp.toPx(), pathEffect = dash)
    drawLine(guide, Offset(0f, height / 3f), Offset(width, height / 3f), strokeWidth = 1.2.dp.toPx(), pathEffect = dash)
    drawLine(guide, Offset(0f, height * 2f / 3f), Offset(width, height * 2f / 3f), strokeWidth = 1.2.dp.toPx(), pathEffect = dash)
    when (scene) {
        Scene.PORTRAIT -> {
            drawOval(fill, topLeft = Offset(width * 0.39f, height * 0.18f), size = Size(width * 0.22f, height * 0.18f))
            drawRoundRect(fill, topLeft = Offset(width * 0.34f, height * 0.38f), size = Size(width * 0.32f, height * 0.36f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(32f, 32f))
            drawLine(guide, Offset(width * 0.32f, height * 0.30f), Offset(width * 0.68f, height * 0.30f), strokeWidth = 1.5.dp.toPx(), pathEffect = dash)
        }
        Scene.FOOD -> {
            drawCircle(fill, radius = width * 0.16f, center = Offset(width * 0.58f, height * 0.50f))
            drawLine(guide, Offset(width * 0.18f, height * 0.78f), Offset(width * 0.82f, height * 0.24f), strokeWidth = 1.6.dp.toPx(), pathEffect = dash)
            drawLine(guide, Offset(width * 0.26f, height * 0.84f), Offset(width * 0.88f, height * 0.32f), strokeWidth = 1.dp.toPx(), pathEffect = dash)
        }
        Scene.LANDSCAPE -> {
            drawLine(guide, Offset(0f, height / 3f), Offset(width, height / 3f), strokeWidth = 2.dp.toPx(), pathEffect = dash)
            drawRect(fill, topLeft = Offset(width * 0.08f, height * 0.62f), size = Size(width * 0.84f, height * 0.18f))
        }
        Scene.ARCHITECTURE -> {
            drawLine(guide, Offset(width * 0.42f, height * 0.14f), Offset(width * 0.42f, height * 0.82f), strokeWidth = 1.5.dp.toPx(), pathEffect = dash)
            drawLine(guide, Offset(width * 0.58f, height * 0.14f), Offset(width * 0.58f, height * 0.82f), strokeWidth = 1.5.dp.toPx(), pathEffect = dash)
            drawRect(fill, topLeft = Offset(width * 0.38f, height * 0.20f), size = Size(width * 0.24f, height * 0.56f))
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

/**
 * 半弧形评分仪表盘，参考 awesome-android-ui 的 ArcProgressStackView / CircleProgress 风格。
 * - 评分值变化时弹簧弹性动画
 * - 弧形颜色渐变：<40 红色, 40-70 黄色, >=70 绿色
 */
@Composable
private fun ScoreArcGauge(
    score: Int,
    color: Color,
    label: String,
    modifier: Modifier = Modifier,
) {
    val clamped = score.coerceIn(0, 100)
    // 弹簧弹性动画，让弧线填充时带有弹性感
    val animatedProgress by animateFloatAsState(
        targetValue = clamped / 100f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 200f),
        label = "scoreArcSpring"
    )
    // 根据评分自动选择颜色（分层渐变）
    val gaugeColor = when {
        clamped >= 85 -> ScorePerfect
        clamped >= 70 -> ScoreGood
        clamped >= 40 -> Color(0xFFFFA500) // 橙色
        else -> color
    }

    Box(
        modifier = modifier.size(width = 130.dp, height = 78.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 9.dp.toPx()
            val d = size.width - stroke
            val topLeft = Offset(stroke / 2f, size.height - stroke / 2f - d / 2f)
            val arcSize = Size(d, d)
            // 背景半圆弧（底部半透明轨道）
            drawArc(
                color = Color(0x33FFFFFF),
                startAngle = 180f,
                sweepAngle = -180f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
            // 进度弧（带弹性动画）
            drawArc(
                color = gaugeColor,
                startAngle = 180f,
                sweepAngle = -(180f * animatedProgress),
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
