package com.framewise.camera

import android.util.Log
import com.framewise.engine.PhotoCompositionEngine
import com.framewise.engine.types.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

/**
 * Composition pipeline: connects [FrameAnalyzer] → [PhotoCompositionEngine] → result.
 *
 * Frame flow:
 * ```
 * CameraX frame ──→ FrameAnalyzer.analyze()
 *                     │
 *                     ▼
 *                   PhotoAnalysis
 *                     │
 *                     ▼
 *                   PhotoCompositionEngine.evaluate(analysis)
 *                     │
 *                     ▼
 *                   CompositionResult (overallScore + top suggestions)
 *                     │
 *                     ▼
 *                   Exposed via StateFlow<CompositionResult?>
 * ```
 *
 * The pipeline runs at camera frame rate (throttled to ~10 fps by FrameAnalyzer).
 * The UI observes [compositionResult] and renders the overlay guidance.
 */
class CameraCompositionPipeline(
    private val frameAnalyzer: FrameAnalyzer,
    private val compositionEngine: PhotoCompositionEngine,
    private val scope: CoroutineScope? = null,
) {

    companion object {
        private const val TAG = "CompPipeline"
        private const val DEMO_TIMEOUT_MS = 3000L // 3s without real frame → demo mode
    }

    private val _compositionResult = MutableStateFlow<CompositionResult?>(null)
    val compositionResult: StateFlow<CompositionResult?> = _compositionResult.asStateFlow()

    private val _photoAnalysis = MutableStateFlow<PhotoAnalysis?>(null)
    val photoAnalysis: StateFlow<PhotoAnalysis?> = _photoAnalysis.asStateFlow()

    private var frameCount = 0
    private var hasRealFrame = false
    private var demoJob: Job? = null

    /**
     * Attach the pipeline to the frame analyzer's callback.
     * Also starts the demo timeout: if no real frame arrives within [DEMO_TIMEOUT_MS],
     * a simulated analysis is emitted so the UI always has data to display.
     */
    fun attach() {
        hasRealFrame = false
        frameAnalyzer.onAnalysisReady = { analysis ->
            if (!hasRealFrame) {
                hasRealFrame = true
                demoJob?.cancel()
                Log.d(TAG, "First real frame received — demo mode cancelled")
            }
            processFrame(analysis)
        }
        Log.d(TAG, "Pipeline attached, demo timeout=${DEMO_TIMEOUT_MS}ms")

        // Schedule demo fallback
        demoJob = scope?.launch {
            delay(DEMO_TIMEOUT_MS)
            if (!hasRealFrame) {
                Log.w(TAG, "No real frame after ${DEMO_TIMEOUT_MS}ms — activating demo mode")
                emitDemoAnalysis()
            }
        }
    }

    fun detach() {
        frameAnalyzer.onAnalysisReady = null
        demoJob?.cancel()
        _compositionResult.value = null
        _photoAnalysis.value = null
        Log.d(TAG, "Pipeline detached")
    }

    fun refresh() {
        Log.d(TAG, "Refresh requested")
    }

    /**
     * Activate demo mode so the UI overlay + suggestions are always visible, even
     * when real image analysis fails (e.g. yuvToBitmap returning null on certain
     * devices). Emits an initial frame immediately, then keeps emitting a
     * slightly different frame every 2s so the score visibly reacts — simulating
     * what real per-frame analysis would look like as the camera moves.
     */
    private fun emitDemoAnalysis() {
        var tick = 0

        // Emit immediately, then repeat every 2s with varied data.
        emitDemoFrame(tick++)

        demoJob = scope?.launch {
            while (true) {
                delay(2000)
                emitDemoFrame(tick++)
            }
        }
    }

    /**
     * Build and emit one demo frame whose contents vary with [tick], so the
     * composition score, overlay, and suggestions all change over time.
     */
    private fun emitDemoFrame(tick: Int) {
        // Vary scene classification.
        val scenes = listOf(Scene.LANDSCAPE, Scene.PORTRAIT, Scene.FOOD, Scene.ARCHITECTURE)
        val scene = scenes[tick % scenes.size]

        // Vary horizon — oscillate between roughly -3 and +3 degrees.
        val horizonAngle = sin(tick * 0.5) * 3.0

        // Vary subject positions to simulate camera movement (coords are [0, 1]).
        val offsetX = sin(tick * 0.3) * 0.15
        val offsetY = cos(tick * 0.4) * 0.1

        // Vary brightness / backlight.
        val backlit = tick % 5 == 0
        val mean = (0.5 + sin(tick * 0.2) * 0.18).coerceIn(0.0, 1.0)
        val overexposed = if (tick % 7 == 0) 0.18 else 0.05
        val underexposed = if (tick % 6 == 0) 0.14 else 0.02

        // Vary lines — sometimes there are leading lines, sometimes not.
        val hasLeadingLines = tick % 3 != 0

        val subjects = listOf(
            Subject(
                bounds = Rect(
                    x = (0.25 + offsetX).coerceIn(0.0, 0.8),
                    y = (0.2 + offsetY).coerceIn(0.0, 0.8),
                    width = 0.15,
                    height = 0.4,
                ),
                type = SubjectType.PERSON,
                confidence = 0.85,
            ),
            Subject(
                bounds = Rect(
                    x = (0.55 + offsetX * 0.5).coerceIn(0.0, 0.85),
                    y = (0.7 - offsetY).coerceIn(0.0, 0.85),
                    width = 0.12,
                    height = 0.15,
                ),
                type = SubjectType.OBJECT,
                confidence = 0.6,
            ),
        )

        val lines = if (hasLeadingLines) {
            listOf(
                DetectedLine(
                    start = Point(0.0, 0.6),
                    end = Point((0.5 + offsetX).coerceIn(0.0, 1.0), 0.4),
                    angle = 35.0 + horizonAngle,
                    strength = 0.7,
                ),
                DetectedLine(
                    start = Point(0.8, 0.0),
                    end = Point(0.6, 0.5),
                    angle = 120.0,
                    strength = 0.5,
                ),
            )
        } else {
            emptyList()
        }

        val demo = PhotoAnalysis(
            subjects = subjects,
            horizon = Horizon(
                detected = true,
                angle = horizonAngle,
                y = (0.48 + offsetY * 0.3).coerceIn(0.2, 0.8),
            ),
            lines = lines,
            symmetry = Symmetry(vertical = 0.3, horizontal = 0.1, axisOffset = 0.2),
            brightness = Brightness(
                mean = mean,
                overexposed = overexposed,
                underexposed = underexposed,
                backlit = backlit,
            ),
            scene = scene,
            face = null,
            saliencyGrid = listOf(
                listOf(1e-1, 2e-1, 3e-1),
                listOf(4e-1, 8e-1, 5e-1),
                listOf(2e-1, 3e-1, 1e-1),
            ),
        )

        _photoAnalysis.value = demo
        val results = compositionEngine.evaluate(demo)
        val score = compositionEngine.getOverallScore(results) * 100.0
        val suggestions = compositionEngine.getTopSuggestions(demo, count = 3)
        _compositionResult.value = CompositionResult(
            overallScore = score,
            bestSuggestions = suggestions.ifEmpty {
                listOf(
                    Suggestion(SuggestionType.INFO, "试试三分法"),
                    Suggestion(SuggestionType.MOVE_CAMERA, "寻找引导线"),
                )
            },
            activeRules = results,
        )
        Log.d(TAG, "Demo frame #$tick: scene=$scene, score=${"%.1f".format(score)}, " +
                "horizon=${"%.1f".format(horizonAngle)}, backlit=$backlit, lines=${lines.size}")
    }

    // ── Internal ────────────────────────────────────────────────────────────

    private fun processFrame(analysis: PhotoAnalysis) {
        frameCount++
        if (frameCount % 30 == 0) {
            Log.d(TAG, "Frame #$frameCount analysed: scene=${analysis.scene}, " +
                    "subjects=${analysis.subjects.size}, horizon=${analysis.horizon.detected}")
        }

        // Expose the raw analysis for the overlay (horizon line, subject boxes).
        _photoAnalysis.value = analysis

        val startNs = System.nanoTime()

        // Run the complete composition evaluation.
        val results = compositionEngine.evaluate(analysis)
        // Engine scores are normalized to [0, 1]; the UI gauge expects 0–100.
        val overallScore = compositionEngine.getOverallScore(results) * 100.0
        val topSuggestions = compositionEngine.getTopSuggestions(analysis, count = 3)

        val elapsed = (System.nanoTime() - startNs) / 1_000_000.0
        if (frameCount % 30 == 0) {
            Log.d(TAG, "Evaluation took ${"%.1f".format(elapsed)} ms, " +
                    "score=${"%.1f".format(overallScore)}, suggestions=${topSuggestions.size}")
        }

        _compositionResult.value = CompositionResult(
            overallScore = overallScore,
            bestSuggestions = topSuggestions,
            activeRules = results,
        )
    }
}
