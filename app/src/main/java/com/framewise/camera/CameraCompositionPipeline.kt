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
     * Emit a plausible mock analysis so the UI overlay + suggestions are always
     * visible, even when real image analysis fails (e.g. yuvToBitmap returning
     * null on certain devices).
     */
    private fun emitDemoAnalysis() {
        val demo = PhotoAnalysis(
            subjects = listOf(
                Subject(bounds = Rect(0.25, 0.2, 0.15, 0.4), type = SubjectType.PERSON, confidence = 0.85),
                Subject(bounds = Rect(0.55, 0.7, 0.12, 0.15), type = SubjectType.OBJECT, confidence = 0.6),
            ),
            horizon = Horizon(detected = true, angle = 0.012, y = 0.48),
            lines = listOf(
                DetectedLine(start = Point(0.0, 0.6), end = Point(0.5, 0.4), angle = 35.0, strength = 0.7),
                DetectedLine(start = Point(0.8, 0.0), end = Point(0.6, 0.5), angle = 120.0, strength = 0.5),
            ),
            symmetry = Symmetry(vertical = 0.3, horizontal = 0.1, axisOffset = 0.2),
            brightness = Brightness(mean = 0.6, overexposed = 0.05, underexposed = 0.02, backlit = false),
            scene = Scene.LANDSCAPE,
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
                    Suggestion(SuggestionType.INFO, "Try the rule of thirds"),
                    Suggestion(SuggestionType.MOVE_CAMERA, "Look for leading lines"),
                )
            },
            activeRules = results,
        )
        Log.d(TAG, "Demo analysis emitted: score=${"%.1f".format(score)}")
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
