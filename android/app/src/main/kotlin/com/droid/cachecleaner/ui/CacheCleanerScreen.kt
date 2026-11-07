package com.droid.cachecleaner.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.droid.cachecleaner.model.AppCacheEntry
import com.droid.cachecleaner.model.ByteFormatter
import com.droid.cachecleaner.model.CacheCategoryId
import com.droid.cachecleaner.model.CacheCategoryItem
import com.droid.cachecleaner.model.CacheCleanerUiState
import com.droid.cachecleaner.model.MediaCandidateItem
import com.droid.cachecleaner.model.MediaCandidateKind
import com.droid.cachecleaner.model.SmartGuardState
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

// Exact Color Palette from Reference Image (`Google Book Purge Lite`)
private val WindowNavyBg = Color(0xFF192030)
private val WindowDeepBg = Color(0xFF131926)
private val FrostedCardBg = Color(0xFF232C3F)
private val FrostedCardBorder = Color(0xFF364156)
private val LiteBadgeBg = Color(0xFF33446B)
private val NeonPurple = Color(0xFF8B5CF6)
private val ElectricBlue = Color(0xFF38BDF8)
private val NeonCyan = Color(0xFF2DD4BF)
private val StatusGreen = Color(0xFF22C55E)
private val SegSystemBlue = Color(0xFF2B549A)
private val SegAppsIndigo = Color(0xFF5E6AD2)
private val SegCacheCyan = Color(0xFF2DD4BF)
private val SegFreeGray = Color(0xFF6E7687)
private val AmberAccent = Color(0xFFFBBF24)
private val RoseDanger = Color(0xFFFB7185)
private val TextPrimary = Color(0xFFF8FAFC)
private val TextSecondary = Color(0xFF94A3B8)
private val TextMuted = Color(0xFF64748B)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CacheCleanerScreen(
  uiState: CacheCleanerUiState,
  onCleanNow: () -> Unit,
  onCleanSingleApp: (String) -> Unit,
  onToggleMediaItem: (String) -> Unit,
  onCleanSelectedMedia: () -> Unit,
  onSimulateSmartGuardPoll: (Int) -> Unit = {},
  onAskGeminiInsight: () -> Unit = {},
  onDismissHeadsUp: () -> Unit = {},
  onRefresh: () -> Unit,
  onSeedSampleCache: () -> Unit,
  onToggleCategory: (CacheCategoryId) -> Unit,
  onOpenSystemCacheDialog: () -> Unit,
  onOpenAppStorageSettings: (String) -> Unit,
  modifier: Modifier = Modifier
) {
  val snapshot = uiState.snapshot
  val smartGuard = uiState.smartGuard
  val scrollState = rememberScrollState()
  var showSettingsPanel by remember { mutableStateOf(true) }
  var showSmartGuardPanel by remember { mutableStateOf(true) }
  val isAnySettingsOpen = showSettingsPanel || showSmartGuardPanel

  val ramPercent = smartGuard.lastPolledRamPercent.takeIf { it > 0 }
    ?: snapshot?.ramStatus?.usedRamPercent?.takeIf { it > 0 }
    ?: 42
  val usedRamText = ByteFormatter.formatBytes(snapshot?.ramStatus?.usedRamBytes ?: 3_607_772_528L)
  val totalRamText = ByteFormatter.formatBytes(snapshot?.ramStatus?.totalRamBytes ?: 8_589_934_592L)

  val totalDisk = (snapshot?.totalBytes ?: 64_000_000_000L).coerceAtLeast(1L)
  val freeDisk = (snapshot?.freeBytes ?: 31_600_000_000L).coerceIn(0L, totalDisk)
  val cacheDisk = uiState.selectedReclaimableBytes.coerceAtLeast(0L)
  val usedNonCache = (totalDisk - freeDisk - cacheDisk).coerceAtLeast(0L)
  val systemDisk = (usedNonCache * 0.40).toLong()
  val appsDisk = (usedNonCache - systemDisk).coerceAtLeast(0L)

  val activeBgAppsCount = max(
    snapshot?.ramStatus?.activeBackgroundProcesses ?: 0,
    snapshot?.installedPackagesCount ?: 18
  )

  Surface(
    modifier = modifier.fillMaxSize(),
    color = WindowDeepBg
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .verticalScroll(scrollState)
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
      // =========================================================================
      // RULE 3: QUIET NON-BLOCKING HEADS-UP NOTIFICATION BANNER (1-Tap "Purge Now")
      // =========================================================================
      if (smartGuard.shouldShowQuietHeadsUp) {
        QuietHeadsUpNotificationBanner(
          smartGuard = smartGuard,
          onPurgeNow = onCleanNow,
          onDismiss = onDismissHeadsUp
        )
      }

      // =========================================================================
      // MAIN GOOGLE BOOK PURGE HERO WINDOW (Matches Uploaded Reference Image)
      // =========================================================================
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(22.dp))
          .background(
            Brush.verticalGradient(
              colors = listOf(Color(0xFF20283B), WindowNavyBg, Color(0xFF161C2B))
            )
          )
          .border(1.dp, FrostedCardBorder, RoundedCornerShape(22.dp))
          .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
      ) {
        // 1. Top Window Bar: "Google Book Purge [Lite]" + Window Controls + "All Systems Normal" Pill
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.Top
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            Text(
              text = "Google Book Purge",
              color = TextPrimary,
              fontSize = 22.sp,
              fontWeight = FontWeight.Bold,
              modifier = Modifier.testTag("hero_app_title")
            )
            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(LiteBadgeBg)
                .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
              Text(
                text = "Lite",
                color = Color(0xFFCBD5E1),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
              )
            }
          }

          Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(12.dp)
          ) {
            Row(
              horizontalArrangement = Arrangement.spacedBy(14.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(text = "—", color = TextSecondary, fontSize = 13.sp)
              Text(text = "❐", color = TextSecondary, fontSize = 12.sp)
              Text(text = "✕", color = TextSecondary, fontSize = 13.sp)
            }

            // "● All Systems Normal" Frosted Pill Badge
            val statusDotColor = when {
              ramPercent > smartGuard.criticalDangerThresholdPercent -> RoseDanger
              ramPercent >= smartGuard.sustainedThresholdPercent -> AmberAccent
              else -> StatusGreen
            }
            Row(
              modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(FrostedCardBg)
                .border(1.dp, FrostedCardBorder, RoundedCornerShape(50))
                .padding(horizontal = 14.dp, vertical = 7.dp)
                .testTag("system_normal_badge"),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              Box(
                modifier = Modifier
                  .size(8.dp)
                  .clip(CircleShape)
                  .background(statusDotColor)
              )
              Text(
                text = if (uiState.isCleaning) "Purging System..." else "All Systems Normal",
                color = TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
              )
            }
          }
        }

        // 2. Center Two-Column Dashboard (Left: Neon RAM Gauge + 3 Pill Buttons | Right: Storage Status Card)
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
          val isWide = maxWidth >= 560.dp
          if (isWide) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(24.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
              ) {
                NeonRamArcGauge(
                  ramPercent = ramPercent,
                  usedRamFormatted = usedRamText,
                  totalRamFormatted = totalRamText
                )
                Spacer(modifier = Modifier.height(14.dp))
                PurgeAndCleanPillButtonsRow(
                  isCleaning = uiState.isCleaning,
                  isGuardrailsOpen = showSmartGuardPanel,
                  onPurgeRam = onCleanNow,
                  onQuickCacheClean = onCleanNow,
                  onAskGeminiConnectGuardrails = {
                    showSmartGuardPanel = true
                    onAskGeminiInsight()
                  }
                )
              }

              StorageStatusFrostedCard(
                systemBytes = systemDisk,
                appsBytes = appsDisk,
                cacheBytes = cacheDisk,
                freeBytes = freeDisk,
                modifier = Modifier.weight(1.05f)
              )
            }
          } else {
            Column(
              modifier = Modifier.fillMaxWidth(),
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
              NeonRamArcGauge(
                ramPercent = ramPercent,
                usedRamFormatted = usedRamText,
                totalRamFormatted = totalRamText
              )
              PurgeAndCleanPillButtonsRow(
                isCleaning = uiState.isCleaning,
                isGuardrailsOpen = showSmartGuardPanel,
                onPurgeRam = onCleanNow,
                onQuickCacheClean = onCleanNow,
                onAskGeminiConnectGuardrails = {
                  showSmartGuardPanel = true
                  onAskGeminiInsight()
                }
              )
              StorageStatusFrostedCard(
                systemBytes = systemDisk,
                appsBytes = appsDisk,
                cacheBytes = cacheDisk,
                freeBytes = freeDisk,
                modifier = Modifier.fillMaxWidth()
              )
            }
          }
        }

        Text(
          text = uiState.statusMessage,
          color = TextSecondary,
          fontSize = 12.sp,
          textAlign = TextAlign.Center,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("status_message")
        )

        uiState.lastCleanResult?.let { clean ->
          Text(
            text = "Last Purge freed ${ByteFormatter.formatBytes(clean.bytesFreed)} disk • ${ByteFormatter.formatBytes(clean.ramBytesFreed)} RAM • Trimmed ${clean.processesTrimmed} bg services • Removed ${clean.orphansAndApksRemoved} orphans/APKs",
            color = NeonCyan,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier
              .fillMaxWidth()
              .testTag("last_clean_summary")
          )
        }

        // 3. Bottom Frosted Dock Bar: "18 Background Apps Active" + Equalizer Bars + Interactive "⚙ Settings" Pill Button
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(FrostedCardBg)
            .border(1.dp, FrostedCardBorder, RoundedCornerShape(14.dp))
            .padding(horizontal = 18.dp, vertical = 12.dp)
            .testTag("bottom_dock_bar"),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = buildAnnotatedString {
              withStyle(SpanStyle(color = TextPrimary, fontWeight = FontWeight.ExtraBold)) {
                append("$activeBgAppsCount ")
              }
              withStyle(SpanStyle(color = TextSecondary, fontWeight = FontWeight.Medium)) {
                append("Background Apps Active")
              }
            },
            fontSize = 13.sp
          )

          EqualizerWaveformBars(modifier = Modifier.padding(horizontal = 8.dp))

          // Settings Pill Button: Closes ALL settings & guardrails when any are open, or opens all when closed
          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(50))
              .background(
                if (isAnySettingsOpen) NeonCyan.copy(alpha = 0.16f) else WindowNavyBg
              )
              .border(
                width = 1.5.dp,
                color = if (isAnySettingsOpen) NeonCyan else FrostedCardBorder,
                shape = RoundedCornerShape(50)
              )
              .clickable {
                if (isAnySettingsOpen) {
                  showSettingsPanel = false
                  showSmartGuardPanel = false
                } else {
                  showSettingsPanel = true
                  showSmartGuardPanel = true
                }
              }
              .padding(horizontal = 14.dp, vertical = 7.dp)
              .testTag("settings_toggle_button"),
            contentAlignment = Alignment.Center
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              Text(
                text = "⚙",
                color = if (isAnySettingsOpen) NeonCyan else TextSecondary,
                fontSize = 14.sp
              )
              Text(
                text = if (isAnySettingsOpen) "Close Settings" else "Settings",
                color = if (isAnySettingsOpen) TextPrimary else TextSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
              )
            }
          }
        }

        // Utility Toolbar inside Settings Panel so closing Settings hides these controls too
        if (showSettingsPanel) {
          FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            OutlinedButton(
              onClick = onRefresh,
              shape = RoundedCornerShape(50),
              modifier = Modifier.testTag("rescan_storage_button")
            ) {
              Text(text = "Rescan System", color = ElectricBlue, fontSize = 12.sp)
            }

            OutlinedButton(
              onClick = onSeedSampleCache,
              shape = RoundedCornerShape(50),
              modifier = Modifier.testTag("seed_sample_cache_button")
            ) {
              Text(text = "Seed Sample Cache", color = AmberAccent, fontSize = 12.sp)
            }

            OutlinedButton(
              onClick = onOpenSystemCacheDialog,
              shape = RoundedCornerShape(50),
              modifier = Modifier.testTag("os_system_cache_button")
            ) {
              Text(text = "OS Deep System Cache Dialog", color = TextPrimary, fontSize = 12.sp)
            }
          }
        }
      }

      // =========================================================================
      // AHRI (GOOGLE GEMINI 3.8 FLASH) & THE ANTI-ANNOYANCE STRATEGY ("SMART GUARD" RULES)
      // Connected directly to the "✦ Ask Gemini" Pill Button and closed by "⚙ Settings"
      // =========================================================================
      if (showSmartGuardPanel) {
        AhriGeminiSmartGuardCard(
          smartGuard = smartGuard,
          onSimulateSustainedPoll = { onSimulateSmartGuardPoll(85) },
          onSimulateCriticalSpike = { onSimulateSmartGuardPoll(95) },
          onAskGemini = onAskGeminiInsight
        )
      }

      // =========================================================================
      // DETAILED MODULES, SEPARATE DUPLICATE MEDIA DETECTOR & INSTALLED APPS
      // Closed completely when "⚙ Settings" is clicked
      // =========================================================================
      if (showSettingsPanel) {
        SeparateMediaDetectorCard(
          mediaItems = snapshot?.mediaItems.orEmpty(),
          selectedMediaIds = uiState.selectedMediaIds,
          selectedMediaBytes = uiState.selectedMediaBytes,
          lastMediaCleanResult = uiState.lastMediaCleanResult,
          onToggleMediaItem = onToggleMediaItem,
          onCleanSelectedMedia = onCleanSelectedMedia
        )

        Text(
          text = "Combined One-Click Purge & Clean Modules (8)",
          color = TextPrimary,
          fontSize = 16.sp,
          fontWeight = FontWeight.Bold
        )

        val categories = snapshot?.categories ?: CacheCategoryId.entries.map {
          CacheCategoryItem(id = it, bytes = 0L, fileCount = 0, samplePaths = emptyList())
        }

        categories.forEach { item ->
          val isSelected = item.id in uiState.selectedCategories
          CategoryCard(
            item = item,
            isSelected = isSelected,
            onToggle = { onToggleCategory(item.id) }
          )
        }

        if (snapshot != null && snapshot.installedApps.isNotEmpty()) {
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text = "Installed Apps on Google Book OS (${snapshot.installedApps.size})",
            color = TextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
          )
          Text(
            text = "Includes Google Play Services, Chrome Browser, YouTube, and every discovered app on this laptop",
            color = TextSecondary,
            fontSize = 12.sp
          )

          snapshot.installedApps.forEach { appEntry ->
            InstalledAppCacheRow(
              app = appEntry,
              onSweepApp = { onCleanSingleApp(appEntry.packageName) },
              onOpenAppSettings = { onOpenAppStorageSettings(appEntry.packageName) }
            )
          }
        }
      }
    }
  }
}

