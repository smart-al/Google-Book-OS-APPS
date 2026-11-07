package com.droid.cachecleaner.data

import android.content.Intent
import com.droid.cachecleaner.model.AppCacheEntry
import com.droid.cachecleaner.model.CacheCategoryId
import com.droid.cachecleaner.model.CacheCategoryItem
import com.droid.cachecleaner.model.CleanResult
import com.droid.cachecleaner.model.MediaCandidateItem
import com.droid.cachecleaner.model.MediaCandidateKind
import com.droid.cachecleaner.model.MediaCleanResult
import com.droid.cachecleaner.model.RamStatus
import com.droid.cachecleaner.model.SmartGuardState
import com.droid.cachecleaner.model.StorageSnapshot
import com.droid.cachecleaner.platform.StoragePlatformBridge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FakeStoragePlatformBridge : StoragePlatformBridge {
  var playServicesBytes: Long = 860_000L
  var browserBytes: Long = 1_140_000L
  var youtubeBytes: Long = 1_460_000L
  var installedAppsBytes: Long = 640_000L
  var orphanApksBytes: Long = 1_760_000L
  var bgServiceTrimmerBytes: Long = 1_000_000L
  var ramPurgeBytes: Long = 1_664_000L
  var tempLogsBytes: Long = 200_000L

  val mediaItemsList = mutableListOf(
    MediaCandidateItem(
      id = "/cache/media_scan/screen_recording_demo_copy(1).mp4",
      fileName = "screen_recording_demo_copy(1).mp4",
      filePath = "/cache/media_scan/screen_recording_demo_copy(1).mp4",
      sizeBytes = 1_240_000L,
      sha256Hash = "a1b2c3d4e5f60718",
      kind = MediaCandidateKind.DUPLICATE_MEDIA,
      duplicateOf = "screen_recording_demo.mp4"
    ),
    MediaCandidateItem(
      id = "/cache/media_scan/4k_studio_wallpaper_uncompressed.raw",
      fileName = "4k_studio_wallpaper_uncompressed.raw",
      filePath = "/cache/media_scan/4k_studio_wallpaper_uncompressed.raw",
      sizeBytes = 1_520_000L,
      sha256Hash = "9988776655443322",
      kind = MediaCandidateKind.LARGE_MEDIA,
      duplicateOf = null
    )
  )

  var cleanCalls: Int = 0
  var singleAppCleanCalls: Int = 0
  var mediaCleanCalls: Int = 0
  var seedCalls: Int = 0
  var geminiCalls: Int = 0
  var headsUpNotificationsPosted: Int = 0
  val systemIntent = Intent("android.os.storage.action.CLEAR_APP_CACHE")
  val appSettingsIntent = Intent("android.settings.APPLICATION_DETAILS_SETTINGS")

  override fun scanStorage(): StorageSnapshot {
    val categories = listOf(
      CacheCategoryItem(
        id = CacheCategoryId.GOOGLE_PLAY_SERVICES,
        bytes = playServicesBytes,
        fileCount = if (playServicesBytes > 0L) 5 else 0,
        samplePaths = listOf("/cache/com.google.android.gms/gms_chimera.bin")
      ),
      CacheCategoryItem(
        id = CacheCategoryId.BROWSER_CACHE,
        bytes = browserBytes,
        fileCount = if (browserBytes > 0L) 5 else 0,
        samplePaths = listOf("/cache/com.android.chrome/f_0001a4_http_chunk.bin")
      ),
      CacheCategoryItem(
        id = CacheCategoryId.YOUTUBE_CACHE,
        bytes = youtubeBytes,
        fileCount = if (youtubeBytes > 0L) 3 else 0,
        samplePaths = listOf("/cache/com.google.android.youtube/exo_player_media_chunk.exo")
      ),
      CacheCategoryItem(
        id = CacheCategoryId.ALL_INSTALLED_APPS,
        bytes = installedAppsBytes,
        fileCount = if (installedAppsBytes > 0L) 6 else 0,
        samplePaths = listOf("/cache/com.droid.cachecleaner/okhttp_response.tmp")
      ),
      CacheCategoryItem(
        id = CacheCategoryId.ORPHAN_AND_OBSOLETE_APKS,
        bytes = orphanApksBytes,
        fileCount = if (orphanApksBytes > 0L) 4 else 0,
        samplePaths = listOf("/cache/orphans_and_apks/old_chrome_v118_installer.apk")
      ),
      CacheCategoryItem(
        id = CacheCategoryId.BACKGROUND_PROCESS_TRIMMER,
        bytes = bgServiceTrimmerBytes,
        fileCount = if (bgServiceTrimmerBytes > 0L) 4 else 0,
        samplePaths = listOf("/cache/bg_service_buffers/gms_background_sync_wakelock_buffer.bin")
      ),
      CacheCategoryItem(
        id = CacheCategoryId.RAM_PURGE,
        bytes = ramPurgeBytes,
        fileCount = if (ramPurgeBytes > 0L) 3 else 0,
        samplePaths = listOf("/cache/ram_purge_buffers/ashmem_inactive_page_cache_slab_01.bin")
      ),
      CacheCategoryItem(
        id = CacheCategoryId.TEMP_AND_LOGS,
        bytes = tempLogsBytes,
        fileCount = if (tempLogsBytes > 0L) 2 else 0,
        samplePaths = listOf("/cache/temp_diagnostics/chromium_net_trace.log")
      )
    )
    val installedApps = listOf(
      AppCacheEntry(
        packageName = "com.google.android.youtube",
        appName = "YouTube",
        category = CacheCategoryId.YOUTUBE_CACHE,
        cacheBytes = youtubeBytes,
        fileCount = if (youtubeBytes > 0L) 3 else 0,
        isSystemApp = false
      ),
      AppCacheEntry(
        packageName = "com.android.chrome",
        appName = "Google Chrome Browser",
        category = CacheCategoryId.BROWSER_CACHE,
        cacheBytes = browserBytes,
        fileCount = if (browserBytes > 0L) 5 else 0,
        isSystemApp = true
      ),
      AppCacheEntry(
        packageName = "com.google.android.gms",
        appName = "Google Play Services",
        category = CacheCategoryId.GOOGLE_PLAY_SERVICES,
        cacheBytes = playServicesBytes,
        fileCount = if (playServicesBytes > 0L) 5 else 0,
        isSystemApp = true
      ),
      AppCacheEntry(
        packageName = "com.droid.cachecleaner",
        appName = "Google Book Purge",
        category = CacheCategoryId.ALL_INSTALLED_APPS,
        cacheBytes = installedAppsBytes,
        fileCount = if (installedAppsBytes > 0L) 6 else 0,
        isSystemApp = false
      )
    )
    val reclaimable = categories.sumOf { it.bytes }
    return StorageSnapshot(
      totalBytes = 64_000_000_000L,
      usedBytes = 24_000_000_000L + reclaimable,
      freeBytes = 40_000_000_000L - reclaimable,
      reclaimableBytes = reclaimable,
      installedPackagesCount = installedApps.size,
      categories = categories,
      installedApps = installedApps,
      ramStatus = RamStatus(
        totalRamBytes = 8_589_934_592L,
        availableRamBytes = 4_982_162_064L,
        usedRamBytes = 3_607_772_528L,
        purgeableRamBytes = bgServiceTrimmerBytes + ramPurgeBytes,
        activeBackgroundProcesses = if (bgServiceTrimmerBytes > 0L) 4 else 1
      ),
      mediaItems = mediaItemsList.toList(),
      scannedAtEpochMs = 1730000000000L
    )
  }

  override fun cleanCaches(selectedCategories: Set<CacheCategoryId>): CleanResult {
    cleanCalls += 1
    var freed = 0L
    var removedFiles = 0
    var appsCleaned = 0
    var orphansRemoved = 0
    var processesTrimmed = 0
    var ramFreed = 0L

    if (CacheCategoryId.GOOGLE_PLAY_SERVICES in selectedCategories && playServicesBytes > 0L) {
      freed += playServicesBytes
      removedFiles += 5
      appsCleaned += 1
      playServicesBytes = 0L
    }
    if (CacheCategoryId.BROWSER_CACHE in selectedCategories && browserBytes > 0L) {
      freed += browserBytes
      removedFiles += 5
      appsCleaned += 1
      browserBytes = 0L
    }
    if (CacheCategoryId.YOUTUBE_CACHE in selectedCategories && youtubeBytes > 0L) {
      freed += youtubeBytes
      removedFiles += 3
      appsCleaned += 1
      youtubeBytes = 0L
    }
    if (CacheCategoryId.ALL_INSTALLED_APPS in selectedCategories && installedAppsBytes > 0L) {
      freed += installedAppsBytes
      removedFiles += 6
      appsCleaned += 1
      installedAppsBytes = 0L
    }
    if (CacheCategoryId.ORPHAN_AND_OBSOLETE_APKS in selectedCategories && orphanApksBytes > 0L) {
      freed += orphanApksBytes
      removedFiles += 4
      orphansRemoved = 4
      orphanApksBytes = 0L
    }
    if (CacheCategoryId.BACKGROUND_PROCESS_TRIMMER in selectedCategories && bgServiceTrimmerBytes > 0L) {
      freed += bgServiceTrimmerBytes
      ramFreed += bgServiceTrimmerBytes
      removedFiles += 4
      processesTrimmed = 4
      bgServiceTrimmerBytes = 0L
    }
    if (CacheCategoryId.RAM_PURGE in selectedCategories && ramPurgeBytes > 0L) {
      freed += ramPurgeBytes
      ramFreed += ramPurgeBytes
      removedFiles += 3
      ramPurgeBytes = 0L
    }
    if (CacheCategoryId.TEMP_AND_LOGS in selectedCategories && tempLogsBytes > 0L) {
      freed += tempLogsBytes
      removedFiles += 2
      tempLogsBytes = 0L
    }

    return CleanResult(
      bytesFreed = freed,
      filesRemoved = removedFiles,
      appsCleanedCount = appsCleaned,
      ramBytesFreed = ramFreed,
      processesTrimmed = processesTrimmed,
      orphansAndApksRemoved = orphansRemoved,
      durationMs = 18L,
      cleanedCategories = selectedCategories,
      updatedSnapshot = scanStorage()
    )
  }

  override fun cleanSingleAppCache(packageName: String): CleanResult {
    singleAppCleanCalls += 1
    var freed = 0L
    var removed = 0
    when (packageName) {
      "com.google.android.gms" -> {
        freed = playServicesBytes
        removed = 5
        playServicesBytes = 0L
      }
      "com.android.chrome" -> {
        freed = browserBytes
        removed = 5
        browserBytes = 0L
      }
      "com.google.android.youtube" -> {
        freed = youtubeBytes
        removed = 3
        youtubeBytes = 0L
      }
      else -> {
        freed = installedAppsBytes
        removed = 6
        installedAppsBytes = 0L
      }
    }
    return CleanResult(
      bytesFreed = freed,
      filesRemoved = removed,
      appsCleanedCount = if (freed > 0L) 1 else 0,
      ramBytesFreed = 0L,
      processesTrimmed = 0,
      orphansAndApksRemoved = 0,
      durationMs = 5L,
      cleanedCategories = setOf(CacheCategoryId.GOOGLE_PLAY_SERVICES),
      updatedSnapshot = scanStorage()
    )
  }

  override fun cleanSelectedMedia(mediaIds: Set<String>): MediaCleanResult {
    mediaCleanCalls += 1
    val toRemove = mediaItemsList.filter { it.id in mediaIds }
    val freed = toRemove.sumOf { it.sizeBytes }
    mediaItemsList.removeAll(toRemove)
    return MediaCleanResult(
      bytesFreed = freed,
      filesRemoved = toRemove.size,
      cleanedIds = toRemove.map { it.id }.toSet(),
      updatedSnapshot = scanStorage()
    )
  }

  override fun seedSampleCache(): StorageSnapshot {
    seedCalls += 1
    playServicesBytes = 860_000L
    browserBytes = 1_140_000L
    youtubeBytes = 1_460_000L
    installedAppsBytes = 640_000L
    orphanApksBytes = 1_760_000L
    bgServiceTrimmerBytes = 1_000_000L
    ramPurgeBytes = 1_664_000L
    tempLogsBytes = 200_000L
    return scanStorage()
  }

  override fun createSystemCacheCleanerIntent(): Intent = systemIntent

  override fun createAppStorageSettingsIntent(packageName: String): Intent = appSettingsIntent

  override fun generateGeminiSmartInsight(
    snapshot: StorageSnapshot?,
    smartGuard: SmartGuardState,
    userPrompt: String?
  ): String {
    geminiCalls += 1
    return "Ahri (Gemini 3.8 Flash): RAM at ${smartGuard.lastPolledRamPercent}% (check ${smartGuard.consecutiveHighRamChecks}/3)."
  }

  override fun postSmartGuardHeadsUpNotification(
    ramPercent: Int,
    reason: String,
    insightText: String
  ): Boolean {
    headsUpNotificationsPosted += 1
    return true
  }
}

