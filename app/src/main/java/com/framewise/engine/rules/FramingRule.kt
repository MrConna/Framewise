package com.framewise.engine.rules

import com.framewise.engine.CompositionRule
import com.framewise.engine.types.PhotoAnalysis
import com.framewise.engine.types.Rect
import com.framewise.engine.types.RuleResult
import com.framewise.engine.types.Subject
import com.framewise.engine.types.Suggestion
import com.framewise.engine.types.SuggestionType
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

object FramingRule : CompositionRule {
    override val id = "framing"
    override val name = "Framing"
    override val priority = 5

    override fun score(analysis: PhotoAnalysis): RuleResult {
        val suggestions = mutableListOf<Suggestion>()
        val subject = primarySubject(analysis)
            ?: return RuleResult(ruleId = id, score = 0.5, passed = true)

        val touched = edgesTouched(subject.bounds)
        val edgeScore = clamp(1.0 - touched * 0.25)

        val perimeterLines = analysis.lines.filter { line ->
            val len = hypot(line.end.x - line.start.x, line.end.y - line.start.y)
            if (len < 0.2) return@filter false
            val mx = (line.start.x + line.end.x) / 2.0
            val my = (line.start.y + line.end.y) / 2.0
            mx < 0.15 || mx > 0.85 || my < 0.15 || my > 0.85
        }

        val sides = mutableSetOf<String>()
        for (line in perimeterLines) {
            val mx = (line.start.x + line.end.x) / 2.0
            val my = (line.start.y + line.end.y) / 2.0
            if (my < 0.15) sides += "top" else if (my > 0.85) sides += "bottom"
            if (mx < 0.15) sides += "left" else if (mx > 0.85) sides += "right"
        }
        val sideCount = sides.size
        val sideScore = clamp(sideCount / 4.0)
        val score = clamp(edgeScore * 0.5 + sideScore * 0.5)

        if (touched >= 3 && sideCount >= 2) {
            suggestions += Suggestion(
                type = SuggestionType.RECOMPOSE,
                text = "主体太靠近画面边缘，应退后让主体完全处于框架内部",
            )
        } else if (sideCount < 2 && score < 0.5) {
            suggestions += Suggestion(
                type = SuggestionType.CHANGE_ANGLE,
                text = "尝试利用拱门/窗户/树枝等自然元素作为前景框架",
            )
        }

        return RuleResult(ruleId = id, score = score, passed = score >= 0.6, suggestions = suggestions)
    }

    private fun primarySubject(analysis: PhotoAnalysis): Subject? =
        analysis.subjects.maxByOrNull { it.confidence * max(it.bounds.width * it.bounds.height, 1e-4) }

    private fun edgesTouched(bounds: Rect): Int {
        var count = 0
        if (bounds.x <= 0.02) count++
        if (bounds.x + bounds.width >= 0.98) count++
        if (bounds.y <= 0.02) count++
        if (bounds.y + bounds.height >= 0.98) count++
        return count
    }

    private fun clamp(value: Double) = max(0.0, min(1.0, value))
}
