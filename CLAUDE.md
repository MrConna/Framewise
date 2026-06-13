# Framewise — Agent 开发指引

## 项目概览
实时拍照构图指导 Android App。CameraX + ML Kit + Jetpack Compose。

## 开发守则
1. **每次发布前先读 docs/HANDOFF.md** — 了解当前痛点和优先级
2. **版本号必须一致** — app/build.gradle.kts 的 versionName 必须等于 git tag（当前 v0.1.6）
3. **每次修改后更新 docs/CHANGELOG.md** — 记录变更、修复的 bug、新增功能
4. **功能完整后再打 tag** — 先 assembleDebug 确保构建通过，再 git tag + gh release

## 快速上手
```bash
export JAVA_HOME=/usr/local/opt/openjdk@17
export ANDROID_HOME=$HOME/Library/Android/sdk
cd Framewise && ./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## 架构
CameraX → FrameAnalyzer → CameraCompositionPipeline → PhotoCompositionEngine → UI

## 当前状态（2026-06-13）

### 已实现功能
- CameraX 实时预览 + 拍照 + 保存到 MediaStore
- 13条构图规则引擎（三分法/引导线/对称/对角线/黄金比例等）
- 变焦滑块（CameraX CameraControl.setZoomRatio）
- 闪光灯切换（CameraControl.enableTorch）
- 彩色滤镜（暖色/冷色/复古/黑白 via ColorMatrix）
- 定时拍摄（3s/10s倒计时）
- 网格辅助线开关
- Demo Mode（3秒无真实帧自动降级，每2秒变化模拟数据）
- 设置持久化（SharedPreferences）
- 全中文界面
- 图库（MediaStore 查询）

### 待修复问题
| 优先级 | 问题 | 说明 |
|--------|------|------|
| P0 | 实时分析未工作 | FrameAnalyzer.yuvToBitmap 在真机可能失败，当前为 demo mode |
| P1 | 图库权限 | Android 13+ 需要请求 READ_MEDIA_IMAGES |
| P2 | 滤镜只覆盖预览 | 拍照保存的 JPEG 未应用滤镜效果 |
| P3 | Demo mode 无真实反馈 | 用户移动摄像头评分不真实变化 |

### 关键文件
- `CameraController.kt` — CameraX 生命周期 + 预览 + 拍照 + 变焦 + 闪光灯
- `FrameAnalyzer.kt` — YUV→Bitmap + 主体/地平线/亮度检测（当前可能失效）
- `CameraCompositionPipeline.kt` — 管线连接 + Demo mode 降级
- `PhotoCompositionEngine.kt` — 规则引擎核心
- `CameraScreen.kt` — Compose UI + 叠加层 + 控制栏
- `SettingsState.kt` — SharedPreferences 持久化

### 版本历史
最新 tag: v0.1.6（versionName 必须同步）
详见 docs/CHANGELOG.md

### 必读文档
- docs/CHANGELOG.md — 版本变更记录
- docs/HANDOFF.md — 待修问题 + 接手指引
- docs/ARCHITECTURE.md — 项目架构 + 文件树
