# Framewise ProGuard/R8 rules.

# Keep ML Kit model classes (loaded reflectively).
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# CameraX
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**

# Kotlin metadata / coroutines (usually handled by AGP defaults; kept for safety).
-keepclassmembers class kotlin.Metadata { *; }