@Composable
private fun QuietHeadsUpNotificationBanner(
  smartGuard: SmartGuardState,
  onPurgeNow: () -> Unit,
  onDismiss: () -> Unit
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(16.dp))
      .background(
        Brush.horizontalGradient(
          colors = listOf(Color(0xFF281E46), Color(0xFF1A2E3B))
        )
      )
      .border(1.5.dp, NeonCyan, RoundedCornerShape(16.dp))
      .padding(horizontal = 16.dp, vertical = 12.dp)
      .testTag("smart_guard_heads_up_banner"),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Text(
          text = "✦ AHRI SMART GUARD • QUIET HEADS-UP",
          color = NeonCyan,
          fontSize = 11.sp,
          fontWeight = FontWeight.ExtraBold
        )
        Text(
          text = smartGuard.triggerReason ?: "Sustained RAM Pressure",
          color = AmberAccent,
          fontSize = 11.sp,
          fontWeight = FontWeight.SemiBold
        )
      }
      Spacer(modifier = Modifier.height(4.dp))
      Text(
        text = smartGuard.geminiInsightText,
        color = TextPrimary,
        fontSize = 12.sp
      )
    }

    Spacer(modifier = Modifier.width(12.dp))

    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      Button(
        onClick = onPurgeNow,
        shape = RoundedCornerShape(50),
        colors = ButtonDefaults.buttonColors(
          containerColor = NeonCyan,
          contentColor = Color(0xFF082F2C)
        ),
        modifier = Modifier.testTag("heads_up_purge_now_button")
      ) {
        Text(
          text = "Purge Now",
          fontSize = 12.sp,
          fontWeight = FontWeight.ExtraBold
        )
      }

      Text(
        text = "✕",
        color = TextSecondary,
        fontSize = 14.sp,
        modifier = Modifier
          .clickable { onDismiss() }
          .padding(6.dp)
          .testTag("heads_up_dismiss_button")
      )
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AhriGeminiSmartGuardCard(
  smartGuard: SmartGuardState,
  onSimulateSustainedPoll: () -> Unit,
  onSimulateCriticalSpike: () -> Unit,
  onAskGemini: () -> Unit
) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(18.dp))
      .background(Color(0xFF28344A))
      .border(1.dp, NeonPurple.copy(alpha = 0.6f), RoundedCornerShape(18.dp))
      .padding(20.dp)
      .testTag("smart_guard_rules_card"),
    verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = "GOOGLE GEMINI AI • ${smartGuard.assistantName.uppercase()}",
          color = NeonCyan,
          fontSize = 11.sp,
          fontWeight = FontWeight.ExtraBold
        )
        Text(
          text = "The Anti-Annoyance Strategy (\"Smart Guard\" Rules)",
          color = TextPrimary,
          fontSize = 18.sp,
          fontWeight = FontWeight.ExtraBold
        )
        Text(
          text = "To keep Ahri helpful rather than irritating, we introduce three guardrails:",
          color = Color(0xFFCBD5E1),
          fontSize = 13.sp,
          fontWeight = FontWeight.SemiBold
        )
      }
      Box(
        modifier = Modifier
          .clip(RoundedCornerShape(50))
          .background(NeonPurple.copy(alpha = 0.2f))
          .border(1.dp, NeonPurple, RoundedCornerShape(50))
          .padding(horizontal = 10.dp, vertical = 5.dp)
      ) {
        Text(
          text = if (smartGuard.isCooldownActive) {
            "Cooldown (${smartGuard.cooldownRemainingMinutes}m)"
          } else {
            "Check ${smartGuard.consecutiveHighRamChecks}/${smartGuard.requiredConsecutiveChecks}"
          },
          color = TextPrimary,
          fontSize = 11.sp,
          fontWeight = FontWeight.Bold,
          modifier = Modifier.testTag("smart_guard_status_badge")
        )
      }
    }

    // Live Ahri (Gemini) AI Insight Bubble
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(12.dp))
        .background(WindowNavyBg)
        .border(1.dp, FrostedCardBorder, RoundedCornerShape(12.dp))
        .padding(12.dp)
    ) {
      Text(
        text = smartGuard.geminiInsightText,
        color = NeonCyan,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.testTag("gemini_insight_text")
      )
    }

    // Rule 1: Cooldown Window
    SmartGuardRuleRow(
      number = "1.",
      title = "Cooldown Window:",
      body = "Once Ahri suggests or triggers a purge, silence notifications for at least 45 to 60 minutes, unless RAM spikes into a critical danger threshold (>92%).",
      statusPill = if (smartGuard.isCooldownActive) {
        "Silenced (${smartGuard.cooldownRemainingMinutes}m left)"
      } else {
        "Window Ready (${smartGuard.cooldownWindowMinutes}m)"
      }
    )

    // Rule 2: Sustained Pressure Check
    SmartGuardRuleRow(
      number = "2.",
      title = "Sustained Pressure Check:",
      body = "Don't trigger on a transient 2-second spike (like opening a heavy tab). Trigger only if RAM stays ≥ 82% for 3 consecutive polling checks (~15–20 seconds).",
      statusPill = "Consecutive ≥82% Checks: ${smartGuard.consecutiveHighRamChecks}/${smartGuard.requiredConsecutiveChecks}"
    )

    // Rule 3: Smart Notification Action
    SmartGuardRuleRow(
      number = "3.",
      title = "Smart Notification Action:",
      body = "Instead of forcing a blocking modal over your screen, send a quiet heads-up notification with a 1-tap \"Purge Now\" action button.",
      statusPill = if (smartGuard.shouldShowQuietHeadsUp) "Quiet Heads-Up Active" else "Zero Blocking Modals"
    )

    // Interactive Guardrail Verification Controls
    FlowRow(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      OutlinedButton(
        onClick = onSimulateSustainedPoll,
        shape = RoundedCornerShape(50),
        modifier = Modifier.testTag("simulate_sustained_poll_button")
      ) {
        Text(
          text = "Simulate 85% Poll (${smartGuard.consecutiveHighRamChecks}/${smartGuard.requiredConsecutiveChecks})",
          color = NeonCyan,
          fontSize = 11.sp
        )
      }

      OutlinedButton(
        onClick = onSimulateCriticalSpike,
        shape = RoundedCornerShape(50),
        modifier = Modifier.testTag("simulate_critical_spike_button")
      ) {
        Text(
          text = "Simulate >92% Critical Spike",
          color = RoseDanger,
          fontSize = 11.sp
        )
      }

      OutlinedButton(
        onClick = onAskGemini,
        shape = RoundedCornerShape(50),
        modifier = Modifier.testTag("ask_gemini_button")
      ) {
        Text(
          text = "✦ Ask Ahri (Gemini)",
          color = NeonPurple,
          fontSize = 11.sp
        )
      }
    }
  }
}

