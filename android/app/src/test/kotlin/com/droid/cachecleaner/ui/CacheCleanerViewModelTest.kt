package com.droid.cachecleaner.ui

import com.droid.cachecleaner.data.CacheCleanerRepository
import com.droid.cachecleaner.data.FakeStoragePlatformBridge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CacheCleanerViewModelTest {

  private lateinit var fakeBridge: FakeStoragePlatformBridge
  private lateinit var repository: CacheCleanerRepository
  private lateinit var viewModel: CacheCleanerViewModel

  @Before
  fun setUp() {
    fakeBridge = FakeStoragePlatformBridge()
    repository = CacheCleanerRepository(fakeBridge)
    viewModel = CacheCleanerViewModel(repository)
  }

  @Test
  fun refreshStorage_loadsAll8CombinedCategoriesAndAutoSelectsDuplicateMediaOnly() {
    viewModel.refreshStorage()
    val state = viewModel.uiState.value
    assertNotNull(state.snapshot)
    assertEquals(8_724_000L, state.selectedReclaimableBytes)
    assertEquals(8, state.selectedCategories.size)
    assertEquals(1, state.selectedMediaIds.size)
    assertEquals(1_240_000L, state.selectedMediaBytes)
  }

  @Test
  fun smartGuardRule2_sustainedPressureCheck_ignoresTransientSpikeAndTriggersAfter3Polls() {
    viewModel.refreshStorage()
    val baseTime = 1_700_000_000_000L

    // Poll 1 at 85% (transient 2-sec spike): must NOT trigger notification
    viewModel.evaluateSmartGuardPoll(ramPercent = 85, nowEpochMs = baseTime)
    assertEquals(1, viewModel.uiState.value.smartGuard.consecutiveHighRamChecks)
    assertFalse(viewModel.uiState.value.smartGuard.shouldShowQuietHeadsUp)
    assertEquals(0, fakeBridge.headsUpNotificationsPosted)

    // Poll 2 at 86%: still 2/3 checks, must NOT trigger notification yet
    viewModel.evaluateSmartGuardPoll(ramPercent = 86, nowEpochMs = baseTime + 6_000L)
    assertEquals(2, viewModel.uiState.value.smartGuard.consecutiveHighRamChecks)
    assertFalse(viewModel.uiState.value.smartGuard.shouldShowQuietHeadsUp)

    // Poll 3 at 85% (~18s sustained >= 82%): triggers quiet heads-up notification!
    viewModel.evaluateSmartGuardPoll(ramPercent = 85, nowEpochMs = baseTime + 12_000L)
    assertEquals(3, viewModel.uiState.value.smartGuard.consecutiveHighRamChecks)
    assertTrue(viewModel.uiState.value.smartGuard.shouldShowQuietHeadsUp)
    assertEquals(1, fakeBridge.headsUpNotificationsPosted)
  }

  @Test
  fun smartGuardRule1_cooldownWindow_silences45To60MinUnlessCriticalDangerAbove92Percent() {
    viewModel.refreshStorage()
    val purgeTime = 1_700_000_000_000L

    // Purge triggers Cooldown Window (50 minutes)
    viewModel.cleanNow(nowEpochMs = purgeTime)
    assertTrue(viewModel.uiState.value.smartGuard.isCooldownActive)

    // 10 minutes later (still inside 45-60m cooldown), 3 consecutive polls at 86% MUST be silenced!
    val tenMinLater = purgeTime + (10 * 60_000L)
    viewModel.evaluateSmartGuardPoll(ramPercent = 86, nowEpochMs = tenMinLater)
    viewModel.evaluateSmartGuardPoll(ramPercent = 86, nowEpochMs = tenMinLater + 6_000L)
    viewModel.evaluateSmartGuardPoll(ramPercent = 86, nowEpochMs = tenMinLater + 12_000L)
    assertTrue(viewModel.uiState.value.smartGuard.isCooldownActive)
    assertFalse(viewModel.uiState.value.smartGuard.shouldShowQuietHeadsUp)
    assertEquals(0, fakeBridge.headsUpNotificationsPosted)

    // Critical Danger Spike (>92%, e.g. 95%) immediately overrides Cooldown Window!
    viewModel.evaluateSmartGuardPoll(ramPercent = 95, nowEpochMs = tenMinLater + 18_000L)
    assertTrue(viewModel.uiState.value.smartGuard.shouldShowQuietHeadsUp)
    assertEquals(1, fakeBridge.headsUpNotificationsPosted)
  }

  @Test
  fun toggleMediaItemAndCleanSelectedMediaItems_cleansOnlySelectedMedia() {
    viewModel.refreshStorage()
    val largeMediaId = "/cache/media_scan/4k_studio_wallpaper_uncompressed.raw"
    viewModel.toggleMediaItemSelection(largeMediaId)
    assertTrue(largeMediaId in viewModel.uiState.value.selectedMediaIds)

    viewModel.cleanSelectedMediaItems()
    val state = viewModel.uiState.value
    assertNotNull(state.lastMediaCleanResult)
    assertEquals(2_760_000L, state.lastMediaCleanResult?.bytesFreed)
    assertEquals(0, state.snapshot?.mediaItems?.size)
  }
}
