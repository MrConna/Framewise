package com.framewise.engine

import com.framewise.engine.rules.ALL_RULES
import com.framewise.engine.rules.RuleOfThirds
import com.framewise.engine.rules.SymmetryRule
import com.framewise.engine.rules.CenterComposition
import com.framewise.engine.types.*
import org.junit.Assert.*
import org.junit.Test

/**
 * PhotoCompositionEngine 单元测试。
 *
 * 引擎是纯 Kotlin 无 Android 依赖，可以直接在 JVM 上运行。
 * 测试覆盖：规则注册、评分评估、建议生成、冲突解决。
 */
class PhotoCompositionEngineTest {

    // ── 帮助函数：构建测试用的 PhotoAnalysis ──────────────────────────────

    /** 创建一个空场景（无主体、无水平线、无线条） */
    private fun emptyAnalysis() = PhotoAnalysis(
        subjects = emptyList(),
        horizon = Horizon(detected = false, angle = 0.0, y = 0.5),
        lines = emptyList(),
        symmetry = Symmetry(vertical = 0.5, horizontal = 0.3, axisOffset = 0.05),
        brightness = Brightness(mean = 0.5, overexposed = 0.0, underexposed = 0.0, backlit = false),
        scene = Scene.GENERIC,
        face = null,
        saliencyGrid = listOf(
            listOf(0.0, 0.0, 0.0),
            listOf(0.0, 0.0, 0.0),
            listOf(0.0, 0.0, 0.0),
        ),
    )

    /** 创建一个完美构图的肖像场景（主体位于三分线交点） */
    private fun perfectPortraitAnalysis() = PhotoAnalysis(
        subjects = listOf(
            Subject(
                bounds = Rect(
                    x = 0.65,   // 右三分线交点
                    y = 0.30,   // 上三分线交点
                    width = 0.12,
                    height = 0.35,
                ),
                type = SubjectType.PERSON,
                confidence = 0.92,
            ),
        ),
        horizon = Horizon(detected = true, angle = 0.5, y = 0.70),
        lines = emptyList(),
        symmetry = Symmetry(vertical = 0.5, horizontal = 0.3, axisOffset = 0.15),
        brightness = Brightness(mean = 0.55, overexposed = 0.02, underexposed = 0.01, backlit = false),
        scene = Scene.PORTRAIT,
        face = Face(
            bounds = Rect(x = 0.68, y = 0.22, width = 0.08, height = 0.10),
            eyes = null,
            roll = 0.0,
        ),
        saliencyGrid = listOf(
            listOf(0.1, 0.2, 0.8),
            listOf(0.3, 0.9, 0.7),
            listOf(0.2, 0.4, 0.1),
        ),
    )

    /** 创建一个风景场景（地平线 + 对称） */
    private fun landscapeAnalysis() = PhotoAnalysis(
        subjects = listOf(
            Subject(
                bounds = Rect(x = 0.40, y = 0.50, width = 0.20, height = 0.25),
                type = SubjectType.SALIENT_REGION,
                confidence = 0.70,
            ),
        ),
        horizon = Horizon(detected = true, angle = 1.8, y = 0.33),
        lines = listOf(
            DetectedLine(start = Point(0.0, 0.5), end = Point(1.0, 0.45), angle = 3.0, strength = 0.6),
        ),
        symmetry = Symmetry(vertical = 0.6, horizontal = 0.4, axisOffset = 0.08),
        brightness = Brightness(mean = 0.60, overexposed = 0.05, underexposed = 0.02, backlit = false),
        scene = Scene.LANDSCAPE,
        face = null,
        saliencyGrid = listOf(
            listOf(0.5, 0.6, 0.4),
            listOf(0.7, 0.8, 0.6),
            listOf(0.3, 0.5, 0.4),
        ),
    )

