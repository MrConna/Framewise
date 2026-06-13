# Framewise × 剪映(CapCut) × 可颂(KeSong) — 对标分析 & 实现计划

## 一、竞品构图功能矩阵

| 功能 | 剪映 CapCut | 可颂 KeSong | Framewise (当前) | 差距 |
|------|------------|-------------|-----------------|------|
| 九宫格/三分法网格 | ✅ | ✅ | ✅ | — |
| 黄金比例螺旋 | ✅ | ❓ | ⚠️ 引擎规则已实现, UI 渲染待上线 | UI 链接待完成 |
| 水平仪 (AR level) | ✅ | ✅ | ✅ 引擎检测 + 界面指示 | 无音频反馈 |
| **场景智能识别** | ✅ | ✅ AI 扫描 | ✅ heuristic + 模板匹配 | 仍弱于 AI |
| **灵感跟拍 (模板推荐)** | ❓ | ✅ 核心功能 | ✅ v0.3.0 场景模板推荐条 | **追平** |
| **姿势推荐 (Pose Library)** | ❓ | ✅ AI 推荐 | ❌ 无 | **大差距** |
| AR 构图引导线 | ✅ | ✅ | ✅ 基础箭头 | 动画/配色弱 |
| AI 自动裁剪/构图建议 | ✅ | ✅ 辅助调整 | ✅ v0.3.0 PostCaptureAnalyzer | **追平** |
| 语音构图指导 | ❓ | ❓ | ❌ 无 | 中差距 |
| 场景匹配滤镜 | ✅ | ✅ | ✅ v0.3.0 PostCaptureAnalyzer suggestFilter | 引擎建议, 未自动应用 |
| 实时曝光/直方图 | ✅ | ✅ | ✅ v0.3.0 HistogramView | **追平** |
| 人像姿势覆盖 (半透明) | ❓ | ✅ 姿势库 | ❌ 无 | 大差距 |
| 拍照前后对比 | ✅ | ✅ | ❌ 无 | 中差距 |
| 多人合影构图 | ❓ | ❓ | ❌ 无 | 中差距 |
| 照片评分/分享 | ❓ | ✅ 社交 | ✅ 本地评分 | 弱 |
| AR 取景框实景叠加 | ❓ | ✅ | ❌ 无 | 中差距 |

## 二、可颂核心功能拆解（最值得对标）

### 2.1 灵感跟拍 (Inspiration Follow-Shot)
**流程**: 打开相机 → AI 扫描场景 → 推荐构图模板 + 姿势 → AR 叠加引导 → 跟着拍
**Framewise 差距**: ✅ **v0.3.0 已实现** SceneTemplate 系统 + 推荐条 UI，姿势推荐未实现

### 2.2 AI 辅助调整构图
**实现**: 拍完后 AI 分析并提出裁剪/旋转建议，用户一键应用
**Framewise 差距**: ✅ **v0.3.0 已实现** PostCaptureAnalyzer + GalleryScreen 展示，照片内一键应用未实现

### 2.3 AR 构图引导
**实现**: 半透明参考图 + 姿势轮廓叠加在取景器上
**Framewise 差距**: 只有文字箭头，没有半透明姿势参考（未变化）

## 三、实现优先级

### P0 — 已完成的优先项

#### ✅ v0.3.0 已完成
| # | 改进 | 状态 |
|---|------|------|
| 3 | **拍后构图分析** | ✅ PostCaptureAnalyzer.kt + GalleryScreen BottomSheet |
| 2 | **场景匹配滤镜建议** | ✅ SceneTemplate.suggestedFilter + 推荐条 UI |

#### 🔲 仍待完成
| # | 改进 | 文件 | 预期效果 |
|---|------|------|---------|
| 1 | **水平仪音频反馈** | CameraController.kt + CameraScreen.kt | 水平线接近水平时发出 beep |
| 4 | **黄金比例螺旋线 UI 展示** | CameraScreen.kt Canvas | 渲染黄金螺旋/三分线叠加层 |

### P1 — 体验突破（3-5天）
| # | 改进 | 文件 | 预期效果 |
|---|------|------|---------|
| 5 | **拍前参考图叠加** | CameraScreen.kt + 素材 | AR 半透明参考姿势/构图模板 |
| 6 | **场景模板系统** | 新建 SceneTemplate.kt | 咖啡店/古建筑/海边 各配构图模板 |
| 7 | **实时直方图** | CameraScreen.kt Canvas 绘制 | 曝光分布直方图小窗 |
| 8 | **引导线动画升级** | CameraScreen.kt | 可颂/剪映级流畅 AR 动画 |

### P2 — 长期能力（1-2周）
| # | 改进 | 文件 | 预期效果 |
|---|------|------|---------|
| 9 | **姿势库 (Pose Library)** | 新建姿势资源 + UI | 30+ 人像姿势参考图 |
| 10 | **多人构图检测** | FrameAnalyzer 扩展 | 检测多人合影推荐对称构图 |
| 11 | **拍后 AI 裁剪建议** | 使用 ML Kit 自拍分割 | 自动推荐最佳裁剪区域 |
| 12 | **社交分享** | 整合分享到可颂/抖音 | 一键分享构图评分 |

## 四、预期修改的文件

### 新增文件
| 文件 | 说明 | 预估行数 |
|------|------|---------|
| `engine/PostCaptureAnalyzer.kt` | 拍后构图分析引擎 | 80 |
| `engine/SceneTemplate.kt` | 场景模板系统（场景→构图建议映射） | 120 |
| `ui/templates/PoseOverlay.kt` | 姿势 / AR 参考叠加层 | 100 |
| `ui/components/HistogramView.kt` | 实时直方图组件 | 60 |
| `res/raw/templates.json` | 场景模板数据 | 200 |
| `engine/AudioFeedback.kt` | 音频反馈（水平仪 beep 等） | 40 |

### 修改文件
| 文件 | 改动 | 预估行数 |
|------|------|---------|
| `camera/CameraController.kt` | 水平仪音频 + 场景->滤镜映射 | +30 |
| `camera/CameraCompositionPipeline.kt` | 集成场景模板推荐 | +20 |
| `engine/PhotoCompositionEngine.kt` | 拍后分析入口 | +30 |
| `ui/CameraScreen.kt` | 直方图 + 参考图叠加 + 水平仪音效 | +80 |
| `ui/GalleryScreen.kt` | 拍后分析展示 | +40 |

## 五、验证命令

```bash
# 1. 构建验证
cd Framewise && ./gradlew assembleDebug

# 2. 运行单元测试
cd Framewise && ./gradlew testDebugUnitTest

# 3. 真机安装
adb install -r app/build/outputs/apk/debug/app-debug.apk

# 4. 日志验证
adb logcat -s Framewise Framewise/FrameAnalyzer Framewise/CompPipeline
```

## 六、提交信息模板

```
feat: 对标剪映/可颂 — 场景模板 + 拍后分析 + 水平仪音频

- feat(camera): 水平仪接近水平时触发音频反馈
- feat(engine): PostCaptureAnalyzer 拍后构图分析 + 改进建议
- feat(engine): SceneTemplate 场景模板系统（咖啡店/古建筑/海边/人像）
- feat(ui): 场景匹配滤镜自动推荐
- feat(ui): 黄金比例/三分线渲染到 Canvas 叠加层
- feat(ui): 实时直方图小窗
- fix(ui): 评分仪表盘颜色根据主题自适应

Closes #对标可颂-灵感跟拍
Closes #对标剪映-AR构图引导
```
