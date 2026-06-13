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
adb logcat -s Framewise CompPipeline CameraX CameraController
```

### 3. Current Pain Points (2026-06-13)

| Priority | Issue | Root Cause | Suggested Fix |
|----------|-------|------------|---------------|
| **P0** | FrameAnalyzer real analysis still not working | Real analyzer path still falls back to demo mode; on-device YUV/image analysis remains unreliable | Fix device-specific `ImageProxy` conversion and verify real `PhotoAnalysis` output before disabling demo fallback |
| **P1** | Gallery permissions need verification | Gallery requests media permission in code, but Android version/device behavior should be tested | Confirm `READ_MEDIA_IMAGES` is requested on Android 13+ and legacy read permission works below Android 13 |
| **P2** | Color filter only applies to preview overlay, not saved JPEG | Current preview uses a Compose overlay rather than a true CameraX/GL preview filter | Apply the same `ColorMatrix` to the saved JPEG or move filtering into a GL/CameraX pipeline |
| **P3** | Timer countdown UI could be smoother | Countdown is functional but uses a static number overlay | Add easing/scale animation and cancel affordance during countdown |

### 4. Key Architecture Notes
- **Engine is pure Kotlin, no Android deps**: `engine/` can be unit-tested on JVM
- **FrameAnalyzer is the weakest link**: real device YUV→Bitmap conversion is fragile. Consider replacing with CameraX `imageAnalysis.setAnalyzer` using `ImageProxy.toBitmap()` if available
- **CameraCompositionPipeline** is the central data flow: FrameAnalyzer → Pipeline → Engine → UI

### 5. Quick Testing on Emulator
The camera doesn't work well on Android emulator. For UI testing:
- Create a `DemoPipeline` that produces mock PhotoAnalysis at 1fps with fake subjects/horizon
- Replace `CameraCompositionPipeline` with `DemoPipeline` via a flag

### 6. Rule Engine Testing
The TypeScript version at `tools/photo-guide-core/` has mock analysis data. The Kotlin engine's logic should match. Test by:
```kotlin
val engine = PhotoCompositionEngine()
engine.registerRules(ALL_RULES)
val analysis = generateMockAnalysis("portrait")
val results = engine.evaluate(analysis)
```