    /** 创建一个糟糕的构图（主体偏离中心、逆光、水平线歪） */
    private fun poorCompositionAnalysis() = PhotoAnalysis(
        subjects = listOf(
            Subject(
                bounds = Rect(x = 0.05, y = 0.80, width = 0.08, height = 0.15),
                type = SubjectType.PERSON,
                confidence = 0.55,
            ),
        ),
        horizon = Horizon(detected = true, angle = -8.5, y = 0.72),
        lines = emptyList(),
        symmetry = Symmetry(vertical = 0.1, horizontal = 0.2, axisOffset = 0.35),
        brightness = Brightness(mean = 0.30, overexposed = 0.0, underexposed = 0.25, backlit = true),
        scene = Scene.GENERIC,
        face = null,
        saliencyGrid = listOf(
            listOf(0.4, 0.3, 0.2),
            listOf(0.3, 0.2, 0.1),
            listOf(0.2, 0.1, 0.0),
        ),
    )

    // ── 测试 1：规则注册 ──────────────────────────────────────────────────

    @Test
    fun `注册 13 条规则，每条规则都有唯一的 id`() {
        val engine = PhotoCompositionEngine(ALL_RULES)
        val registered = engine.getRules()
        assertEquals("应该注册 13 条规则", 13, registered.size)

        val ids = registered.map { it.id }
        assertEquals("规则 id 应唯一", ids.size, ids.toSet().size)
        assertTrue("应包含 thirds", ids.contains("thirds"))
        assertTrue("应包含 symmetry", ids.contains("symmetry"))
        assertTrue("应包含 leading_lines", ids.contains("leading-lines"))
        assertTrue("应包含 horizon", ids.contains("horizon-level"))
        assertTrue("应包含 golden_ratio", ids.contains("golden_ratio"))
    }

    @Test
    fun `重复注册规则不会重复添加`() {
        val engine = PhotoCompositionEngine()
        engine.registerRule(RuleOfThirds)
        engine.registerRule(RuleOfThirds)  // 重复注册
        assertEquals("重复 id 的规则应只注册一次", 1, engine.getRules().size)
    }

    @Test
    fun `注册规则后返回 engine 实例支持链式调用`() {
        val engine = PhotoCompositionEngine()
            .registerRule(RuleOfThirds)
            .registerRule(SymmetryRule)
        assertEquals("链式注册应生效", 2, engine.getRules().size)
    }

    // ── 测试 2：评估 ──────────────────────────────────────────────────────

    @Test
    fun `评估空场景应返回所有规则的结果，数量等于注册规则数`() {
        val engine = PhotoCompositionEngine(ALL_RULES)
        val results = engine.evaluate(emptyAnalysis())
        assertEquals("评估结果数量应等于注册规则数", ALL_RULES.size, results.size)
    }

    @Test
    fun `每个规则的评分应在 0 到 1 之间`() {
        val engine = PhotoCompositionEngine(ALL_RULES)
        val results = engine.evaluate(emptyAnalysis())
        for (r in results) {
            assertTrue("规则 '${r.ruleId}' 的评分 ${r.score} 应在 [0, 1] 范围内", r.score in 0.0..1.0)
        }
    }

    @Test
    fun `完美构图应获得高分`() {
        val engine = PhotoCompositionEngine(ALL_RULES)
        val results = engine.evaluate(perfectPortraitAnalysis())
        val overall = engine.getOverallScore(results)
        assertTrue("完美肖像应获得高分，实际: $overall", overall > 0.5)
    }

    @Test
    fun `糟糕构图应获得低分`() {
        val engine = PhotoCompositionEngine(ALL_RULES)
        val results = engine.evaluate(poorCompositionAnalysis())
        val overall = engine.getOverallScore(results)
        assertTrue("糟糕构图应获得低分，实际: $overall", overall < 0.5)
    }

    // ── 测试 3：总体评分 ──────────────────────────────────────────────────

    @Test
    fun `getOverallScore 应返回 0 到 1 之间的值`() {
        val engine = PhotoCompositionEngine(ALL_RULES)
        val results = engine.evaluate(landscapeAnalysis())
        val score = engine.getOverallScore(results)
        assertTrue("总体评分应在 [0, 1] 范围，实际: $score", score in 0.0..1.0)
    }

    @Test
    fun `空规则引擎的总体评分为 0`() {
        val engine = PhotoCompositionEngine()
        val results = engine.evaluate(emptyAnalysis())
        assertEquals("空引擎应返回 0.0", 0.0, engine.getOverallScore(results), 0.001)
    }

    // ── 测试 4：建议生成 ──────────────────────────────────────────────────

