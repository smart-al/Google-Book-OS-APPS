package com.droid.cachecleaner.model

enum class CacheCategoryId(
  val title: String,
  val subtitle: String
) {
  GOOGLE_PLAY_SERVICES(
    title = "Google Play Services & Store",
    subtitle = "com.google.android.gms, Play Store (com.android.vending) & GSF caches"
  ),
  BROWSER_CACHE(
    title = "Browser & WebView Cache",
    subtitle = "Chrome Browser, System WebView, HTTP disk cache & shader/tile caches"
  ),
  YOUTUBE_CACHE(
    title = "YouTube & Media Streaming Cache",
    subtitle = "YouTube (com.google.android.youtube) ExoPlayer video chunks & thumbnails"
  ),
  ALL_INSTALLED_APPS(
    title = "All Installed Google Book OS Apps",
    subtitle = "Shared & per-package caches across every installed application on this laptop"
  ),
  ORPHAN_AND_OBSOLETE_APKS(
    title = "Orphaned Files & Obsolete APK Scanner",
    subtitle = "Uninstalled app residuals (.obb/.bak/.crdownload) & obsolete .apk installers"
  ),
  BACKGROUND_PROCESS_TRIMMER(
    title = "Background Process & Service Trimmer",
    subtitle = "Trims idle background app processes & releases background service staging buffers"
  ),
  RAM_PURGE(
    title = "RAM Purge (macOS-style sudo purge)",
    subtitle = "Flushes reclaimable VM/heap caches, triggers GC/finalization & purges inactive RAM"
  ),
  TEMP_AND_LOGS(
    title = "System Temp, Staging & Logs",
    subtitle = "Temporary build staging files, crash traces, and residual diagnostic artifacts"
  )
}

data class AppCacheEntry(
  val packageName: String,
  val appName: String,
  val category: CacheCategoryId,
  val cacheBytes: Long,
  val fileCount: Int,
  val isSystemApp: Boolean
)

data class CacheCategoryItem(
  val id: CacheCategoryId,
  val bytes: Long,
  val fileCount: Int,
  val samplePaths: List<String>
)

data class RamStatus(
  val totalRamBytes: Long,
  val availableRamBytes: Long,
  val usedRamBytes: Long,
  val purgeableRamBytes: Long,
  val activeBackgroundProcesses: Int
) {
  val usedRamPercent: Int
    get() = if (totalRamBytes > 0L) {
      ((usedRamBytes.toDouble() / totalRamBytes.toDouble()) * 100.0).toInt().coerceIn(0, 100)
    } else {
      0
    }
}

enum class MediaCandidateKind(val label: String) {
  DUPLICATE_MEDIA("SHA-256 Exact Duplicate"),
  LARGE_MEDIA("Large Media File")
}

data class MediaCandidateItem(
  val id: String,
  val fileName: String,
  val filePath: String,
  val sizeBytes: Long,
  val sha256Hash: String,
  val kind: MediaCandidateKind,
  val duplicateOf: String? = null
)

/**
 * The Anti-Annoyance Strategy ("Smart Guard" Rules) powered by Ahri (Google Gemini 3.8 Flash):
 * 1. Cooldown Window: Silence notifications for 45–60 minutes after a purge/suggestion,
 *    unless RAM spikes into a critical danger threshold (>92%).
 * 2. Sustained Pressure Check: Don't trigger on a transient 2-second spike; trigger only if
 *    RAM stays >= 82% for 3 consecutive polling checks (~15–20 seconds).
 * 3. Smart Notification Action: Instead of a blocking modal, send a quiet heads-up notification
 *    with a 1-tap "Purge Now" action button.
 */
