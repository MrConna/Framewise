# 📋 Framewise — Changelog & Bug History

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
