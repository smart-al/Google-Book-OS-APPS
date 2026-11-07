/**
 * Google Book Purge Lite — Powered by Google Gemini 3.8 Flash ("Ahri" Smart Guard)
 * Implements:
 *   - 260° Neon RAM Arc Gauge + "Purge RAM", "Quick Cache Clean", and "✦ Ask Gemini" Pill Buttons
 *   - "✦ Ask Gemini" Pill Button connects directly to the Smart Guard Guardrails panel & queries Ahri
 *   - "⚙ Settings" Pill Button cleanly closes ALL open settings & guardrails panels when clicked
 *   - 4-Segment Storage Status Card (System, Apps, Cache, Free)
 *   - The Anti-Annoyance Strategy ("Smart Guard" Rules)
 */

const ARC_RADIUS = 102;
const ARC_CENTER_X = 140;
const ARC_CENTER_Y = 140;
const ARC_CIRCUMFERENCE = 2 * Math.PI * ARC_RADIUS; // 640.88
const ARC_SWEEP_DEG = 260;
const ARC_START_DEG = 130;
const ARC_MAX_LENGTH = (ARC_SWEEP_DEG / 360) * ARC_CIRCUMFERENCE; // 462.86

const ALL_ONE_CLICK_CATEGORIES = [
  "google_play_services",
  "browser_cache",
  "youtube_cache",
  "all_installed_apps",
  "orphan_and_obsolete_apks",
  "background_process_trimmer",
  "ram_purge",
  "system_temp_logs",
];

const state = {
  selectedCategories: new Set(ALL_ONE_CLICK_CATEGORIES),
  selectedPackages: new Set(),
  selectedMediaPaths: new Set(),
  packagesInitialized: false,
  mediaInitialized: false,
  searchQuery: "",
  isCleaning: false,
  lastStatus: null,
};

function formatBytes(bytes) {
  if (!Number.isFinite(bytes) || bytes <= 0) return "0 B";
  const units = ["B", "KB", "MB", "GB", "TB"];
  const idx = Math.min(Math.floor(Math.log(bytes) / Math.log(1024)), units.length - 1);
  const val = bytes / Math.pow(1024, idx);
  return `${val.toFixed(idx === 0 ? 0 : 1)} ${units[idx]}`;
}

function formatGB(bytes) {
  if (!Number.isFinite(bytes) || bytes <= 0) return "0 GB";
  const gb = bytes / Math.pow(1024, 3);
  if (gb >= 10) return `${gb.toFixed(1)} GB`;
  if (gb >= 0.1) return `${gb.toFixed(2)} GB`;
  const mb = bytes / Math.pow(1024, 2);
  return `${mb.toFixed(1)} MB`;
}

function setArcGaugePercent(percent) {
  const clamped = Math.max(0, Math.min(100, percent));
  const arcFill = document.getElementById("storageGaugeArc");
  if (arcFill) {
    const filledLen = (clamped / 100) * ARC_MAX_LENGTH;
    arcFill.setAttribute("stroke-dasharray", `${filledLen.toFixed(2)} ${ARC_CIRCUMFERENCE.toFixed(2)}`);
  }

  const thumb = document.getElementById("arcThumbIndicator");
  if (thumb) {
    const angleDeg = ARC_START_DEG + (clamped / 100) * ARC_SWEEP_DEG;
    const angleRad = (angleDeg * Math.PI) / 180;
    const cx = ARC_CENTER_X + ARC_RADIUS * Math.cos(angleRad);
    const cy = ARC_CENTER_Y + ARC_RADIUS * Math.sin(angleRad);
    thumb.setAttribute("cx", cx.toFixed(2));
    thumb.setAttribute("cy", cy.toFixed(2));
  }
}

