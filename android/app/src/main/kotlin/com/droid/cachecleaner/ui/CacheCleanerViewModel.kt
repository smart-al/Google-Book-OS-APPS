package com.droid.cachecleaner.ui

import android.content.Intent
import androidx.lifecycle.ViewModel
import com.droid.cachecleaner.data.CacheCleanerRepository
import com.droid.cachecleaner.model.ByteFormatter
import com.droid.cachecleaner.model.CacheCategoryId
import com.droid.cachecleaner.model.CacheCleanerUiState
import com.droid.cachecleaner.model.MediaCandidateKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Pure JVM-testable ViewModel managing Google Book Purge & Ahri (Gemini 3.8 Flash) Smart Guard state.
 */
class CacheCleanerViewModel(
  private val repository: CacheCleanerRepository
) : ViewModel() {

  private val _uiState = MutableStateFlow(CacheCleanerUiState())
  val uiState: StateFlow<CacheCleanerUiState> = _uiState.asStateFlow()

  fun refreshStorage() {
    _uiState.update { it.copy(isLoading = true, errorMessage = null) }
    try {
      val snapshot = repository.getStorageSnapshot()
      val reclaimableFormatted = ByteFormatter.formatBytes(snapshot.reclaimableBytes)
      val duplicateIds = snapshot.mediaItems
        .filter { it.kind == MediaCandidateKind.DUPLICATE_MEDIA }
        .map { it.id }
        .toSet()
      _uiState.update { state ->
        state.copy(
          isLoading = false,
          snapshot = snapshot,
          selectedMediaIds = duplicateIds,
          statusMessage = "Scanned ${snapshot.installedPackagesCount} apps • $reclaimableFormatted One-Click reclaimable"
        )
      }
    } catch (e: Throwable) {
      _uiState.update {
        it.copy(
          isLoading = false,
          errorMessage = e.message ?: "Failed to scan storage",
          statusMessage = "Error scanning storage"
        )
      }
    }
  }

  fun toggleCategory(categoryId: CacheCategoryId) {
    _uiState.update { state ->
      val updated = state.selectedCategories.toMutableSet()
      if (categoryId in updated) {
        updated.remove(categoryId)
      } else {
        updated.add(categoryId)
      }
      state.copy(selectedCategories = updated)
    }
  }

  fun toggleMediaItemSelection(mediaId: String) {
    _uiState.update { state ->
      val updated = state.selectedMediaIds.toMutableSet()
      if (mediaId in updated) {
        updated.remove(mediaId)
      } else {
        updated.add(mediaId)
      }
      state.copy(selectedMediaIds = updated)
    }
  }

  fun evaluateSmartGuardPoll(
    ramPercent: Int,
    nowEpochMs: Long = System.currentTimeMillis()
  ) {
    val currentState = _uiState.value
    val sg = currentState.smartGuard
    val cooldownMs = sg.cooldownWindowMinutes * 60_000L
    val lastEpoch = sg.lastNotificationOrPurgeEpochMs
    val elapsedMs = if (lastEpoch != null) (nowEpochMs - lastEpoch).coerceAtLeast(0L) else Long.MAX_VALUE
    val cooldownActive = lastEpoch != null && elapsedMs < cooldownMs
    val cooldownRemMin = if (cooldownActive) {
      (((cooldownMs - elapsedMs) + 59_999L) / 60_000L).toInt().coerceAtLeast(1)
    } else {
      0
    }

    val newConsecutive = if (ramPercent >= sg.sustainedThresholdPercent) {
      sg.consecutiveHighRamChecks + 1
    } else {
      0
    }

    val isCriticalDanger = ramPercent > sg.criticalDangerThresholdPercent
    val isSustainedPressure = newConsecutive >= sg.requiredConsecutiveChecks && !cooldownActive

    if (isCriticalDanger || isSustainedPressure) {
      val reason = if (isCriticalDanger) {
        "Critical RAM Danger (>92%) — Cooldown Override"
      } else {
        "Sustained Pressure (≥82% for $newConsecutive checks)"
      }

      val provisionalGuard = sg.copy(
        lastPolledRamPercent = ramPercent,
        consecutiveHighRamChecks = newConsecutive,
        isCooldownActive = true,
        cooldownRemainingMinutes = sg.cooldownWindowMinutes,
        lastNotificationOrPurgeEpochMs = nowEpochMs,
        shouldShowQuietHeadsUp = true,
        triggerReason = reason
      )

      val insight = repository.requestGeminiSmartInsight(
        snapshot = currentState.snapshot,
        smartGuard = provisionalGuard,
        userPrompt = null
      )

      repository.dispatchSmartGuardHeadsUpNotification(
        ramPercent = ramPercent,
        reason = reason,
        insightText = insight
      )

      _uiState.update { state ->
        state.copy(
          smartGuard = provisionalGuard.copy(geminiInsightText = insight),
          statusMessage = "Ahri Smart Guard: $reason • 1-Tap Purge Ready"
        )
      }
    } else {
      val updatedGuard = sg.copy(
        lastPolledRamPercent = ramPercent,
        consecutiveHighRamChecks = newConsecutive,
        isCooldownActive = cooldownActive,
        cooldownRemainingMinutes = cooldownRemMin,
        shouldShowQuietHeadsUp = if (ramPercent < sg.sustainedThresholdPercent) false else sg.shouldShowQuietHeadsUp
      )
      val insight = repository.requestGeminiSmartInsight(
        snapshot = currentState.snapshot,
        smartGuard = updatedGuard,
        userPrompt = null
      )
      _uiState.update { state ->
        state.copy(
          smartGuard = updatedGuard.copy(geminiInsightText = insight)
        )
      }
    }
  }

  fun refreshGeminiInsight(userPrompt: String? = null) {
    val currentState = _uiState.value
    val insight = repository.requestGeminiSmartInsight(
      snapshot = currentState.snapshot,
      smartGuard = currentState.smartGuard,
      userPrompt = userPrompt ?: "Evaluate Smart Guard guardrails & RAM pressure"
    )
    _uiState.update { state ->
      state.copy(
        smartGuard = state.smartGuard.copy(geminiInsightText = insight),
        statusMessage = "Ahri (Gemini 3.8 Flash) evaluated Smart Guard guardrails"
      )
    }
  }

  fun dismissSmartGuardHeadsUp(nowEpochMs: Long = System.currentTimeMillis()) {
    _uiState.update { state ->
      val sg = state.smartGuard
      state.copy(
        smartGuard = sg.copy(
          shouldShowQuietHeadsUp = false,
          lastNotificationOrPurgeEpochMs = nowEpochMs,
          isCooldownActive = true,
          cooldownRemainingMinutes = sg.cooldownWindowMinutes
        ),
        statusMessage = "Ahri Smart Guard: Heads-up silenced (${sg.cooldownWindowMinutes}m cooldown active)"
      )
    }
  }

  fun cleanNow(nowEpochMs: Long = System.currentTimeMillis()) {
    val selected = _uiState.value.selectedCategories
    if (selected.isEmpty()) {
      _uiState.update {
        it.copy(statusMessage = "Select at least one cache category to clean")
      }
      return
    }

    _uiState.update { it.copy(isCleaning = true, errorMessage = null) }
    try {
      val result = repository.cleanSelectedCaches(selected)
      val freedFormatted = ByteFormatter.formatBytes(result.bytesFreed)
      val ramFormatted = ByteFormatter.formatBytes(result.ramBytesFreed)
      _uiState.update { state ->
        val sg = state.smartGuard
        val cooledGuard = sg.copy(
          lastNotificationOrPurgeEpochMs = nowEpochMs,
          isCooldownActive = true,
          cooldownRemainingMinutes = sg.cooldownWindowMinutes,
          consecutiveHighRamChecks = 0,
          lastPolledRamPercent = result.updatedSnapshot.ramStatus.usedRamPercent,
          shouldShowQuietHeadsUp = false,
          triggerReason = null
        )
        val insight = repository.requestGeminiSmartInsight(
          snapshot = result.updatedSnapshot,
          smartGuard = cooledGuard,
          userPrompt = "post-purge summary"
        )
        state.copy(
          isCleaning = false,
          snapshot = result.updatedSnapshot,
          lastCleanResult = result,
          smartGuard = cooledGuard.copy(geminiInsightText = insight),
          statusMessage = "Cleaned $freedFormatted • Purged $ramFormatted RAM • Trimmed ${result.processesTrimmed} bg services"
        )
      }
    } catch (e: Throwable) {
      _uiState.update {
        it.copy(
          isCleaning = false,
          errorMessage = e.message ?: "Cache cleanup failed",
          statusMessage = "Cache cleanup encountered an error"
        )
      }
    }
  }

  fun cleanSelectedMediaItems() {
    val selectedIds = _uiState.value.selectedMediaIds
    if (selectedIds.isEmpty()) {
      _uiState.update {
        it.copy(statusMessage = "Select at least one duplicate or large media item to clean")
      }
      return
    }

    try {
      val mediaResult = repository.cleanSelectedMedia(selectedIds)
      val freedFormatted = ByteFormatter.formatBytes(mediaResult.bytesFreed)
      val remainingDuplicates = mediaResult.updatedSnapshot.mediaItems
        .filter { it.kind == MediaCandidateKind.DUPLICATE_MEDIA }
        .map { it.id }
        .toSet()
      _uiState.update { state ->
        state.copy(
          snapshot = mediaResult.updatedSnapshot,
          selectedMediaIds = remainingDuplicates,
          lastMediaCleanResult = mediaResult,
          statusMessage = "Removed ${mediaResult.filesRemoved} media files ($freedFormatted freed)"
        )
      }
    } catch (e: Throwable) {
      _uiState.update {
        it.copy(errorMessage = e.message ?: "Failed to clean selected media")
      }
    }
  }

  fun cleanSingleApp(packageName: String) {
    _uiState.update { it.copy(isCleaning = true, errorMessage = null) }
    try {
      val result = repository.cleanAppCache(packageName)
      val freedFormatted = ByteFormatter.formatBytes(result.bytesFreed)
      _uiState.update { state ->
        state.copy(
          isCleaning = false,
          snapshot = result.updatedSnapshot,
          lastCleanResult = result,
          statusMessage = "Cleaned $freedFormatted from $packageName (${result.filesRemoved} files)"
        )
      }
    } catch (e: Throwable) {
      _uiState.update {
        it.copy(
          isCleaning = false,
          errorMessage = e.message ?: "Failed to clean $packageName",
          statusMessage = "Error cleaning $packageName"
        )
      }
    }
  }

  fun seedSampleCache() {
    try {
      val snapshot = repository.seedSampleCacheData()
      val reclaimableFormatted = ByteFormatter.formatBytes(snapshot.reclaimableBytes)
      val duplicateIds = snapshot.mediaItems
        .filter { it.kind == MediaCandidateKind.DUPLICATE_MEDIA }
        .map { it.id }
        .toSet()
      _uiState.update { state ->
        state.copy(
          snapshot = snapshot,
          selectedMediaIds = duplicateIds,
          statusMessage = "Seeded caches, orphans, APKs, RAM buffers & duplicate media • $reclaimableFormatted ready"
        )
      }
    } catch (e: Throwable) {
      _uiState.update {
        it.copy(errorMessage = e.message ?: "Failed to seed sample cache")
      }
    }
  }

  fun createSystemCacheCleanerIntent(): Intent {
    return repository.buildSystemCacheCleanerIntent()
  }

  fun createAppStorageSettingsIntent(packageName: String): Intent {
    return repository.buildAppStorageSettingsIntent(packageName)
  }
}