@Composable
private fun SmartGuardRuleRow(
  number: String,
  title: String,
  body: String,
  statusPill: String
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(12.dp))
      .background(Color(0xFF1E2738))
      .padding(12.dp),
    horizontalArrangement = Arrangement.spacedBy(10.dp),
    verticalAlignment = Alignment.Top
  ) {
    Text(
      text = number,
      color = TextPrimary,
      fontSize = 14.sp,
      fontWeight = FontWeight.ExtraBold
    )
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = "$title $body",
        color = Color(0xFFE2E8F0),
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium
      )
      Spacer(modifier = Modifier.height(4.dp))
      Text(
        text = statusPill,
        color = NeonCyan,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold
      )
    }
  }
}

@Composable
private fun NeonRamArcGauge(
  ramPercent: Int,
  usedRamFormatted: String,
  totalRamFormatted: String
) {
  Box(
    contentAlignment = Alignment.Center,
    modifier = Modifier.size(210.dp)
  ) {
    Canvas(modifier = Modifier.size(188.dp)) {
      val strokeWidth = 14.dp.toPx()
      val glowStrokeWidth = 24.dp.toPx()
      val startAngle = 135f
      val fullSweep = 270f
      val arcBrush = Brush.linearGradient(
        colors = listOf(NeonPurple, ElectricBlue, NeonCyan),
        start = Offset(0f, size.height),
        end = Offset(size.width, 0f)
      )

      drawArc(
        brush = Brush.linearGradient(
          colors = listOf(
            NeonPurple.copy(alpha = 0.24f),
            ElectricBlue.copy(alpha = 0.24f),
            NeonCyan.copy(alpha = 0.24f)
          ),
          start = Offset(0f, size.height),
          end = Offset(size.width, 0f)
        ),
        startAngle = startAngle,
        sweepAngle = fullSweep,
        useCenter = false,
        style = Stroke(width = glowStrokeWidth, cap = StrokeCap.Round)
      )

      drawArc(
        brush = arcBrush,
        startAngle = startAngle,
        sweepAngle = fullSweep,
        useCenter = false,
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
      )

      val clampedPct = ramPercent.coerceIn(5, 95) / 100f
      val indicatorAngleDeg = startAngle + (fullSweep * clampedPct)
      val indicatorAngleRad = Math.toRadians(indicatorAngleDeg.toDouble())
      val radius = size.minDimension / 2f
      val cx = (size.width / 2f) + (radius * cos(indicatorAngleRad)).toFloat()
      val cy = (size.height / 2f) + (radius * sin(indicatorAngleRad)).toFloat()

      drawCircle(
        color = Color.White.copy(alpha = 0.35f),
        radius = 10.dp.toPx(),
        center = Offset(cx, cy)
      )
      drawCircle(
        color = Color.White,
        radius = 6.dp.toPx(),
        center = Offset(cx, cy)
      )
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      Text(
        text = "$ramPercent%",
        color = TextPrimary,
        fontSize = 44.sp,
        fontWeight = FontWeight.ExtraBold,
        modifier = Modifier.testTag("ram_percent_label")
      )
      Text(
        text = "RAM in Use",
        color = TextSecondary,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium
      )
      Spacer(modifier = Modifier.height(2.dp))
      Text(
        text = "$usedRamFormatted of $totalRamFormatted",
        color = TextMuted,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium
      )
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PurgeAndCleanPillButtonsRow(
  isCleaning: Boolean,
  isGuardrailsOpen: Boolean,
  onPurgeRam: () -> Unit,
  onQuickCacheClean: () -> Unit,
  onAskGeminiConnectGuardrails: () -> Unit
) {
  FlowRow(
    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
    verticalArrangement = Arrangement.spacedBy(10.dp)
  ) {
    Box(
      modifier = Modifier
        .clip(RoundedCornerShape(50))
        .background(NeonPurple.copy(alpha = 0.12f))
        .border(1.5.dp, NeonPurple, RoundedCornerShape(50))
        .clickable(enabled = !isCleaning) { onPurgeRam() }
        .padding(horizontal = 20.dp, vertical = 11.dp)
        .testTag("purge_ram_button"),
      contentAlignment = Alignment.Center
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Text(text = "✦", color = NeonPurple, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text(
          text = "Purge RAM",
          color = TextPrimary,
          fontSize = 13.sp,
          fontWeight = FontWeight.SemiBold
        )
      }
    }

    Box(
      modifier = Modifier
        .clip(RoundedCornerShape(50))
        .background(NeonCyan.copy(alpha = 0.12f))
        .border(1.5.dp, NeonCyan, RoundedCornerShape(50))
        .clickable(enabled = !isCleaning) { onQuickCacheClean() }
        .padding(horizontal = 20.dp, vertical = 11.dp)
        .testTag("clean_cache_now_button"),
      contentAlignment = Alignment.Center
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Text(text = "🧹", color = NeonCyan, fontSize = 13.sp)
        Text(
          text = "Quick Cache Clean",
          color = TextPrimary,
          fontSize = 13.sp,
          fontWeight = FontWeight.SemiBold
        )
      }
    }

    // 3rd Glowing Pill Button: "✦ Ask Gemini" connecting directly to the Smart Guard Guardrails
    Box(
      modifier = Modifier
        .clip(RoundedCornerShape(50))
        .background(
          Brush.horizontalGradient(
            colors = listOf(
              ElectricBlue.copy(alpha = if (isGuardrailsOpen) 0.24f else 0.14f),
              NeonPurple.copy(alpha = if (isGuardrailsOpen) 0.24f else 0.14f)
            )
          )
        )
        .border(
          width = 1.5.dp,
          brush = Brush.horizontalGradient(colors = listOf(ElectricBlue, NeonPurple)),
          shape = RoundedCornerShape(50)
        )
        .clickable { onAskGeminiConnectGuardrails() }
        .padding(horizontal = 20.dp, vertical = 11.dp)
        .testTag("ask_gemini_pill_button"),
      contentAlignment = Alignment.Center
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Text(text = "✦", color = ElectricBlue, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
        Text(
          text = "Ask Gemini",
          color = TextPrimary,
          fontSize = 13.sp,
          fontWeight = FontWeight.SemiBold
        )
      }
    }
  }
}

@Composable
private fun StorageStatusFrostedCard(
  systemBytes: Long,
  appsBytes: Long,
  cacheBytes: Long,
  freeBytes: Long,
  modifier: Modifier = Modifier
) {
  val total = (systemBytes + appsBytes + cacheBytes + freeBytes).coerceAtLeast(1L).toFloat()
  val sysWeight = (systemBytes / total).coerceAtLeast(0.12f)
  val appsWeight = (appsBytes / total).coerceAtLeast(0.18f)
  val cacheWeight = (cacheBytes / total).coerceIn(0.06f, 0.25f)
  val freeWeight = (freeBytes / total).coerceAtLeast(0.25f)

  Column(
    modifier = modifier
      .clip(RoundedCornerShape(18.dp))
      .background(FrostedCardBg)
      .border(1.dp, FrostedCardBorder, RoundedCornerShape(18.dp))
      .padding(22.dp)
      .testTag("storage_status_card"),
    verticalArrangement = Arrangement.spacedBy(18.dp)
  ) {
    Text(
      text = "Storage Status",
      color = TextPrimary,
      fontSize = 17.sp,
      fontWeight = FontWeight.Bold
    )

    Row(
      modifier = Modifier
        .fillMaxWidth()
        .height(28.dp)
        .clip(RoundedCornerShape(8.dp))
    ) {
      Box(
        modifier = Modifier
          .weight(sysWeight)
          .fillMaxHeight()
          .background(SegSystemBlue)
      )
      Box(
        modifier = Modifier
          .weight(appsWeight)
          .fillMaxHeight()
          .background(SegAppsIndigo)
      )
      Box(
        modifier = Modifier
          .weight(cacheWeight)
          .fillMaxHeight()
          .background(SegCacheCyan)
      )
      Box(
        modifier = Modifier
          .weight(freeWeight)
          .fillMaxHeight()
          .background(SegFreeGray)
      )
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        StorageLegendDotItem(
          dotColor = SegSystemBlue,
          label = "System (${ByteFormatter.formatBytes(systemBytes)})",
          modifier = Modifier.weight(1f)
        )
        StorageLegendDotItem(
          dotColor = SegAppsIndigo,
          label = "Apps (${ByteFormatter.formatBytes(appsBytes)})",
          modifier = Modifier.weight(1f)
        )
      }
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        StorageLegendDotItem(
          dotColor = SegCacheCyan,
          label = "Cache (${ByteFormatter.formatBytes(cacheBytes)})",
          modifier = Modifier
            .weight(1f)
            .testTag("reclaimable_bytes_label")
        )
        StorageLegendDotItem(
          dotColor = SegFreeGray,
          label = "Free (${ByteFormatter.formatBytes(freeBytes)})",
          modifier = Modifier.weight(1f)
        )
      }
    }
  }
}

