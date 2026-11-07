# Google Book Purge `Lite` (`v2.2.0`)

Lightweight One-Click Cache Storage Cleaner, Background Service Trimmer, `sudo purge` RAM Releaser, and **Google Gemini (`gemini-3.8-flash` / "Ahri" Smart Guard)** Optimizer for Google Book OS laptops.

## Included Components
- **`web/`** — Zero-bloat Python 3 Web Application (`http://127.0.0.1:8765`) with 260° Neon RAM Arc Gauge, 4-Segment Storage Status card, 8 combined One-Click engines, separate SHA-256 Large & Duplicate Media Detector, and Ahri (`gemini-3.8-flash`) Smart Guard rules.
- **`android/`** — Native Android Application (`com.droid.cachecleaner`, `versionCode = 6`, `versionName = "2.2.0"`) built with Kotlin & Jetpack Compose Material 3, MVVM + Constructor DI + `StoragePlatformBridge` Factory pattern, and 14/14 automated unit, Robolectric, and Compose UI tests (`82.88%` line coverage, Audit Score `93.1/100`).

## Smart Guard ("Anti-Annoyance Strategy") Rules
1. **Cooldown Window (45–60 min)**: Silences notifications for 50 minutes after a purge or suggestion unless RAM spikes into critical danger (`>92%`).
2. **Sustained Pressure Check (≥82% for 3 consecutive checks, ~15–20s)**: Ignores transient 2-second spikes and only triggers on sustained RAM pressure.
3. **Smart Notification Action**: Dispatches a quiet heads-up notification with a 1-tap **"Purge Now"** action button instead of a blocking modal.
