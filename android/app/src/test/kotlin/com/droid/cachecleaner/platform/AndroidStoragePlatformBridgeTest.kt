package com.droid.cachecleaner.platform

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.droid.cachecleaner.model.CacheCategoryId
import com.droid.cachecleaner.model.MediaCandidateKind
import com.droid.cachecleaner.model.SmartGuardState
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [34])
class AndroidStoragePlatformBridgeTest {

  private lateinit var context: Context
  private lateinit var tempSharedDir: File
  private lateinit var bridge: AndroidStoragePlatformBridge

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    tempSharedDir = File(context.cacheDir, "robolectric_shared_staging").apply { mkdirs() }
    bridge = AndroidStoragePlatformBridge(
      context = context,
      sharedStagingRoot = tempSharedDir
    )
  }

  @Test
  fun seedAndScanStorage_detectsAll8CategoriesRamStatusAndSha256Duplicates() {
    val snapshot = bridge.seedSampleCache()
    assertTrue("Expected positive reclaimable bytes", snapshot.reclaimableBytes > 0L)
    assertEquals(8, snapshot.categories.size)
    assertTrue(snapshot.ramStatus.totalRamBytes > 0L)
    assertTrue(snapshot.ramStatus.purgeableRamBytes > 0L)

    val duplicates = snapshot.mediaItems.filter { it.kind == MediaCandidateKind.DUPLICATE_MEDIA }
    val largeMedia = snapshot.mediaItems.filter { it.kind == MediaCandidateKind.LARGE_MEDIA }
    assertTrue("Expected SHA-256 duplicates detected", duplicates.size >= 2)
    assertTrue("Expected large media detected", largeMedia.isNotEmpty())
  }

  @Test
  fun cleanCaches_sweepsOrphansTrimsServicesAndPurgesRamWhilePreservingMedia() {
    val initial = bridge.seedSampleCache()
    val initialMediaCount = initial.mediaItems.size
    assertTrue(initialMediaCount >= 3)

    val cleanResult = bridge.cleanCaches(CacheCategoryId.entries.toSet())
    assertTrue("Expected freed bytes > 0", cleanResult.bytesFreed > 0L)
    assertTrue("Expected ramBytesFreed > 0", cleanResult.ramBytesFreed > 0L)
    assertTrue("Expected processesTrimmed > 0", cleanResult.processesTrimmed > 0)
    assertTrue("Expected orphansAndApksRemoved > 0", cleanResult.orphansAndApksRemoved > 0)
    assertEquals(0L, cleanResult.updatedSnapshot.reclaimableBytes)
    assertEquals(initialMediaCount, cleanResult.updatedSnapshot.mediaItems.size)
  }

  @Test
  fun generateGeminiSmartInsightAndPostQuietHeadsUpNotification_succeedOnRobolectric() {
    val snapshot = bridge.seedSampleCache()
    val smartGuard = SmartGuardState(
      lastPolledRamPercent = 88,
      consecutiveHighRamChecks = 3,
      shouldShowQuietHeadsUp = true
    )
    val insight = bridge.generateGeminiSmartInsight(snapshot, smartGuard, "Analyze RAM pressure")
    assertTrue(insight.contains("Ahri (Gemini 3.8 Flash)"))

    val posted = bridge.postSmartGuardHeadsUpNotification(
      ramPercent = 88,
      reason = "Sustained Pressure (3/3 checks)",
      insightText = insight
    )
    assertTrue(posted)
  }
}
