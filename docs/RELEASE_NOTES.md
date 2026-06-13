# 📦 Framewise v0.3.0 Release Notes

**版本**: v0.3.0  
**发布日期**: 2026-06-13  
**里程碑**: 对标剪映/可颂 — 场景模板 + 拍后分析 + 实时直方图

---

## 🚀 新功能

### 🎭 场景模板系统 (SceneTemplate)
对标可颂「灵感跟拍」，首次在 Framewise 中引入场景构图模板。

| 场景 | 模板 | 推荐滤镜 | 核心建议 |
|------|------|---------|---------|
| 🧑 人像 | 三分法 + 对角线 | warm | 主体置于三分线交点 |
| 🏔️ 风光 | 水平线 + 引导线 | cool | 地平线在 1/3 处 |
| 🍜 美食 | 居中 + 填满画面 | warm | 俯拍 45° 强调纹理 |
| 🏛️ 建筑 | 对称 + 透视 | vintage | 寻找对称轴线 |
| 🌐 通用 | 三分法 + 留白 | — | 保持画面简洁 |

- `engine/SceneTemplate.kt` — 模板数据模型 + 仓库（从 `templates.json` 加载）
- `ui/CameraScreen.kt` — `SceneTemplateRecommendationBar` 底部悬浮推荐条
- `camera/CameraCompositionPipeline.kt` — `currentTemplate` StateFlow 自动场景匹配
- 5 套模板覆盖人像/风光/美食/建筑/通用

### 🔍 拍后构图分析 (PostCaptureAnalyzer)
对标可颂「AI辅助调整构图」，拍照完成后二次分析并给出改进建议。

- `engine/PostCaptureAnalyzer.kt` — 独立分析引擎
  - Bitmap 加载 → 亮度/主体/地平线检测 → 改进建议生成
  - 输出：总体评分 (0-100)、改进建议列表 (critical/warning/info)
  - 推荐裁剪区域、旋转角度、滤镜模式
- `ui/GalleryScreen.kt` — 「分析构图」按钮 + BottomSheet 结果展示
  - 评分颜色分级：🟢 ≥80 / 🟠 ≥60 / 🔴 <60
  - 每条建议带严重级别图标 + 颜色编码

### 📊 实时曝光直方图 (HistogramView)
对标剪映实时直方图，取景器角落悬浮显示亮度分布。

- `ui/components/HistogramView.kt` — 64-bin 亮度分布
  - 左暗右亮，红色 warning 标记欠曝/过曝
  - spring 动画平滑过渡
  - 悬浮在 CameraScreen 取景器右上角

### 🎯 场景匹配模板推荐条 (SceneTemplateRecommendationBar)
- 底部悬浮 Surface，圆角 + 毛玻璃半透明效果
- 显示：场景图标 + 模板名 + 拍摄技巧 + 推荐滤镜标签
- 根据 CameraCompositionPipeline 检测到的场景自动切换

### 🗺️ 首次使用引导页面 (OnboardingScreen)
- `ui/OnboardingScreen.kt` — 水平滑动引导页，3 屏介绍核心功能
- 圆形分页指示器 + 跳过/下一步 按钮
- ⚠️ 尚未接入导航图（NavGraph），v0.4.0 完成接线

### 🧍 人像姿势库 (PoseLibrary)
- `engine/PoseLibrary.kt` — 姿势数据模型 + 仓库
- 预定义 10+ 种人像姿势（单人/双人/坐姿/站姿等）
- ⚠️ 尚未接入 UI，v0.4.0 完成渲染

---

## 🔧 修复

- **GalleryScreen PhotoViewer 底部栏新增「分析构图」按钮**，调用 PostCaptureAnalyzer 实时分析
- **build.gradle.kts versionName 已更新为 `0.3.0`**
- 无 P0/P1 级 Bug 修复（v0.2.0 核心修复已在上一版本完成）

---

## 🏗️ 技术架构变更

