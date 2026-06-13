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
| **P0** | Composition suggestions not showing | FrameAnalyzer yuvToBitmap likely fails → emptyAnalysis → no suggestions | Add device-specific YUV→Bitmap fix or use CameraX ImageProxy directly. On some devices YUV_420_888 format differs. |
| **P0** | Settings not persisting | rememberSaveable is per-Screen, lost on navigation | Replace SettingsState singleton with DataStore/SharedPreferences |
| **P1** | No photo capture feedback | takePhoto() callback is empty | Add Toast + shutter animation + thumbnail preview |
| **P2** | Canvas overlay may not be visible on all devices | Z-ordering of AndroidView + Canvas in Box layout | Verify overlay layer renders above PreviewView |
| **P2** | Gallery uses Coil but no permissions request | READ_MEDIA_IMAGES not requested | Add runtime permission for Android 13+ |

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
