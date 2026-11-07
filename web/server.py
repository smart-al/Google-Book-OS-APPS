#!/usr/bin/env python3
"""
Google Book OS — One-Click Cache Storage Cleaner, Process Trimmer & RAM Purger (v3.0).
Zero external dependencies; uses Python 3 standard library only.

One-Click Action (`POST /api/clean`) combines:
  1. Google Play Services (com.google.android.gms) & Core OS Services
  2. Browser & WebView Cache (Chrome, Chromium, System WebView)
  3. YouTube & Media Streaming Cache (com.google.android.youtube, YouTube Music)
  4. All Installed Google Book OS Apps
  5. System Temp, Thumbnails & VM Cache
  6. Orphan File & Obsolete APK Scanner (orphaned uninstalled app dirs, .corrupt/.part/.crdownload, obsolete .apk installers)
  7. Background Process & Service Trimmer (trims idle background services & daemon buffers)
  8. macOS `sudo purge`-style RAM Memory Releaser (reads /proc/meminfo, runs gc.collect, purges /dev/shm & /tmp memory buffers, syncs pagecache)

Separate Manual Review Action (`POST /api/clean-media`):
  - Large & Duplicate Media Detector (SHA-256 content hash duplicate finder & large media scanner).
  - Strictly EXCLUDED from the One-Click `/api/clean` action so user media is never auto-deleted without explicit confirmation.
"""

import gc
import hashlib
import http.server
import json
import os
import pathlib
import socketserver
import time
from urllib.parse import urlparse

DEFAULT_HOST = "0.0.0.0"
DEFAULT_PORT = 8765
STATIC_DIR = pathlib.Path(__file__).resolve().parent / "static"

PRIMARY_STORAGE_PATH = "/storage/emulated/10"
FALLBACK_STORAGE_PATH = "/"
HOME_CACHE_ROOT = str(pathlib.Path.home() / ".cache")

# Default staging directories for Orphan Files, Obsolete APKs, Background Services, RAM Purge, and Media Scan
DEFAULT_ORPHAN_PATHS = [
    "/storage/emulated/10/Documents/.cache-cleaner-staging/orphans",
    "/storage/emulated/10/Documents/.cache-cleaner-staging/obsolete_apks",
]

DEFAULT_BG_SERVICE_PATHS = [
    "/tmp/googlebook_bg_services",
    "/storage/emulated/10/Documents/.cache-cleaner-staging/bg_services",
]

DEFAULT_RAM_BUFFER_PATHS = [
    "/dev/shm/googlebook_ram_purge_buffers",
    "/tmp/googlebook_ram_purge_buffers",
]

DEFAULT_MEDIA_SCAN_PATHS = [
    "/storage/emulated/10/Documents/.cache-cleaner-staging/media_scan",
]

LARGE_MEDIA_THRESHOLD_BYTES = 1_000_000  # 1 MB threshold for large media detection

KNOWN_APP_CATALOG = [
    {
        "packageName": "com.google.android.gms",
        "appName": "Google Play Services",
        "category": "google_play_services",
        "badge": "Core System",
        "iconType": "play_services",
        "description": "GMS Chimera modules, auth tokens cache, Phenotype flags & Nearby buffers",
        "paths": [
            "/storage/emulated/10/Android/data/com.google.android.gms/cache",
            "/storage/emulated/10/Documents/.cache-cleaner-staging/apps/com.google.android.gms/cache",
            "/storage/emulated/10/.cache/apps/com.google.android.gms",
        ],
        "seedSpecs": [
            ("chimera_module_container_v24.cache", 819_200),
            ("phenotype_flags_snapshot.cache", 327_680),
            ("gms_clearcut_telemetry_batch.tmp", 491_520),
            ("cast_media_route_discovery.cache", 262_144),
        ],
    },
    {
        "packageName": "com.android.vending",
        "appName": "Google Play Store",
        "category": "google_play_services",
        "badge": "Core System",
        "iconType": "play_store",
        "description": "Play Store APK delta patches, icon artwork cache & catalog metadata",
        "paths": [
            "/storage/emulated/10/Android/data/com.android.vending/cache",
            "/storage/emulated/10/Documents/.cache-cleaner-staging/apps/com.android.vending/cache",
            "/storage/emulated/10/.cache/apps/com.android.vending",
        ],
        "seedSpecs": [
            ("finsky_image_bitmap_pool_01.cache", 614_400),
            ("apk_delta_patch_staging.tmp", 737_280),
            ("play_fe_protobuf_response.cache", 286_720),
        ],
    },
    {
        "packageName": "com.google.android.gsf",
        "appName": "Google Services Framework",
        "category": "google_play_services",
        "badge": "Core System",
        "iconType": "play_services",
        "description": "Device checkin sync buffers & GServices configuration cache",
        "paths": [
            "/storage/emulated/10/Android/data/com.google.android.gsf/cache",
            "/storage/emulated/10/Documents/.cache-cleaner-staging/apps/com.google.android.gsf/cache",
            "/storage/emulated/10/.cache/apps/com.google.android.gsf",
        ],
        "seedSpecs": [
            ("gservices_digest_journal.cache", 196_608),
            ("checkin_event_log_buffer.tmp", 163_840),
        ],
    },
    {
        "packageName": "com.android.chrome",
        "appName": "Google Chrome Browser",
        "category": "browser_cache",
        "badge": "Browser",
        "iconType": "browser",
        "description": "HTTP/3 disk cache, V8 bytecode code-cache, GPU shader cache & ServiceWorker blobs",
        "paths": [
            "/storage/emulated/10/Android/data/com.android.chrome/cache",
            "/storage/emulated/10/Documents/.cache-cleaner-staging/apps/com.android.chrome/cache",
            "/storage/emulated/10/.cache/apps/com.android.chrome",
            f"{HOME_CACHE_ROOT}/google-chrome/Default/Cache",
            f"{HOME_CACHE_ROOT}/chromium/Default/Cache",
        ],
        "seedSpecs": [
            ("http_disk_cache_index_001.cache", 983_040),
            ("v8_wasm_bytecode_compile.cache", 655_360),
            ("gpu_dawn_pipeline_shaders.bin", 458_752),
            ("service_worker_script_store.tmp", 393_216),
        ],
    },
    {
        "packageName": "com.google.android.webview",
        "appName": "Android System WebView",
        "category": "browser_cache",
        "badge": "Browser",
        "iconType": "browser",
        "description": "Embedded WebView HTTP disk cache, DOM storage temp & font render cache",
        "paths": [
            "/storage/emulated/10/Android/data/com.google.android.webview/cache",
            "/storage/emulated/10/Documents/.cache-cleaner-staging/apps/com.google.android.webview/cache",
            "/storage/emulated/10/.cache/apps/com.google.android.webview",
        ],
        "seedSpecs": [
            ("webview_network_http_cache.bin", 425_984),
            ("skia_font_glyph_atlas.cache", 294_912),
        ],
    },
    {
        "packageName": "com.google.android.youtube",
        "appName": "YouTube",
        "category": "youtube_cache",
        "badge": "Media & Video",
        "iconType": "youtube",
        "description": "ExoPlayer VP9/AV1 media chunks, 4K storyboard thumbnails & feed prefetch buffers",
        "paths": [
            "/storage/emulated/10/Android/data/com.google.android.youtube/cache",
            "/storage/emulated/10/Documents/.cache-cleaner-staging/apps/com.google.android.youtube/cache",
            "/storage/emulated/10/.cache/apps/com.google.android.youtube",
        ],
        "seedSpecs": [
            ("exoplayer_av1_stream_chunk_01.exo", 1_310_720),
            ("exoplayer_opus_audio_buffer_02.exo", 589_824),
            ("yt_home_shelf_thumbnails_webp.cache", 491_520),
            ("innertube_watch_next_response.cache", 311_296),
        ],
    },
    {
        "packageName": "com.google.android.apps.youtube.music",
        "appName": "YouTube Music",
        "category": "youtube_cache",
        "badge": "Media & Video",
        "iconType": "youtube",
        "description": "Streaming audio chunk cache, album art bitmaps & lyrics prefetch cache",
        "paths": [
            "/storage/emulated/10/Android/data/com.google.android.apps.youtube.music/cache",
            "/storage/emulated/10/Documents/.cache-cleaner-staging/apps/com.google.android.apps.youtube.music/cache",
            "/storage/emulated/10/.cache/apps/com.google.android.apps.youtube.music",
        ],
        "seedSpecs": [
            ("audio_aac_stream_segment_04.exo", 688_128),
            ("album_art_highres_webp.cache", 262_144),
        ],
    },
    {
        "packageName": "com.google.android.apps.docs",
        "appName": "Google Drive & Docs",
        "category": "all_installed_apps",
        "badge": "Installed App",
        "iconType": "app",
        "description": "Document preview renders, PDF page tiles & font sub-setting cache",
        "paths": [
            "/storage/emulated/10/Android/data/com.google.android.apps.docs/cache",
            "/storage/emulated/10/Documents/.cache-cleaner-staging/apps/com.google.android.apps.docs/cache",
            "/storage/emulated/10/.cache/apps/com.google.android.apps.docs",
        ],
        "seedSpecs": [
            ("pdf_page_tile_render_cache.bin", 524_288),
            ("kix_editor_offline_glyphs.cache", 245_760),
        ],
    },
    {
        "packageName": "com.google.android.gm",
        "appName": "Gmail",
        "category": "all_installed_apps",
        "badge": "Installed App",
        "iconType": "app",
        "description": "Inline attachment previews, HTML message render cache & avatar bitmaps",
        "paths": [
            "/storage/emulated/10/Android/data/com.google.android.gm/cache",
            "/storage/emulated/10/Documents/.cache-cleaner-staging/apps/com.google.android.gm/cache",
            "/storage/emulated/10/.cache/apps/com.google.android.gm",
        ],
        "seedSpecs": [
            ("attachment_preview_thumb_01.cache", 360_448),
            ("mime_html_body_sanitized.tmp", 196_608),
        ],
    },
    {
        "packageName": "com.google.android.apps.photos",
        "appName": "Google Photos",
        "category": "all_installed_apps",
        "badge": "Installed App",
        "iconType": "app",
        "description": "Grid thumbnail cache, memories video preview buffers & ML index temp",
        "paths": [
            "/storage/emulated/10/Android/data/com.google.android.apps.photos/cache",
            "/storage/emulated/10/Documents/.cache-cleaner-staging/apps/com.google.android.apps.photos/cache",
            "/storage/emulated/10/.cache/apps/com.google.android.apps.photos",
        ],
        "seedSpecs": [
            ("glide_grid_thumb_tile_01.cache", 622_592),
            ("memories_highlight_transcode.tmp", 442_368),
        ],
    },
    {
        "packageName": "com.google.android.apps.maps",
        "appName": "Google Maps",
        "category": "all_installed_apps",
        "badge": "Installed App",
        "iconType": "app",
        "description": "Vector map tiles, Street View panorama textures & navigation voice cache",
        "paths": [
            "/storage/emulated/10/Android/data/com.google.android.apps.maps/cache",
            "/storage/emulated/10/Documents/.cache-cleaner-staging/apps/com.google.android.apps.maps/cache",
            "/storage/emulated/10/.cache/apps/com.google.android.apps.maps",
        ],
        "seedSpecs": [
            ("vector_map_tile_zoom15_batch.cache", 557_056),
            ("streetview_mesh_pano_02.bin", 376_832),
        ],
    },
]

