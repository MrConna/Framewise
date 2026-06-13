# 📋 Framewise — Changelog & Bug History

## v0.3.0 (2026-06-13) — 对标剪映/可颂：场景模板 + 拍后分析 + 实时直方图

### 🆕 新增功能

- **🎭 场景模板系统 (SceneTemplate)** — 对标可颂「灵感跟拍」
  - `engine/SceneTemplate.kt` + `templates.json` 5 套场景模板（人像/风光/美食/建筑/通用）
  - 每套模板含推荐构图规则、滤镜建议、拍摄技巧、姿势提示
  - `SceneTemplateRepository` 从 JSON 加载模板，支持运行时匹配
- **🔍 拍后构图分析 (PostCaptureAnalyzer)** — 对标可颂「AI辅助调整」
  - `engine/PostCaptureAnalyzer.kt` 对已拍照进行二次构图分析
  - 输出评分 0-100、改进建议列表、推荐裁剪/旋转/滤镜
  - `GalleryScreen` BottomSheet 展示分析结果，含评分颜色分级
- **📊 实时曝光直方图 (HistogramView)** — 对标剪映实时直方图
  - `ui/components/HistogramView.kt` 64-bin 亮度分布直方图
  - 左暗右亮，红色 warning 标记欠曝/过曝
  - spring 动画平滑过渡，悬浮在取景器角落
- **🎯 场景匹配模板推荐条 (SceneTemplateRecommendationBar)**
  - `CameraScreen.kt` 底部悬浮推荐条，根据 AI 检测场景显示对应模板
  - 显示场景图标、模板名、拍摄提示、推荐滤镜标签
  - `CameraCompositionPipeline` 链路：FrameAnalyzer → Scene 识别 → 模板匹配 → UI 渲染

### ✅ 新增集成
- **GalleyScreen** 接入 PostCaptureAnalyzer，支持点击「分析构图」按钮查看拍后分析
- **CameraScreen** 集成 HistogramView 和 SceneTemplateRecommendationBar
- **CameraCompositionPipeline** 新增 `currentTemplate` StateFlow 场景模板匹配

### 📋 待继续
- 水平仪音频反馈（AudioFeedback）
- 黄金比例螺旋线 Canvas 渲染
- Lottie 引导动画
- 拍前参考图叠加（AR 半透明构图模板）

## v0.2.0 (2026-06-13)

### 修复
- **P0: FrameAnalyzer 真实分析终于工作！**
  - 新增直接 YUV_420_888 → RGB 转换通道（yuv420ToRgb888Direct）
  - 修复 NV21 交错逻辑，正确使用 uStride/vStride 和 pixelStride
  - 修复 centreLuma4 硬编码 stride=256
  - 自适应亮度阈值 + 性能监控 + 详细调试日志
  - 4x4 对比度网格替代简单中心加权主体检测
  - 简单边缘检测的 line detection
- **P1: 图库权限验证** — READ_MEDIA_IMAGES (13+) + READ_EXTERNAL_STORAGE (maxSdk=32)
- **P2: 滤镜应用到保存的 JPEG** — CameraController.applyFilterToSavedImage() IO 异步处理
- **P3: 定时器体验优化** — countdownProgress + cancelTimer + 圆形进度环
- **相机错误重试** — CameraX 绑定失败自动重试 2 次

### 新增
- **📳 拍照触感反馈** — 快门按下 HapticFeedbackConstants.KEYBOARD_TAP，保存后 CONFIRM
- **🎨 深色/浅色主题切换** — SettingsState.themeMode（system/dark/light），设置界面 FilterChip 三态选择
- **🔄 页面过渡动画** — slide+fade 组合动画，前进/返回各有独立方向
- **🔍 图库双指缩放** — PhotoViewer 支持 pinch-to-zoom + 重置按钮
- **🪟 底部栏毛玻璃** — Compose blur(2.dp) 效果
- **📊 设置界面统计卡片** — 拍摄数统计、版本号、技术栈信息
- **🧪 引擎单元测试** — 14 项测试覆盖所有核心功能

### 优化
- 🎯 评分仪表盘：半弧形 spring 弹性动画
- ⏱️ 定时器：圆形进度环 + spring 数字缩放 + 点击取消
- 💡 建议标签：slide+fade 组合动画
- 🔄 设置开关：弹性颜色过渡动画
- 🎨 滤镜选择：spring 高亮动画
- 🖼️ 图库：pinch-to-zoom / 刷新按钮 / 权限引导文本
- ⚙️ 设置：图标化 + 分区 + 信息卡片

### Known Issues
- 真机实时分析仍需实际测试验证
- Lottie 引导动画未集成
- 无照片对比功能（拍前 vs 拍后）

## v0.1.6 (2026-06-13)
- 🎨 Color filters: original, warm, cool, vintage, black & white
- 📐 Grid overlay toggle (show/hide rule of thirds lines)
- ⏱️ Capture timer: 3s / 10s countdown before photo

## v0.1.5 (2026-06-13)
- 🔍 Zoom slider (right edge of screen)
- 🔦 Torch/flash toggle button
- 🌐 Full Chinese UI (all strings localized)
- 📱 VersionName synced to match git tag

## v0.1.4 (2026-06-13)
- Demo mode now produces varying data every 2s (score changes dynamically)
- Scene rotates between landscape/portrait/food/architecture

## v0.1.3 (2026-06-13)
- 🌐 Full Chinese localization
- 🖼️ Gallery fixed: MediaStore query now shows captured photos
- 🎨 UI redesign: score semicircle, frosted glass bottom bar, pulse shutter animation
- 💡 Demo Mode: 3s timeout fallback when real analysis fails

## v0.1.2 (2026-06-13)
### Fixed
- **Version now displays correctly** (was hardcoded 0.1.0)
- **Camera error diagnostics**: try-catch around CameraX binding, error banner on screen with Retry button
- **Permission denial guide**: shows "enable in Settings" when permission permanently denied

### Known Issues
- Settings still not persisting across navigation (rememberSaveable limited scope)
- No photo capture feedback (Toast + flash not added yet)
- Composition suggestions not showing (FrameAnalyzer likely fails on real device)

---

## v0.1.1-alpha (2026-06-13)
### Fixed
- **CameraScreen now uses real PhotoCompositionEngine** (was hardcoded mock logic)
- **Empty scene fallback**: shows "Point at a scene" hint after 2s
- **takePhoto error logging** added

### Known Issues
- Settings changes lost on navigation
- No photo capture feedback
- No composition suggestions showing
- Black screen returning from Settings

---

## v0.1.0-alpha (2026-06-13)
### Initial Release
- Android project scaffold (Gradle + Compose + CameraX + ML Kit)
- 13 composition rules (TypeScript → Kotlin)
- Camera pipeline (CameraX preview + ImageAnalysis)
- FrameAnalyzer (YUV→Bitmap → subject/horizon/brightness detection)
- CameraCompositionPipeline (connects analyzer → engine)
- UI: CameraScreen with overlay, SettingsScreen, GalleryScreen
- Rule engine core (PhotoCompositionEngine with register/evaluate/score)

### Known Issues at Launch
- Camera permission not requested properly
- Rule engine not wired to CameraScreen (hardcoded mock)
- No feedback on photo capture
- Settings not persisted
- Suggestions not showing
