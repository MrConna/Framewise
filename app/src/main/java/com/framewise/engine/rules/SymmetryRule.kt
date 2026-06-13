package com.framewise.engine.rules

import com.framewise.engine.CompositionRule
import com.framewise.engine.types.PhotoAnalysis
import com.framewise.engine.types.RuleResult
import com.framewise.engine.types.Suggestion
import com.framewise.engine.types.SuggestionType
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

object SymmetryRule : CompositionRule {
    override val id = "symmetry"
    override val name = "Symmetry"
    override val priority = 82

    override fun score(analysis: PhotoAnalysis): RuleResult {
        val explicit = max(analysis.symmetry.vertical, analysis.symmetry.horizontal)
        val gridScore = gridSymmetry(analysis.saliencyGrid)
        val score = clamp01(max(explicit, gridScore))
        val suggestions = if (score >= 0.7) {
            emptyList()
        } else {
            listOf(
                Suggestion(
                    type = SuggestionType.RECOMPOSE,
                    text = "Center the camera on the symmetry axis or crop evenly from both sides.",
                ),
            )
        }
        return RuleResult(ruleId = id, score = score, passed = score >= 0.7, suggestions = suggestions)
    }

    private fun gridSymmetry(grid: List<List<Double>>): Double {
        if (grid.isEmpty() || grid.firstOrNull()?.isEmpty() != false) return 0.0
        val vertical = compareHalves(grid, vertical = true)
        val horizontal = compareHalves(grid, vertical = false)
        return max(vertical, horizontal)
    }

    private fun compareHalves(grid: List<List<Double>>, vertical: Boolean): Double {
        val height = grid.size
        val width = grid.first().size
        var diff = 0.0
        var total = 0.0
        val h = if (vertical) height else height / 2
        val w = if (vertical) width / 2 else width
        for (y in 0 until h) {
            for (x in 0 until w) {
                val a = grid.getOrNull(y)?.getOrNull(x) ?: 0.0
                val b = if (vertical) {
                    grid.getOrNull(y)?.getOrNull(width - 1 - x) ?: 0.0
                } else {
                    grid.getOrNull(height - 1 - y)?.getOrNull(x) ?: 0.0
                }
                diff += abs(a - b)
                total += maxOf(abs(a), abs(b), 1.0)
            }
        }
        return clamp01(1.0 - diff / total)
    }

    private fun clamp01(value: Double) = max(0.0, min(1.0, value))
}
