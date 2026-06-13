package com.framewise.engine.rules

import com.framewise.engine.CompositionRule
import com.framewise.engine.types.PhotoAnalysis
import com.framewise.engine.types.RuleResult
import com.framewise.engine.types.Scene
import com.framewise.engine.types.Subject
import com.framewise.engine.types.SubjectType
import com.framewise.engine.types.Suggestion
import com.framewise.engine.types.SuggestionType
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

object Headroom : CompositionRule {
    override val id = "headroom"
    override val name = "Headroom & Eye Room"
    override val priority = 7

    private const val idealHeadroom = 0.1

    override fun score(analysis: PhotoAnalysis): RuleResult {
        val suggestions = mutableListOf<Suggestion>()
        val face = analysis.face
        val subject = if (face != null) null else primarySubject(analysis)
        val isPortrait = analysis.scene == Scene.PORTRAIT || face != null || subject?.type == SubjectType.PERSON

        if (!isPortrait || (face == null && subject == null)) {
            return RuleResult(ruleId = id, score = 0.8, passed = true, suggestions = suggestions)
        }

        val topY = face?.bounds?.y ?: subject!!.bounds.y
        val headroomGap = topY
        val score = clamp(1.0 - abs(headroomGap - idealHeadroom) / 0.25)

        if (headroomGap < 0.03) {
            suggestions += Suggestion(
                type = SuggestionType.MOVE_CAMERA,
                text = "头顶太挤，向下移一点留出头顶空间",
                params = mapOf("dy" to round3(idealHeadroom - headroomGap)),
            )
        } else if (headroomGap > 0.25) {
            suggestions += Suggestion(
                type = SuggestionType.ADJUST_ZOOM,
                text = "头顶空间过多，靠近或放大主体",
                params = mapOf("dy" to round3(idealHeadroom - headroomGap)),
            )
        }

        if (face != null && abs(face.roll) > 6.0) {
            suggestions += Suggestion(
                type = SuggestionType.ROTATE,
                text = "人物头部倾斜，转正画面",
                params = mapOf("degrees" to round2(-face.roll)),
            )
        }

        return RuleResult(ruleId = id, score = score, passed = score >= 0.7, suggestions = suggestions)
    }

    private fun primarySubject(analysis: PhotoAnalysis): Subject? =
        analysis.subjects.maxByOrNull { it.confidence * max(it.bounds.width * it.bounds.height, 1e-4) }

    private fun clamp(value: Double) = max(0.0, min(1.0, value))

    private fun round2(value: Double) = round(value * 100.0) / 100.0

    private fun round3(value: Double) = round(value * 1000.0) / 1000.0
}
