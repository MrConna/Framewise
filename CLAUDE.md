# Framewise — Agent 开发指引

## 项目概览
实时拍照构图指导 Android App。CameraX + ML Kit + Jetpack Compose。

## 版本
Current: v0.3.0 (对标剪映/可颂：场景模板 + 拍后分析 + 实时直方图)
Next: v0.4.0 (音频反馈 + 螺旋线UI + Lottie 动画)

## 开发守则
1. **每次发布前先读 docs/HANDOFF.md** — 了解当前痛点和优先级
2. **版本号必须一致** — app/build.gradle.kts 的 versionName 必须等于 git tag
3. **每次修改后更新 docs/CHANGELOG.md**
4. **功能完整后再打 tag** — assembleDebug + testDebugUnitTest 通过

## 快速上手
```bash
export JAVA_HOME=/usr/local/opt/openjdk@17
export ANDROID_HOME=$HOME/Library/Android/sdk
cd Framewise && ./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## 架构
```
CameraX → FrameAnalyzer → CameraCompositionPipeline → PhotoCompositionEngine → UI
                                    ↓
                          SceneTemplateRepository
```

## 核心文件

### Camera Pipeline
- `CameraController.kt` — CameraX 生命周期 + 拍照 + 变焦 + 闪光灯 + 滤镜 + 定时器 + 水平仪音频
- `FrameAnalyzer.kt` — YUV→RGB 直连转换 + 主体/地平线/亮度/场景检测
- `CameraCompositionPipeline.kt` — 管线连接 + Demo mode + 场景模板匹配

### Engine
- `PhotoCompositionEngine.kt` — 13条规则引擎核心
- `rules/*.kt` — 13条构图规则（三分法/对称/引导线等）
- `SceneTemplate.kt` — 场景模板系统（对标可颂「灵感跟拍」）
- `PostCaptureAnalyzer.kt` — 拍后构图分析（对标可颂 AI辅助调整）
- `PoseLibrary.kt` — 人像姿势库

### UI
- `CameraScreen.kt` — 主界面：取景器 + 网格 + 评分 + 滤镜 + 直方图 + 模板推荐 + 水平仪
- `SettingsScreen.kt` — 设置：规则开关 + 主题切换 + 统计
- `GalleryScreen.kt` — 图库：网格 + 查看器 + 缩放 + 拍后分析
- `OnboardingScreen.kt` — 首次使用引导
- `NavGraph.kt` — 导航 + 过渡动画
- `components/HistogramView.kt` — 实时直方图

### Docs
- `docs/BENCHMARK.md` — 对标剪映/可颂分析
- `docs/CHANGELOG.md` — 版本变更记录
- `docs/HANDOFF.md` — 待修问题 + 接手指引
- `docs/ARCHITECTURE.md` — 项目架构

## 当前状态（2026-06-13）

### ✅ 已完成（v0.2.0）
- P0: FrameAnalyzer YUV→RGB转换
- P1: 图库权限
- P2: 滤镜保存到JPEG
- P3: 定时器体验优化
- UI动画大升级（spring/弧形/滑动）
- 主题切换（深色/浅色）
- 页面过渡动画
- 底部毛玻璃
- Haptic反馈
- 图库缩放
- 引擎测试 17 项

### ✅ 已完成（v0.3.0）
- 🎭 场景模板系统（SceneTemplate + templates.json 5 套模板 + 推荐条 UI）
- 🔍 拍后构图分析（PostCaptureAnalyzer + GalleryScreen BottomSheet）
- 📊 实时直方图（HistogramView + CameraScreen 悬浮渲染）
- 🎯 场景匹配模板推荐（SceneTemplateRecommendationBar + Pipeline 链路）
- 🗺️ 首次使用引导页（OnboardingScreen.kt 创建，待接线）
- 🧍 人像姿势库（PoseLibrary.kt 创建，待接线）

### 🔄 待处理（v0.4.0）
- P0: 水平仪音频反馈（水平接近时 beep）
- P0: 黄金比例螺旋线 Canvas 渲染
- P1: Lottie 引导动画集成（OnboardingScreen 已创建）
- P1: 姿势库接线（PoseLibrary 已创建）
- P2: 拍前参考图叠加（AR 半透明构图模板）
- —: 真机测试
- —: 打tag v0.3.0
