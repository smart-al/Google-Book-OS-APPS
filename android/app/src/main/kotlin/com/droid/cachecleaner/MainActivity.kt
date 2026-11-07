package com.droid.cachecleaner

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.droid.cachecleaner.data.CacheCleanerRepository
import com.droid.cachecleaner.platform.DefaultStoragePlatformBridgeFactory
import com.droid.cachecleaner.platform.StoragePlatformBridgeFactory
import com.droid.cachecleaner.ui.CacheCleanerScreen
import com.droid.cachecleaner.ui.CacheCleanerViewModel
import com.droid.cachecleaner.ui.theme.GoogleBookCacheCleanerTheme

class MainActivity : ComponentActivity() {

  private val bridgeFactory: StoragePlatformBridgeFactory = DefaultStoragePlatformBridgeFactory()

  private val viewModel: CacheCleanerViewModel by lazy {
    val bridge = bridgeFactory.create(applicationContext)
    val repository = CacheCleanerRepository(bridge)
    ViewModelProvider(
      this,
      object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
          return CacheCleanerViewModel(repository) as T
        }
      }
    )[CacheCleanerViewModel::class.java]
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    viewModel.refreshStorage()

    setContent {
      GoogleBookCacheCleanerTheme {
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
        CacheCleanerScreen(
          uiState = uiState,
          onCleanNow = { viewModel.cleanNow() },
          onCleanSingleApp = { pkg -> viewModel.cleanSingleApp(pkg) },
          onToggleMediaItem = { id -> viewModel.toggleMediaItemSelection(id) },
          onCleanSelectedMedia = { viewModel.cleanSelectedMediaItems() },
          onSimulateSmartGuardPoll = { ramPct -> viewModel.evaluateSmartGuardPoll(ramPct) },
          onAskGeminiInsight = { viewModel.refreshGeminiInsight() },
          onDismissHeadsUp = { viewModel.dismissSmartGuardHeadsUp() },
          onRefresh = { viewModel.refreshStorage() },
          onSeedSampleCache = { viewModel.seedSampleCache() },
          onToggleCategory = { viewModel.toggleCategory(it) },
          onOpenSystemCacheDialog = { launchSystemCacheCleanerDialog() },
          onOpenAppStorageSettings = { pkg -> launchAppStorageSettings(pkg) }
        )
      }
    }
  }

  private fun launchSystemCacheCleanerDialog() {
    val primaryIntent = viewModel.createSystemCacheCleanerIntent()
    try {
      startActivity(primaryIntent)
    } catch (_: Throwable) {
      runCatching {
        startActivity(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS))
      }
    }
  }

  private fun launchAppStorageSettings(packageName: String) {
    val intent = viewModel.createAppStorageSettingsIntent(packageName)
    runCatching {
      startActivity(intent)
    }.onFailure {
      runCatching {
        startActivity(Intent(Settings.ACTION_MANAGE_ALL_APPLICATIONS_SETTINGS))
      }
    }
  }
}
