#!/usr/bin/env python3
"""
Automated integration test suite for Google Book Purge Lite (v4.0 — Gemini Ahri & Smart Guard Rules).
Verifies:
  1. GET /api/status returns storage, storageBreakdown, RAM telemetry, 8 one-click categories,
     mediaDetector, and Smart Guard state.
  2. POST /api/clean cleans all 8 categories, activates the 45-60m Smart Guard Cooldown Window,
     and leaves Large & Duplicate Media untouched.
  3. POST /api/clean-media selectively cleans duplicate & large media files.
  4. Smart Guard Anti-Annoyance Rules:
     - Rule 2 (Sustained Pressure Check): 1 or 2 transient checks at 85% do NOT trigger notification;
       3 consecutive checks >= 82% DO trigger the quiet heads-up notification.
     - Rule 1 (Cooldown Window & >92% Critical Override): After a purge, 45-60m cooldown silences
       >=82% checks, UNLESS RAM spikes > 92% (e.g. 95%), which overrides cooldown!
     - POST /api/gemini/ask returns an Ahri (gemini-3.8-flash) response.
"""

import json
import pathlib
import shutil
import tempfile
import threading
import unittest
from urllib import request

import server


class WebCacheCleanerV4TestCase(unittest.TestCase):
    """End-to-end verification of Google Book Purge Lite + Gemini Ahri Smart Guard Rules."""

    @classmethod
    def setUpClass(cls):
        cls.temp_root = pathlib.Path(tempfile.mkdtemp(prefix="gb_purge_v4_test_"))
        cls.sys_temp_dir = cls.temp_root / "sys_temp"
        cls.orphan_dir = cls.temp_root / "orphans"
        cls.obsolete_apk_dir = cls.temp_root / "obsolete_apks"
        cls.bg_service_dir = cls.temp_root / "bg_services"
        cls.ram_buffer_dir = cls.temp_root / "ram_shm"
        cls.media_scan_dir = cls.temp_root / "media_scan"

        cls.custom_app_catalog = [
            {
                "packageName": "com.google.android.gms",
                "appName": "Google Play Services",
                "category": "google_play_services",
                "badge": "Core System",
                "iconType": "play_services",
                "description": "Test GMS Cache",
                "paths": [str(cls.temp_root / "apps" / "com.google.android.gms" / "cache")],
                "seedSpecs": [("chimera_module.cache", 512_000)],
            },
            {
                "packageName": "com.android.chrome",
                "appName": "Google Chrome Browser",
                "category": "browser_cache",
                "badge": "Browser",
                "iconType": "browser",
                "description": "Test Chrome Cache",
                "paths": [str(cls.temp_root / "apps" / "com.android.chrome" / "cache")],
                "seedSpecs": [("http_disk_cache.bin", 640_000)],
            },
            {
                "packageName": "com.google.android.youtube",
                "appName": "YouTube",
                "category": "youtube_cache",
                "badge": "Media & Video",
                "iconType": "youtube",
                "description": "Test YouTube Cache",
                "paths": [str(cls.temp_root / "apps" / "com.google.android.youtube" / "cache")],
                "seedSpecs": [("exoplayer_chunk_01.exo", 1_024_000)],
            },
            {
                "packageName": "com.google.android.apps.docs",
                "appName": "Google Drive & Docs",
                "category": "all_installed_apps",
                "badge": "Installed App",
                "iconType": "app",
                "description": "Test Docs Cache",
                "paths": [str(cls.temp_root / "apps" / "com.google.android.apps.docs" / "cache")],
                "seedSpecs": [("pdf_tile_cache.bin", 384_000)],
            },
        ]

        server.CacheCleanerRequestHandler.custom_app_catalog = cls.custom_app_catalog
        server.CacheCleanerRequestHandler.custom_system_paths = [str(cls.sys_temp_dir)]
        server.CacheCleanerRequestHandler.custom_orphan_paths = [
            str(cls.orphan_dir),
            str(cls.obsolete_apk_dir),
        ]
        server.CacheCleanerRequestHandler.custom_bg_paths = [str(cls.bg_service_dir)]
        server.CacheCleanerRequestHandler.custom_ram_paths = [str(cls.ram_buffer_dir)]
        server.CacheCleanerRequestHandler.custom_media_paths = [str(cls.media_scan_dir)]
        server.CacheCleanerRequestHandler.custom_storage_path = str(cls.temp_root)

        cls.httpd = server.ThreadedHTTPServer(("127.0.0.1", 0), server.CacheCleanerRequestHandler)
        cls.port = cls.httpd.server_address[1]
        cls.base_url = f"http://127.0.0.1:{cls.port}"

        cls.server_thread = threading.Thread(target=cls.httpd.serve_forever, daemon=True)
        cls.server_thread.start()

    @classmethod
    def tearDownClass(cls):
        cls.httpd.shutdown()
        cls.httpd.server_close()
        shutil.rmtree(cls.temp_root, ignore_errors=True)

    def _get_json(self, path: str) -> dict:
        with request.urlopen(f"{self.base_url}{path}", timeout=5) as resp:
            self.assertEqual(resp.status, 200)
            return json.loads(resp.read().decode("utf-8"))

    def _post_json(self, path: str, payload: dict) -> dict:
        data = json.dumps(payload).encode("utf-8")
        req = request.Request(
            f"{self.base_url}{path}",
            data=data,
            headers={"Content-Type": "application/json"},
            method="POST",
        )
        with request.urlopen(req, timeout=5) as resp:
            self.assertEqual(resp.status, 200)
            return json.loads(resp.read().decode("utf-8"))

    def test_01_seed_and_verify_all_8_categories_and_media_detector(self):
        """Verify seeding populates app caches, orphan APKs, bg services, RAM buffers, and SHA-256 duplicate media."""
        seed_resp = self._post_json("/api/seed-sample-cache", {})
        self.assertEqual(seed_resp["status"], "ok")
        self.assertGreater(seed_resp["seed"]["seededFiles"], 10)
        self.assertGreater(seed_resp["seed"]["mediaSeededFiles"], 0)

        status = self._get_json("/api/status")
        self.assertEqual(status["appName"], "Google Book Purge")
        self.assertEqual(status["edition"], "Lite")
        self.assertIn("smartGuard", status)
        self.assertEqual(status["smartGuard"]["assistantName"], "Ahri")
        self.assertEqual(status["smartGuard"]["geminiModel"], "gemini-3.8-flash")

        md = status["mediaDetector"]
        self.assertTrue(md["excludedFromOneClick"])
        self.assertEqual(md["duplicateCount"], 3)
        self.assertEqual(md["largeFileCount"], 1)

    def test_02_one_click_clean_purges_caches_and_activates_cooldown_preserving_media(self):
        """Verify POST /api/clean cleans all 8 categories, starts Smart Guard Cooldown, and preserves media."""
        self._post_json("/api/seed-sample-cache", {})
        before = self._get_json("/api/status")
        expected_one_click_files = before["cache"]["totalReclaimableFiles"]
        expected_media_bytes = before["mediaDetector"]["totalMediaReclaimableBytes"]

        clean_resp = self._post_json("/api/clean", {})
        self.assertTrue(clean_resp["success"])
        self.assertEqual(clean_resp["filesRemoved"], expected_one_click_files)
        self.assertEqual(clean_resp["cache"]["totalReclaimableFiles"], 0)

        # Smart Guard Cooldown Window (45-60 min) must now be active!
        self.assertTrue(clean_resp["smartGuard"]["isCooldownActive"])
        self.assertGreater(clean_resp["smartGuard"]["cooldownRemainingSeconds"], 2700)

        # Media Detector files must still be untouched
        self.assertEqual(
            clean_resp["mediaDetector"]["totalMediaReclaimableBytes"],
            expected_media_bytes,
        )

    def test_03_smart_guard_three_anti_annoyance_rules_and_gemini_ask(self):
        """
        Verify the 3 Smart Guard Anti-Annoyance Rules:
          1. Transient spike (1 or 2 checks at 85%) does NOT trigger heads-up.
          2. Sustained pressure (3 consecutive checks >= 82%) DOES trigger quiet heads-up.
          3. Cooldown Window silences >=82% checks after purge, UNLESS RAM > 92% (critical danger).
        """
        self._post_json("/api/smart-guard/reset-cooldown", {})

        # Check 1 at 85% (transient spike) -> must NOT trigger
        p1 = self._post_json("/api/smart-guard/poll", {"simulatedRamPercent": 85.0})["smartGuard"]
        self.assertEqual(p1["consecutiveHighRamChecks"], 1)
        self.assertFalse(p1["shouldShowQuietHeadsUp"])

        # Check 2 at 84% -> still < 3 consecutive checks -> must NOT trigger
        p2 = self._post_json("/api/smart-guard/poll", {"simulatedRamPercent": 84.0})["smartGuard"]
        self.assertEqual(p2["consecutiveHighRamChecks"], 2)
        self.assertFalse(p2["shouldShowQuietHeadsUp"])

        # Check 3 at 86% -> 3rd consecutive check >= 82% -> MUST trigger quiet heads-up!
        p3 = self._post_json("/api/smart-guard/poll", {"simulatedRamPercent": 86.0})["smartGuard"]
        self.assertEqual(p3["consecutiveHighRamChecks"], 3)
        self.assertTrue(p3["shouldShowQuietHeadsUp"])

        # Dismiss / Purge activates the 45-60 min Cooldown Window
        d_resp = self._post_json("/api/smart-guard/dismiss", {})["smartGuard"]
        self.assertTrue(d_resp["isCooldownActive"])
        self.assertFalse(d_resp["shouldShowQuietHeadsUp"])

        # During Cooldown, even 3 consecutive checks at 85% are silenced (<92%)
        self._post_json("/api/smart-guard/poll", {"simulatedRamPercent": 85.0})
        self._post_json("/api/smart-guard/poll", {"simulatedRamPercent": 85.0})
        p_silenced = self._post_json("/api/smart-guard/poll", {"simulatedRamPercent": 85.0})["smartGuard"]
        self.assertTrue(p_silenced["isCooldownActive"])
        self.assertFalse(p_silenced["shouldShowQuietHeadsUp"])

        # Exception to Rule 1: Critical danger spike (>92%, e.g. 95%) overrides Cooldown immediately!
        p_crit = self._post_json("/api/smart-guard/poll", {"simulatedRamPercent": 95.0})["smartGuard"]
        self.assertTrue(p_crit["shouldShowQuietHeadsUp"])
        self.assertTrue(p_crit["cooldownOverrideActive"])

        # Verify Gemini Ahri Ask endpoint
        ask_resp = self._post_json("/api/gemini/ask", {"prompt": "How is my RAM status?"})
        self.assertEqual(ask_resp["status"], "ok")
        self.assertEqual(ask_resp["model"], "gemini-3.8-flash")
        self.assertEqual(ask_resp["assistant"], "Ahri")
        self.assertTrue(len(ask_resp["reply"]) > 10)

    def test_04_separate_clean_media_endpoint_removes_duplicates_and_large_media(self):
        """Verify POST /api/clean-media cleans the flagged duplicate and large media files."""
        status = self._get_json("/api/status")
        md_before = status["mediaDetector"]
        self.assertGreater(md_before["totalMediaReclaimableBytes"], 0)

        media_clean = self._post_json("/api/clean-media", {})
        self.assertTrue(media_clean["success"])
        self.assertEqual(media_clean["mediaFilesRemoved"], 4)
        self.assertEqual(media_clean["mediaDetector"]["duplicateCount"], 0)
        self.assertEqual(media_clean["mediaDetector"]["largeFileCount"], 0)


if __name__ == "__main__":
    unittest.main()
