package com.framewise.engine

import com.framewise.engine.types.CompositionResult
import com.framewise.engine.types.PhotoAnalysis
import com.framewise.engine.types.RuleResult
import com.framewise.engine.types.Suggestion
import com.framewise.engine.types.SuggestionType

/**
 * Kotlin port of the TypeScript [PhotoCompositionEngine].
 *
 * Registration and evaluation mirror the TS design:
 *   - Rules register by [id] (duplicates ignored).
 *   - Conflict pairs suppress contradictory results.
 *   - [evaluate] returns all rule results sorted by urgency.
 *   - [getTopSuggestions] returns the N most urgent suggestions.
 *   - [getOverallScore] returns the priority-weighted average.
 */
class PhotoCompositionEngine(
    rules: List<CompositionRule> = emptyList(),
) {

    private val rules = mutableListOf<CompositionRule>()

    /** Pairs of rule ids that pull in opposite directions. */
    private val conflicts = listOf(
        "symmetry" to "thirds",
        "center" to "thirds",
        "golden_ratio" to "thirds",
        "fill_the_frame" to "negative_space",
    )

    /** The margin a winner needs to suppress the loser. */
    private val conflictMargin = 0.15

    init {
        registerRules(rules)
    }

    fun registerRule(rule: CompositionRule): PhotoCompositionEngine {
        if (rules.none { it.id == rule.id }) {
            rules.add(rule)
        }
        return this
    }

    fun registerRules(rules: List<CompositionRule>): PhotoCompositionEngine {
        for (r in rules) registerRule(r)
        return this
    }

    fun getRules(): List<CompositionRule> = rules.toList()

    fun evaluate(analysis: PhotoAnalysis): List<RuleResult> {
        val results = rules.map { it.score(analysis) }
        val resolved = resolveConflicts(results)
        return resolved.sortedByDescending { urgency(it) }
    }

    fun getTopSuggestions(analysis: PhotoAnalysis, count: Int = 3): List<Suggestion> {
        val results = evaluate(analysis)
        val out = mutableListOf<Suggestion>()
        for (r in results) {
            if (r.suppressed || r.passed) continue
            for (s in r.suggestions) {
                out.add(s)
                if (out.size >= count) return out
            }
        }
        return out
    }

    fun getOverallScore(results: List<RuleResult>): Double {
        var weighted = 0.0
        var weight = 0.0
        for (res in results) {
            if (res.suppressed) continue
            val w = rules.find { it.id == res.ruleId }?.priority?.toDouble() ?: 1.0
            weighted += res.score * w
            weight += w
        }
        return if (weight > 0.0) weighted / weight else 0.0
    }

    private fun resolveConflicts(results: List<RuleResult>): List<RuleResult> {
        val out = results.map { it.copy(suggestions = it.suggestions.toList()) }.toMutableList()
        val index = mutableMapOf<String, Int>()
        out.forEachIndexed { i, r -> index[r.ruleId] = i }

        for ((a, b) in conflicts) {
            val ia = index[a] ?: continue
            val ib = index[b] ?: continue
            val ra = out[ia]
            val rb = out[ib]
            if (kotlin.math.abs(ra.score - rb.score) < conflictMargin) continue
            val loser = if (ra.score > rb.score) rb else ra
            out[if (loser === ra) ia else ib] = loser.copy(suppressed = true, suggestions = emptyList())
        }
        return out
    }

    private fun urgency(result: RuleResult): Double {
        if (result.suppressed) return -1.0
        val priority = rules.find { it.id == result.ruleId }?.priority?.toDouble() ?: 1.0
        return priority * (1.0 - result.score)
    }
}