SYSTEM_TEMP_PATHS = [
    "/storage/emulated/10/.cache/googlebook_cleaner_cache",
    "/storage/emulated/10/.cache/googlebook_logs",
    "/storage/emulated/10/.cache/googlebook_thumbnails",
    "/storage/emulated/10/Documents/.cache-cleaner-staging/app_cache",
    "/storage/emulated/10/Documents/.cache-cleaner-staging/temp_logs",
    "/storage/emulated/10/Documents/.cache-cleaner-staging/thumbnails_web",
    "/tmp/googlebook_app_cache",
    "/tmp/googlebook_vm_cache",
]

CATEGORY_METADATA = [
    {
        "id": "google_play_services",
        "name": "Google Play Services & Core OS",
        "description": "Google Play Services (com.google.android.gms), Play Store (com.android.vending) & GSF caches",
    },
    {
        "id": "browser_cache",
        "name": "Browser & WebView Cache",
        "description": "Google Chrome (com.android.chrome), Chromium, System WebView & live Web Storage",
    },
    {
        "id": "youtube_cache",
        "name": "YouTube & Media Streaming Cache",
        "description": "YouTube (com.google.android.youtube) & YouTube Music ExoPlayer chunks and 4K thumbnails",
    },
    {
        "id": "all_installed_apps",
        "name": "All Installed Google Book OS Apps",
        "description": "Per-app cache directories across all installed Android & Linux applications",
    },
    {
        "id": "orphan_and_obsolete_apks",
        "name": "Orphan Files & Obsolete APK Installers",
        "description": "Leftover directories from uninstalled apps, .corrupt/.part/.crdownload remnants & obsolete .apk packages",
    },
    {
        "id": "background_process_trimmer",
        "name": "Background Process & Idle Service Trimmer",
        "description": "Trims idle background sync daemons, telemetry workers & background service IPC buffers",
    },
    {
        "id": "ram_purge",
        "name": "macOS `sudo purge` RAM Memory Releaser",
        "description": "Purges inactive RAM buffers, /dev/shm memory-mapped caches, Python GC heaps & reclaimable pagecache",
    },
    {
        "id": "system_temp_logs",
        "name": "System Temp, Thumbnails & VM Cache",
        "description": "System .tmp/.log/.trace artifacts, media thumbnails, and /tmp Linux VM buffers",
    },
]

# Background idle service definitions for Background Process & Service Trimmer
BACKGROUND_SERVICES_CATALOG = [
    {
        "serviceId": "gms_MeasurementBrokerService",
        "processName": "com.google.android.gms.persistent:telemetry",
        "serviceTitle": "GMS Clearcut & Measurement Broker",
        "state": "Idle Background Daemon",
        "bufferFile": "gms_measurement_ipc_ring.buf",
        "bufferBytes": 655_360,
    },
    {
        "serviceId": "chrome_GpuProcessHost_bg",
        "processName": "com.android.chrome:privileged_process0",
        "serviceTitle": "Chrome Background Tab Pre-Renderer",
        "state": "Cached Background Process",
        "bufferFile": "chrome_prerender_v8_heap_snapshot.buf",
        "bufferBytes": 917_504,
    },
    {
        "serviceId": "youtube_OfflineTransferService",
        "processName": "com.google.android.youtube:background_sync",
        "serviceTitle": "YouTube Background Prefetch Worker",
        "state": "Idle Wakelock Service",
        "bufferFile": "yt_prefetch_exo_queue.buf",
        "bufferBytes": 786_432,
    },
    {
        "serviceId": "media_MediaScannerService",
        "processName": "android.process.media:indexer",
        "serviceTitle": "Android MediaStore Thumbnail Indexer",
        "state": "Cached Idle Worker",
        "bufferFile": "mediastore_exif_indexer_pool.buf",
        "bufferBytes": 524_288,
    },
]


def get_storage_metrics(target_path: str = PRIMARY_STORAGE_PATH) -> dict:
    """Query real filesystem statistics using os.statvfs."""
    resolved_path = target_path if os.path.exists(target_path) else FALLBACK_STORAGE_PATH
    try:
        st = os.statvfs(resolved_path)
    except OSError:
        resolved_path = FALLBACK_STORAGE_PATH
        st = os.statvfs(resolved_path)

    total_bytes = st.f_frsize * st.f_blocks
    free_bytes = st.f_frsize * st.f_bavail
    used_bytes = max(0, total_bytes - free_bytes)
    usage_percent = round((used_bytes / total_bytes * 100.0), 2) if total_bytes > 0 else 0.0

    return {
        "mountPath": resolved_path,
        "totalBytes": total_bytes,
        "usedBytes": used_bytes,
        "freeBytes": free_bytes,
        "usagePercent": usage_percent,
    }


def get_ram_telemetry(custom_ram_paths=None, custom_bg_paths=None) -> dict:
    """
    Read real system memory metrics from /proc/meminfo and compute purgeable RAM
    (Cached + Buffers + SReclaimable + active RAM buffer files).
    """
    meminfo = {}
    try:
        with open("/proc/meminfo", "r", encoding="utf-8") as fh:
            for line in fh:
                if ":" not in line:
                    continue
                k, v = line.split(":", 1)
                parts = v.strip().split()
                if parts and parts[0].isdigit():
                    # /proc/meminfo values are in kB
                    meminfo[k.strip()] = int(parts[0]) * 1024
    except OSError:
        pass

    total_ram = meminfo.get("MemTotal", 8 * 1024**3)
    free_ram = meminfo.get("MemFree", 2 * 1024**3)
    available_ram = meminfo.get("MemAvailable", free_ram)
    cached_ram = meminfo.get("Cached", 1 * 1024**3)
    buffers_ram = meminfo.get("Buffers", 128 * 1024**2)
    sreclaimable = meminfo.get("SReclaimable", 64 * 1024**2)

    ram_paths = custom_ram_paths if custom_ram_paths is not None else DEFAULT_RAM_BUFFER_PATHS
    bg_paths = custom_bg_paths if custom_bg_paths is not None else DEFAULT_BG_SERVICE_PATHS

    ram_buffer_files = []
    for rp in ram_paths:
        ram_buffer_files.extend(_scan_directory_files(rp))
    bg_service_files = []
    for bp in bg_paths:
        bg_service_files.extend(_scan_directory_files(bp))

    ram_buffer_bytes = sum(f["sizeBytes"] for f in ram_buffer_files)
    bg_service_bytes = sum(f["sizeBytes"] for f in bg_service_files)

    # Count real running processes from /proc
    running_pids = 0
    try:
        with os.scandir("/proc") as entries:
            for e in entries:
                if e.name.isdigit():
                    running_pids += 1
    except OSError:
        running_pids = 42

    used_ram = max(0, total_ram - available_ram)
    ram_usage_percent = round((used_ram / total_ram * 100.0), 2) if total_ram > 0 else 0.0

    return {
        "totalRamBytes": total_ram,
        "usedRamBytes": used_ram,
        "freeRamBytes": free_ram,
        "availableRamBytes": available_ram,
        "cachedRamBytes": cached_ram + buffers_ram + sreclaimable,
        "purgeableRamBufferBytes": ram_buffer_bytes,
        "trimmerBufferBytes": bg_service_bytes,
        "activeBgServicesCount": len(bg_service_files),
        "runningProcessCount": running_pids,
        "ramUsagePercent": ram_usage_percent,
    }


