package com.framewise.camera

import android.util.Log
import com.framewise.engine.PhotoCompositionEngine
import com.framewise.engine.types.CompositionResult
import com.framewise.engine.types.PhotoAnalysis
import com.framewise.engine.types.RuleResult
import com.framewise.engine.types.Suggestion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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
) {

    companion object {
        private const val TAG = "CompPipeline"
    }

    private val _compositionResult = MutableStateFlow<CompositionResult?>(null)
    val compositionResult: StateFlow<CompositionResult?> = _compositionResult.asStateFlow()

    /**
     * The most recent raw [PhotoAnalysis]. The UI overlay observes this to draw
     * the detected horizon and subject boxes (the [compositionResult] only
     * carries scores + suggestions).
     */
    private val _photoAnalysis = MutableStateFlow<PhotoAnalysis?>(null)
    val photoAnalysis: StateFlow<PhotoAnalysis?> = _photoAnalysis.asStateFlow()

    /** Number of frames analysed since start. */
    private var frameCount = 0

    /**
     * Attach the pipeline to the frame analyzer's callback.
     * Call this once after the camera is started.
     */
    fun attach() {
        frameAnalyzer.onAnalysisReady = { analysis ->
            processFrame(analysis)
        }
        Log.d(TAG, "Pipeline attached")
    }

    /**
     * Detach from the frame analyzer.
     */
    fun detach() {
        frameAnalyzer.onAnalysisReady = null
        _compositionResult.value = null
        _photoAnalysis.value = null
        Log.d(TAG, "Pipeline detached")
    }

    /**
     * Force a re-evaluation of the last [PhotoAnalysis] (useful after the
     * user changes a setting that affects rule weighting).
     */
    fun refresh() {
        // The [compositionResult] already holds the last result; the UI can
        // observe it without re-processing. If the engine uses mutable state
        // this is where we'd re-evaluate.
        Log.d(TAG, "Refresh requested (no-op, result already emitted)")
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
