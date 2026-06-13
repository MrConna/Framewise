package com.framewise.engine.rules

import com.framewise.engine.CompositionRule
import com.framewise.engine.types.PhotoAnalysis
import com.framewise.engine.types.RuleResult
import com.framewise.engine.types.Suggestion
import com.framewise.engine.types.SuggestionType
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

object HorizonLevel : CompositionRule {
    override val id = "horizon-level"
    override val name = "Horizon Level"
    override val priority = 88

    override fun score(analysis: PhotoAnalysis): RuleResult {
        val angle = horizonAngle(analysis)
            ?: return result(id, 0.5, emptyList())
        val deviation = abs(angle)
        val score = clamp01(1.0 - deviation / 5.0)
        val suggestions = if (score >= 0.8) {
            emptyList()
        } else {
            listOf(
                Suggestion(
                    type = SuggestionType.ROTATE,
                    text = "Rotate the camera or crop to make the horizon level.",
                    params = mapOf("rotateDeg" to -angle),
                ),
            )
        }
        return result(id, score, suggestions)
    }

    private fun horizonAngle(analysis: PhotoAnalysis): Double? {
        if (analysis.horizon.detected) return analysis.horizon.angle
        return analysis.lines
            .filter { line ->
                val angle = ((line.angle % 180.0) + 180.0) % 180.0
                abs(angle) < 15.0 || abs(angle - 180.0) < 15.0
            }
            .maxByOrNull { it.strength }
            ?.angle
    }

    private fun clamp01(value: Double) = max(0.0, min(1.0, value))

    private fun result(ruleId: String, score: Double, suggestions: List<Suggestion>) =
        RuleResult(ruleId = ruleId, score = score, passed = score >= 0.8, suggestions = suggestions)
}
