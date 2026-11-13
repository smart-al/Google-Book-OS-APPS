# Proguard rules for LiquidGlass3D
-keep class org.tensorflow.lite.** { *; }
-keep class com.google.ai.edge.litert.** { *; }
-dontwarn org.tensorflow.lite.**

# Preserve AGSL runtime shader and renderer classes
-keep class com.droid.liquidglass3d.** { *; }

# Compose rules
-keep class androidx.compose.** { *; }
