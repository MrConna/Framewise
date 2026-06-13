package com.framewise.engine.rules

import com.framewise.engine.CompositionRule
import com.framewise.engine.types.DetectedLine
import com.framewise.engine.types.PhotoAnalysis
import com.framewise.engine.types.RuleResult
import com.framewise.engine.types.Suggestion
import com.framewise.engine.types.SuggestionType
import kotlin.math.max
import kotlin.math.min

object DiagonalRule : CompositionRule {
    override val id = "diagonal"
    override val name = "Diagonal"
    override val priority = 5

    override fun score(analysis: PhotoAnalysis): RuleResult {
        val suggestions = mutableListOf<Suggestion>()
        val subjectDiagonal = 0.5

        val angles = classifyLineAngles(analysis.lines)
        val totalLines = angles.horizontal + angles.vertical + angles.diagonalUp + angles.diagonalDown
        val lineDiagonal = if (totalLines > 0) {
            val diagonalFraction = (angles.diagonalUp + angles.diagonalDown).toDouble() / totalLines
            clamp(diagonalFraction * 2.0)
        } else {
            0.5
        }

        val score = clamp(subjectDiagonal * 0.3 + lineDiagonal * 0.7)

        if (score < 0.5 && totalLines > 3) {
            suggestions += Suggestion(
                type = SuggestionType.CHANGE_ANGLE,
                text = "画面太平淡，旋转相机让主体/线条沿对角线方向",
                params = mapOf("angleHint" to 45),
            )
        } else if (totalLines < 3) {
            suggestions += Suggestion(
                type = SuggestionType.INFO,
                text = "对角线构图可增加动感，尝试安排主体沿对角线方向",
            )
        }

        return RuleResult(ruleId = id, score = score, passed = score >= 0.6, suggestions = suggestions)
    }

    private data class AngleBuckets(
        val horizontal: Int,
        val vertical: Int,
        val diagonalUp: Int,
        val diagonalDown: Int,
    )

    private fun classifyLineAngles(lines: List<DetectedLine>): AngleBuckets {
        var horizontal = 0
        var vertical = 0
        var diagonalUp = 0
        var diagonalDown = 0
        for (line in lines) {
            val angle = ((line.angle % 180.0) + 180.0) % 180.0
            when {
                (angle in 0.0..15.0) || (angle >= 165.0 && angle < 180.0) -> horizontal++
                angle in 75.0..105.0 -> vertical++
                angle in 30.0..60.0 -> diagonalUp++
                angle in 120.0..150.0 -> diagonalDown++
            }
        }
        return AngleBuckets(horizontal, vertical, diagonalUp, diagonalDown)
    }

    private fun clamp(value: Double) = max(0.0, min(1.0, value))
}