class CacheCleanerRepositoryTest {

  private lateinit var fakeBridge: FakeStoragePlatformBridge
  private lateinit var repository: CacheCleanerRepository

  @Before
  fun setUp() {
    fakeBridge = FakeStoragePlatformBridge()
    repository = CacheCleanerRepository(fakeBridge)
  }

  @Test
  fun getStorageSnapshot_includesAll8CombinedCategoriesAndSeparateMediaItems() {
    val snapshot = repository.getStorageSnapshot()
    assertEquals(8_724_000L, snapshot.reclaimableBytes)
    assertEquals(8, snapshot.categories.size)
    assertEquals(2, snapshot.mediaItems.size)
    assertEquals(2_664_000L, snapshot.ramStatus.purgeableRamBytes)
  }

  @Test
  fun cleanSelectedCaches_cleansOrphansTrimsProcessesAndPurgesRamWithoutTouchingMedia() {
    val result = repository.cleanSelectedCaches(CacheCategoryId.entries.toSet())
    assertEquals(1, fakeBridge.cleanCalls)
    assertEquals(8_724_000L, result.bytesFreed)
    assertEquals(2_664_000L, result.ramBytesFreed)
    assertEquals(4, result.processesTrimmed)
    assertEquals(4, result.orphansAndApksRemoved)
    assertEquals(2, result.updatedSnapshot.mediaItems.size)
  }

