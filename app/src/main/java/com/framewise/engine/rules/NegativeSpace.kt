package com.framewise.engine.rules

import com.framewise.engine.CompositionRule
import com.framewise.engine.types.PhotoAnalysis
import com.framewise.engine.types.RuleResult
import com.framewise.engine.types.Subject
import com.framewise.engine.types.Suggestion
import com.framewise.engine.types.SuggestionType
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

object NegativeSpace : CompositionRule {
    override val id = "negative_space"
    override val name = "Negative Space"
    override val priority = 4

    override fun score(analysis: PhotoAnalysis): RuleResult {
        val suggestions = mutableListOf<Suggestion>()
        val subject = primarySubject(analysis)
            ?: return RuleResult(ruleId = id, score = 0.5, passed = true)

        val ratio = subjectAreaRatio(subject)
        val areaScore = when {
            ratio <= 0.05 -> 1.0
            ratio <= 0.25 -> clamp(1.0 - (ratio - 0.05) / 0.2)
            else -> 0.0
        }

        val cx = subject.bounds.x + subject.bounds.width / 2.0
        val cy = subject.bounds.y + subject.bounds.height / 2.0
        val offCenter = hypot(cx - 0.5, cy - 0.5)
        val placementScore = clamp(offCenter * 3.0)

        var bgSaliency = 0.0
        var bgCells = 0
        val grid = analysis.saliencyGrid
        for (row in grid.indices) {
            val rowValues = grid[row]
            for (col in rowValues.indices) {
                val cellWidth = 1.0 / rowValues.size.coerceAtLeast(1)
                val cellHeight = 1.0 / grid.size
                val cellX = col * cellWidth
                val cellY = row * cellHeight
                val inside = cx >= cellX && cx <= cellX + cellWidth && cy >= cellY && cy <= cellY + cellHeight
                if (!inside) {
                    bgSaliency += rowValues[col]
                    bgCells++
                }
            }
        }
        val bgEmpty = if (bgCells > 0) clamp(1.0 - bgSaliency / bgCells) else 0.5
        val score = clamp(areaScore * 0.5 + placementScore * 0.2 + bgEmpty * 0.3)

        if (ratio > 0.25) {
            suggestions += Suggestion(
                type = SuggestionType.ADJUST_ZOOM,
                text = "退后或拉远焦距，让主体在画面中更小以增强留白效果",
                params = mapOf("zoomOutFactor" to round1(ratio / 0.15)),
            )
        } else if (placementScore < 0.4) {
            suggestions += Suggestion(
                type = SuggestionType.MOVE_CAMERA,
                text = "留白构图时主体应远离画面中心，移到一侧",
                params = mapOf("dx" to round3(0.17 - cx), "dy" to round3(0.5 - cy)),
            )
        }

        if (bgEmpty < 0.4 && ratio <= 0.25) {
            suggestions += Suggestion(
                type = SuggestionType.CHANGE_ANGLE,
                text = "背景杂乱，寻找简洁的背景（天空/墙面/水面）增强留白效果",
            )
        }

        return RuleResult(ruleId = id, score = score, passed = score >= 0.6, suggestions = suggestions)
    }

    private fun primarySubject(analysis: PhotoAnalysis): Subject? =
        analysis.subjects.maxByOrNull { it.confidence * max(it.bounds.width * it.bounds.height, 1e-4) }

    private fun subjectAreaRatio(subject: Subject) = subject.bounds.width * subject.bounds.height

    private fun clamp(value: Double) = max(0.0, min(1.0, value))

    private fun round1(value: Double) = round(value * 10.0) / 10.0

    private fun round3(value: Double) = round(value * 1000.0) / 1000.0
}
