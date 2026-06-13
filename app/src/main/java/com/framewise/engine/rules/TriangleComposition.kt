package com.framewise.engine.rules

import com.framewise.engine.CompositionRule
import com.framewise.engine.types.PhotoAnalysis
import com.framewise.engine.types.Point
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

object TriangleComposition : CompositionRule {
    override val id = "triangle"
    override val name = "Triangle Composition"
    override val priority = 4

    override fun score(analysis: PhotoAnalysis): RuleResult {
        val suggestions = mutableListOf<Suggestion>()
        val points = mutableListOf<Point>()

        for (subject in topSubjects(analysis, 3)) {
            points += Point(subject.bounds.x + subject.bounds.width / 2.0, subject.bounds.y + subject.bounds.height / 2.0)
        }

        if (points.size < 3 && analysis.face != null) {
            val face = analysis.face
            val faceCenter = Point(face.bounds.x + face.bounds.width / 2.0, face.bounds.y + face.bounds.height / 2.0)
            if (points.none { distance(it, faceCenter) < 0.05 }) points += faceCenter
            if (face.bounds.width > 0.05) {
                val shoulderY = face.bounds.y + face.bounds.height * 1.2
                val shoulderOffset = face.bounds.width * 0.8
                val left = Point(faceCenter.x - shoulderOffset, shoulderY)
                val right = Point(faceCenter.x + shoulderOffset, shoulderY)
                if (points.none { distance(it, left) < 0.05 }) points += left
                if (points.none { distance(it, right) < 0.05 }) points += right
            }
        }

        if (points.size < 3) {
            return RuleResult(
                ruleId = id,
                score = 0.5,
                passed = true,
                suggestions = listOf(Suggestion(type = SuggestionType.INFO, text = "增加主体数量或安排三人成三角站位")),
            )
        }

        val (a, b, c) = points.take(3)
        val triArea = abs(a.x * (b.y - c.y) + b.x * (c.y - a.y) + c.x * (a.y - b.y)) / 2.0
        val areaScore = clamp(triArea * 5.0)
        val eqScore = equilateralScore(a, b, c)

        val sorted = listOf(a, b, c).sortedByDescending { it.y }
        val bottomEdgeAngle = abs(atan2(sorted[0].y - sorted[1].y, sorted[0].x - sorted[1].x) * 180.0 / PI)
        val stabilityScore = clamp(1.0 - min(bottomEdgeAngle, 180.0 - bottomEdgeAngle) / 45.0)
        val score = clamp(areaScore * 0.4 + eqScore * 0.3 + stabilityScore * 0.3)

        if (triArea < 0.08) {
            suggestions += Suggestion(type = SuggestionType.RECOMPOSE, text = "三角形太小，拉开三个元素的间距覆盖更大画面范围")
        }
        if (stabilityScore < 0.4) {
            suggestions += Suggestion(type = SuggestionType.CHANGE_ANGLE, text = "三角形底部倾斜，调整角度让底部接近水平")
        }

        return RuleResult(ruleId = id, score = score, passed = score >= 0.55, suggestions = suggestions)
    }

    private fun topSubjects(analysis: PhotoAnalysis, n: Int): List<Subject> =
        analysis.subjects.sortedByDescending { it.confidence * it.bounds.width * it.bounds.height }.take(n)

    private fun distance(a: Point, b: Point) = hypot(a.x - b.x, a.y - b.y)

    private fun equilateralScore(a: Point, b: Point, c: Point): Double {
        val d1 = distance(a, b)
        val d2 = distance(b, c)
        val d3 = distance(c, a)
        val mn = minOf(d1, d2, d3)
        val mx = maxOf(d1, d2, d3)
        return if (mx > 1e-4) mn / mx else 0.0
    }

    private fun clamp(value: Double) = max(0.0, min(1.0, value))
}
