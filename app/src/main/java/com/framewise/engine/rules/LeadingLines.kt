package com.framewise.engine.rules

import com.framewise.engine.CompositionRule
import com.framewise.engine.types.DetectedLine
import com.framewise.engine.types.PhotoAnalysis
import com.framewise.engine.types.Point
import com.framewise.engine.types.Rect
import com.framewise.engine.types.RuleResult
import com.framewise.engine.types.Subject
import com.framewise.engine.types.Suggestion
import com.framewise.engine.types.SuggestionType
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

object LeadingLines : CompositionRule {
    override val id = "leading-lines"
    override val name = "Leading Lines"
    override val priority = 84

    override fun score(analysis: PhotoAnalysis): RuleResult {
        val subject = primarySubject(analysis)
        val lines = analysis.lines.filter { lineLength(it) > 0.0 }
        if (subject == null || lines.isEmpty()) {
            return result(
                id,
                0.0,
                listOf(
                    Suggestion(
                        type = SuggestionType.CHANGE_ANGLE,
                        text = "Look for roads, railings, shadows, or architecture that can lead toward the subject.",
                    ),
                ),
            )
        }

        val target = rectCenter(subject.bounds)
        val lineScores = lines.map { lineDirectness(it, target) * clamp01(it.strength) }
        val best = max(0.0, lineScores.maxOrNull() ?: 0.0)
        val strongCount = lineScores.count { it > 0.6 }
        val convergenceBonus = if (strongCount >= 2) 0.15 else 0.0
        val score = clamp01(best * 0.75 + averageTop(lineScores, 3) * 0.25 + convergenceBonus)
        val suggestions = if (score >= 0.6) {
            emptyList()
        } else {
            listOf(
                Suggestion(
                    type = SuggestionType.RECOMPOSE,
                    text = "Adjust the camera so strong lines converge toward the subject instead of away from it.",
                ),
            )
        }
        return result(id, score, suggestions)
    }

    private fun lineDirectness(line: DetectedLine, target: Point): Double {
        val toTargetA = angleBetween(line.start, target)
        val toTargetB = angleBetween(line.end, target)
        val lineAngle = normalizeAngle(line.angle)
        val directness = maxOf(
            angleScore(lineAngle, toTargetA),
            angleScore(lineAngle, toTargetB),
            angleScore(lineAngle + 180.0, toTargetA),
            angleScore(lineAngle + 180.0, toTargetB),
        )
        val distance = minOf(
            distancePointToLine(target, line.start, line.end),
            hypot(target.x - line.start.x, target.y - line.start.y),
            hypot(target.x - line.end.x, target.y - line.end.y),
        )
        return directness * clamp01(1.0 - distance / 0.35)
    }

    private fun primarySubject(analysis: PhotoAnalysis): Subject? =
        analysis.subjects.maxByOrNull { it.confidence * it.bounds.width * it.bounds.height }

    private fun rectCenter(rect: Rect) = Point(rect.x + rect.width / 2.0, rect.y + rect.height / 2.0)

    private fun angleBetween(a: Point, b: Point) =
        normalizeAngle(atan2(b.y - a.y, b.x - a.x) * 180.0 / PI)

    private fun angleScore(a: Double, b: Double): Double {
        val delta = min(abs(normalizeAngle(a) - normalizeAngle(b)), 360.0 - abs(normalizeAngle(a) - normalizeAngle(b)))
        return clamp01(1.0 - delta / 30.0)
    }

    private fun normalizeAngle(angle: Double): Double = ((angle % 360.0) + 360.0) % 360.0

    private fun distancePointToLine(point: Point, a: Point, b: Point): Double {
        val numerator = abs((b.y - a.y) * point.x - (b.x - a.x) * point.y + b.x * a.y - b.y * a.x)
        val denominator = hypot(b.y - a.y, b.x - a.x).takeIf { it != 0.0 } ?: 1.0
        return numerator / denominator
    }

    private fun lineLength(line: DetectedLine) = hypot(line.end.x - line.start.x, line.end.y - line.start.y)

    private fun averageTop(values: List<Double>, n: Int): Double {
        val top = values.sortedDescending().take(n)
        return if (top.isNotEmpty()) top.average() else 0.0
    }

    private fun clamp01(value: Double) = max(0.0, min(1.0, value))

    private fun result(ruleId: String, score: Double, suggestions: List<Suggestion>) =
        RuleResult(ruleId = ruleId, score = score, passed = score >= 0.6, suggestions = suggestions)
}