async function clearBrowserSideCaches() {
  let clearedItems = 0;

  if ("caches" in window) {
    try {
      const keys = await window.caches.keys();
      for (const key of keys) {
        await window.caches.delete(key);
        clearedItems++;
      }
    } catch (_) {}
  }

  try {
    clearedItems += sessionStorage.length;
    sessionStorage.clear();
    const toRemove = [];
    for (let i = 0; i < localStorage.length; i++) {
      const k = localStorage.key(i);
      if (k && (k.includes("cache") || k.includes("temp") || k.includes("tmp") || k.includes("yt"))) {
        toRemove.push(k);
      }
    }
    toRemove.forEach((k) => {
      localStorage.removeItem(k);
      clearedItems++;
    });
  } catch (_) {}

  if (window.indexedDB && typeof window.indexedDB.databases === "function") {
    try {
      const dbs = await window.indexedDB.databases();
      for (const db of dbs) {
        if (db.name && (db.name.includes("cache") || db.name.includes("temp"))) {
          window.indexedDB.deleteDatabase(db.name);
          clearedItems++;
        }
      }
    } catch (_) {}
  }

  const badge = document.getElementById("browserCacheStatusBadge");
  if (badge) {
    badge.textContent = `Cleaned (${clearedItems} web stores)`;
  }
  return clearedItems;
}

function updateSmartGuardUI(smartGuard) {
  if (!smartGuard) return;

  const cooldownBadge = document.getElementById("sgCooldownBadge");
  const sustainedBadge = document.getElementById("sgSustainedBadge");
  const notifBadge = document.getElementById("sgNotificationBadge");
  const adviceEl = document.getElementById("ahriAdviceText");
  const headsUpBanner = document.getElementById("smartGuardHeadsUp");
  const headsUpTitle = document.getElementById("headsUpTitle");
  const headsUpReason = document.getElementById("headsUpReason");

  if (cooldownBadge) {
    if (smartGuard.cooldownOverrideActive) {
      cooldownBadge.textContent = "CRITICAL >92% OVERRIDE!";
    } else if (smartGuard.isCooldownActive) {
      cooldownBadge.textContent = `Silenced (${smartGuard.cooldownRemainingMinutes}m left)`;
    } else {
      cooldownBadge.textContent = "Ready (45–60m Guardrail)";
    }
  }

  if (sustainedBadge) {
    const c = smartGuard.consecutiveHighRamChecks || 0;
    const req = smartGuard.rules?.requiredConsecutiveChecks || 3;
    sustainedBadge.textContent = `${c} / ${req} Checks (>=82%)`;
  }

  if (notifBadge) {
    notifBadge.textContent = smartGuard.shouldShowQuietHeadsUp
      ? "1-Tap Heads-Up Active"
      : "Quiet (No Modal)";
  }

  if (adviceEl && smartGuard.ahriAdvice) {
    adviceEl.textContent = smartGuard.ahriAdvice;
  }

  if (headsUpBanner) {
    if (smartGuard.shouldShowQuietHeadsUp) {
      headsUpBanner.classList.remove("hidden");
      if (headsUpTitle) {
        headsUpTitle.textContent = smartGuard.cooldownOverrideActive
          ? `Ahri Smart Guard • Critical Danger (>92% Cooldown Override)`
          : `Ahri Smart Guard • Sustained RAM Pressure (>=82% for ${smartGuard.consecutiveHighRamChecks} checks)`;
      }
      if (headsUpReason) {
        headsUpReason.textContent = smartGuard.triggerReason;
      }
    } else {
      headsUpBanner.classList.add("hidden");
    }
  }
}

function badgeClassForCategory(catId) {
  if (catId === "google_play_services") return "badge-gms";
  if (catId === "browser_cache") return "badge-browser";
  if (catId === "youtube_cache") return "badge-youtube";
  return "badge-app";
}

