# Framewise 优化计划 — 参考 awesome-android-ui

## Agent 分工

| # | Agent | 优势 | 任务 |
|---|-------|------|------|
| 0 | claude | 架构推理、复杂问题诊断 | P0: FrameAnalyzer YUV→Bitmap 修复 (最难的) |
| 1 | codex | 代码生成、Compose UI | UI动画：评分仪表盘、快门动画、定时器 |
| 2 | agy | 图像处理、算法 | P2: 滤镜应用到保存的JPEG + P3: 定时器动画 |
| 3 | deepseek | 分析、测试、代码审查 | P1: 图库权限 + 引擎单元测试 |
| 4 | gemma4:12B | 小模型适合简单任务 | 设置界面优化 + 文档更新 |

## 参考：awesome-android-ui 优化灵感

### 1. 评分仪表盘 (ScoreBadge)
- **参考**: `ArcProgressStackView`, `CircleProgress`, `ColorArcProgressBar`
- **优化**: 把当前纯数字改成半弧形进度条，带 spring 弹性动画
- **文件**: `CameraScreen.kt` — `ScoreBadge` composable

### 2. 快门按钮 (Shutter)
- **参考**: `circular-progress-button`, `KTLoadingButton`, `FABProgressCircle`
- **优化**: 拍照时按钮变成进度环，成功时绿色脉冲动画，失败时红色抖动
- **文件**: `CameraScreen.kt` — shutter button section

### 3. 定时器倒计时 (Timer)
- **参考**: `CircleTimer`
- **优化**: 圆形倒计时进度环 + 取消按钮 + spring 数字动画
- **文件**: `CameraScreen.kt` — `countdownSeconds` 部分

### 4. 底部控制栏毛玻璃
- **参考**: `EtsyBlur`, `Android StackBlur`, `TransformationLayout`
- **优化**: 用 Compose `Blur` 实现 frosted glass 效果
- **文件**: `CameraScreen.kt` — bottom control bar

### 5. 图库缩放 (Gallery)
- **参考**: `TouchImageView`
- **优化**: Pinch-to-zoom + double-tap zoom on photo viewer
- **文件**: `GalleryScreen.kt` — `PhotoViewer` composable

### 6. 设置界面切换开关
- **参考**: `AwesomeSwitch`
- **优化**: 自定义 animated switch toggles with spring animation
- **文件**: `SettingsScreen.kt`

### 7. 颜色主题扩展
- **参考**: 多色主题支持
- **优化**: 除了深色模式外增加更多主题色方案
- **文件**: `ui/theme/Color.kt`

### 8. 过渡动画 (Navigation)
- **参考**: `transitions-everywhere`, `CircularReveal`
- **优化**: 页面切换动画，从快门展开到图库的 circular reveal

## 执行计划

### Phase 1: Bug 修复 (高优先级)
1. **P0**: FrameAnalyzer 真实 YUV→Bitmap 修复
   - 改用 ImageProxy.toBitmap() (CameraX 1.4+)
   - 修复 centreLuma4 硬编码 stride
   - 添加真机调试日志
2. **P1**: 图库权限 READ_MEDIA_IMAGES 验证
3. **P2**: 滤镜应用到保存的 JPEG

### Phase 2: UI 动画优化 (中优先级)
4. 评分仪表盘动画 (spring arc gauge)
5. 快门按钮动画 (progress ring)
6. 定时器圆形倒计时 + 取消按钮

### Phase 3: 用户体验增强 (低优先级)
7. 底部控制栏毛玻璃效果
8. 图库查看器缩放
9. 设置开关动画
10. 页面过渡动画
