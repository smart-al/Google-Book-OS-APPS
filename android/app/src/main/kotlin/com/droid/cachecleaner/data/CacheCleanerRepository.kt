package com.droid.cachecleaner.data

import android.content.Intent
import com.droid.cachecleaner.model.CacheCategoryId
import com.droid.cachecleaner.model.CleanResult
import com.droid.cachecleaner.model.MediaCleanResult
import com.droid.cachecleaner.model.SmartGuardState
import com.droid.cachecleaner.model.StorageSnapshot
import com.droid.cachecleaner.platform.StoragePlatformBridge

/**
 * Repository mediating between the UI/ViewModel and the platform storage bridge.
 * Constructor-injected with [StoragePlatformBridge] for full JVM unit testability.
 */
class CacheCleanerRepository(
  private val platformBridge: StoragePlatformBridge
) {
  fun getStorageSnapshot(): StorageSnapshot {
    return platformBridge.scanStorage()
  }

  fun cleanSelectedCaches(selectedCategories: Set<CacheCategoryId>): CleanResult {
    return platformBridge.cleanCaches(selectedCategories)
  }

  fun cleanAppCache(packageName: String): CleanResult {
    return platformBridge.cleanSingleAppCache(packageName)
  }

  fun cleanSelectedMedia(mediaIds: Set<String>): MediaCleanResult {
    return platformBridge.cleanSelectedMedia(mediaIds)
  }

  fun seedSampleCacheData(): StorageSnapshot {
    return platformBridge.seedSampleCache()
  }

  fun buildSystemCacheCleanerIntent(): Intent {
    return platformBridge.createSystemCacheCleanerIntent()
  }

  fun buildAppStorageSettingsIntent(packageName: String): Intent {
    return platformBridge.createAppStorageSettingsIntent(packageName)
  }

  fun requestGeminiSmartInsight(
    snapshot: StorageSnapshot?,
    smartGuard: SmartGuardState,
    userPrompt: String? = null
  ): String {
    return platformBridge.generateGeminiSmartInsight(
      snapshot = snapshot,
      smartGuard = smartGuard,
      userPrompt = userPrompt
    )
  }

  fun dispatchSmartGuardHeadsUpNotification(
    ramPercent: Int,
    reason: String,
    insightText: String
  ): Boolean {
    return platformBridge.postSmartGuardHeadsUpNotification(
      ramPercent = ramPercent,
      reason = reason,
      insightText = insightText
    )
  }
}
