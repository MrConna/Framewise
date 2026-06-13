package com.framewise.engine.rules

import com.framewise.engine.CompositionRule

val ALL_RULES: List<CompositionRule> = listOf(
    RuleOfThirds,
    SymmetryRule,
    LeadingLines,
    HorizonLevel,
    Headroom,
    ExposureRule,
    FramingRule,
    DiagonalRule,
    CenterComposition,
    NegativeSpace,
    TriangleComposition,
    FillTheFrame,
    GoldenRatio,
).sortedByDescending { it.priority }
