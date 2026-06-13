package com.framewise.ui

import android.view.ViewGroup
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.framewise.camera.CameraCompositionPipeline
import com.framewise.camera.CameraController
import com.framewise.camera.FrameAnalyzer
import com.framewise.engine.PhotoCompositionEngine
import com.framewise.engine.rules.ALL_RULES
import com.framewise.engine.types.SuggestionType
import com.framewise.ui.theme.*
import kotlinx.coroutines.delay
import kotlin.math.abs

@Composable
fun CameraScreen(
    onNavigateToSettings: () -> Unit,
    onNavigateToGallery: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Real composition pipeline: FrameAnalyzer → PhotoCompositionEngine (13 rules) → result.
    val frameAnalyzer = remember { FrameAnalyzer() }
    val pipeline = remember {
        CameraCompositionPipeline(
            frameAnalyzer = frameAnalyzer,
            compositionEngine = PhotoCompositionEngine(ALL_RULES),
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

    DisposableEffect(lifecycleOwner) {
        cameraController.bindToLifecycle()
        onDispose {
            pipeline.detach()
            frameAnalyzer.release()
        }
    }

    // Issue 2 fallback: if no scene has been detected (null/empty analysis) for
    // more than 2 seconds, prompt the user instead of showing a blank overlay.
    val sceneEmpty = photoAnalysis.let { a ->
        a == null || (a.subjects.isEmpty() && !a.horizon.detected && a.lines.isEmpty())
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

    Box(modifier = Modifier.fillMaxSize().background(BackgroundDark)) {
        // 1. Full screen camera preview
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )

        // 2. Compose Canvas overlay
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height

            // A. Rule of Thirds
            // Draw 2 vertical lines
            drawLine(
                color = TransparentGrid,
                start = Offset(width / 3f, 0f),
                end = Offset(width / 3f, height),
                strokeWidth = 1.5.dp.toPx()
            )
            drawLine(
                color = TransparentGrid,
                start = Offset(width * 2f / 3f, 0f),
                end = Offset(width * 2f / 3f, height),
                strokeWidth = 1.5.dp.toPx()
            )
            // Draw 2 horizontal lines
            drawLine(
                color = TransparentGrid,
                start = Offset(0f, height / 3f),
                end = Offset(width, height / 3f),
                strokeWidth = 1.5.dp.toPx()
            )
            drawLine(
                color = TransparentGrid,
                start = Offset(0f, height * 2f / 3f),
                end = Offset(width, height * 2f / 3f),
                strokeWidth = 1.5.dp.toPx()
            )

            // B. Horizon Level Indicator
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

            // C. Subject Framing Boxes
            photoAnalysis?.subjects?.forEach { subject ->
                val box = subject.bounds
                val boxX = (box.x * width).toFloat()
                val boxY = (box.y * height).toFloat()
                val boxW = (box.width * width).toFloat()
                val boxH = (box.height * height).toFloat()

                drawRect(
                    color = AccentBlue.copy(alpha = 0.5f),
                    topLeft = Offset(boxX, boxY),
                    size = Size(boxW, boxH),
                    style = Stroke(width = 2.dp.toPx())
                )
            }
        }

        // C2. Empty-scene fallback hint (Issue 2)
        if (showEmptyHint) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = SurfaceDark.copy(alpha = 0.8f),
                contentColor = White,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp)
            ) {
                Text(
                    text = "Point at a scene to get composition tips",
                    style = MaterialTheme.typography.bodyLarge,
                    color = White,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)
                )
            }
        }

        // D. Top Left Settings Icon
        IconButton(
            onClick = onNavigateToSettings,
            modifier = Modifier
                .statusBarsPadding()
                .align(Alignment.TopStart)
                .padding(16.dp)
                .background(SurfaceDark.copy(alpha = 0.7f), CircleShape)
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Settings",
                tint = White
            )
        }

        // E. Top Right Score Gauge & Camera Toggle
        Row(
            modifier = Modifier
                .statusBarsPadding()
                .align(Alignment.TopEnd)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Camera toggle
            IconButton(
                onClick = { cameraController.flipCamera() },
                modifier = Modifier
                    .background(SurfaceDark.copy(alpha = 0.7f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Cached,
                    contentDescription = "Flip Camera",
                    tint = White
                )
            }

            // Circular composition score gauge
            compositionResult?.let { result ->
                val scoreColor = when {
                    result.overallScore >= 85 -> ScorePerfect
                    result.overallScore >= 70 -> ScoreGood
                    else -> ScoreBad
                }
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .background(SurfaceDark.copy(alpha = 0.8f), CircleShape)
                        .border(3.dp, scoreColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = result.overallScore.toInt().toString(),
                        style = MaterialTheme.typography.titleMedium,
                        color = White
                    )
                }
            }
        }

        // F. Bottom suggestions and controls panel
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Suggestions chips
            compositionResult?.let { result ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
                ) {
                    result.bestSuggestions.forEach { suggestion ->
                        SuggestionChip(suggestion = suggestion)
                    }
                }
            }

            // Bottom control bar (Gallery icon, Shutter button)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                // Gallery button on the left
                IconButton(
                    onClick = onNavigateToGallery,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .size(54.dp)
                        .background(SurfaceDark.copy(alpha = 0.7f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.PhotoLibrary,
                        contentDescription = "Gallery",
                        tint = White
                    )
                }

                // Shutter FAB in the center
                LargeFloatingActionButton(
                    onClick = {
                        cameraController.takePhoto { uri ->
                            // Photo captured successfully!
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
    }
}

@Composable
fun SuggestionChip(suggestion: com.framewise.engine.types.Suggestion) {
    Surface(
        shape = CircleShape,
        color = SurfaceDark.copy(alpha = 0.85f),
        contentColor = White,
        modifier = Modifier.padding(2.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Icon representing SuggestionType
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
                text = suggestion.text,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