data class SmartGuardState(
  val assistantName: String = "Ahri (Gemini 3.8 Flash)",
  val modelId: String = "gemini-3.8-flash",
  val cooldownWindowMinutes: Int = 50,
  val lastNotificationOrPurgeEpochMs: Long? = null,
  val cooldownRemainingMinutes: Int = 0,
  val isCooldownActive: Boolean = false,
  val sustainedThresholdPercent: Int = 82,
  val criticalDangerThresholdPercent: Int = 92,
  val requiredConsecutiveChecks: Int = 3,
  val consecutiveHighRamChecks: Int = 0,
  val lastPolledRamPercent: Int = 42,
  val shouldShowQuietHeadsUp: Boolean = false,
  val triggerReason: String? = null,
  val geminiInsightText: String = "Ahri (Gemini 3.8 Flash): Smart Guard active. Monitoring RAM pressure with 3-check sustained filter (≥82%) and 50-min anti-annoyance cooldown."
)

data class StorageSnapshot(
  val totalBytes: Long,
  val usedBytes: Long,
  val freeBytes: Long,
  val reclaimableBytes: Long,
  val installedPackagesCount: Int,
  val categories: List<CacheCategoryItem>,
  val installedApps: List<AppCacheEntry>,
  val ramStatus: RamStatus,
  val mediaItems: List<MediaCandidateItem>,
  val scannedAtEpochMs: Long
) {
  val usedRatio: Float
    get() = if (totalBytes > 0L) (usedBytes.toDouble() / totalBytes.toDouble()).toFloat().coerceIn(0f, 1f) else 0f

  val usedPercent: Int
    get() = (usedRatio * 100f).toInt().coerceIn(0, 100)
}

data class CleanResult(
  val bytesFreed: Long,
  val filesRemoved: Int,
  val appsCleanedCount: Int,
  val ramBytesFreed: Long,
  val processesTrimmed: Int,
  val orphansAndApksRemoved: Int,
  val durationMs: Long,
  val cleanedCategories: Set<CacheCategoryId>,
  val updatedSnapshot: StorageSnapshot
)

data class MediaCleanResult(
  val bytesFreed: Long,
  val filesRemoved: Int,
  val cleanedIds: Set<String>,
  val updatedSnapshot: StorageSnapshot
)

data class CacheCleanerUiState(
  val isLoading: Boolean = false,
  val isCleaning: Boolean = false,
  val snapshot: StorageSnapshot? = null,
  val selectedCategories: Set<CacheCategoryId> = CacheCategoryId.entries.toSet(),
  val selectedMediaIds: Set<String> = emptySet(),
  val smartGuard: SmartGuardState = SmartGuardState(),
  val lastCleanResult: CleanResult? = null,
  val lastMediaCleanResult: MediaCleanResult? = null,
  val statusMessage: String = "Ready to clean caches, orphans, trim services & sudo purge RAM",
  val errorMessage: String? = null
) {
  val selectedReclaimableBytes: Long
    get() = snapshot?.categories
      ?.filter { it.id in selectedCategories }
      ?.sumOf { it.bytes }
      ?: 0L

  val selectedFileCount: Int
    get() = snapshot?.categories
      ?.filter { it.id in selectedCategories }
      ?.sumOf { it.fileCount }
      ?: 0

  val selectedAppsCount: Int
    get() = snapshot?.installedApps
      ?.count { it.category in selectedCategories && it.cacheBytes > 0L }
      ?: 0

  val selectedMediaBytes: Long
    get() = snapshot?.mediaItems
      ?.filter { it.id in selectedMediaIds }
      ?.sumOf { it.sizeBytes }
      ?: 0L
}

object ByteFormatter {
  fun formatBytes(bytes: Long): String {
    val safe = bytes.coerceAtLeast(0L)
    return when {
      safe >= 1_073_741_824L -> String.format(java.util.Locale.US, "%.2f GB", safe / 1_073_741_824.0)
      safe >= 1_048_576L -> String.format(java.util.Locale.US, "%.2f MB", safe / 1_048_576.0)
      safe >= 1_024L -> String.format(java.util.Locale.US, "%.1f KB", safe / 1_024.0)
      else -> "$safe B"
    }
  }
}
