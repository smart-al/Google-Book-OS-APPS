# 💻 Google Book OS Applications Suite

A curated suite of native and web applications built and optimized specifically for **Google Book OS** (16:10 large-screen laptops, desktop windows, and convertible tablets).

---

## 📱 Applications in this Repository

### 1. 💧 [LiquidGlass3D (`LiquidGlass3D/`)](LiquidGlass3D/README.md)
**2D-to-3D Spatial Live Wallpaper & Liquid Glass Widget Engine**
- **Android 17 (API 37)** Kotlin + Jetpack Compose Material 3 live wallpaper powered by **Android AGSL (`RuntimeShader`)**.
- **Tri-Model On-Device AI Pipeline (LiteRT)**:
  - *Depth Anything V2 Small* for monocular depth estimation and 3D ray-displacement (`depth - 0.35`).
  - *MediaPipe Selfie Multiclass* for portrait semantic segmentation (`hair`, `face`, `body`, `clothes`).
  - *U²-Net (FP16)* for general salient-object segmentation (pets, vehicles, nature, products).
- **Interactive Liquid Glass Clock**:
  - Movable anywhere across the desktop screen via direct **mouse dragging** or precision sliders.
  - **Adaptive Image Color Blending**: Frosted glass panel dynamically absorbs and reflects ambient colors from the underlying photo.
  - **Depth Layer Occlusion**: Toggle the clock between sitting **Behind Subject** (3D depth-occluded) or **In Front of Subject** (floating glass).
  - **36 Color Themes**: 8 solid glass tones + 28 multi-stop liquid gradients (*Cyberpunk, Solar Flare, Aurora, Prism Spectrum, Bioluminescent Abyss, etc.*).
- **16:10 Standard Aspect Ratio**: Optimized for Google Book laptop displays with dual-pane layout, mouse hover parallax, and hardware gyroscope tilt.

---

### 2. ⚡ [Google Book Purge `Lite` (`android/` & `web/`)](README.md)
**One-Click Cache Storage Cleaner & Memory Optimizer**
- **`web/`**: Zero-bloat Python 3 Web Application (`http://127.0.0.1:8765`) with 260° Neon RAM Arc Gauge, 4-Segment Storage Status, 8 One-Click purge engines, and large/duplicate media detector.
- **`android/`**: Native Android app (`com.droid.cachecleaner`, `versionName = "2.2.0"`) with MVVM, Robolectric/Compose tests (`82.88%` coverage, Audit Score `93.1/100`), and Gemini Smart Guard rules.

---

## 📄 License
Licensed under the Apache License, Version 2.0. See [LICENSE](LiquidGlass3D/LICENSE) for details.
