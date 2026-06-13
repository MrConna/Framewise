package com.framewise.engine.rules

import com.framewise.engine.CompositionRule
import com.framewise.engine.types.PhotoAnalysis
import com.framewise.engine.types.Point
import com.framewise.engine.types.Rect
import com.framewise.engine.types.RuleResult
import com.framewise.engine.types.Subject
import com.framewise.engine.types.Suggestion
import com.framewise.engine.types.SuggestionType
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

object GoldenRatio : CompositionRule {
    override val id = "golden_ratio"
    override val name = "Golden Ratio"
    override val priority = 8

    private val goldenPoints = listOf(
        Point(0.382, 0.382),
        Point(0.618, 0.382),
        Point(0.382, 0.618),
        Point(0.618, 0.618),
    )

    private val thirdsIntersections = listOf(
        Point(1.0 / 3.0, 1.0 / 3.0),
        Point(2.0 / 3.0, 1.0 / 3.0),
        Point(1.0 / 3.0, 2.0 / 3.0),
        Point(2.0 / 3.0, 2.0 / 3.0),
    )

    override fun score(analysis: PhotoAnalysis): RuleResult {
        val suggestions = mutableListOf<Suggestion>()
        val subject = primarySubject(analysis)
            ?: return RuleResult(ruleId = id, score = 0.5, passed = true)

        val center = rectCenter(subject.bounds)
        val maxGoldenDistance = hypot(0.382, 0.382)
        val nearestGolden = nearestPoint(center, goldenPoints)
        val goldenScore = clamp(1.0 - nearestGolden.distance / maxGoldenDistance)

        val maxThirdsDistance = hypot(1.0 / 3.0, 1.0 / 3.0)
        val thirdsScore = clamp(1.0 - nearestPoint(center, thirdsIntersections).distance / maxThirdsDistance)

        val margin = goldenScore - thirdsScore
        var score = when {
            margin >= 0.1 -> clamp(goldenScore * 0.85 + 0.15)
            margin <= -0.1 -> clamp(goldenScore * 0.4)
            else -> clamp(goldenScore * 0.6 + 0.2)
        }

        val isSingleSubject = analysis.subjects.size <= 1
        if (!isSingleSubject) {
            score = clamp(score * 0.5)
            suggestions += Suggestion(type = SuggestionType.INFO, text = "多主体场景黄金螺旋效果有限，可改用三分法")
        } else if (score < 0.5) {
            suggestions += Suggestion(
                type = SuggestionType.MOVE_CAMERA,
                text = "将主体移到黄金分割交点附近（约画面 0.382 或 0.618 位置）",
                params = mapOf(
                    "dx" to round3(nearestGolden.point.x - center.x),
                    "dy" to round3(nearestGolden.point.y - center.y),
                ),
            )
        }

        return RuleResult(ruleId = id, score = score, passed = score >= 0.7, suggestions = suggestions)
    }

    private data class Nearest(val point: Point, val distance: Double)

    private fun primarySubject(analysis: PhotoAnalysis): Subject? =
        analysis.subjects.maxByOrNull { it.confidence * max(it.bounds.width * it.bounds.height, 1e-4) }

    private fun rectCenter(rect: Rect) = Point(rect.x + rect.width / 2.0, rect.y + rect.height / 2.0)

    private fun nearestPoint(point: Point, targets: List<Point>): Nearest =
        targets.fold(Nearest(targets.first(), Double.POSITIVE_INFINITY)) { best, target ->
            val distance = hypot(point.x - target.x, point.y - target.y)
            if (distance < best.distance) Nearest(target, distance) else best
        }

    private fun clamp(value: Double) = max(0.0, min(1.0, value))

    private fun round3(value: Double) = round(value * 1000.0) / 1000.0
}
