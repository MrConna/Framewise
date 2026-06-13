package com.framewise.engine.types

/**
 * Mirror of the TypeScript photo-composition engine types.
 * All coordinate values are normalized to [0, 1] relative to the frame.
 */

data class Point(val x: Double, val y: Double)

data class Rect(val x: Double, val y: Double, val width: Double, val height: Double)

enum class SubjectType { PERSON, FACE, OBJECT, SALIENT_REGION }

data class Subject(
    val bounds: Rect,
    val type: SubjectType,
    val confidence: Double,
)

data class DetectedLine(
    val start: Point,
    val end: Point,
    val angle: Double,
    val strength: Double,
)

data class Horizon(
    val detected: Boolean,
    val angle: Double,
    val y: Double,
)

data class Symmetry(
    val vertical: Double,
    val horizontal: Double,
    val axisOffset: Double,
)

data class Brightness(
    val mean: Double,
    val overexposed: Double,
    val underexposed: Double,
    val backlit: Boolean,
    val histogram: List<Int> = emptyList(),
)

enum class Scene {
    PORTRAIT, FOOD, LANDSCAPE, ARCHITECTURE, STREET, PRODUCT, GENERIC
}

data class Face(
    val bounds: Rect,
    val eyes: List<Point>?,
    val roll: Double,
)

data class PhotoAnalysis(
    val subjects: List<Subject>,
    val horizon: Horizon,
    val lines: List<DetectedLine>,
    val symmetry: Symmetry,
    val brightness: Brightness,
    val scene: Scene,
    val face: Face?,
    val saliencyGrid: List<List<Double>>,
)

enum class SuggestionType {
    MOVE_CAMERA, ROTATE, ADJUST_ZOOM, CHANGE_ANGLE, ADJUST_EXPOSURE, RECOMPOSE, INFO
}

data class Suggestion(
    val type: SuggestionType,
    val text: String,
    val params: Map<String, Any> = emptyMap(),
)

data class RuleResult(
    val ruleId: String,
    val score: Double,
    val passed: Boolean,
    val suggestions: List<Suggestion> = emptyList(),
    val suppressed: Boolean = false,
)

data class CompositionResult(
    val overallScore: Double,
    val bestSuggestions: List<Suggestion>,
    val activeRules: List<RuleResult>,
)