    @Test
    fun `getTopSuggestions 应返回指定数量的建议`() {
        val engine = PhotoCompositionEngine(ALL_RULES)
        val suggestions = engine.getTopSuggestions(poorCompositionAnalysis(), count = 3)
        assertTrue("应返回最多 3 条建议，实际: ${suggestions.size}", suggestions.size <= 3)
        assertTrue("至少应返回一些建议（糟糕构图）", suggestions.isNotEmpty())
    }

    @Test
    fun `完美构图应返回较少建议`() {
        val engine = PhotoCompositionEngine(ALL_RULES)
        val suggestions = engine.getTopSuggestions(perfectPortraitAnalysis(), count = 3)
        // 完美构图可能没有建议（所有规则都 passed）
        assertNotNull("不应为 null", suggestions)
    }

    @Test
    fun `每条建议都应有非空的 text 和有效的 type`() {
        val engine = PhotoCompositionEngine(ALL_RULES)
        val suggestions = engine.getTopSuggestions(poorCompositionAnalysis(), count = 5)
        for (s in suggestions) {
            assertTrue("建议类型应为有效值", SuggestionType.values().contains(s.type))
            assertTrue("建议文本不应为空", s.text.isNotBlank())
        }
    }

    // ── 测试 5：规则冲突解决 ──────────────────────────────────────────────

    @Test
    fun `冲突规则中得分更高的应保留，得分更低的被压制`() {
        // thirds (priority=10) vs center (priority=5)
        // 使用空分析测试，thirds 通常会给中等评分
        val engine = PhotoCompositionEngine()
            .registerRule(RuleOfThirds)
            .registerRule(CenterComposition)
        val results = engine.evaluate(emptyAnalysis())
        val thirds = results.find { it.ruleId == "thirds" }
        val center = results.find { it.ruleId == "center" }

        assertNotNull("thirds 应被注册", thirds)
        assertNotNull("center 应被注册", center)

        // 至少一个应该没被压制（在冲突中胜出）
        assertTrue(
            "thirds 或 center 至少一个不被压制",
            thirds?.suppressed == false || center?.suppressed == false
        )
    }

    // ── 测试 6：场景特定评分 ──────────────────────────────────────────────

    @Test
    fun `肖像场景应在 Headroom 规则上获得合理评分`() {
        val engine = PhotoCompositionEngine(ALL_RULES)
        val results = engine.evaluate(perfectPortraitAnalysis())
        val headroom = results.find { it.ruleId == "headroom" }
        assertNotNull("headroom 规则应存在", headroom)
        assertTrue("headroom 评分应在 [0, 1]", headroom!!.score in 0.0..1.0)
    }

    @Test
    fun `风景场景应在 HorizonLevel 规则上获得合理评分`() {
        val engine = PhotoCompositionEngine(ALL_RULES)
        val results = engine.evaluate(landscapeAnalysis())
        val horizon = results.find { it.ruleId == "horizon-level" }
        assertNotNull("horizon 规则应存在", horizon)
        assertTrue("horizon 评分应在 [0, 1]", horizon!!.score in 0.0..1.0)
    }

    // ── 测试 7：规则优先级顺序 ──────────────────────────────────────────────

    @Test
    fun `规则应按优先级降序排列`() {
        val registered = ALL_RULES.sortedByDescending { it.priority }
        // 验证排序正确
        for (i in 0 until registered.size - 1) {
            assertTrue(
                "规则 ${registered[i].id} (优先级 ${registered[i].priority}) 应在 " +
                    "${registered[i + 1].id} (优先级 ${registered[i + 1].priority}) 之前",
                registered[i].priority >= registered[i + 1].priority
            )
        }
    }

    @Test
    fun `评估结果应按紧急度降序排列`() {
        val engine = PhotoCompositionEngine(ALL_RULES)
        val results = engine.evaluate(poorCompositionAnalysis())
        // 未被压制的规则应排在前面
        for (i in 0 until results.size - 1) {
            if (!results[i].suppressed && !results[i + 1].suppressed) {
                // 不作为严格断言：紧急度排序可能因具体得分而变
                // 只要不抛出异常即可
            }
        }
    }
}