function renderCategories(categories) {
  const container = document.getElementById("categoriesList");
  if (!container) return;
  container.innerHTML = "";

  categories.forEach((cat) => {
    const isChecked = state.selectedCategories.has(cat.id);
    const item = document.createElement("div");
    item.className = "category-item";

    item.innerHTML = `
      <div class="category-top">
        <div class="cat-check-row">
          <input type="checkbox" id="cat_${cat.id}" data-cat-id="${cat.id}" ${isChecked ? "checked" : ""} />
          <label for="cat_${cat.id}"><strong>${cat.name}</strong></label>
        </div>
        <div class="cat-meta-badges">
          <span class="cat-count-badge">${cat.fileCount} items</span>
          <span class="cat-size-badge">${formatBytes(cat.sizeBytes)}</span>
        </div>
      </div>
      <p class="cat-desc">${cat.description}</p>
    `;

    const checkbox = item.querySelector("input[type='checkbox']");
    checkbox.addEventListener("change", (e) => {
      if (e.target.checked) {
        state.selectedCategories.add(cat.id);
        const apps = (state.lastStatus?.cache?.installedApps || []).filter((a) => a.category === cat.id);
        apps.forEach((a) => state.selectedPackages.add(a.packageName));
      } else {
        state.selectedCategories.delete(cat.id);
        const apps = (state.lastStatus?.cache?.installedApps || []).filter((a) => a.category === cat.id);
        apps.forEach((a) => state.selectedPackages.delete(a.packageName));
      }
      renderInstalledApps(state.lastStatus?.cache?.installedApps || []);
      updateSelectedReclaimableBadge();
    });

    container.appendChild(item);
  });
}

function renderInstalledApps(apps) {
  const grid = document.getElementById("installedAppsGrid");
  if (!grid) return;
  grid.innerHTML = "";

  const query = state.searchQuery.trim().toLowerCase();
  const filtered = apps.filter((app) => {
    if (!query) return true;
    return (
      app.appName.toLowerCase().includes(query) ||
      app.packageName.toLowerCase().includes(query) ||
      (app.badge && app.badge.toLowerCase().includes(query))
    );
  });

  filtered.forEach((app) => {
    const isChecked = state.selectedPackages.has(app.packageName);
    const card = document.createElement("div");
    card.className = "app-card";
    const bClass = badgeClassForCategory(app.category);

    card.innerHTML = `
      <div>
        <div class="app-card-top">
          <div class="app-title-group">
            <input type="checkbox" id="pkg_${app.packageName}" data-pkg="${app.packageName}" ${isChecked ? "checked" : ""} />
            <label for="pkg_${app.packageName}">
              <span class="app-name">${app.appName}</span>
              <span class="app-pkg">${app.packageName}</span>
            </label>
          </div>
          <span class="app-badge ${bClass}">${app.badge}</span>
        </div>
        <p class="app-desc">${app.description}</p>
      </div>
      <div class="app-card-footer">
        <span class="cat-count-badge">${app.fileCount} cache files</span>
        <span class="cat-size-badge">${formatBytes(app.cacheBytes)}</span>
      </div>
    `;

    const cb = card.querySelector("input[type='checkbox']");
    cb.addEventListener("change", (e) => {
      if (e.target.checked) {
        state.selectedPackages.add(app.packageName);
        state.selectedCategories.add(app.category);
      } else {
        state.selectedPackages.delete(app.packageName);
      }
      updateSelectedReclaimableBadge();
    });

    grid.appendChild(card);
  });
}

