package com.droid.cachecleaner.ui

import android.app.Application
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.droid.cachecleaner.data.CacheCleanerRepository
import com.droid.cachecleaner.data.FakeStoragePlatformBridge
import com.droid.cachecleaner.model.CacheCategoryId
import com.droid.cachecleaner.ui.theme.GoogleBookCacheCleanerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [34])
class CacheCleanerComposeUiTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @Test
  fun googleBookPurgeScreen_displaysSmartGuardRulesAndTriggersQuietHeadsUpPurgeNow() {
    val fakeBridge = FakeStoragePlatformBridge()
    val repository = CacheCleanerRepository(fakeBridge)
    val viewModel = CacheCleanerViewModel(repository)
    viewModel.refreshStorage()

    composeTestRule.setContent {
      val state by viewModel.uiState.collectAsState()
      GoogleBookCacheCleanerTheme {
        CacheCleanerScreen(
          uiState = state,
          onCleanNow = { viewModel.cleanNow() },
          onCleanSingleApp = { viewModel.cleanSingleApp(it) },
          onToggleMediaItem = { viewModel.toggleMediaItemSelection(it) },
          onCleanSelectedMedia = { viewModel.cleanSelectedMediaItems() },
          onSimulateSmartGuardPoll = { viewModel.evaluateSmartGuardPoll(it) },
          onAskGeminiInsight = { viewModel.refreshGeminiInsight() },
          onDismissHeadsUp = { viewModel.dismissSmartGuardHeadsUp() },
          onRefresh = { viewModel.refreshStorage() },
          onSeedSampleCache = { viewModel.seedSampleCache() },
          onToggleCategory = { viewModel.toggleCategory(it) },
          onOpenSystemCacheDialog = {},
          onOpenAppStorageSettings = {}
        )
      }
    }

    composeTestRule.onNodeWithTag("hero_app_title").assertExists()
    composeTestRule.onNodeWithText("The Anti-Annoyance Strategy (\"Smart Guard\" Rules)").assertExists()

    // Simulate >92% critical spike to trigger the Quiet Heads-Up Notification Banner
    composeTestRule
      .onNodeWithTag("simulate_critical_spike_button")
      .performScrollTo()
      .performClick()

    assertTrue(viewModel.uiState.value.smartGuard.shouldShowQuietHeadsUp)

    // Click the 1-Tap "Purge Now" button inside the quiet heads-up banner
    composeTestRule
      .onNodeWithTag("heads_up_purge_now_button")
      .performScrollTo()
      .performClick()

    assertEquals(0L, viewModel.uiState.value.selectedReclaimableBytes)
    assertFalse(viewModel.uiState.value.smartGuard.shouldShowQuietHeadsUp)
    assertTrue(viewModel.uiState.value.smartGuard.isCooldownActive)
  }

  @Test
  fun googleBookPurgeScreen_displaysOrphanScannerServiceTrimmerRamPurgeAndMediaDetector() {
    val fakeBridge = FakeStoragePlatformBridge()
    val repository = CacheCleanerRepository(fakeBridge)
    val viewModel = CacheCleanerViewModel(repository)
    viewModel.refreshStorage()

    var toggledCategory: CacheCategoryId? = null

    composeTestRule.setContent {
      GoogleBookCacheCleanerTheme {
        CacheCleanerScreen(
          uiState = viewModel.uiState.value,
          onCleanNow = {},
          onCleanSingleApp = {},
          onToggleMediaItem = {},
          onCleanSelectedMedia = {},
          onRefresh = {},
          onSeedSampleCache = {},
          onToggleCategory = { toggledCategory = it },
          onOpenSystemCacheDialog = {},
          onOpenAppStorageSettings = {}
        )
      }
    }

    composeTestRule
      .onNodeWithTag("category_card_${CacheCategoryId.ORPHAN_AND_OBSOLETE_APKS.name}")
      .performScrollTo()
      .performClick()
    assertEquals(CacheCategoryId.ORPHAN_AND_OBSOLETE_APKS, toggledCategory)

    composeTestRule
      .onNodeWithTag("category_card_${CacheCategoryId.RAM_PURGE.name}")
      .performScrollTo()
      .performClick()
    assertEquals(CacheCategoryId.RAM_PURGE, toggledCategory)
  }

  @Test
  fun settingsPillButton_closesAllSettingsAndAskGeminiPillConnectsToGuardrails() {
    val fakeBridge = FakeStoragePlatformBridge()
    val repository = CacheCleanerRepository(fakeBridge)
    val viewModel = CacheCleanerViewModel(repository)
    viewModel.refreshStorage()

    composeTestRule.setContent {
      val state by viewModel.uiState.collectAsState()
      GoogleBookCacheCleanerTheme {
        CacheCleanerScreen(
          uiState = state,
          onCleanNow = { viewModel.cleanNow() },
          onCleanSingleApp = { viewModel.cleanSingleApp(it) },
          onToggleMediaItem = { viewModel.toggleMediaItemSelection(it) },
          onCleanSelectedMedia = { viewModel.cleanSelectedMediaItems() },
          onSimulateSmartGuardPoll = { viewModel.evaluateSmartGuardPoll(it) },
          onAskGeminiInsight = { viewModel.refreshGeminiInsight() },
          onDismissHeadsUp = { viewModel.dismissSmartGuardHeadsUp() },
          onRefresh = { viewModel.refreshStorage() },
          onSeedSampleCache = { viewModel.seedSampleCache() },
          onToggleCategory = { viewModel.toggleCategory(it) },
          onOpenSystemCacheDialog = {},
          onOpenAppStorageSettings = {}
        )
      }
    }

    // Initially, both Smart Guard guardrails and detailed Settings modules are visible
    composeTestRule.onNodeWithTag("smart_guard_rules_card").assertExists()
    composeTestRule.onNodeWithTag("clean_media_button").assertExists()
    composeTestRule.onNodeWithTag("rescan_storage_button").assertExists()

    // 1. Clicking the "⚙ Settings" pill button closes ALL settings and guardrails at once
    composeTestRule
      .onNodeWithTag("settings_toggle_button")
      .performScrollTo()
      .performClick()

    composeTestRule.onNodeWithTag("smart_guard_rules_card").assertDoesNotExist()
    composeTestRule.onNodeWithTag("clean_media_button").assertDoesNotExist()
    composeTestRule.onNodeWithTag("rescan_storage_button").assertDoesNotExist()

    // 2. Clicking the "✦ Ask Gemini" pill button connects to & opens the Smart Guard Guardrails
    val requestsBefore = fakeBridge.geminiCalls
    composeTestRule
      .onNodeWithTag("ask_gemini_pill_button")
      .performScrollTo()
      .performClick()

    composeTestRule.onNodeWithTag("smart_guard_rules_card").assertExists()
    assertEquals(requestsBefore + 1, fakeBridge.geminiCalls)

    // 3. Clicking the "⚙ Settings" pill button again closes the open guardrails cleanly
    composeTestRule
      .onNodeWithTag("settings_toggle_button")
      .performScrollTo()
      .performClick()

    composeTestRule.onNodeWithTag("smart_guard_rules_card").assertDoesNotExist()
    composeTestRule.onNodeWithTag("clean_media_button").assertDoesNotExist()
  }
}
