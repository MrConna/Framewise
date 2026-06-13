# 🧑‍💻 Framewise — Developer Handoff Guide

## How to Pick Up Development

### 1. First Build
```bash
export JAVA_HOME=/usr/local/opt/openjdk@17
export ANDROID_HOME=$HOME/Library/Android/sdk
cd Framewise && ./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 2. Debug with Logcat
```bash
adb logcat -s Framewise Framewise/FrameAnalyzer Framewise/CompPipeline Framewise/CameraX Framewise/CameraController
```

### 3. Current Status (2026-06-13) — v0.3.0

| Priority | Issue | Status | Notes |
|----------|-------|--------|-------|
| **P0** | FrameAnalyzer 真实 YUV→Bitmap 转换 | ✅ **v0.2.0** | 新增直接 YUV→RGB 转换通道 |
| **P1** | 图库权限 | ✅ **v0.2.0** | AndroidManifest 已包含权限声明 |
| **P2** | 滤镜应用到保存的 JPEG | ✅ **v0.2.0** | `applyColorFilterToBitmap()` |
| **P3** | 定时器 UX | ✅ **v0.2.0** | 圆形进度环 + 取消按钮 + spring |
| **P0** | 场景模板系统 (SceneTemplate) | ✅ **v0.3.0** | 对标可颂「灵感跟拍」, 5 套模板 + 推荐条 UI |
| **P0** | 拍后构图分析 (PostCaptureAnalyzer) | ✅ **v0.3.0** | 对标可颂「AI辅助调整」, GalleryScreen BottomSheet |
| **P0** | 实时直方图 (HistogramView) | ✅ **v0.3.0** | 对标剪映, CameraScreen 悬浮渲染 |
| **P1** | 场景匹配模板推荐条 | ✅ **v0.3.0** | SceneTemplateRecommendationBar + Pipeline 链路 |

### 4. New Features in v0.3.0 — 对标剪映/可颂

- **🎭 场景模板系统 (SceneTemplate)** — 对标可颂「灵感跟拍」
  - 5 套场景模板（人像/风光/美食/建筑/通用），从 JSON 加载
  - 每套含推荐构图规则、滤镜建议、拍摄技巧、姿势提示
  - `CameraCompositionPipeline` 自动匹配场景 → 更新推荐条
- **🔍 拍后构图分析 (PostCaptureAnalyzer)** — 对标可颂「AI辅助调整」
  - 已拍照二次分析：评分 0-100、改进建议、裁剪/旋转/滤镜推荐
  - `GalleryScreen` 点击「分析构图」按钮 → BottomSheet 展示
- **📊 实时曝光直方图 (HistogramView)** — 对标剪映
  - 64-bin 亮度分布直方图，spring 动画平滑更新
  - 欠曝/过曝红色 warning 标记
- **🎯 场景匹配模板推荐条 (SceneTemplateRecommendationBar)**
  - 底部悬浮推荐条，显示场景图标 + 模板名 + 拍摄提示 + 推荐滤镜
  - Pipeline 链路闭环：FrameAnalyzer → Scene → 模板匹配 → UI

### 5. Key Architecture Notes
- **Engine is pure Kotlin, no Android deps**: `engine/` can be unit-tested on JVM
- **FrameAnalyzer 路径**: YUV_420_888 → yuv420ToRgb888Direct (首选) → NV21→JPEG (fallback)
- **CameraCompositionPipeline** is the central data flow: FrameAnalyzer → Pipeline → Engine → UI
- **滤镜两通道**:
  1. 预览层: `FilterPreviewOverlay` (Compose ColorMatrix overlay)
  2. 保存照片: `CameraController.applyFilterToSavedImage()` (IO 线程异步 Bitmap 处理)

### 6. Version Rule
**app/build.gradle.kts 的 versionName 必须与 git tag 一致**

每次发布前:
1. 更新 versionName 为新的 tag 号
2. 执行 assembleDebug 确认构建通过
3. git tag + gh release
4. 更新 docs/CHANGELOG.md

### 7. Quick Testing on Emulator
The camera doesn't work well on Android emulator. For UI testing:
- Demo mode activates after 3s without real frames — shows varying data every 2s
- All composition rules produce mock results in demo mode

### 8. Rule Engine Testing
```kotlin
val engine = PhotoCompositionEngine()
engine.registerRules(ALL_RULES)
val analysis = generateMockAnalysis("portrait")
val results = engine.evaluate(analysis)
```

单元测试：`app/src/test/java/com/framewise/engine/PhotoCompositionEngineTest.kt`（14 tests）

### 9. 已知待改进

#### ✅ v0.2.0 新增
| 改进项 | 状态 | 参考 |
|--------|------|------|
| 🖼️ 图库双指缩放 | ✅ pinch-to-zoom + 重置 | awesome-android-ui: TouchImageView |
| 📳 拍照 Haptic | ✅ KEYBOARD_TAP + CONFIRM | Android HapticFeedback |
| 🎨 主题切换 | ✅ system/dark/light 三态 | FilterChip + SettingsState |
| 🔄 页面过渡动画 | ✅ slide+fade 组合 | buildAnimatedNavHost |
| 🪟 底部栏毛玻璃 | ✅ Compose blur(2.dp) | awesome-android-ui: EtsyBlur |

#### ✅ v0.3.0 新增 — 对标剪映/可颂
| 改进项 | 状态 | 参考 |
|--------|------|------|
| 🎭 场景模板系统 | ✅ SceneTemplate + 5 套模板 + 推荐条 UI | 对标可颂「灵感跟拍」 |
| 🔍 拍后构图分析 | ✅ PostCaptureAnalyzer + GalleryScreen BottomSheet | 对标可颂「AI辅助调整」 |
| 📊 实时直方图 | ✅ HistogramView + CameraScreen 悬浮渲染 | 对标剪映实时直方图 |
| 🎯 场景匹配模板推荐 | ✅ SceneTemplateRecommendationBar + Pipeline 链路 | CameraCompositionPipeline 自动匹配 |

#### 📋 仍待完成
| 改进项 | 说明 | 来源/参考 | 优先级 |
|--------|------|-----------|--------|
| 水平仪音频反馈 | 水平接近时发出 beep | 对标剪映水平仪 | P0 |
| 黄金比例螺旋线 UI | Canvas 渲染黄金螺旋叠加层 | 对标剪映构图辅助 | P0 |
| Lottie 引导动画 | 首次使用引导动效 | awesome-android-ui: Lottie | P1 |
| Lottie 加载动画 | 图库加载 Lottie | awesome-android-ui: Lottie+Compose | P1 |
| 拍前参考图叠加 | AR 半透明构图模板叠加 | 对标可颂 AR 引导 | P1 |
| 自定义相机分辨率 | 分辨率选择器 | CameraX ResolutionSelector | P2 |
| 表情/手势快门 | 微笑检测自动拍摄 | CameraX + ML Kit | P2 |
| 照片对比 | 拍前/拍后对比 | 用户体验改进 | P2 |
| 真机测试 | YUV→RGB 直连效果 | 需真机验证 | — |