function renderMediaDetector(mediaDetector) {
  const container = document.getElementById("mediaItemsList");
  if (!container || !mediaDetector) return;
  container.innerHTML = "";

  const allItems = [
    ...(mediaDetector.duplicateFiles || []),
    ...(mediaDetector.largeFiles || []),
  ];

  if (!state.mediaInitialized) {
    (mediaDetector.duplicateFiles || []).forEach((d) => state.selectedMediaPaths.add(d.path));
    (mediaDetector.largeFiles || []).forEach((l) => state.selectedMediaPaths.add(l.path));
    state.mediaInitialized = true;
  } else {
    const validPaths = new Set(allItems.map((i) => i.path));
    Array.from(state.selectedMediaPaths).forEach((p) => {
      if (!validPaths.has(p)) state.selectedMediaPaths.delete(p);
    });
  }

  if (allItems.length === 0) {
    container.innerHTML = `<p class="card-subtitle">No duplicate or large media files flagged. Click "+ Seed Test Caches &amp; Duplicate Media" to test.</p>`;
    updateMediaReclaimableBadge(allItems);
    return;
  }

  allItems.forEach((item, idx) => {
    const isChecked = state.selectedMediaPaths.has(item.path);
    const card = document.createElement("div");
    card.className = "media-card";

    const isDup = item.kind === "duplicate";
    const badgeLabel = isDup ? `SHA-256 Duplicate #${item.duplicateIndex}` : "Large Media (&ge;1 MB)";
    const badgeCls = isDup ? "badge-dup" : "badge-large";
    const detailLine = isDup
      ? `Identical SHA-256 (<code>${item.sha256}...</code>) to kept original <strong>${item.originalName}</strong>`
      : `Large media file • SHA-256: <code>${item.sha256}...</code>`;

    card.innerHTML = `
      <div>
        <div class="app-card-top">
          <div class="app-title-group">
            <input type="checkbox" id="media_${idx}" data-path="${item.path}" ${isChecked ? "checked" : ""} />
            <label for="media_${idx}">
              <span class="app-name">${item.name}</span>
              <span class="app-pkg">${item.path}</span>
            </label>
          </div>
          <span class="app-badge ${badgeCls}">${badgeLabel}</span>
        </div>
        <p class="app-desc">${detailLine}</p>
      </div>
      <div class="app-card-footer">
        <span class="cat-count-badge">${isDup ? "Safe to remove copy" : "Manual review"}</span>
        <span class="cat-size-badge">${formatBytes(item.sizeBytes)}</span>
      </div>
    `;

    const cb = card.querySelector("input[type='checkbox']");
    cb.addEventListener("change", (e) => {
      if (e.target.checked) {
        state.selectedMediaPaths.add(item.path);
      } else {
        state.selectedMediaPaths.delete(item.path);
      }
      updateMediaReclaimableBadge(allItems);
    });

    container.appendChild(card);
  });

  updateMediaReclaimableBadge(allItems);
}

function updateMediaReclaimableBadge(allItems) {
  let bytes = 0;
  let count = 0;
  allItems.forEach((item) => {
    if (state.selectedMediaPaths.has(item.path)) {
      bytes += item.sizeBytes;
      count++;
    }
  });
  const badge = document.getElementById("mediaReclaimableBadge");
  if (badge) {
    badge.textContent = `${formatBytes(bytes)} Selected (${count} files)`;
  }
  const btn = document.getElementById("cleanMediaBtn");
  if (btn) {
    btn.disabled = count === 0;
  }
}

function updateSelectedReclaimableBadge() {
  if (!state.lastStatus) return;
  const { cache } = state.lastStatus;
  const apps = cache.installedApps || [];
  let selectedBytes = 0;
  let selectedFiles = 0;

  apps.forEach((app) => {
    if (state.selectedPackages.has(app.packageName)) {
      selectedBytes += app.cacheBytes;
      selectedFiles += app.fileCount;
    }
  });

  const nonAppCatIds = [
    "orphan_and_obsolete_apks",
    "background_process_trimmer",
    "ram_purge",
    "system_temp_logs",
  ];
  (cache.categories || []).forEach((c) => {
    if (nonAppCatIds.includes(c.id) && state.selectedCategories.has(c.id)) {
      selectedBytes += c.sizeBytes;
      selectedFiles += c.fileCount;
    }
  });

  document.getElementById("reclaimableBadge").textContent = `${formatBytes(selectedBytes)} Reclaimable`;
  document.getElementById("totalFilesBadge").textContent = `${selectedFiles} items (${state.selectedPackages.size} apps)`;
}

