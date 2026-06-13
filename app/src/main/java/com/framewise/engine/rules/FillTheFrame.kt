package com.framewise.engine.rules

import com.framewise.engine.CompositionRule
import com.framewise.engine.types.PhotoAnalysis
import com.framewise.engine.types.Rect
import com.framewise.engine.types.RuleResult
import com.framewise.engine.types.Scene
import com.framewise.engine.types.Subject
import com.framewise.engine.types.SubjectType
import com.framewise.engine.types.Suggestion
import com.framewise.engine.types.SuggestionType
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

object FillTheFrame : CompositionRule {
    override val id = "fill_the_frame"
    override val name = "Fill the Frame"
    override val priority = 5

    override fun score(analysis: PhotoAnalysis): RuleResult {
        val suggestions = mutableListOf<Suggestion>()
        val subject = primarySubject(analysis)
        val suitsFill =
            analysis.scene == Scene.PRODUCT ||
                analysis.scene == Scene.FOOD ||
                (analysis.scene == Scene.PORTRAIT && subject?.type == SubjectType.FACE)

        if (subject == null) {
            return RuleResult(ruleId = id, score = 0.5, passed = true)
        }

        val ratio = subjectAreaRatio(subject)
        val fillScore = when {
            ratio >= 0.70 -> clamp(1.0 - (1.0 - ratio) / 0.3)
            ratio >= 0.40 -> clamp((ratio - 0.40) / 0.3)
            else -> 0.0
        }
        val touched = edgesTouched(subject.bounds)
        val edgeScore = clamp(touched / 4.0)
        val sceneWeight = if (suitsFill) 1.0 else 0.4
        val score = clamp((fillScore * 0.6 + edgeScore * 0.4) * sceneWeight)

        if (suitsFill && ratio < 0.5) {
            suggestions += Suggestion(
                type = SuggestionType.ADJUST_ZOOM,
                text = "靠近主体或拉近焦距，排除背景干扰让主体充满画面",
                params = mapOf("zoomFactor" to round1(0.7 / ratio)),
            )
        }

        if (!suitsFill && score < 0.4) {
            suggestions += Suggestion(
                type = SuggestionType.INFO,
                text = "当前场景不太适合填充构图，裁切过多可能丢失环境信息",
            )
        }

        if (analysis.face != null && touched >= 3) {
            suggestions += Suggestion(type = SuggestionType.RECOMPOSE, text = "面部太靠近画面边缘，退后确保面部完整")
        }

        return RuleResult(ruleId = id, score = score, passed = score >= 0.65, suggestions = suggestions)
    }

    private fun primarySubject(analysis: PhotoAnalysis): Subject? =
        analysis.subjects.maxByOrNull { it.confidence * max(it.bounds.width * it.bounds.height, 1e-4) }

    private fun subjectAreaRatio(subject: Subject) = subject.bounds.width * subject.bounds.height

    private fun edgesTouched(bounds: Rect): Int {
        var count = 0
        if (bounds.x <= 0.02) count++
        if (bounds.x + bounds.width >= 0.98) count++
        if (bounds.y <= 0.02) count++
        if (bounds.y + bounds.height >= 0.98) count++
        return count
    }

    private fun clamp(value: Double) = max(0.0, min(1.0, value))

    private fun round1(value: Double) = round(value * 10.0) / 10.0
}