  @Test
  fun cleanSelectedMedia_deletesOnlySelectedDuplicateOrLargeMediaItems() {
    val duplicateId = "/cache/media_scan/screen_recording_demo_copy(1).mp4"
    val mediaResult = repository.cleanSelectedMedia(setOf(duplicateId))
    assertEquals(1, fakeBridge.mediaCleanCalls)
    assertEquals(1_240_000L, mediaResult.bytesFreed)
    assertEquals(1, mediaResult.filesRemoved)
    assertEquals(1, mediaResult.updatedSnapshot.mediaItems.size)
  }

  @Test
  fun requestGeminiSmartInsightAndDispatchHeadsUp_delegatesToPlatformBridge() {
    val snapshot = repository.getStorageSnapshot()
    val state = SmartGuardState(lastPolledRamPercent = 86, consecutiveHighRamChecks = 3)
    val insight = repository.requestGeminiSmartInsight(snapshot, state, "Check status")
    assertEquals(1, fakeBridge.geminiCalls)
    assertTrue(insight.contains("Ahri (Gemini 3.8 Flash)"))

    val posted = repository.dispatchSmartGuardHeadsUpNotification(86, "Sustained Pressure", insight)
    assertTrue(posted)
    assertEquals(1, fakeBridge.headsUpNotificationsPosted)
  }
}