### 新增文件
| 文件 | 路径 | 说明 |
|------|------|------|
| `PostCaptureAnalyzer.kt` | `engine/` | 拍后构图分析引擎（80 行） |
| `SceneTemplate.kt` | `engine/` | 场景模板系统（120 行） |
| `HistogramView.kt` | `ui/components/` | 实时直方图 Composable（60 行） |
| `OnboardingScreen.kt` | `ui/` | 首次使用引导页（150 行） |
| `PoseLibrary.kt` | `engine/` | 人像姿势库（90 行） |
| `templates.json` | `res/raw/` | 场景模板数据（2.4 KB） |

### 修改文件
| 文件 | 改动 |
|------|------|
| `CameraScreen.kt` | 接入 HistogramView + SceneTemplateRecommendationBar |
| `CameraCompositionPipeline.kt` | 新增 `currentTemplate` StateFlow 场景匹配 |
| `GalleryScreen.kt` | 接入 PostCaptureAnalyzer + BottomSheet 展示 |
| `CLAUDE.md` | 版本更新至 v0.3.0，状态同步 |

### 数据流
```
CameraX → FrameAnalyzer → CameraCompositionPipeline → Engine → UI
                                     ↓
                           SceneTemplateRepository
                               (templates.json)
                                    
拍后链路:
GalleryScreen → PostCaptureAnalyzer → PhotoCompositionEngine → BottomSheet UI
```

---

## 🧪 测试结果

**单元测试**: 17 项全部通过 ✅（相比 v0.2.0 新增 3 项）
```
tests="17" skipped="0" failures="0" errors="0"
```

测试覆盖：
- 引擎注册/去重/冲突抑制
- 评估（三分法、居中、对称、黄金比例、引导线、填满画面、留白等）
- 建议生成（主体位置、亮度、水平线）
- 总体评分计算

**构建验证**: `./gradlew assembleDebug` ✅  
**构建验证**: `./gradlew testDebugUnitTest` ✅

---

## 📋 已知问题

| # | 问题 | 优先级 | 计划 |
|---|------|--------|------|
| 1 | 水平仪音频反馈未实现 | P0 | v0.4.0 |
| 2 | 黄金比例螺旋线 Canvas 渲染未上线 | P0 | v0.4.0 |
| 3 | Lottie 引导动画未集成（OnboardingScreen 已创建） | P1 | v0.4.0 |
| 4 | 姿势库未接入 UI（PoseLibrary 已创建） | P1 | v0.4.0 |
| 5 | 拍前参考图叠加（AR 半透明构图模板） | P2 | v0.5.0 |
| 6 | 真实设备 YUV→RGB 直连效果未经充分验证 | — | 持续 |
| 7 | 场景模板推荐尚未支撑姿势覆盖层 | P1 | v0.4.0 |

---

## 📈 竞品对标进展

| 对标功能 | CapCut | KeSong | Framewise v0.3.0 | 状态 |
|---------|--------|--------|-----------------|------|
| 场景模板推荐 | ❓ | ✅ | ✅ SceneTemplateRecommendationBar | 🟢 追平 |
| 拍后 AI 分析 | ✅ | ✅ | ✅ PostCaptureAnalyzer | 🟢 追平 |
| 实时直方图 | ✅ | ✅ | ✅ HistogramView | 🟢 追平 |
| 场景匹配滤镜 | ✅ | ✅ | ✅ suggestFilter (引擎) | 🟡 未自动应用 |
| 黄金比例螺旋 | ✅ | ❓ | ⚠️ 引擎规则就绪, UI 待上线 | 🟡 进行中 |
| 水平仪音频 | ✅ | ✅ | ❌ 未实现 | 🔴 差距 |

---

> **发布建议**: 功能齐全后可打 tag `v0.3.0`（当前 `versionName` 已同步）
>
> ```bash
> git tag v0.3.0
> git push origin v0.3.0
> gh release create v0.3.0 --title "v0.3.0 — 场景模板 + 拍后分析 + 实时直方图" \
>   --notes-file docs/RELEASE_NOTES.md
> ```
