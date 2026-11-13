# 💧 LiquidGlass3D

[![Android](https://img.shields.io/badge/Android-17%20(API%2037)-3DDC84?style=flat&logo=android&logoColor=white)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.3-7F52FF?style=flat&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?style=flat&logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![AGSL](https://img.shields.io/badge/Shader-AGSL%20RuntimeShader-FF4081?style=flat)](https://developer.android.com/develop/ui/views/graphics/agsl)
[![LiteRT](https://img.shields.io/badge/AI%20Engine-Google%20LiteRT-FFA000?style=flat&logo=tensorflow&logoColor=white)](https://ai.google.dev/edge/litert)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

**LiquidGlass3D** is an advanced on-device 2D-to-3D spatial wallpaper and desktop customization suite crafted specifically for **Google Book OS** (16:10 large-screen laptops, desktop windows, and convertible tablets).

Using on-device monocular depth estimation, multi-model AI subject segmentation, and real-time **Android AGSL (`RuntimeShader`)**, LiquidGlass3D converts any 2D photo into a living 3D parallax scene with an iOS-inspired frosted "Liquid Glass" clock widget that dynamically refracts and blends with the underlying image.

---

## ✨ Features

- **Any-Image 2D-to-3D Spatial Conversion**
  - Pick any photo from your gallery via Android Photo Picker (`PickVisualMedia`).
  - Automatically runs an on-device **Depth Anything V2 Small** model via LiteRT on background coroutines (`Dispatchers.Default`), producing a normalized depth map for real-time ray-parallax mapping.
- **Tri-Model AI Scene Router & Segmentation**
  - **Portrait (Person)**: Detects human subjects using MediaPipe Selfie Multiclass (`face`, `hair`, `body`, `clothes`) and blends with U²-Net for natural hair strands and sharp clothing edges.
  - **Object (Pets, Products, Vehicles)**: Employs U²-Net salient-object segmentation for crisp silhouettes.
  - **Nature / Landscape**: Smooth depth-foreground ramp derived from monocular depth estimation.
  - Instant mask switching via UI chip selector (`Auto (AI)`, `Person`, `Object`, `Nature`).
- **Interactive Liquid Glass AGSL Shader Engine**
  - **Depth UV Displacement**: `p = base + off * (depth - 0.35) * reach` lets foreground subjects pop forward while backgrounds recess into 3D space.
  - **Liquid Glass Refraction**: Dynamic lens distortion, frosted disc-kernel blur, and chromatic dispersion along subject edges.
  - **Adaptive Image Color Blending**: Frosted clock background directly samples ambient colors and lighting from the underlying photo, seamlessly blending into the image.
  - **Depth Layer Occlusion**: Toggle the clock between sitting **Behind the Subject** (sandwiched inside 3D depth space) or **In Front of Subject** (floating top layer).
- **Movable Clock Widget with Desktop Mouse Dragging**
  - Directly click and drag the clock anywhere across the screen using your mouse or touch.
  - Includes precision position sliders (X, Y) and a quick one-tap **Center** button.
- **36 Rich Color & Gradient Themes**
  - 8 Solid Glass Tones (*Pure White, Ice Cyan, Soft Gold, Rose Quartz, Mint Glass, Electric Violet, Neon Coral, Obsidian*).
  - 28 Multi-Stop Liquid Gradients (*Cyberpunk, Sunset Glow, Aurora, Solar Flare, Bioluminescent Abyss, Sakura Blossom, Retro Arcade, Polar Aurora, Cyber Matrix, Mariana Trench, Cotton Candy, Prism Spectrum*, and more).
- **Google Book OS 16:10 Laptop Display Optimization**
  - Live preview locked to standard **16:10 aspect ratio** (e.g. 2560×1600, 1920×1200).
  - Adaptive dual-pane desktop layout (`>= 840.dp`) with responsive Material 3 controls.
- **Dual-Input Parallax Engine**
  - Hardware gyroscope & lid tilt via `Sensor.TYPE_ROTATION_VECTOR` when tilting or moving the screen.
  - Smooth desktop trackpad / mouse pointer hover blending (`onTouchEvent` & `ACTION_HOVER_MOVE`).
  - Wallpaper page scroll integration (`onOffsetsChanged`).
- **Built-in Sample 3D Scene**
  - One-tap **"Try Sample Scene"** button allows testing the 3D depth parallax, moving clock, and shader refraction instantly without needing to pick a file first.

---

## 🏗️ Architecture

```
                       ┌─────────────────────────┐
                       │   Android Photo Picker  │
                       └────────────┬────────────┘
                                    │ Uri
                                    ▼
                       ┌─────────────────────────┐
                       │   DepthEstimator.kt     │
                       │   (LiteRT Background)   │
                       └──────┬───────────┬──────┘
                              │           │
           ┌──────────────────┴─────┐   ┌─┴────────────────────────┐
           ▼                         ▼   ▼                          ▼
┌───────────────────────┐ ┌────────────────────┐ ┌───────────────────┐
│ Depth Anything V2     │ │ MediaPipe Selfie   │ │ U²-Net (FP16)     │
│ Small INT8 (.tflite)  │ │ Multiclass (.tflite│ │ Salient Object    │
└──────────┬────────────┘ └─────────┬──────────┘ └─────────┬─────────┘
           │ Depth Map              │                      │
           │                        └───────────┬──────────┘
           │                                    │ AI Mask Router
           ▼                                    ▼
     [ depth.png ]                        [ mask.png ]
           │                                    │
           └──────────────────┬─────────────────┘
                              ▼
           ┌────────────────────────────────────┐
           │      LiquidGlassRenderer.kt        │
           │      LiquidGlassShader.agsl        │
           ├────────────────────────────────────┤
           │  • Iterative Depth UV Displace     │
           │  • Frosted Disc-Kernel Blur        │
           │  • Chromatic Edge Dispersion       │
           │  • Ambient Image Color Blending    │
           │  • Dual-Layer Depth Occlusion      │
           └──────────────────┬─────────────────┘
                              │
               ┌──────────────┴──────────────┐
               ▼                             ▼
   ┌───────────────────────┐     ┌───────────────────────┐
   │     MainActivity      │     │ LiquidGlassWallpaper  │
   │  Jetpack Compose UI   │     │    WallpaperService   │
   │  (16:10 Live Preview) │     │   (Native Surface)    │
   └───────────────────────┘     └───────────────────────┘
```

---

## 🛠️ Tech Stack & Requirements

- **Target SDK**: Android 17 (API 37)
- **Minimum SDK**: Android 13 (API 33 - required for AGSL `RuntimeShader`)
- **Language**: Kotlin 2.3
- **UI Framework**: Jetpack Compose (Material 3, BOM `2024.10.01`)
- **Graphics**: AGSL (`android.graphics.RuntimeShader`, Hardware-accelerated `Canvas`)
- **AI Runtime**: Google LiteRT 1.4.0 (`org.tensorflow.lite:tensorflow-lite`)
- **Sensors**: `Sensor.TYPE_ROTATION_VECTOR`, `SensorManager.remapCoordinateSystem`

---

## 🚀 Building & Running

### 1. Prerequisites
- Android SDK with platform `android-37.0` and build-tools `37.0.0` or `36.1.0`.
- Java 21 JDK.

### 2. Build the Debug APK
```bash
./gradlew assembleDebug --project-cache-dir="$HOME/.cache/projects/LiquidGlass3D/gradle"
```

The output APK will be generated at:
```
app/build/outputs/apk/debug/LiquidGlass3D-debug.apk
```

### 3. Setting Up Live Wallpaper
1. Install and launch **LiquidGlass3D**.
2. Tap **"Try Sample Scene"** (or choose any image from your gallery).
3. Drag the clock widget to your preferred position on the screen.
4. Customize the clock font, color theme, and depth layer (Behind vs. In Front).
5. Tap **"Set Wallpaper"** to apply to your Google Book OS desktop and lock screen.

---

## 📄 License

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE) for details.