function updateStorageSegmentedBar(storage, breakdown, reclaimableCacheBytes) {
  const b = breakdown || {
    systemBytes: Math.floor((storage.usedBytes || 0) * 0.4),
    appsBytes: Math.floor((storage.usedBytes || 0) * 0.6),
    cacheBytes: reclaimableCacheBytes || 0,
    freeBytes: storage.freeBytes || 0,
  };

  document.getElementById("legendSystemText").textContent = `System (${formatGB(b.systemBytes)})`;
  document.getElementById("legendAppsText").textContent = `Apps (${formatGB(b.appsBytes)})`;
  document.getElementById("legendCacheText").textContent = `Cache (${formatBytes(reclaimableCacheBytes)})`;
  document.getElementById("legendFreeText").textContent = `Free (${formatGB(b.freeBytes)})`;

  const total = Math.max(1, storage.totalBytes || 1);
  let sysPct = Math.max(8, (b.systemBytes / total) * 100);
  let appsPct = Math.max(12, (b.appsBytes / total) * 100);
  let cachePct = reclaimableCacheBytes > 0 ? Math.max(5, (reclaimableCacheBytes / total) * 100) : 0.5;
  let freePct = Math.max(15, 100 - (sysPct + appsPct + cachePct));

  document.getElementById("segSystem").style.width = `${sysPct.toFixed(1)}%`;
  document.getElementById("segApps").style.width = `${appsPct.toFixed(1)}%`;
  document.getElementById("segCache").style.width = `${cachePct.toFixed(1)}%`;
  document.getElementById("segFree").style.width = `${freePct.toFixed(1)}%`;
}

function updateUIWithStatus(data) {
  state.lastStatus = data;
  const { storage, storageBreakdown, ram, cache, mediaDetector, smartGuard } = data;

  const apps = cache.installedApps || [];
  if (!state.packagesInitialized) {
    apps.forEach((a) => state.selectedPackages.add(a.packageName));
    state.packagesInitialized = true;
  } else {
    apps.forEach((a) => {
      if (state.selectedCategories.has(a.category)) {
        state.selectedPackages.add(a.packageName);
      }
    });
  }

  if (ram) {
    const displayedPct = smartGuard?.lastEvaluatedRamPercent
      ? Math.round(smartGuard.lastEvaluatedRamPercent)
      : Math.round(ram.ramUsagePercent || 42);
    document.getElementById("ramPercentText").textContent = `${displayedPct}%`;
    document.getElementById("ramUsedOfTotalText").textContent =
      `${formatGB(ram.usedRamBytes)} of ${formatGB(ram.totalRamBytes)}`;
    setArcGaugePercent(displayedPct);
  }

  document.getElementById("mountPathBadge").textContent = storage.mountPath;
  document.getElementById("totalSpaceVal").textContent = formatBytes(storage.totalBytes);
  document.getElementById("usedSpaceVal").textContent = formatBytes(storage.usedBytes);
  document.getElementById("freeSpaceVal").textContent = formatBytes(storage.freeBytes);
  document.getElementById("usagePercentText").textContent = `${storage.usagePercent}%`;

  updateStorageSegmentedBar(storage, storageBreakdown, cache.totalReclaimableBytes);

  const activeBgCount = (apps.length || 11) + (ram?.activeBgServicesCount || 4);
  const dockCountEl = document.getElementById("dockBgAppsCount");
  if (dockCountEl) {
    dockCountEl.textContent = `${activeBgCount}`;
  }

  const countPill = document.getElementById("installedCountPill");
  if (countPill) {
    countPill.textContent = `${apps.length} Installed Apps`;
  }

  if (smartGuard) {
    updateSmartGuardUI(smartGuard);
  }

  renderCategories(cache.categories || []);
  renderInstalledApps(apps);
  renderMediaDetector(mediaDetector);
  updateSelectedReclaimableBadge();
}

async function fetchStatus() {
  const res = await fetch("/api/status");
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  const data = await res.json();
  updateUIWithStatus(data);
  return data;
}