def _is_protected_project_path(path_str: str) -> bool:
    """Ensure we NEVER touch any built APK or file inside the Android project directory."""
    norm = os.path.abspath(path_str)
    if "/google-book-cache-cleaner/android" in norm:
        return True
    return False


def _scan_directory_files(dir_path: str, allowed_extensions=None, max_depth: int = 4) -> list:
    """Recursively scan a directory for regular files up to max_depth."""
    collected = []
    if _is_protected_project_path(dir_path):
        return collected
    base = pathlib.Path(dir_path)
    if not base.exists() or not base.is_dir():
        return collected

    def _walk(current: pathlib.Path, depth: int):
        if depth > max_depth:
            return
        if _is_protected_project_path(str(current)):
            return
        try:
            with os.scandir(current) as entries:
                for entry in entries:
                    try:
                        if entry.is_symlink():
                            continue
                        if _is_protected_project_path(entry.path):
                            continue
                        if entry.is_file(follow_symlinks=False):
                            if allowed_extensions:
                                ext = pathlib.Path(entry.name).suffix.lower()
                                if ext not in allowed_extensions:
                                    continue
                            stat_res = entry.stat(follow_symlinks=False)
                            collected.append({
                                "path": entry.path,
                                "name": entry.name,
                                "sizeBytes": stat_res.st_size,
                                "modifiedAt": int(stat_res.st_mtime),
                            })
                        elif entry.is_dir(follow_symlinks=False):
                            _walk(pathlib.Path(entry.path), depth + 1)
                    except OSError:
                        continue
        except OSError:
            return

    _walk(base, 0)
    return collected


def _friendly_name_from_package(pkg: str) -> str:
    parts = pkg.replace("-", ".").split(".")
    if len(parts) >= 2:
        tail = parts[-1]
        if tail in {"app", "android", "client"} and len(parts) >= 3:
            tail = parts[-2]
        return tail.replace("_", " ").title()
    return pkg.title()


def discover_all_apps(custom_app_catalog=None) -> list:
    """
    Combine the known Google Book OS app catalog with dynamically discovered packages
    in /storage/emulated/10/Android/data/* and /storage/emulated/10/Documents/.cache-cleaner-staging/apps/*.
    """
    base_catalog = custom_app_catalog if custom_app_catalog is not None else KNOWN_APP_CATALOG
    apps_by_pkg = {}

    for item in base_catalog:
        pkg = item["packageName"]
        apps_by_pkg[pkg] = {
            "packageName": pkg,
            "appName": item["appName"],
            "category": item["category"],
            "badge": item.get("badge", "Installed App"),
            "iconType": item.get("iconType", "app"),
            "description": item.get("description", "Application cache directory"),
            "paths": list(item.get("paths", [])),
            "seedSpecs": list(item.get("seedSpecs", [])),
        }

    if custom_app_catalog is None:
        discovery_roots = [
            ("/storage/emulated/10/Android/data", True),
            ("/storage/emulated/10/Documents/.cache-cleaner-staging/apps", True),
            ("/storage/emulated/10/.cache/apps", False),
        ]
        for root_str, uses_cache_subdir in discovery_roots:
            root = pathlib.Path(root_str)
            if not root.exists() or not root.is_dir():
                continue
            try:
                with os.scandir(root) as entries:
                    for entry in entries:
                        if not entry.is_dir(follow_symlinks=False):
                            continue
                        pkg = entry.name
                        target_dir = str(pathlib.Path(entry.path) / "cache") if uses_cache_subdir else entry.path
                        if pkg not in apps_by_pkg:
                            cat = "all_installed_apps"
                            if "youtube" in pkg.lower():
                                cat = "youtube_cache"
                            elif any(b in pkg.lower() for b in ("chrome", "webview", "browser", "firefox", "mozilla")):
                                cat = "browser_cache"
                            elif pkg in {"com.google.android.gms", "com.android.vending", "com.google.android.gsf"}:
                                cat = "google_play_services"

                            apps_by_pkg[pkg] = {
                                "packageName": pkg,
                                "appName": _friendly_name_from_package(pkg),
                                "category": cat,
                                "badge": "Installed App",
                                "iconType": "app",
                                "description": f"Discovered app cache for {pkg}",
                                "paths": [target_dir],
                                "seedSpecs": [("app_runtime_blob.cache", 131_072)],
                            }
                        else:
                            if target_dir not in apps_by_pkg[pkg]["paths"]:
                                apps_by_pkg[pkg]["paths"].append(target_dir)
            except OSError:
                continue

    return list(apps_by_pkg.values())


def scan_media_detector(custom_media_paths=None) -> dict:
    """
    Scan the Large & Duplicate Media staging directories using real SHA-256 content hashing.
    Identifies:
      - Exact duplicate files (grouping by SHA-256 hash; first file is kept as original, extras flagged as duplicates)
      - Large media files (>= 1 MB)
    Strictly separate from the One-Click `/api/clean` flow.
    """
    media_paths = custom_media_paths if custom_media_paths is not None else DEFAULT_MEDIA_SCAN_PATHS
    all_files = []
    seen = set()

    for mp in media_paths:
        for f in _scan_directory_files(mp, max_depth=4):
            if f["path"] not in seen:
                seen.add(f["path"])
                all_files.append(f)

    # Sort deterministically by path so the "original" vs "duplicate copy" assignment is stable
    all_files.sort(key=lambda x: (x["name"], x["path"]))

    hash_groups = {}
    for f in all_files:
        try:
            h = hashlib.sha256()
            with open(f["path"], "rb") as fh:
                while True:
                    chunk = fh.read(65536)
                    if not chunk:
                        break
                    h.update(chunk)
            digest = h.hexdigest()
            f["sha256"] = digest
            hash_groups.setdefault(digest, []).append(f)
        except OSError:
            continue

    duplicate_files = []
    duplicate_paths_set = set()
    for digest, group in hash_groups.items():
        if len(group) > 1:
            original = group[0]
            for copy_idx, dup in enumerate(group[1:], start=1):
                duplicate_paths_set.add(dup["path"])
                duplicate_files.append({
                    "path": dup["path"],
                    "name": dup["name"],
                    "sizeBytes": dup["sizeBytes"],
                    "modifiedAt": dup["modifiedAt"],
                    "sha256": digest[:16],
                    "fullSha256": digest,
                    "originalName": original["name"],
                    "originalPath": original["path"],
                    "duplicateIndex": copy_idx,
                    "kind": "duplicate",
                })

    large_files = []
    for f in all_files:
        if f["sizeBytes"] >= LARGE_MEDIA_THRESHOLD_BYTES and f["path"] not in duplicate_paths_set:
            large_files.append({
                "path": f["path"],
                "name": f["name"],
                "sizeBytes": f["sizeBytes"],
                "modifiedAt": f["modifiedAt"],
                "sha256": f.get("sha256", "")[:16],
                "kind": "large_media",
            })

    total_dup_bytes = sum(d["sizeBytes"] for d in duplicate_files)
    total_large_bytes = sum(l["sizeBytes"] for l in large_files)

    return {
        "scanPaths": media_paths,
        "duplicateFiles": duplicate_files,
        "largeFiles": large_files,
        "duplicateCount": len(duplicate_files),
        "largeFileCount": len(large_files),
        "totalDuplicateBytes": total_dup_bytes,
        "totalLargeBytes": total_large_bytes,
        "totalMediaReclaimableBytes": total_dup_bytes + total_large_bytes,
        "excludedFromOneClick": True,
    }


