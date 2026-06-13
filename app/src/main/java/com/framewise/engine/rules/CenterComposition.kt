package com.framewise.engine.rules

import com.framewise.engine.CompositionRule
import com.framewise.engine.types.PhotoAnalysis
import com.framewise.engine.types.Point
import com.framewise.engine.types.Rect
import com.framewise.engine.types.RuleResult
import com.framewise.engine.types.Scene
import com.framewise.engine.types.Subject
import com.framewise.engine.types.SubjectType
import com.framewise.engine.types.Suggestion
import com.framewise.engine.types.SuggestionType
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

object CenterComposition : CompositionRule {
    override val id = "center"
    override val name = "Center Composition"
    override val priority = 7

    override fun score(analysis: PhotoAnalysis): RuleResult {
        val suggestions = mutableListOf<Suggestion>()
        val subject = primarySubject(analysis)
        val bestSym = max(analysis.symmetry.vertical, analysis.symmetry.horizontal)
        val isSymmetric = bestSym >= 0.55

        if (subject == null) {
            return RuleResult(ruleId = id, score = 0.5, passed = true)
        }

        val center = rectCenter(subject.bounds)
        val maxOffset = hypot(0.5, 0.5)
        val offset = hypot(center.x - 0.5, center.y - 0.5)
        val offsetScore = clamp(1.0 - offset / maxOffset)

        val suitsCenter =
            analysis.scene == Scene.ARCHITECTURE ||
                analysis.scene == Scene.PRODUCT ||
                (analysis.scene == Scene.PORTRAIT && subject.type == SubjectType.FACE)

        val score = when {
            isSymmetric -> clamp(offsetScore * 0.7 + 0.3)
            suitsCenter -> clamp(offsetScore * 0.6 + 0.2)
            else -> clamp(offsetScore * 0.4 + 0.1)
        }

        if (isSymmetric && offset > 0.08) {
            suggestions += Suggestion(
                type = SuggestionType.MOVE_CAMERA,
                text = "对称场景主体应居中，把主体移到画面正中央",
                params = mapOf("dx" to round3(0.5 - center.x), "dy" to round3(0.5 - center.y)),
            )
        } else if (!isSymmetric && !suitsCenter && score < 0.5) {
            suggestions += Suggestion(
                type = SuggestionType.INFO,
                text = "非对称场景中心构图可能较平淡，可尝试三分法",
            )
        }

        return RuleResult(ruleId = id, score = score, passed = score >= 0.65, suggestions = suggestions)
    }

    private fun primarySubject(analysis: PhotoAnalysis): Subject? =
        analysis.subjects.maxByOrNull { it.confidence * max(it.bounds.width * it.bounds.height, 1e-4) }

    private fun rectCenter(rect: Rect) = Point(rect.x + rect.width / 2.0, rect.y + rect.height / 2.0)

    private fun clamp(value: Double) = max(0.0, min(1.0, value))

    private fun round3(value: Double) = kotlin.math.round(value * 1000.0) / 1000.0
}