async function performOneClickClean(mode = "all") {
  if (state.isCleaning) return;
  state.isCleaning = true;

  const cleanBtn = document.getElementById("oneClickCleanBtn");
  const purgeBtn = document.getElementById("oneClickPurgeRamBtn");
  const cleanTitle = document.getElementById("cleanBtnTitle");
  const purgeTitle = document.getElementById("purgeRamBtnTitle");
  const waveform = document.getElementById("dockWaveform");
  const statusText = document.getElementById("systemStatusText");

  cleanBtn.disabled = true;
  purgeBtn.disabled = true;
  if (waveform) waveform.classList.add("active");
  if (statusText) statusText.textContent = mode === "ram" ? "Purging RAM & Services..." : "Cleaning Cache & Purging...";

  if (mode === "ram") {
    purgeTitle.textContent = "Purging RAM...";
  } else {
    cleanTitle.textContent = "Cleaning...";
  }

  try {
    const selectedCategories = Array.from(state.selectedCategories);
    const selectedPackages = Array.from(state.selectedPackages);
    const res = await fetch("/api/clean", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        categories: selectedCategories,
        packages: selectedPackages,
      }),
    });
    const result = await res.json();

    let browserCleared = 0;
    const browserToggle = document.getElementById("cleanBrowserStorageToggle");
    if (browserToggle && browserToggle.checked) {
      browserCleared = await clearBrowserSideCaches();
    }

    updateUIWithStatus({
      storage: result.storage,
      storageBreakdown: result.storageBreakdown,
      ram: result.ram,
      cache: result.cache,
      mediaDetector: result.mediaDetector,
      smartGuard: result.smartGuard,
    });

    const banner = document.getElementById("cleanupBanner");
    const bannerTitle = document.getElementById("bannerTitle");
    const bannerDetails = document.getElementById("bannerDetails");
    const ramFreed = result.ramPurge?.ramBytesReclaimed || 0;
    bannerTitle.textContent = `Freed ${formatBytes(result.bytesFreed)} + ${formatBytes(ramFreed)} RAM`;
    bannerDetails.textContent = `(${result.appsCleanedCount} apps, ${result.orphansRemovedCount} orphans, ${result.processesTrimmedCount} services trimmed • 50m Smart Guard Cooldown Active)`;
    banner.classList.remove("hidden");

    if (statusText) statusText.textContent = "All Systems Normal";
  } catch (err) {
    alert(`Operation failed: ${err.message}`);
  } finally {
    state.isCleaning = false;
    cleanBtn.disabled = false;
    purgeBtn.disabled = false;
    cleanTitle.textContent = "Quick Cache Clean";
    purgeTitle.textContent = "Purge RAM";
    if (waveform) waveform.classList.remove("active");
  }
}

async function sendSmartGuardPoll(simulatedRamPercent) {
  const res = await fetch("/api/smart-guard/poll", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ simulatedRamPercent }),
  });
  const data = await res.json();
  if (data.smartGuard) {
    updateSmartGuardUI(data.smartGuard);
    const pct = Math.round(data.evaluatedRamPercent || 42);
    document.getElementById("ramPercentText").textContent = `${pct}%`;
    setArcGaugePercent(pct);
  }
  return data;
}

async function simulateSustainedPressure() {
  await sendSmartGuardPoll(84.0);
  await sendSmartGuardPoll(85.0);
  await sendSmartGuardPoll(86.0);
}

async function dismissSmartGuardHeadsUp() {
  const res = await fetch("/api/smart-guard/dismiss", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({}),
  });
  const data = await res.json();
  if (data.smartGuard) updateSmartGuardUI(data.smartGuard);
}

async function resetSmartGuardState() {
  const res = await fetch("/api/smart-guard/reset-cooldown", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({}),
  });
  const data = await res.json();
  if (data.smartGuard) updateSmartGuardUI(data.smartGuard);
  await fetchStatus();
}