@Composable
private fun StorageLegendDotItem(
  dotColor: Color,
  label: String,
  modifier: Modifier = Modifier
) {
  Row(
    modifier = modifier,
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    Box(
      modifier = Modifier
        .size(8.dp)
        .clip(CircleShape)
        .background(dotColor)
    )
    Text(
      text = label,
      color = TextSecondary,
      fontSize = 12.sp,
      fontWeight = FontWeight.Medium,
      maxLines = 1
    )
  }
}

@Composable
private fun EqualizerWaveformBars(modifier: Modifier = Modifier) {
  val heightsDp = listOf(5, 7, 10, 6, 12, 8, 16, 22, 18, 10, 7, 11, 9, 14, 17, 19, 12, 8)
  Row(
    modifier = modifier,
    horizontalArrangement = Arrangement.spacedBy(3.dp),
    verticalAlignment = Alignment.Bottom
  ) {
    heightsDp.forEachIndexed { idx, h ->
      val barColor = when {
        idx < 6 -> NeonPurple
        idx in 6..10 -> NeonCyan
        else -> Color(0xFF475569)
      }
      Box(
        modifier = Modifier
          .width(3.dp)
          .height(h.dp)
          .clip(RoundedCornerShape(2.dp))
          .background(barColor)
      )
    }
  }
}