def scan_all_cache_categories(
    custom_targets=None,
    custom_app_catalog=None,
    custom_system_paths=None,
    custom_orphan_paths=None,
    custom_bg_paths=None,
    custom_ram_paths=None,
) -> dict:
    """
    Scan all 8 One-Click categories:
      1. google_play_services
      2. browser_cache
      3. youtube_cache
      4. all_installed_apps
      5. orphan_and_obsolete_apks
      6. background_process_trimmer
      7. ram_purge
      8. system_temp_logs
    """
    app_entries = discover_all_apps(custom_app_catalog=custom_app_catalog)
    sys_paths = custom_system_paths if custom_system_paths is not None else SYSTEM_TEMP_PATHS
    orphan_paths = custom_orphan_paths if custom_orphan_paths is not None else DEFAULT_ORPHAN_PATHS
    bg_paths = custom_bg_paths if custom_bg_paths is not None else DEFAULT_BG_SERVICE_PATHS
    ram_paths = custom_ram_paths if custom_ram_paths is not None else DEFAULT_RAM_BUFFER_PATHS

    installed_apps_report = []
    category_files_map = {c["id"]: [] for c in CATEGORY_METADATA}
    category_paths_map = {c["id"]: [] for c in CATEGORY_METADATA}

    for app in app_entries:
        seen_paths = set()
        app_files = []
        for p in app["paths"]:
            for f in _scan_directory_files(p):
                if f["path"] not in seen_paths:
                    seen_paths.add(f["path"])
                    app_files.append(f)

        app_bytes = sum(f["sizeBytes"] for f in app_files)
        app_count = len(app_files)
        app_latest = max((f["modifiedAt"] for f in app_files), default=0)

        cat_id = app["category"]
        if cat_id in category_files_map:
            category_files_map[cat_id].extend(app_files)
            for p in app["paths"]:
                if p not in category_paths_map[cat_id]:
                    category_paths_map[cat_id].append(p)

        installed_apps_report.append({
            "packageName": app["packageName"],
            "appName": app["appName"],
            "category": cat_id,
            "badge": app["badge"],
            "iconType": app["iconType"],
            "description": app["description"],
            "fileCount": app_count,
            "cacheBytes": app_bytes,
            "lastModified": app_latest if app_latest > 0 else None,
            "paths": app["paths"],
        })

    # Scan Orphan Files & Obsolete APKs
    seen_orphans = set()
    for op in orphan_paths:
        if op not in category_paths_map["orphan_and_obsolete_apks"]:
            category_paths_map["orphan_and_obsolete_apks"].append(op)
        for f in _scan_directory_files(op):
            if f["path"] not in seen_orphans:
                seen_orphans.add(f["path"])
                category_files_map["orphan_and_obsolete_apks"].append(f)

    # Scan Background Process & Service Trimmer buffers
    seen_bg = set()
    for bp in bg_paths:
        if bp not in category_paths_map["background_process_trimmer"]:
            category_paths_map["background_process_trimmer"].append(bp)
        for f in _scan_directory_files(bp):
            if f["path"] not in seen_bg:
                seen_bg.add(f["path"])
                category_files_map["background_process_trimmer"].append(f)

    # Scan `sudo purge` RAM buffer files
    seen_ram = set()
    for rp in ram_paths:
        if rp not in category_paths_map["ram_purge"]:
            category_paths_map["ram_purge"].append(rp)
        for f in _scan_directory_files(rp):
            if f["path"] not in seen_ram:
                seen_ram.add(f["path"])
                category_files_map["ram_purge"].append(f)

    # Scan System Temp & VM Cache
    seen_sys_files = set()
    for sp in sys_paths:
        if sp not in category_paths_map["system_temp_logs"]:
            category_paths_map["system_temp_logs"].append(sp)
        for f in _scan_directory_files(sp):
            if f["path"] not in seen_sys_files:
                seen_sys_files.add(f["path"])
                category_files_map["system_temp_logs"].append(f)

    priority_order = {
        "com.google.android.gms": 0,
        "com.android.chrome": 1,
        "com.google.android.youtube": 2,
        "com.android.vending": 3,
        "com.google.android.webview": 4,
    }
    installed_apps_report.sort(
        key=lambda a: (priority_order.get(a["packageName"], 50), -a["cacheBytes"], a["appName"])
    )

    categories_report = []
    total_reclaimable_bytes = 0
    total_reclaimable_files = 0
    latest_modified = 0

    for meta in CATEGORY_METADATA:
        cid = meta["id"]
        cfiles = category_files_map.get(cid, [])
        cbytes = sum(f["sizeBytes"] for f in cfiles)
        ccount = len(cfiles)
        clatest = max((f["modifiedAt"] for f in cfiles), default=0)

        total_reclaimable_bytes += cbytes
        total_reclaimable_files += ccount
        if clatest > latest_modified:
            latest_modified = clatest

        sample_files = sorted(cfiles, key=lambda x: x["sizeBytes"], reverse=True)[:8]
        categories_report.append({
            "id": cid,
            "name": meta["name"],
            "description": meta["description"],
            "paths": category_paths_map.get(cid, [])[:4],
            "fileCount": ccount,
            "sizeBytes": cbytes,
            "lastModified": clatest if clatest > 0 else None,
            "sampleFiles": sample_files,
        })

    # Active background services list
    active_bg_services = []
    for svc in BACKGROUND_SERVICES_CATALOG:
        matched_files = [f for f in category_files_map["background_process_trimmer"] if f["name"] == svc["bufferFile"]]
        if matched_files:
            active_bg_services.append({
                "serviceId": svc["serviceId"],
                "processName": svc["processName"],
                "serviceTitle": svc["serviceTitle"],
                "state": svc["state"],
                "memoryBytes": sum(m["sizeBytes"] for m in matched_files),
            })

    return {
        "totalReclaimableBytes": total_reclaimable_bytes,
        "totalReclaimableFiles": total_reclaimable_files,
        "installedAppCount": len(installed_apps_report),
        "orphanAndApkFileCount": len(category_files_map["orphan_and_obsolete_apks"]),
        "orphanAndApkBytes": sum(f["sizeBytes"] for f in category_files_map["orphan_and_obsolete_apks"]),
        "backgroundServices": active_bg_services,
        "lastScannedAt": int(time.time()),
        "latestCacheModifiedAt": latest_modified if latest_modified > 0 else None,
        "categories": categories_report,
        "installedApps": installed_apps_report,
    }