async function askAhriGemini(customPrompt = null) {
  const inputEl = document.getElementById("ahriPromptInput");
  const askBtn = document.getElementById("askAhriBtn");
  const askPillTitle = document.getElementById("askGeminiPillTitle");
  const prompt = customPrompt !== null
    ? customPrompt
    : (inputEl && inputEl.value.trim() ? inputEl.value.trim() : "Evaluate my RAM pressure and Smart Guard guardrails.");

  if (askBtn) askBtn.disabled = true;
  if (askPillTitle) askPillTitle.textContent = "Asking Gemini...";
  try {
    const res = await fetch("/api/gemini/ask", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ prompt }),
    });
    const data = await res.json();
    if (data.smartGuard) {
      updateSmartGuardUI(data.smartGuard);
    }
    if (data.reply) {
      document.getElementById("ahriAdviceText").textContent = data.reply;
    }
  } finally {
    if (askBtn) askBtn.disabled = false;
    if (askPillTitle) askPillTitle.textContent = "Ask Gemini";
  }
}

async function performCleanMedia() {
  const paths = Array.from(state.selectedMediaPaths);
  if (paths.length === 0) return;

  const btn = document.getElementById("cleanMediaBtn");
  btn.disabled = true;
  try {
    const res = await fetch("/api/clean-media", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ paths }),
    });
    const result = await res.json();

    if (state.lastStatus) {
      state.lastStatus.storage = result.storage;
      state.lastStatus.mediaDetector = result.mediaDetector;
      updateUIWithStatus(state.lastStatus);
    }

    const banner = document.getElementById("cleanupBanner");
    document.getElementById("bannerTitle").textContent =
      `Media Cleaned — Freed ${formatBytes(result.mediaBytesFreed)}`;
    document.getElementById("bannerDetails").textContent =
      `Removed ${result.mediaFilesRemoved} selected duplicate/large media files in ${result.durationMs} ms.`;
    banner.classList.remove("hidden");
  } finally {
    btn.disabled = false;
  }
}

async function seedSampleCache() {
  const seedBtn = document.getElementById("seedCacheBtn");
  seedBtn.disabled = true;
  try {
    const res = await fetch("/api/seed-sample-cache", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({}),
    });
    const data = await res.json();
    state.selectedCategories = new Set(ALL_ONE_CLICK_CATEGORIES);
    (data.cache?.installedApps || []).forEach((a) => state.selectedPackages.add(a.packageName));
    state.mediaInitialized = false;
    updateUIWithStatus(data);
  } finally {
    seedBtn.disabled = false;
  }
}

function syncSettingsButtonState() {
  const sgSection = document.getElementById("smartGuardSection");
  const advDrawer = document.getElementById("advancedDrawer");
  const settingsBtn = document.getElementById("settingsToggleBtn");
  const settingsLabel = document.getElementById("settingsBtnLabel");
  const askPillBtn = document.getElementById("askGeminiPillBtn");

  const isSgOpen = sgSection && !sgSection.classList.contains("hidden");
  const isAdvOpen = advDrawer && !advDrawer.classList.contains("hidden");
  const anyOpen = isSgOpen || isAdvOpen;

  if (settingsBtn) {
    settingsBtn.setAttribute("aria-expanded", anyOpen ? "true" : "false");
    settingsBtn.classList.toggle("settings-open", anyOpen);
  }
  if (settingsLabel) {
    settingsLabel.textContent = anyOpen ? "Close Settings" : "Settings";
  }
  if (askPillBtn) {
    askPillBtn.classList.toggle("active-pill", Boolean(isSgOpen));
  }
}

function closeAllSettingsPanels() {
  const sgSection = document.getElementById("smartGuardSection");
  const advDrawer = document.getElementById("advancedDrawer");
  if (sgSection) sgSection.classList.add("hidden");
  if (advDrawer) advDrawer.classList.add("hidden");
  syncSettingsButtonState();
}

function handleSettingsPillClick() {
  const sgSection = document.getElementById("smartGuardSection");
  const advDrawer = document.getElementById("advancedDrawer");
  const anyOpen =
    (sgSection && !sgSection.classList.contains("hidden")) ||
    (advDrawer && !advDrawer.classList.contains("hidden"));

  if (anyOpen) {
    // User requirement: "Have the button close all the settings"
    closeAllSettingsPanels();
  } else {
    if (advDrawer) advDrawer.classList.remove("hidden");
    if (sgSection) sgSection.classList.remove("hidden");
    syncSettingsButtonState();
  }
}

