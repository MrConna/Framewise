# 📸 Framewise — Architecture & Development Guide

## Project Overview
Real-time photo composition guide Android app. On-device ML + rule engine provides live composition suggestions through the camera viewfinder.

## Features
- Real-time composition advice (13 rules)
- CameraX preview + capture
- Zoom slider using `CameraControl.setZoomRatio`
- Torch/flash toggle
- Color filters with `ColorMatrix`: warm, cool, vintage, black & white
- Capture timer with 3s / 10s countdown
- Grid overlay toggle for rule-of-thirds lines
- Settings persistence with `SharedPreferences`
- Gallery backed by `MediaStore`
- Demo mode fallback after 3s when real analysis fails

## Tech Stack
- **Language**: Kotlin
- **UI**: Jetpack Compose + Material3
- **Camera**: CameraX (camera-camera2, camera-lifecycle, camera-view 1.4+)
- **ML**: ML Kit (segmentation-selfie, pose-detection)
- **Build**: Gradle 8.10.2, AGP 8.2+
- **Min SDK**: 26 | **Target SDK**: 35

## Project Structure
```
Framewise/
├── build.gradle.kts              # Project-level build
├── settings.gradle.kts           # Module includes
├── gradle.properties             # Gradle config
├── gradle/wrapper/               # Gradle wrapper (v8.10.2)
├── gradlew                       # Build script
└── app/
    ├── build.gradle.kts          # App module (deps, versions)
    ├── proguard-rules.pro
    └── src/main/
        ├── AndroidManifest.xml   # Permissions, activity
        ├── res/                  # Resources (strings, themes, icons)
        └── java/com/framewise/
            ├── MainActivity.kt           # Entry point, permission gate
            ├── SettingsState.kt          # Global settings singleton
            ├── camera/                   # Camera + analysis pipeline
            │   ├── CameraController.kt       # CameraX lifecycle, capture, zoom, torch, filters, timer
            │   ├── FrameAnalyzer.kt          # YUV→Bitmap, subject/horizon/light detection
            │   ├── CameraCompositionPipeline.kt  # Connects analyzer → engine
            │   ├── ImageProcessor.kt            # Image processing helpers
            │   └── PhotoCapture.kt           # MediaStore save logic
            ├── engine/                   # Composition rule engine
            │   ├── types/Types.kt            # All data types
            │   ├── PhotoCompositionEngine.kt  # Engine: register, evaluate, score
            │   └── rules/                    # 13 composition rules
            │       ├── AllRules.kt             # Exports ALL_RULES list
            │       ├── RuleOfThirds.kt
            │       ├── LeadingLines.kt
            │       ├── SymmetryRule.kt
            │       ├── Framing.kt
            │       ├── Diagonal.kt
            │       ├── CenterComposition.kt
            │       ├── NegativeSpace.kt
            │       ├── TriangleComposition.kt
            │       ├── FillTheFrame.kt
            │       ├── GoldenRatio.kt
            │       ├── HorizonLevel.kt
            │       ├── Headroom.kt
            │       └── Exposure.kt
            └── ui/                       # UI screens
                ├── CameraScreen.kt          # Main camera, overlays, filters, timer, capture controls
                ├── SettingsScreen.kt         # Rule toggles
                ├── GalleryScreen.kt          # Captured photos
                ├── navigation/NavGraph.kt    # NavHost (Camera ↔ Settings ↔ Gallery)
                └── theme/                    # Material3 theme
                    ├── Color.kt
                    └── Type.kt
```

## Architecture Flow
```
CameraX (PreviewView + ImageAnalysis)
  │
  ▼
FrameAnalyzer.analyzeFrame()     ← YUV → Bitmap → OpenCV/ML analysis
  │
  ▼
PhotoAnalysis (subjects, horizon, lines, brightness, scene...)
  │
  ▼
CameraCompositionPipeline.processFrame()
  │
  ├──► PhotoCompositionEngine.evaluate(analysis) → RuleResult[]
  │       │   (runs all 13 rules, each scores 0..1)
  │       └── getOverallScore() → 0..100
  │       └── getTopSuggestions(count) → Suggestion[]
  │
  ▼
CompositionResult (score + suggestions + activeRules)
  │
  ▼
CameraScreen.kt observes via StateFlow → renders:
  ├── Canvas overlay (toggleable rule of thirds grid, horizon line, subject boxes)
  ├── Score gauge (top-right circular indicator)
  ├── Camera controls (zoom, torch, timer, filters)
  └── Suggestion chips (bottom, top 3 actionable tips)
```

## Build & Run
```bash
# Prerequisites: JDK 17+, Android SDK 35
export JAVA_HOME=/usr/local/opt/openjdk@17
export ANDROID_HOME=$HOME/Library/Android/sdk

# Debug build
cd Framewise && ./gradlew assembleDebug

# Install to connected device
adb install -r app/build/outputs/apk/debug/app-debug.apk

# View logs (filter Framewise)
adb logcat -s Framewise:* CompPipeline:* CameraX:* CameraController:*
```

## Key Dependencies (app/build.gradle.kts)
| Dependency | Version | Purpose |
|---|---|---|
| CameraX (core/camera2/lifecycle/view) | 1.4.0 | Camera preview + capture |
| Jetpack Compose BOM | 2024.09.00 | UI framework |
| Navigation Compose | 2.8.0 | Screen navigation |
| ML Kit segmentation-selfie | 16.0.0-beta6 | Subject segmentation |
| ML Kit pose-detection | 18.0.0-beta5 | Pose detection |
| Coil Compose | 2.7.0 | Image loading in Gallery |
| Material Icons Extended | — | UI icons |