@Composable
private fun SeparateMediaDetectorCard(
  mediaItems: List<MediaCandidateItem>,
  selectedMediaIds: Set<String>,
  selectedMediaBytes: Long,
  lastMediaCleanResult: com.droid.cachecleaner.model.MediaCleanResult?,
  onToggleMediaItem: (String) -> Unit,
  onCleanSelectedMedia: () -> Unit
) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(18.dp))
      .background(FrostedCardBg)
      .border(1.dp, AmberAccent.copy(alpha = 0.6f), RoundedCornerShape(18.dp))
      .padding(16.dp)
      .testTag("duplicate_media_section"),
    verticalArrangement = Arrangement.spacedBy(10.dp)
  ) {
    Column(modifier = Modifier.fillMaxWidth()) {
      Text(
        text = "SEPARATE REVIEW • EXCLUDED FROM QUICK CACHE CLEAN",
        color = AmberAccent,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold
      )
      Text(
        text = "Large & Duplicate Media Detector",
        color = TextPrimary,
        fontSize = 17.sp,
        fontWeight = FontWeight.ExtraBold
      )
      Text(
        text = "SHA-256 content-verified duplicates & large media files (>1 MB). Protected from One-Click sweeps.",
        color = TextSecondary,
        fontSize = 12.sp
      )
    }

    lastMediaCleanResult?.let { res ->
      Text(
        text = "Media Cleanup removed ${res.filesRemoved} files (${ByteFormatter.formatBytes(res.bytesFreed)})",
        color = NeonCyan,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.testTag("media_clean_summary")
      )
    }

    if (mediaItems.isEmpty()) {
      Text(
        text = "No duplicate or large media files detected.",
        color = TextSecondary,
        fontSize = 13.sp
      )
    } else {
      mediaItems.forEach { item ->
        val isChecked = item.id in selectedMediaIds
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(WindowNavyBg)
            .border(
              1.dp,
              if (isChecked) AmberAccent.copy(alpha = 0.7f) else FrostedCardBorder,
              RoundedCornerShape(10.dp)
            )
            .clickable { onToggleMediaItem(item.id) }
            .padding(10.dp)
            .testTag("media_item_${item.fileName}"),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Checkbox(
            checked = isChecked,
            onCheckedChange = { onToggleMediaItem(item.id) },
            colors = CheckboxDefaults.colors(
              checkedColor = AmberAccent,
              uncheckedColor = TextSecondary
            )
          )
          Spacer(modifier = Modifier.width(6.dp))
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = item.fileName,
              color = TextPrimary,
              fontSize = 13.sp,
              fontWeight = FontWeight.Bold
            )
            val subtitle = if (item.kind == MediaCandidateKind.DUPLICATE_MEDIA) {
              "${item.kind.label} of ${item.duplicateOf} • sha256:${item.sha256Hash}"
            } else {
              "${item.kind.label} • sha256:${item.sha256Hash}"
            }
            Text(
              text = subtitle,
              color = if (item.kind == MediaCandidateKind.DUPLICATE_MEDIA) AmberAccent else ElectricBlue,
              fontSize = 11.sp
            )
          }
          Text(
            text = ByteFormatter.formatBytes(item.sizeBytes),
            color = TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
          )
        }
      }

      Button(
        onClick = onCleanSelectedMedia,
        enabled = selectedMediaIds.isNotEmpty(),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
          containerColor = AmberAccent,
          contentColor = Color(0xFF1E1B18),
          disabledContainerColor = Color(0xFF1E293B),
          disabledContentColor = TextSecondary
        ),
        modifier = Modifier
          .fillMaxWidth()
          .height(48.dp)
          .testTag("clean_media_button")
      ) {
        Text(
          text = "Clean Selected Duplicates & Large Media (${ByteFormatter.formatBytes(selectedMediaBytes)})",
          fontSize = 13.sp,
          fontWeight = FontWeight.ExtraBold
        )
      }
    }
  }
}

