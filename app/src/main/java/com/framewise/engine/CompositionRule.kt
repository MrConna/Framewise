package com.framewise.engine

import com.framewise.engine.types.PhotoAnalysis
import com.framewise.engine.types.RuleResult

/**
 * A single composition rule — the Kotlin equivalent of TypeScript's
 * [CompositionRule] interface.
 *
 * Each rule is a stateless scoring function: given a [PhotoAnalysis], return a
 * [RuleResult] with a [score] in [0, 1], a [passed] flag, and actionable
 * [suggestions] when the rule is violated.
 */
interface CompositionRule {
    val id: String
    val name: String
    val priority: Int

    fun score(analysis: PhotoAnalysis): RuleResult
}
