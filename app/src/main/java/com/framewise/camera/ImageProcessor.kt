package com.framewise.camera

import android.graphics.Bitmap
import com.framewise.engine.types.Subject

/**
 * Interface for ML-based or heuristic image processors that detect subjects
 * and faces in a frame. The default [FrameAnalyzer] fallback uses a simple
 * centre-weighted heuristic when no [ImageProcessor] is supplied.
 *
 * In a production app this would delegate to ML Kit object detection,
 * a custom TFLite model, or MediaPipe face detection.
 */
interface ImageProcessor {

    /**
     * Detect all subjects (people, faces, salient objects) in the given bitmap.
     * The bitmap is always 512 px wide (maintaining aspect ratio) — the
     * [FrameAnalyzer] handles the downscale.
     */
    fun detectSubjects(bitmap: Bitmap): List<Subject>
}