@Composable
private fun CategoryCard(
  item: CacheCategoryItem,
  isSelected: Boolean,
  onToggle: () -> Unit
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(14.dp))
      .background(FrostedCardBg)
      .border(
        width = 1.dp,
        color = if (isSelected) NeonCyan.copy(alpha = 0.6f) else FrostedCardBorder,
        shape = RoundedCornerShape(14.dp)
      )
      .clickable { onToggle() }
      .padding(14.dp)
      .testTag("category_card_${item.id.name}"),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Checkbox(
      checked = isSelected,
      onCheckedChange = { onToggle() },
      colors = CheckboxDefaults.colors(
        checkedColor = NeonCyan,
        uncheckedColor = TextSecondary
      )
    )
    Spacer(modifier = Modifier.width(8.dp))
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = item.id.title,
        color = TextPrimary,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold
      )
      Text(
        text = item.id.subtitle,
        color = TextSecondary,
        fontSize = 12.sp
      )
    }
    Spacer(modifier = Modifier.width(8.dp))
    Column(horizontalAlignment = Alignment.End) {
      Text(
        text = ByteFormatter.formatBytes(item.bytes),
        color = if (item.bytes > 0L) NeonCyan else TextSecondary,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold
      )
      Text(
        text = "${item.fileCount} items",
        color = TextSecondary,
        fontSize = 11.sp
      )
    }
  }
}