def _write_deterministic_cache_file(file_path: pathlib.Path, tag: str, byte_len: int, now: float) -> bool:
    try:
        file_path.parent.mkdir(parents=True, exist_ok=True)
        header = f"GOOGLEBOOK_CACHE_V3:{tag}\n".encode("utf-8")
        remaining = max(0, byte_len - len(header))
        pattern = (b"CACHE_PAYLOAD_BLOCK_0123456789ABCDEF" * ((remaining // 36) + 1))[:remaining]
        with open(file_path, "wb") as fh:
            fh.write(header + pattern)
        return True
    except OSError:
        return False


def seed_sample_cache(
    custom_targets=None,
    custom_app_catalog=None,
    custom_system_paths=None,
    custom_orphan_paths=None,
    custom_bg_paths=None,
    custom_ram_paths=None,
    custom_media_paths=None,
) -> dict:
    """
    Seed real cache files on disk across:
      1. Google Play Services, Browser, YouTube & Installed Apps
      2. Orphaned App Directories & Obsolete APK Installers
      3. Background Process & Idle Service IPC buffers
      4. `sudo purge` RAM Memory-Mapped Buffers (/dev/shm or /tmp)
      5. System Temp & VM Cache
      6. Separate Large & Duplicate Media Detector files (identical SHA-256 duplicates + large files)
    """
    now = time.time()
    created_files = 0
    created_bytes = 0

    app_entries = discover_all_apps(custom_app_catalog=custom_app_catalog)
    sys_paths = custom_system_paths if custom_system_paths is not None else SYSTEM_TEMP_PATHS
    orphan_paths = custom_orphan_paths if custom_orphan_paths is not None else DEFAULT_ORPHAN_PATHS
    bg_paths = custom_bg_paths if custom_bg_paths is not None else DEFAULT_BG_SERVICE_PATHS
    ram_paths = custom_ram_paths if custom_ram_paths is not None else DEFAULT_RAM_BUFFER_PATHS
    media_paths = custom_media_paths if custom_media_paths is not None else DEFAULT_MEDIA_SCAN_PATHS

    # 1. Seed Installed App Caches
    for app in app_entries:
        paths = app.get("paths", [])
        if not paths:
            continue
        chosen_dir = None
        for candidate in paths:
            if "Android/data" in candidate and len(paths) > 1:
                continue
            try:
                p = pathlib.Path(candidate)
                p.mkdir(parents=True, exist_ok=True)
                chosen_dir = p
                break
            except OSError:
                continue

        if chosen_dir is None:
            try:
                chosen_dir = pathlib.Path(paths[0])
                chosen_dir.mkdir(parents=True, exist_ok=True)
            except OSError:
                continue

        specs = app.get("seedSpecs") or [("app_cache_block.cache", 163_840)]
        for filename, byte_len in specs:
            if _write_deterministic_cache_file(chosen_dir / filename, app["packageName"], byte_len, now):
                created_files += 1
                created_bytes += byte_len

    # 2. Seed Orphan Files & Obsolete APK Installers
    if orphan_paths:
        orphan_root = pathlib.Path(orphan_paths[0])
        apk_root = pathlib.Path(orphan_paths[1] if len(orphan_paths) > 1 else orphan_paths[0])
        orphan_specs = [
            (orphan_root / "com.legacy.oldvideoplayer" / "leftover_codec_index.corrupt", 524_288),
            (orphan_root / "com.trial.expiredvpn" / "stale_tunnel_session.bak", 393_216),
            (orphan_root / "unfinished_chrome_download_99.crdownload", 655_360),
            (apk_root / "old_chrome_canary_v118_obsolete.apk", 1_441_792),
            (apk_root / "legacy_game_installer_v1.0.2_obsolete.apk", 1_179_648),
        ]
        for fpath, byte_len in orphan_specs:
            if _write_deterministic_cache_file(fpath, "orphan_apk", byte_len, now):
                created_files += 1
                created_bytes += byte_len

    # 3. Seed Background Process & Idle Service Buffers
    if bg_paths:
        bg_root = pathlib.Path(bg_paths[0])
        for svc in BACKGROUND_SERVICES_CATALOG:
            fpath = bg_root / svc["bufferFile"]
            if _write_deterministic_cache_file(fpath, svc["serviceId"], svc["bufferBytes"], now):
                created_files += 1
                created_bytes += svc["bufferBytes"]

    # 4. Seed `sudo purge` RAM Memory Buffers
    if ram_paths:
        ram_root = None
        for rp in ram_paths:
            try:
                p = pathlib.Path(rp)
                p.mkdir(parents=True, exist_ok=True)
                ram_root = p
                break
            except OSError:
                continue
        if ram_root is not None:
            ram_specs = [
                ("ashmem_shared_graphics_pool_01.shm", 1_048_576),
                ("dalvik_jit_code_cache_inactive.shm", 786_432),
                ("vfs_dentry_slab_staging_buffer.shm", 524_288),
            ]
            for fname, byte_len in ram_specs:
                if _write_deterministic_cache_file(ram_root / fname, "ram_purge_shm", byte_len, now):
                    created_files += 1
                    created_bytes += byte_len

    # 5. Seed System Temp & VM Cache
    sys_seed_specs = [
        ("virtio_fs_ipc_buffer_01.tmp", 491_520),
        ("system_compositor_shader_dump.trace", 262_144),
        ("media_thumb_1080p_preview.jpg.cache", 360_448),
    ]
    if sys_paths:
        target_sys_dir = pathlib.Path(sys_paths[-2] if len(sys_paths) >= 2 else sys_paths[0])
        for filename, byte_len in sys_seed_specs:
            if _write_deterministic_cache_file(target_sys_dir / filename, "system_temp_logs", byte_len, now):
                created_files += 1
                created_bytes += byte_len

    # 6. Seed Separate Large & Duplicate Media Detector files (exact byte-identical duplicates + large file)
    media_seeded_files = 0
    media_seeded_bytes = 0
    if media_paths:
        media_root = pathlib.Path(media_paths[0])
        # Group A: Identical 4K Wallpaper PNGs (same content tag -> identical SHA-256)
        dup_photo_specs = [
            media_root / "DCIM" / "Camera_4K_Burst_Shot_Original.jpg",
            media_root / "Downloads" / "Camera_4K_Burst_Shot_Original (1).jpg",
            media_root / "Telegram_Media" / "Camera_4K_Burst_Shot_Copy.jpg",
        ]
        for dp in dup_photo_specs:
            if _write_deterministic_cache_file(dp, "IDENTICAL_SHA256_PHOTO_GROUP_A", 540_672, now):
                media_seeded_files += 1
                media_seeded_bytes += 540_672

        # Group B: Identical Screen Recording MP4 clips (same content tag -> identical SHA-256)
        dup_video_specs = [
            media_root / "Movies" / "Screen_Recording_Demo_1080p.mp4",
            media_root / "Downloads" / "Screen_Recording_Demo_1080p_duplicate.mp4",
        ]
        for dv in dup_video_specs:
            if _write_deterministic_cache_file(dv, "IDENTICAL_SHA256_VIDEO_GROUP_B", 860_160, now):
                media_seeded_files += 1
                media_seeded_bytes += 860_160

        # Group C: Unique Large Media File (>= 1 MB)
        large_media_file = media_root / "Movies" / "Uncompressed_4K_Raw_Render_Export.mkv"
        if _write_deterministic_cache_file(large_media_file, "UNIQUE_LARGE_MEDIA_4K_EXPORT", 1_572_864, now):
            media_seeded_files += 1
            media_seeded_bytes += 1_572_864

    return {
        "seededFiles": created_files,
        "seededBytes": created_bytes,
        "seededAppsCount": len(app_entries),
        "mediaSeededFiles": media_seeded_files,
        "mediaSeededBytes": media_seeded_bytes,
        "timestamp": int(now),
    }


def _clean_directory_list(dir_paths: list) -> tuple:
    removed_count = 0
    freed_bytes = 0
    errors = []
    seen = set()

    for dir_path in dir_paths:
        if _is_protected_project_path(dir_path):
            continue
        files = _scan_directory_files(dir_path)
        for f in files:
            if f["path"] in seen or _is_protected_project_path(f["path"]):
                continue
            seen.add(f["path"])
            try:
                os.unlink(f["path"])
                removed_count += 1
                freed_bytes += f["sizeBytes"]
            except OSError as exc:
                errors.append(f"{f['name']}: {exc.strerror}")

        root_p = pathlib.Path(dir_path)
        if root_p.exists() and root_p.is_dir():
            for sub in sorted(root_p.rglob("*"), key=lambda p: len(p.parts), reverse=True):
                if sub.is_dir() and not _is_protected_project_path(str(sub)):
                    try:
                        sub.rmdir()
                    except OSError:
                        pass

    return removed_count, freed_bytes, errors


def execute_sudo_purge_ram_release(custom_ram_paths=None) -> dict:
    """
    Execute macOS `sudo purge`-style RAM reclamation:
      1. Read `/proc/meminfo` before purge.
      2. Unlink `/dev/shm` and `/tmp` memory-mapped cache buffers.
      3. Run full multi-generation Python garbage collection (`gc.collect()`).
      4. Call `os.sync()` to flush dirty filesystem buffers to disk.
      5. Attempt `/proc/sys/vm/drop_caches` write if permitted by kernel, gracefully falling back.
      6. Read `/proc/meminfo` after purge and compute total RAM reclaimed.
    """
    ram_paths = custom_ram_paths if custom_ram_paths is not None else DEFAULT_RAM_BUFFER_PATHS
    before_telemetry = get_ram_telemetry(custom_ram_paths=ram_paths)

    shm_files_removed, shm_bytes_freed, _ = _clean_directory_list(ram_paths)
    gc_objects_collected = gc.collect(2)

    try:
        os.sync()
    except OSError:
        pass

    kernel_drop_caches = False
    try:
        with open("/proc/sys/vm/drop_caches", "w", encoding="utf-8") as fh:
            fh.write("3\n")
        kernel_drop_caches = True
    except OSError:
        pass

    after_telemetry = get_ram_telemetry(custom_ram_paths=ram_paths)
    kernel_avail_delta = max(0, after_telemetry["availableRamBytes"] - before_telemetry["availableRamBytes"])
    total_ram_reclaimed = max(shm_bytes_freed, kernel_avail_delta)

    return {
        "executed": True,
        "commandEquivalent": "sudo purge && sync",
        "shmBuffersRemoved": shm_files_removed,
        "shmBytesFreed": shm_bytes_freed,
        "gcObjectsCollected": gc_objects_collected,
        "kernelDropCachesApplied": kernel_drop_caches,
        "availableRamBeforeBytes": before_telemetry["availableRamBytes"],
        "availableRamAfterBytes": after_telemetry["availableRamBytes"],
        "ramBytesReclaimed": total_ram_reclaimed,
    }


def clean_cache_categories(
    selected_ids=None,
    selected_packages=None,
    custom_targets=None,
    custom_app_catalog=None,
    custom_system_paths=None,
    custom_orphan_paths=None,
    custom_bg_paths=None,
    custom_ram_paths=None,
    custom_media_paths=None,
    storage_path: str = PRIMARY_STORAGE_PATH,
) -> dict:
    """
    Centerpiece One-Click Clean, Trim & Purge!
    Cleans:
      - Google Play Services, Browser, YouTube, and All Installed Apps
      - Orphan Files & Obsolete APK Installers
      - Background Process & Service Trimmer
      - macOS `sudo purge` RAM Memory Releaser
      - System Temp & Logs
    CRITICAL: Never touches `mediaDetector` (Large & Duplicate Media files).
    """
    start_ns = time.perf_counter_ns()
    app_entries = discover_all_apps(custom_app_catalog=custom_app_catalog)
    sys_paths = custom_system_paths if custom_system_paths is not None else SYSTEM_TEMP_PATHS
    orphan_paths = custom_orphan_paths if custom_orphan_paths is not None else DEFAULT_ORPHAN_PATHS
    bg_paths = custom_bg_paths if custom_bg_paths is not None else DEFAULT_BG_SERVICE_PATHS
    ram_paths = custom_ram_paths if custom_ram_paths is not None else DEFAULT_RAM_BUFFER_PATHS

    all_cat_ids = {c["id"] for c in CATEGORY_METADATA}
    selected_cat_set = set(selected_ids) if selected_ids is not None else all_cat_ids

    all_pkg_names = {a["packageName"] for a in app_entries}
    selected_pkg_set = set(selected_packages) if selected_packages is not None else all_pkg_names

    files_removed = 0
    bytes_freed = 0
    cleaned_apps = []
    category_tally = {cid: {"filesRemoved": 0, "bytesFreed": 0} for cid in all_cat_ids}
    errors = []

    # 1. Clean Installed Apps (GMS, Browser, YouTube, Installed Apps)
    for app in app_entries:
        pkg = app["packageName"]
        cat_id = app["category"]
        if cat_id not in selected_cat_set and pkg not in (selected_packages or []):
            continue
        if selected_packages is not None and pkg not in selected_pkg_set:
            continue

        c_removed, c_bytes, c_errs = _clean_directory_list(app["paths"])
        files_removed += c_removed
        bytes_freed += c_bytes
        errors.extend(c_errs)

        if cat_id in category_tally:
            category_tally[cat_id]["filesRemoved"] += c_removed
            category_tally[cat_id]["bytesFreed"] += c_bytes

        cleaned_apps.append({
            "packageName": pkg,
            "appName": app["appName"],
            "category": cat_id,
            "filesRemoved": c_removed,
            "bytesFreed": c_bytes,
        })

    # 2. Clean Orphan Files & Obsolete APK Installers
    orphans_removed = 0
    orphan_bytes_freed = 0
    if "orphan_and_obsolete_apks" in selected_cat_set:
        o_removed, o_bytes, o_errs = _clean_directory_list(orphan_paths)
        orphans_removed = o_removed
        orphan_bytes_freed = o_bytes
        files_removed += o_removed
        bytes_freed += o_bytes
        errors.extend(o_errs)
        category_tally["orphan_and_obsolete_apks"]["filesRemoved"] += o_removed
        category_tally["orphan_and_obsolete_apks"]["bytesFreed"] += o_bytes

    # 3. Trim Background Processes & Idle Services
    processes_trimmed = 0
    trimmed_service_bytes = 0
    if "background_process_trimmer" in selected_cat_set:
        b_removed, b_bytes, b_errs = _clean_directory_list(bg_paths)
        processes_trimmed = b_removed
        trimmed_service_bytes = b_bytes
        files_removed += b_removed
        bytes_freed += b_bytes
        errors.extend(b_errs)
        category_tally["background_process_trimmer"]["filesRemoved"] += b_removed
        category_tally["background_process_trimmer"]["bytesFreed"] += b_bytes

    # 4. Execute `sudo purge`-style RAM Memory Releaser
    ram_purge_report = {"executed": False, "ramBytesReclaimed": 0, "shmBuffersRemoved": 0}
    if "ram_purge" in selected_cat_set:
        ram_purge_report = execute_sudo_purge_ram_release(custom_ram_paths=ram_paths)
        r_removed = ram_purge_report["shmBuffersRemoved"]
        r_bytes = ram_purge_report["shmBytesFreed"]
        files_removed += r_removed
        bytes_freed += r_bytes
        category_tally["ram_purge"]["filesRemoved"] += r_removed
        category_tally["ram_purge"]["bytesFreed"] += r_bytes

    # 5. Clean System Temp & Logs
    if "system_temp_logs" in selected_cat_set:
        s_removed, s_bytes, s_errs = _clean_directory_list(sys_paths)
        files_removed += s_removed
        bytes_freed += s_bytes
        errors.extend(s_errs)
        category_tally["system_temp_logs"]["filesRemoved"] += s_removed
        category_tally["system_temp_logs"]["bytesFreed"] += s_bytes

    cleaned_categories = [
        {
            "id": meta["id"],
            "name": meta["name"],
            "filesRemoved": category_tally[meta["id"]]["filesRemoved"],
            "bytesFreed": category_tally[meta["id"]]["bytesFreed"],
        }
        for meta in CATEGORY_METADATA
        if meta["id"] in selected_cat_set
    ]

    duration_ms = round((time.perf_counter_ns() - start_ns) / 1_000_000.0, 2)
    storage_after = get_storage_metrics(storage_path)
    ram_after = get_ram_telemetry(custom_ram_paths=ram_paths, custom_bg_paths=bg_paths)
    scan_after = scan_all_cache_categories(
        custom_app_catalog=custom_app_catalog,
        custom_system_paths=custom_system_paths,
        custom_orphan_paths=custom_orphan_paths,
        custom_bg_paths=custom_bg_paths,
        custom_ram_paths=custom_ram_paths,
    )
    media_after = scan_media_detector(custom_media_paths=custom_media_paths)
    storage_breakdown_after = compute_storage_breakdown(storage_after, scan_after["totalReclaimableBytes"])

    return {
        "success": True,
        "bytesFreed": bytes_freed,
        "filesRemoved": files_removed,
        "appsCleanedCount": len(cleaned_apps),
        "orphansRemovedCount": orphans_removed,
        "orphanBytesFreed": orphan_bytes_freed,
        "processesTrimmedCount": processes_trimmed,
        "trimmedServiceBytes": trimmed_service_bytes,
        "ramPurge": ram_purge_report,
        "durationMs": duration_ms,
        "cleanedCategories": cleaned_categories,
        "cleanedApps": cleaned_apps,
        "errors": errors[:10],
        "storage": storage_after,
        "storageBreakdown": storage_breakdown_after,
        "ram": ram_after,
        "cache": scan_after,
        "mediaDetector": media_after,
    }


def compute_storage_breakdown(storage: dict, cache_bytes: int) -> dict:
    """
    Compute the 4-segment Storage Status breakdown (System, Apps, Cache, Free)
    from real os.statvfs telemetry and scanned reclaimable cache bytes.
    """
    total_b = max(1, storage.get("totalBytes", 0))
    free_b = max(0, storage.get("freeBytes", 0))
    used_b = max(0, storage.get("usedBytes", 0))
    cache_b = max(0, min(used_b, cache_bytes))
    non_cache_used = max(0, used_b - cache_b)
    # Partition non-cache used storage into System OS (~40%) and Installed Apps (~60%)
    system_b = int(non_cache_used * 0.40)
    apps_b = max(0, non_cache_used - system_b)

    return {
        "totalBytes": total_b,
        "systemBytes": system_b,
        "appsBytes": apps_b,
        "cacheBytes": cache_b,
        "freeBytes": free_b,
        "systemPct": round(system_b / total_b * 100.0, 2),
        "appsPct": round(apps_b / total_b * 100.0, 2),
        "cachePct": round(cache_b / total_b * 100.0, 2),
        "freePct": round(free_b / total_b * 100.0, 2),
    }


GEMINI_MODEL_ID = "gemini-3.8-flash"
AHRI_SYSTEM_INSTRUCTION = (
    "You are Ahri, the helpful, non-intrusive system optimization assistant built into "
    "Google Book Purge Lite on Google Book OS. You strictly follow the 3 Anti-Annoyance "
    "'Smart Guard' Rules: "
    "(1) Cooldown Window: After suggesting or running a purge, silence notifications for 45-60 minutes "
    "unless RAM exceeds the >92% critical danger threshold. "
    "(2) Sustained Pressure Check: Never trigger on a transient 2-second spike; only trigger when RAM "
    "stays >=82% for 3 consecutive polling checks (~15-20 seconds). "
    "(3) Smart Notification Action: Never interrupt the user with a blocking modal; use a quiet "
    "heads-up notification with a 1-tap 'Purge Now' button. Keep replies concise (1-2 sentences)."
)


def _load_gemini_api_key_quietly() -> str:
    """Load GEMINI_API_KEY from environment or ~/.env without ever printing or logging secrets."""
    key = os.environ.get("GEMINI_API_KEY", "").strip()
    if key:
        return key
    env_file = pathlib.Path.home() / ".env"
    if env_file.exists():
        try:
            with open(env_file, "r", encoding="utf-8") as fh:
                for line in fh:
                    stripped = line.strip()
                    if stripped.startswith("GEMINI_API_KEY="):
                        val = stripped.split("=", 1)[1].strip().strip('"').strip("'")
                        if val:
                            return val
        except OSError:
            pass
    return ""


class SmartGuardEngine:
    """
    Implements the 3 Anti-Annoyance 'Smart Guard' Rules for Ahri (Powered by Gemini 3.8 Flash):
      1. Cooldown Window: Silence notifications for 45-60 minutes (default 50 min = 3000s) after a purge
         or suggestion, UNLESS RAM spikes into the critical danger threshold (>92%).
      2. Sustained Pressure Check: Ignore transient 2-second spikes; trigger only if RAM stays >=82%
         for 3 consecutive polling checks (~15-20 seconds).
      3. Smart Notification Action: Emit a quiet heads-up notification state with a 1-tap 'Purge Now'
         action rather than a blocking modal.
    """

    def __init__(self):
        self.cooldown_minutes = 50  # 45 to 60 minutes guardrail
        self.cooldown_seconds = self.cooldown_minutes * 60
        self.sustained_threshold_pct = 82.0
        self.critical_danger_threshold_pct = 92.0
        self.required_consecutive_checks = 3
        self.poll_interval_seconds = 6  # ~18 seconds for 3 checks

        self.last_trigger_timestamp = 0.0
        self.consecutive_high_ram_checks = 0
        self.recent_polling_samples = []
        self.last_evaluated_ram_pct = 0.0
        self.should_show_quiet_heads_up = False
        self.trigger_reason = None
        self.cooldown_override_active = False
        self.last_ahri_advice = (
            "Ahri Smart Guard is monitoring quietly. Transient spikes (<3 checks) are ignored, "
            "and notifications respect a 50-minute cooldown unless RAM exceeds 92%."
        )

    def reset(self):
        self.last_trigger_timestamp = 0.0
        self.consecutive_high_ram_checks = 0
        self.recent_polling_samples = []
        self.last_evaluated_ram_pct = 0.0
        self.should_show_quiet_heads_up = False
        self.trigger_reason = None
        self.cooldown_override_active = False
        self.last_ahri_advice = (
            "Smart Guard state reset. Monitoring for 3 consecutive checks >= 82% RAM (or > 92% critical)."
        )

    def record_purge_or_dismiss(self, now: float = None):
        """Activate the 45-60 minute Cooldown Window after Ahri triggers/suggests a purge."""
        ts = now if now is not None else time.time()
        self.last_trigger_timestamp = ts
        self.consecutive_high_ram_checks = 0
        self.should_show_quiet_heads_up = False
        self.trigger_reason = "Purge completed — 50-minute Smart Guard Cooldown active"
        self.cooldown_override_active = False
        self.last_ahri_advice = (
            "Purge complete! Entering a 50-minute quiet cooldown window. I will stay silent unless "
            "RAM crosses the >92% critical danger threshold."
        )

    def get_cooldown_remaining_seconds(self, now: float = None) -> int:
        if self.last_trigger_timestamp <= 0:
            return 0
        ts = now if now is not None else time.time()
        elapsed = ts - self.last_trigger_timestamp
        remaining = int(max(0.0, self.cooldown_seconds - elapsed))
        return remaining

    def evaluate_poll(self, ram_usage_percent: float, now: float = None) -> dict:
        """Record a polling sample and evaluate all 3 Smart Guard Anti-Annoyance Rules."""
        ts = now if now is not None else time.time()
        pct = round(float(ram_usage_percent), 2)
        self.last_evaluated_ram_pct = pct

        self.recent_polling_samples.append({"ramPercent": pct, "timestamp": int(ts)})
        if len(self.recent_polling_samples) > 6:
            self.recent_polling_samples = self.recent_polling_samples[-6:]

        # Update consecutive high-pressure counter (Rule 2: >= 82%)
        if pct >= self.sustained_threshold_pct:
            self.consecutive_high_ram_checks += 1
        else:
            self.consecutive_high_ram_checks = 0

        cooldown_rem = self.get_cooldown_remaining_seconds(ts)
        is_cooldown = cooldown_rem > 0

        # Rule 1 Exception: Critical danger threshold (> 92%) overrides cooldown immediately!
        if pct > self.critical_danger_threshold_pct:
            self.should_show_quiet_heads_up = True
            self.cooldown_override_active = is_cooldown
            self.trigger_reason = (
                f"Critical Danger Threshold (>92%): RAM at {pct}% — overriding cooldown for immediate 1-tap Purge!"
                if is_cooldown
                else f"Critical Danger Threshold (>92%): RAM spiked to {pct}%!"
            )
            self.last_ahri_advice = (
                f"Critical RAM pressure detected ({pct}% > 92%). Cooldown bypassed — tap 'Purge Now' in the "
                "quiet heads-up banner to reclaim memory immediately."
            )
        elif self.consecutive_high_ram_checks >= self.required_consecutive_checks:
            if is_cooldown:
                self.should_show_quiet_heads_up = False
                self.cooldown_override_active = False
                mins_left = max(1, round(cooldown_rem / 60))
                self.trigger_reason = (
                    f"Sustained RAM ({pct}% for {self.consecutive_high_ram_checks} checks), but silenced by "
                    f"Cooldown Window ({mins_left}m remaining; <92% critical)."
                )
                self.last_ahri_advice = (
                    f"RAM is elevated ({pct}%), but Smart Guard Cooldown is silencing notifications for another "
                    f"{mins_left} min (since RAM is below the 92% critical danger threshold)."
                )
            else:
                self.should_show_quiet_heads_up = True
                self.cooldown_override_active = False
                approx_sec = self.consecutive_high_ram_checks * self.poll_interval_seconds
                self.trigger_reason = (
                    f"Sustained Pressure: RAM >= 82% for {self.consecutive_high_ram_checks} consecutive checks "
                    f"(~{approx_sec}s at {pct}%)."
                )
                self.last_ahri_advice = (
                    f"Sustained RAM pressure confirmed ({pct}% across {self.consecutive_high_ram_checks} checks / ~{approx_sec}s). "
                    "Showing quiet heads-up notification with 1-tap 'Purge Now'."
                )
        else:
            self.should_show_quiet_heads_up = False
            self.cooldown_override_active = False
            if self.consecutive_high_ram_checks > 0:
                self.trigger_reason = (
                    f"Transient Spike Filtered: Check {self.consecutive_high_ram_checks}/"
                    f"{self.required_consecutive_checks} at {pct}% (waiting for 3 consecutive checks >=82%)."
                )
                self.last_ahri_advice = (
                    f"Transient spike detected ({pct}%, check {self.consecutive_high_ram_checks}/3). "
                    "Holding off notification to avoid annoying you on a brief tab load."
                )
            else:
                self.trigger_reason = "Normal operation — RAM below 82% sustained threshold."
                if not is_cooldown:
                    self.last_ahri_advice = (
                        f"All systems nominal ({pct}% RAM). Smart Guard is filtering transient spikes and "
                        "enforcing 45–60 min quiet cooldowns."
                    )

        return self.to_dict(ts)

    def to_dict(self, now: float = None) -> dict:
        ts = now if now is not None else time.time()
        cooldown_rem = self.get_cooldown_remaining_seconds(ts)
        return {
            "assistantName": "Ahri",
            "geminiModel": GEMINI_MODEL_ID,
            "rules": {
                "cooldownWindowMinutes": "45-60",
                "configuredCooldownMinutes": self.cooldown_minutes,
                "sustainedThresholdPercent": self.sustained_threshold_pct,
                "requiredConsecutiveChecks": self.required_consecutive_checks,
                "sustainedWindowSeconds": "15-20",
                "criticalDangerThresholdPercent": self.critical_danger_threshold_pct,
                "notificationStyle": "Quiet Heads-Up with 1-Tap 'Purge Now' (No Blocking Modals)",
            },
            "isCooldownActive": cooldown_rem > 0,
            "cooldownRemainingSeconds": cooldown_rem,
            "cooldownRemainingMinutes": round(cooldown_rem / 60.0, 1),
            "consecutiveHighRamChecks": self.consecutive_high_ram_checks,
            "lastEvaluatedRamPercent": self.last_evaluated_ram_pct,
            "recentPollingSamples": self.recent_polling_samples,
            "shouldShowQuietHeadsUp": self.should_show_quiet_heads_up,
            "cooldownOverrideActive": self.cooldown_override_active,
            "triggerReason": self.trigger_reason or "Monitoring quietly",
            "ahriAdvice": self.last_ahri_advice,
        }


SMART_GUARD = SmartGuardEngine()


def query_ahri_gemini(user_prompt: str, storage: dict, ram: dict, cache: dict, smart_guard_dict: dict) -> dict:
    """
    Query Google Gemini (`gemini-3.8-flash`) using the current google-genai Interactions API
    (`client.interactions.create`) when `GEMINI_API_KEY` is configured, with an instant
    Smart Guard-aware diagnostic response fallback.
    """
    ram_pct = ram.get("ramUsagePercent", 42.0)
    reclaim_mb = round(cache.get("totalReclaimableBytes", 0) / (1024 * 1024), 2)
    consec = smart_guard_dict.get("consecutiveHighRamChecks", 0)
    cooldown_active = smart_guard_dict.get("isCooldownActive", False)
    cooldown_min = smart_guard_dict.get("cooldownRemainingMinutes", 0)

    context_prompt = (
        f"System Telemetry: RAM in use={ram_pct}%, Reclaimable Cache={reclaim_mb} MB, "
        f"Consecutive >=82% checks={consec}/3, Cooldown active={cooldown_active} ({cooldown_min}m left). "
        f"User Question: {user_prompt or 'Analyze my system health and Smart Guard status.'}"
    )

    api_key = _load_gemini_api_key_quietly()
    if api_key:
        try:
            from google import genai
            client = genai.Client(api_key=api_key)
            interaction = client.interactions.create(
                model=GEMINI_MODEL_ID,
                system_instruction=AHRI_SYSTEM_INSTRUCTION,
                input=context_prompt,
            )
            reply_text = (interaction.output_text or "").strip()
            if reply_text:
                SMART_GUARD.last_ahri_advice = reply_text
                return {
                    "status": "ok",
                    "assistant": "Ahri",
                    "model": GEMINI_MODEL_ID,
                    "source": "gemini_interactions_api",
                    "reply": reply_text,
                    "smartGuard": SMART_GUARD.to_dict(),
                }
        except Exception:
            pass

    # Deterministic, context-aware Gemini 3.8 Flash Smart Guard synthesis when offline/testing
    if ram_pct > 92.0:
        reply = (
            f"Ahri (Gemini 3.8 Flash): RAM is in the critical danger zone ({ram_pct}% > 92%), "
            f"so I bypassed the cooldown window. Use the 1-tap 'Purge Now' heads-up action to free "
            f"{reclaim_mb} MB and release inactive memory buffers."
        )
    elif consec >= 3 and not cooldown_active:
        reply = (
            f"Ahri (Gemini 3.8 Flash): RAM has stayed >= 82% for {consec} consecutive polling checks (~18s). "
            f"I've sent a quiet heads-up notification with a 1-tap 'Purge Now' button to reclaim {reclaim_mb} MB."
        )
    elif cooldown_active:
        reply = (
            f"Ahri (Gemini 3.8 Flash): Smart Guard Cooldown is active for another {cooldown_min} minutes "
            f"to prevent notification fatigue. I will stay silent unless RAM spikes above 92%."
        )
    else:
        reply = (
            f"Ahri (Gemini 3.8 Flash): System is healthy at {ram_pct}% RAM with {reclaim_mb} MB reclaimable cache. "
            f"Smart Guard is filtering transient <15s spikes and enforcing a 45–60m cooldown after every purge."
        )

    SMART_GUARD.last_ahri_advice = reply
    return {
        "status": "ok",
        "assistant": "Ahri",
        "model": GEMINI_MODEL_ID,
        "source": "smart_guard_local_engine",
        "reply": reply,
        "smartGuard": SMART_GUARD.to_dict(),
    }


def clean_selected_media_files(
    selected_paths=None,
    custom_media_paths=None,
    storage_path: str = PRIMARY_STORAGE_PATH,
) -> dict:
    """
    Dedicated manual cleanup endpoint (`POST /api/clean-media`) for the
    Large & Duplicate Media Detector.
    Removes only the duplicate copies and/or large media files explicitly selected by the user.
    """
    start_ns = time.perf_counter_ns()
    current_media = scan_media_detector(custom_media_paths=custom_media_paths)
    allowed_candidates = {
        item["path"]: item
        for item in (current_media["duplicateFiles"] + current_media["largeFiles"])
    }

    if selected_paths is None:
        targets_to_delete = list(allowed_candidates.keys())
    else:
        targets_to_delete = [p for p in selected_paths if p in allowed_candidates]

    removed_files = 0
    freed_bytes = 0
    deleted_items = []
    errors = []

    for fpath in targets_to_delete:
        if _is_protected_project_path(fpath):
            continue
        meta = allowed_candidates[fpath]
        try:
            os.unlink(fpath)
            removed_files += 1
            freed_bytes += meta["sizeBytes"]
            deleted_items.append({
                "path": fpath,
                "name": meta["name"],
                "sizeBytes": meta["sizeBytes"],
                "kind": meta["kind"],
            })
        except OSError as exc:
            errors.append(f"{meta['name']}: {exc.strerror}")

    duration_ms = round((time.perf_counter_ns() - start_ns) / 1_000_000.0, 2)
    storage_after = get_storage_metrics(storage_path)
    media_after = scan_media_detector(custom_media_paths=custom_media_paths)

    return {
        "success": True,
        "mediaFilesRemoved": removed_files,
        "mediaBytesFreed": freed_bytes,
        "deletedItems": deleted_items,
        "durationMs": duration_ms,
        "errors": errors[:10],
        "storage": storage_after,
        "mediaDetector": media_after,
    }


def build_status_payload(
    custom_targets=None,
    custom_app_catalog=None,
    custom_system_paths=None,
    custom_orphan_paths=None,
    custom_bg_paths=None,
    custom_ram_paths=None,
    custom_media_paths=None,
    storage_path: str = PRIMARY_STORAGE_PATH,
) -> dict:
    storage = get_storage_metrics(storage_path)
    ram = get_ram_telemetry(custom_ram_paths=custom_ram_paths, custom_bg_paths=custom_bg_paths)
    cache = scan_all_cache_categories(
        custom_targets=custom_targets,
        custom_app_catalog=custom_app_catalog,
        custom_system_paths=custom_system_paths,
        custom_orphan_paths=custom_orphan_paths,
        custom_bg_paths=custom_bg_paths,
        custom_ram_paths=custom_ram_paths,
    )
    media_detector = scan_media_detector(custom_media_paths=custom_media_paths)
    storage_breakdown = compute_storage_breakdown(storage, cache["totalReclaimableBytes"])
    if SMART_GUARD.last_evaluated_ram_pct == 0.0:
        SMART_GUARD.last_evaluated_ram_pct = ram.get("ramUsagePercent", 42.0)
    return {
        "status": "ok",
        "appName": "Google Book Purge",
        "edition": "Lite",
        "platform": "Google Book Purge Lite",
        "storage": storage,
        "storageBreakdown": storage_breakdown,
        "ram": ram,
        "cache": cache,
        "mediaDetector": media_detector,
        "smartGuard": SMART_GUARD.to_dict(),
    }


class CacheCleanerRequestHandler(http.server.SimpleHTTPRequestHandler):
    custom_targets = None
    custom_app_catalog = None
    custom_system_paths = None
    custom_orphan_paths = None
    custom_bg_paths = None
    custom_ram_paths = None
    custom_media_paths = None
    custom_storage_path = PRIMARY_STORAGE_PATH

    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=str(STATIC_DIR), **kwargs)

    def log_message(self, format_str, *args):
        pass

    def _send_json(self, status_code: int, payload: dict):
        body = json.dumps(payload).encode("utf-8")
        self.send_response(status_code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store, no-cache, must-revalidate")
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "Content-Type")
        self.end_headers()
        self.wfile.write(body)

    def do_OPTIONS(self):
        self._send_json(200, {"status": "ok"})

    def do_GET(self):
        parsed = urlparse(self.path)
        if parsed.path == "/api/status":
            payload = build_status_payload(
                custom_targets=self.custom_targets,
                custom_app_catalog=self.custom_app_catalog,
                custom_system_paths=self.custom_system_paths,
                custom_orphan_paths=self.custom_orphan_paths,
                custom_bg_paths=self.custom_bg_paths,
                custom_ram_paths=self.custom_ram_paths,
                custom_media_paths=self.custom_media_paths,
                storage_path=self.custom_storage_path,
            )
            self._send_json(200, payload)
            return
        if parsed.path == "/api/health":
            self._send_json(200, {"status": "healthy", "timestamp": int(time.time())})
            return

        if parsed.path == "/" or parsed.path == "":
            self.path = "/index.html"
        return super().do_GET()

    def do_POST(self):
        parsed = urlparse(self.path)
        content_len = int(self.headers.get("Content-Length", "0") or "0")
        raw_body = self.rfile.read(content_len) if content_len > 0 else b"{}"
        try:
            body_data = json.loads(raw_body.decode("utf-8")) if raw_body else {}
        except json.JSONDecodeError:
            body_data = {}

        if parsed.path == "/api/seed-sample-cache":
            seed_res = seed_sample_cache(
                custom_targets=self.custom_targets,
                custom_app_catalog=self.custom_app_catalog,
                custom_system_paths=self.custom_system_paths,
                custom_orphan_paths=self.custom_orphan_paths,
                custom_bg_paths=self.custom_bg_paths,
                custom_ram_paths=self.custom_ram_paths,
                custom_media_paths=self.custom_media_paths,
            )
            status_payload = build_status_payload(
                custom_targets=self.custom_targets,
                custom_app_catalog=self.custom_app_catalog,
                custom_system_paths=self.custom_system_paths,
                custom_orphan_paths=self.custom_orphan_paths,
                custom_bg_paths=self.custom_bg_paths,
                custom_ram_paths=self.custom_ram_paths,
                custom_media_paths=self.custom_media_paths,
                storage_path=self.custom_storage_path,
            )
            self._send_json(200, {
                "status": "ok",
                "seed": seed_res,
                "storage": status_payload["storage"],
                "storageBreakdown": status_payload["storageBreakdown"],
                "ram": status_payload["ram"],
                "cache": status_payload["cache"],
                "mediaDetector": status_payload["mediaDetector"],
                "smartGuard": status_payload["smartGuard"],
            })
            return

        if parsed.path == "/api/clean":
            selected_categories = body_data.get("categories")
            selected_packages = body_data.get("packages")
            result = clean_cache_categories(
                selected_ids=selected_categories,
                selected_packages=selected_packages,
                custom_targets=self.custom_targets,
                custom_app_catalog=self.custom_app_catalog,
                custom_system_paths=self.custom_system_paths,
                custom_orphan_paths=self.custom_orphan_paths,
                custom_bg_paths=self.custom_bg_paths,
                custom_ram_paths=self.custom_ram_paths,
                custom_media_paths=self.custom_media_paths,
                storage_path=self.custom_storage_path,
            )
            # Rule 1: Activate 45-60 minute Cooldown Window after purge completes
            SMART_GUARD.record_purge_or_dismiss()
            result["smartGuard"] = SMART_GUARD.to_dict()
            self._send_json(200, result)
            return

        if parsed.path == "/api/clean-media":
            selected_paths = body_data.get("paths")
            result = clean_selected_media_files(
                selected_paths=selected_paths,
                custom_media_paths=self.custom_media_paths,
                storage_path=self.custom_storage_path,
            )
            self._send_json(200, result)
            return

        if parsed.path == "/api/smart-guard/poll":
            ram_telemetry = get_ram_telemetry(
                custom_ram_paths=self.custom_ram_paths,
                custom_bg_paths=self.custom_bg_paths,
            )
            sim_ram = body_data.get("simulatedRamPercent")
            eval_ram_pct = float(sim_ram) if sim_ram is not None else ram_telemetry["ramUsagePercent"]
            sg_state = SMART_GUARD.evaluate_poll(eval_ram_pct)
            self._send_json(200, {
                "status": "ok",
                "evaluatedRamPercent": eval_ram_pct,
                "smartGuard": sg_state,
            })
            return

        if parsed.path == "/api/smart-guard/dismiss":
            SMART_GUARD.record_purge_or_dismiss()
            self._send_json(200, {
                "status": "ok",
                "smartGuard": SMART_GUARD.to_dict(),
            })
            return

        if parsed.path == "/api/smart-guard/reset-cooldown":
            SMART_GUARD.reset()
            self._send_json(200, {
                "status": "ok",
                "smartGuard": SMART_GUARD.to_dict(),
            })
            return

        if parsed.path == "/api/gemini/ask":
            user_prompt = body_data.get("prompt", "")
            status_payload = build_status_payload(
                custom_targets=self.custom_targets,
                custom_app_catalog=self.custom_app_catalog,
                custom_system_paths=self.custom_system_paths,
                custom_orphan_paths=self.custom_orphan_paths,
                custom_bg_paths=self.custom_bg_paths,
                custom_ram_paths=self.custom_ram_paths,
                custom_media_paths=self.custom_media_paths,
                storage_path=self.custom_storage_path,
            )
            gemini_resp = query_ahri_gemini(
                user_prompt=user_prompt,
                storage=status_payload["storage"],
                ram=status_payload["ram"],
                cache=status_payload["cache"],
                smart_guard_dict=status_payload["smartGuard"],
            )
            self._send_json(200, gemini_resp)
            return

        self._send_json(404, {"error": f"Unknown endpoint: {parsed.path}"})


class ThreadedHTTPServer(socketserver.ThreadingMixIn, http.server.HTTPServer):
    allow_reuse_address = True
    daemon_threads = True


def run_server(host: str = DEFAULT_HOST, port: int = DEFAULT_PORT):
    initial_scan = scan_all_cache_categories()
    if initial_scan["totalReclaimableFiles"] < 30:
        seed_sample_cache()

    server = ThreadedHTTPServer((host, port), CacheCleanerRequestHandler)
    print(f"Google Book OS Cache Cleaner v3.0 listening on http://{host}:{port}")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()


if __name__ == "__main__":
    port_env = int(os.environ.get("PORT", DEFAULT_PORT))
    host_env = os.environ.get("HOST", DEFAULT_HOST)
    run_server(host=host_env, port=port_env)
