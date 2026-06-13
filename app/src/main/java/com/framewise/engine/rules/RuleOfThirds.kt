package com.framewise.engine.rules

import com.framewise.engine.CompositionRule
import com.framewise.engine.types.PhotoAnalysis
import com.framewise.engine.types.Point
import com.framewise.engine.types.Rect
import com.framewise.engine.types.RuleResult
import com.framewise.engine.types.Subject
import com.framewise.engine.types.Suggestion
import com.framewise.engine.types.SuggestionType
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

object RuleOfThirds : CompositionRule {
    // TS id is "rule-of-thirds"; "thirds" matches the conflict-pair keys in
    // PhotoCompositionEngine so symmetry/center/golden_ratio conflicts resolve.
    override val id = "thirds"
    override val name = "Rule of Thirds"
    override val priority = 90

    override fun score(analysis: PhotoAnalysis): RuleResult {
        val frame = frameSize(analysis)
        val subject = primarySubject(analysis)
        val suggestions = mutableListOf<Suggestion>()
        val scores = mutableListOf<Double>()

        if (subject != null) {
            val center = rectCenter(subject.bounds)
            val intersections = listOf(1.0 / 3.0, 2.0 / 3.0).flatMap { x ->
                listOf(1.0 / 3.0, 2.0 / 3.0).map { y ->
                    Point(x * frame.width, y * frame.height)
                }
            }
            val nearest = nearestPoint(center, intersections)
            val maxDistance = hypot(frame.width, frame.height) / 3.0
            val subjectScore = clamp01(1.0 - nearest.distance / maxDistance)
            scores += subjectScore
            if (subjectScore < 0.7) {
                suggestions += Suggestion(
                    type = SuggestionType.RECOMPOSE,
                    text = "Move the main subject toward a rule-of-thirds intersection.",
                    params = mapOf(
                        "target" to nearest.point,
                        "dx" to (nearest.point.x - center.x),
                        "dy" to (nearest.point.y - center.y),
                    ),
                )
            }
        }

        if (analysis.horizon.detected) {
            val angle = abs(analysis.horizon.angle)
            val horizonY = normalizeCoord(analysis.horizon.y, frame.height)
            val nearestLine = listOf(frame.height / 3.0, frame.height * 2.0 / 3.0)
                .minBy { abs(it - horizonY) }
            val offset = abs(horizonY - nearestLine) / frame.height
            val horizonScore = clamp01(1.0 - offset / 0.15 - angle / 8.0)
            scores += horizonScore
            if (offset > 0.05 || angle > 2.0) {
                suggestions += Suggestion(
                    type = SuggestionType.RECOMPOSE,
                    text = "Align the horizon with the upper or lower third line.",
                    params = mapOf(
                        "targetY" to nearestLine,
                        "dy" to (nearestLine - horizonY),
                        "rotateDeg" to -angle,
                    ),
                )
            }
        }

        val score = if (scores.isNotEmpty()) scores.average() else 0.0
        return result(id, score, suggestions)
    }

    private data class FrameSize(val width: Double, val height: Double)
    private data class Nearest(val point: Point, val distance: Double)

    private fun frameSize(analysis: PhotoAnalysis): FrameSize {
        val grid = analysis.saliencyGrid
        if (grid.isNotEmpty() && grid.firstOrNull()?.isNotEmpty() == true) {
            return FrameSize(grid.first().size.toDouble(), grid.size.toDouble())
        }
        val maxX = max(
            1.0,
            maxOf(
                analysis.subjects.maxOfOrNull { it.bounds.x + it.bounds.width } ?: 1.0,
                analysis.lines.maxOfOrNull { max(it.start.x, it.end.x) } ?: 1.0,
            ),
        )
        val maxY = max(
            1.0,
            maxOf(
                analysis.subjects.maxOfOrNull { it.bounds.y + it.bounds.height } ?: 1.0,
                analysis.lines.maxOfOrNull { max(it.start.y, it.end.y) } ?: 1.0,
            ),
        )
        return FrameSize(if (maxX <= 1.0) 1.0 else maxX, if (maxY <= 1.0) 1.0 else maxY)
    }

    private fun primarySubject(analysis: PhotoAnalysis): Subject? =
        analysis.subjects.maxByOrNull { it.confidence * area(it.bounds) }

    private fun rectCenter(rect: Rect) = Point(rect.x + rect.width / 2.0, rect.y + rect.height / 2.0)

    private fun nearestPoint(point: Point, targets: List<Point>): Nearest =
        targets.fold(Nearest(targets.first(), Double.POSITIVE_INFINITY)) { best, target ->
            val distance = hypot(point.x - target.x, point.y - target.y)
            if (distance < best.distance) Nearest(target, distance) else best
        }

    private fun normalizeCoord(value: Double, max: Double) = if (value <= 1.0) value * max else value

    private fun area(rect: Rect) = rect.width * rect.height

    private fun clamp01(value: Double) = max(0.0, min(1.0, value))

    private fun result(ruleId: String, score: Double, suggestions: List<Suggestion>) =
        RuleResult(ruleId = ruleId, score = score, passed = score >= 0.7, suggestions = suggestions)
}