@Composable
private fun InstalledAppCacheRow(
  app: AppCacheEntry,
  onSweepApp: () -> Unit,
  onOpenAppSettings: () -> Unit
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(12.dp))
      .background(FrostedCardBg)
      .border(1.dp, FrostedCardBorder, RoundedCornerShape(12.dp))
      .padding(horizontal = 14.dp, vertical = 12.dp)
      .testTag("app_row_${app.packageName}"),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = app.appName,
        color = TextPrimary,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold
      )
      Text(
        text = app.packageName,
        color = TextSecondary,
        fontSize = 11.sp
      )
      Text(
        text = "${ByteFormatter.formatBytes(app.cacheBytes)} • ${app.fileCount} cached files",
        color = if (app.cacheBytes > 0L) NeonCyan else TextSecondary,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold
      )
    }

    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      OutlinedButton(
        onClick = onSweepApp,
        enabled = app.cacheBytes > 0L,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.testTag("sweep_app_${app.packageName}")
      ) {
        Text(
          text = "Clean",
          color = if (app.cacheBytes > 0L) NeonCyan else TextSecondary,
          fontSize = 11.sp,
          fontWeight = FontWeight.Bold
        )
      }
      OutlinedButton(
        onClick = onOpenAppSettings,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.testTag("settings_app_${app.packageName}")
      ) {
        Text(
          text = "OS Info",
          color = ElectricBlue,
          fontSize = 11.sp
        )
      }
    }
  }
}