async function openGuardrailsAndAskGemini() {
  const sgSection = document.getElementById("smartGuardSection");
  if (sgSection) {
    sgSection.classList.remove("hidden");
    syncSettingsButtonState();
    sgSection.scrollIntoView({ behavior: "smooth", block: "nearest" });
  }
  await askAhriGemini();
}

document.addEventListener("DOMContentLoaded", () => {
  document.getElementById("oneClickCleanBtn").addEventListener("click", () => performOneClickClean("all"));
  document.getElementById("oneClickPurgeRamBtn").addEventListener("click", () => performOneClickClean("ram"));
  document.getElementById("askGeminiPillBtn").addEventListener("click", openGuardrailsAndAskGemini);
  document.getElementById("headerGeminiBadgeBtn").addEventListener("click", openGuardrailsAndAskGemini);

  document.getElementById("headsUpPurgeNowBtn").addEventListener("click", () => performOneClickClean("all"));
  document.getElementById("headsUpDismissBtn").addEventListener("click", dismissSmartGuardHeadsUp);

  document.getElementById("simTransientBtn").addEventListener("click", () => sendSmartGuardPoll(85.0));
  document.getElementById("simSustainedBtn").addEventListener("click", simulateSustainedPressure);
  document.getElementById("simCriticalBtn").addEventListener("click", () => sendSmartGuardPoll(95.0));
  document.getElementById("resetSmartGuardBtn").addEventListener("click", resetSmartGuardState);
  document.getElementById("askAhriBtn").addEventListener("click", () => askAhriGemini());

  document.getElementById("cleanMediaBtn").addEventListener("click", performCleanMedia);
  document.getElementById("refreshBtn").addEventListener("click", fetchStatus);
  document.getElementById("seedCacheBtn").addEventListener("click", seedSampleCache);

  // Settings pill & close buttons close ALL settings panels (Smart Guard + Advanced Drawer)
  document.getElementById("settingsToggleBtn").addEventListener("click", handleSettingsPillClick);
  document.getElementById("closeSmartGuardBtn").addEventListener("click", closeAllSettingsPanels);
  document.getElementById("closeAllSettingsBtn").addEventListener("click", closeAllSettingsPanels);
  document.getElementById("bgAppsDockBtn").addEventListener("click", handleSettingsPillClick);
  document.getElementById("maxWinBtn").addEventListener("click", handleSettingsPillClick);
  document.getElementById("minWinBtn").addEventListener("click", closeAllSettingsPanels);

  document.getElementById("dismissBannerBtn").addEventListener("click", () => {
    document.getElementById("cleanupBanner").classList.add("hidden");
  });
  document.getElementById("closeBannerWinBtn").addEventListener("click", () => {
    document.getElementById("cleanupBanner").classList.add("hidden");
    closeAllSettingsPanels();
  });

  document.getElementById("themeToggleBtn").addEventListener("click", () => {
    const html = document.documentElement;
    const current = html.getAttribute("data-theme") || "dark";
    html.setAttribute("data-theme", current === "dark" ? "light" : "dark");
  });

  const searchInput = document.getElementById("appSearchInput");
  if (searchInput) {
    searchInput.addEventListener("input", (e) => {
      state.searchQuery = e.target.value || "";
      renderInstalledApps(state.lastStatus?.cache?.installedApps || []);
    });
  }

  const selectAllBtn = document.getElementById("selectAllAppsBtn");
  if (selectAllBtn) {
    selectAllBtn.addEventListener("click", () => {
      const apps = state.lastStatus?.cache?.installedApps || [];
      apps.forEach((a) => state.selectedPackages.add(a.packageName));
      state.selectedCategories = new Set(ALL_ONE_CLICK_CATEGORIES);
      renderCategories(state.lastStatus?.cache?.categories || []);
      renderInstalledApps(apps);
      updateSelectedReclaimableBadge();
    });
  }

  syncSettingsButtonState();
  fetchStatus();
});
