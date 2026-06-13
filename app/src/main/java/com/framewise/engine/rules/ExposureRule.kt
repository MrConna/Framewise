package com.framewise.engine.rules

import com.framewise.engine.CompositionRule
import com.framewise.engine.types.PhotoAnalysis
import com.framewise.engine.types.RuleResult
import com.framewise.engine.types.Suggestion
import com.framewise.engine.types.SuggestionType
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

object ExposureRule : CompositionRule {
    override val id = "exposure"
    override val name = "Exposure"
    override val priority = 8

    override fun score(analysis: PhotoAnalysis): RuleResult {
        val suggestions = mutableListOf<Suggestion>()
        val brightness = analysis.brightness

        val overPen = clamp(brightness.overexposed / 0.1)
        val underPen = clamp(brightness.underexposed / 0.1)
        val meanPen = clamp(abs(brightness.mean - 0.5) / 0.4)

        var score = clamp(1.0 - (overPen * 0.4 + underPen * 0.4 + meanPen * 0.2))

        if (brightness.overexposed > 0.1) {
            suggestions += Suggestion(
                type = SuggestionType.ADJUST_EXPOSURE,
                text = "高光过曝，降低曝光补偿",
                params = mapOf("ev" to -0.7),
            )
        }
        if (brightness.underexposed > 0.1 || brightness.mean < 0.3) {
            suggestions += Suggestion(
                type = SuggestionType.ADJUST_EXPOSURE,
                text = "画面偏暗，提高曝光或点测主体",
                params = mapOf("ev" to 0.7),
            )
        }
        if (brightness.backlit) {
            suggestions += Suggestion(
                type = SuggestionType.CHANGE_ANGLE,
                text = "主体逆光，换个角度避开强光或点击主体测光",
            )
            score = clamp(score - 0.15)
        }

        return RuleResult(ruleId = id, score = score, passed = score >= 0.7, suggestions = suggestions)
    }

    private fun clamp(value: Double) = max(0.0, min(1.0, value))
}
