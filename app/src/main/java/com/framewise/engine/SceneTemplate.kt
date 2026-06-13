package com.framewise.engine

import android.content.Context
import android.util.Log
import com.framewise.engine.types.Scene
import org.json.JSONArray
import org.json.JSONObject

/**
 * 场景构图模板（对标可颂「灵感跟拍」）。
 *
 * 每个场景类型配一组构图建议，从 templates.json 加载。
 */
data class SceneTemplate(
    val scene: Scene,
    val label: String,
    val icon: String,
    val recommendedRules: List<String>,
    val suggestedFilter: String,
    val description: String,
    val tips: List<String>,
    val poseHint: String? = null,
    val subjectX: Double = 0.5,
    val subjectY: Double = 0.33,
    val subjectSize: Double = 0.15,
)

/**
 * 场景模板仓库，从 [templates.json] 加载。
 */
object SceneTemplateRepository {

    private const val TAG = "SceneTemplateRepo"
    private var templates: Map<Scene, SceneTemplate> = emptyMap()
    private var loaded = false

    /** 从 raw resource 加载模板数据。需在应用启动时调用一次。 */
    fun load(context: Context) {
        if (loaded) return
        try {
            val rawId = context.resources.getIdentifier("templates", "raw", context.packageName)
            if (rawId == 0) { Log.w(TAG, "templates.json not found"); createDefaults(); return }
            val json = context.resources.openRawResource(rawId).bufferedReader().use { it.readText() }
            val arr = JSONArray(json)
            val map = mutableMapOf<Scene, SceneTemplate>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val scene = sceneFromString(obj.getString("scene"))
                val tips = mutableListOf<String>()
                val tipsArr = obj.optJSONArray("tips")
                if (tipsArr != null) for (j in 0 until tipsArr.length()) tips.add(tipsArr.getString(j))
                map[scene] = SceneTemplate(
                    scene = scene,
                    label = obj.getString("label"),
                    icon = obj.getString("icon"),
                    recommendedRules = obj.getJSONArray("recommendedRules").let { a -> (0 until a.length()).map { a.getString(it) } },
                    suggestedFilter = obj.getString("suggestedFilter"),
                    description = obj.getString("description"),
                    tips = tips,
                    poseHint = obj.optString("poseHint", null),
                    subjectX = obj.optDouble("subjectX", 0.5),
                    subjectY = obj.optDouble("subjectY", 0.33),
                    subjectSize = obj.optDouble("subjectSize", 0.15),
                )
            }
            templates = map
            loaded = true
            Log.d(TAG, "Loaded ${map.size} scene templates")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load templates", e)
            createDefaults()
        }
    }

    /** 获取场景匹配的模板，没有匹配返回 null */
    fun getTemplate(scene: Scene): SceneTemplate? = templates[scene]

    /** 获取所有模板 */
    fun getAllTemplates(): Collection<SceneTemplate> = templates.values

    private fun createDefaults() {
        templates = mapOf(
            Scene.PORTRAIT to SceneTemplate(
                Scene.PORTRAIT, "人像", "🧑",
                listOf("thirds", "headroom", "leading_lines"), "warm",
                "竖向构图，人物头部放在上三分线交点",
                listOf("降低机位10cm，让下巴与下三分线对齐", "侧光拍摄增强面部立体感", "背景选择简洁纯色"),
                poseHint = "身体微侧45°，重心放在后脚，双手自然下垂或插兜",
                subjectY = 0.30, subjectSize = 0.20,
            ),
            Scene.LANDSCAPE to SceneTemplate(
                Scene.LANDSCAPE, "风景", "🌄",
                listOf("thirds", "horizon", "leading_lines"), "cool",
                "地平线对齐上三分线，突出前景",
                listOf("降低机位到膝盖高度增加层次感", "寻找道路/河流作为引导线", "使用小光圈保证前后景清晰"),
                subjectY = 0.33, subjectSize = 0.12,
            ),
            Scene.FOOD to SceneTemplate(
                Scene.FOOD, "美食", "🍜",
                listOf("center", "golden_ratio", "diagonal"), "vintage",
                "手机与桌面呈45°角俯拍，主体在中央偏右",
                listOf("侧光拍摄让阴影在左前方", "搭配餐具/桌布增加画面层次", "背景虚化突出主体"),
                subjectX = 0.58, subjectY = 0.45, subjectSize = 0.25,
            ),
            Scene.ARCHITECTURE to SceneTemplate(
                Scene.ARCHITECTURE, "建筑", "🏛️",
                listOf("symmetry", "thirds", "golden_ratio"), "bw",
                "站在建筑中轴线上，左右对称",
                listOf("使用广角端拍摄完整建筑", "检查竖线是否垂直", "选择晴天早晚光线柔和的时段"),
                subjectX = 0.50, subjectY = 0.40, subjectSize = 0.18,
            ),
            Scene.GENERIC to SceneTemplate(
                Scene.GENERIC, "通用", "📸",
                listOf("thirds", "leading_lines", "fill_the_frame"), "original",
                "尝试三分法构图，让主体占据画面三分之一",
                listOf("寻找干净的背景", "注意画面边缘不要有杂物", "尝试不同角度拍摄"),
                subjectY = 0.33, subjectSize = 0.15,
            ),
        )
        loaded = true
        Log.d(TAG, "Created ${templates.size} default templates")
    }

    private fun sceneFromString(s: String): Scene = when (s.lowercase()) {
        "portrait" -> Scene.PORTRAIT
        "landscape" -> Scene.LANDSCAPE
        "food" -> Scene.FOOD
        "architecture" -> Scene.ARCHITECTURE
        else -> Scene.GENERIC
    }
}
