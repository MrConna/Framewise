package com.framewise.engine

import com.framewise.engine.types.Scene

data class PoseSuggestion(
    val id: String,
    val name: String,
    val icon: String,
    val description: String,
    val suitableScenes: List<Scene>,
)

object PoseLibrary {
    val portraitPoses = listOf(
        PoseSuggestion(
            id = "standing_relaxed",
            name = "自然站姿",
            icon = "🧍",
            description = "双脚一前一后，肩膀放松，身体微微转向光源。",
            suitableScenes = listOf(Scene.PORTRAIT, Scene.STREET, Scene.PRODUCT),
        ),
        PoseSuggestion(
            id = "side_profile",
            name = "侧身回望",
            icon = "↩️",
            description = "身体侧向镜头，头部回看，保留肩颈线条。",
            suitableScenes = listOf(Scene.PORTRAIT, Scene.STREET),
        ),
        PoseSuggestion(
            id = "wall_lean",
            name = "靠墙半身",
            icon = "🧱",
            description = "肩背轻靠墙面，一只脚点地，手臂自然交叠。",
            suitableScenes = listOf(Scene.PORTRAIT, Scene.STREET, Scene.ARCHITECTURE),
        ),
        PoseSuggestion(
            id = "seated_forward",
            name = "坐姿前倾",
            icon = "🪑",
            description = "坐在椅沿，身体向前，手肘轻放膝盖。",
            suitableScenes = listOf(Scene.PORTRAIT, Scene.FOOD, Scene.PRODUCT),
        ),
        PoseSuggestion(
            id = "hand_near_face",
            name = "托腮近景",
            icon = "🤍",
            description = "一只手靠近脸侧，眼神看向镜头外侧。",
            suitableScenes = listOf(Scene.PORTRAIT),
        ),
        PoseSuggestion(
            id = "walking_motion",
            name = "行走抓拍",
            icon = "🚶",
            description = "向镜头斜前方慢走，前脚落地时按下快门。",
            suitableScenes = listOf(Scene.PORTRAIT, Scene.STREET),
        ),
        PoseSuggestion(
            id = "arms_crossed",
            name = "交叉手臂",
            icon = "✦",
            description = "手臂轻交叉，不要耸肩，适合更利落的气质。",
            suitableScenes = listOf(Scene.PORTRAIT, Scene.ARCHITECTURE),
        ),
        PoseSuggestion(
            id = "look_over_shoulder",
            name = "背身回眸",
            icon = "👀",
            description = "背向镜头站立，头部转回，留出背景空间。",
            suitableScenes = listOf(Scene.PORTRAIT, Scene.LANDSCAPE, Scene.STREET),
        ),
        PoseSuggestion(
            id = "hands_in_pockets",
            name = "插兜站姿",
            icon = "🧥",
            description = "双手或单手插兜，身体重心放在后脚。",
            suitableScenes = listOf(Scene.PORTRAIT, Scene.STREET),
        ),
        PoseSuggestion(
            id = "hair_adjust",
            name = "整理头发",
            icon = "💫",
            description = "一手抬起整理头发，另一手自然垂放。",
            suitableScenes = listOf(Scene.PORTRAIT),
        ),
        PoseSuggestion(
            id = "low_angle_power",
            name = "低机位站姿",
            icon = "📐",
            description = "镜头略低，人物站直，腿部形成稳定三角。",
            suitableScenes = listOf(Scene.PORTRAIT, Scene.ARCHITECTURE),
        ),
        PoseSuggestion(
            id = "window_light",
            name = "窗边侧光",
            icon = "☀️",
            description = "脸侧朝向窗光，身体斜坐或斜站，暗面保留轮廓。",
            suitableScenes = listOf(Scene.PORTRAIT, Scene.FOOD),
        ),
    )

    fun recommendedFor(scene: Scene): List<PoseSuggestion> =
        portraitPoses.filter { scene in it.suitableScenes }
}
