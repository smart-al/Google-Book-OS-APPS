package com.droid.liquidglass3d

import android.content.Context
import android.graphics.Bitmap
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LiquidGlassRobolectricTest {

    private val context: Context
        get() = RuntimeEnvironment.getApplication()

    @Test
    fun testSceneStoreSaveAndLoadSettings() {
        val initial = SceneStore.loadSettings(context)
        assertNotNull(initial)

        val updated = GlassSettings(
            spatialEnabled = false,
            depthIntensity = 1.35f,
            refraction = 0.95f,
            glassBlend = 0.40f,
            maskMode = MaskMode.PORTRAIT,
            clockScale = 1.20f,
            clockPosX = 0.75f,
            clockPosY = 0.30f,
            clockInFront = true,
            clockColorPreset = ClockColorPreset.SOLAR_FLARE,
            clockFont = ClockFont.MODERN,
            showDate = false,
            solidColor = 0xFF1E88E5.toInt(),
            mixedColorA = 0xFFE53935.toInt(),
            mixedColorB = 0xFF00F5D4.toInt(),
            mixWeight = 0.5f,
            livingBackgroundEnabled = false,
            livingMotionMode = LivingMotionMode.OCEAN,
            livingMotionIntensity = 1.15f,
            popOut3d = false,
        )

        SceneStore.saveSettings(context, updated)
        val loaded = SceneStore.loadSettings(context)

        assertEquals(updated.spatialEnabled, loaded.spatialEnabled)
        assertEquals(updated.depthIntensity, loaded.depthIntensity, 0.001f)
        assertEquals(updated.refraction, loaded.refraction, 0.001f)
        assertEquals(updated.glassBlend, loaded.glassBlend, 0.001f)
        assertEquals(updated.maskMode, loaded.maskMode)
        assertEquals(updated.clockScale, loaded.clockScale, 0.001f)
        assertEquals(updated.clockPosX, loaded.clockPosX, 0.001f)
        assertEquals(updated.clockPosY, loaded.clockPosY, 0.001f)
        assertEquals(updated.clockInFront, loaded.clockInFront)
        assertEquals(updated.clockColorPreset, loaded.clockColorPreset)
        assertEquals(updated.clockFont, loaded.clockFont)
        assertEquals(updated.showDate, loaded.showDate)
        assertEquals(updated.solidColor, loaded.solidColor)
        assertEquals(updated.mixedColorA, loaded.mixedColorA)
        assertEquals(updated.mixedColorB, loaded.mixedColorB)
        assertEquals(updated.mixWeight, loaded.mixWeight, 0.001f)
        assertEquals(updated.livingBackgroundEnabled, loaded.livingBackgroundEnabled)
        assertEquals(updated.livingMotionMode, loaded.livingMotionMode)
        assertEquals(updated.livingMotionIntensity, loaded.livingMotionIntensity, 0.001f)
        assertEquals(updated.popOut3d, loaded.popOut3d)
    }

    @Test
    fun testSceneStoreDetectedModePersistence() {
        SceneStore.saveDetectedMode(context, MaskMode.NATURE)
        val detected = SceneStore.loadDetectedMode(context)
        assertEquals(MaskMode.NATURE, detected)

        SceneStore.saveDetectedMode(context, MaskMode.PORTRAIT)
        assertEquals(MaskMode.PORTRAIT, SceneStore.loadDetectedMode(context))

        SceneStore.saveDetectedMotion(context, LivingMotionMode.OCEAN)
        assertEquals(LivingMotionMode.OCEAN, SceneStore.loadDetectedMotion(context))
    }

    @Test
    fun testSceneStoreVersionIncrement() {
        val prefs = SceneStore.prefs(context)
        val initialVer = prefs.getLong(SceneStore.KEY_SCENE_VERSION, 0L)
        SceneStore.bumpSceneVersion(context)
        val nextVer = prefs.getLong(SceneStore.KEY_SCENE_VERSION, 0L)
        assertEquals(initialVer + 1, nextVer)
    }

    @Test
    fun testSceneStoreHasSceneWhenFilesMissing() {
        val dir = context.filesDir
        File(dir, SceneStore.SOURCE_FILE).delete()
        File(dir, SceneStore.DEPTH_FILE).delete()
        File(dir, SceneStore.MASK_FILE).delete()

        assertFalse(SceneStore.hasScene(context))
        assertEquals(null, SceneStore.loadScene(context))
    }

    @Test
    fun testParallaxControllerPointerAndSmoothing() {
        val controller = ParallaxController(context)
        assertEquals(0f, controller.x, 0.001f)
        assertEquals(0f, controller.y, 0.001f)

        // Simulate pointer at right-bottom edge
        controller.onPointer(1000f, 1000f, 1000f, 1000f, active = true)
        // Advance smoothing steps
        for (i in 0 until 10) {
            controller.step(0.016f)
        }
        assertTrue("X should deflect positively towards pointer", controller.x > 0.05f)
        assertTrue("Y should deflect positively towards pointer", controller.y > 0.05f)

        // Test page offset
        controller.onPageOffset(1.0f)
        controller.step(0.016f)
        assertTrue("X should respond to page offset", controller.x > 0.05f)

        // Test pointer exit
        controller.onPointerExit()
        controller.step(0.016f)

        controller.start()
        controller.stop()
    }

    @Test
    fun testDepthModelMissingException() {
        val ex = DepthModelMissingException("test_model.tflite")
        assertNotNull(ex.message)
        assertTrue(ex.message!!.contains("test_model.tflite"))
    }

    @Test
    fun testPreparedSceneHolder() {
        val bmp1 = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
        val bmp2 = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
        val bmp3 = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)

        val prepared = PreparedScene(bmp1, bmp2, bmp3, 10, 10)
        assertEquals(10, prepared.width)
        assertEquals(10, prepared.height)
        assertEquals(bmp1, prepared.photo)
        assertEquals(bmp2, prepared.depth)
        assertEquals(bmp3, prepared.mask)
    }

    @Test
    fun testSceneBitmapsHolder() {
        val bmp1 = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
        val bmp2 = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
        val bmp3 = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)

        val scene = SceneBitmaps(bmp1, bmp2, bmp3, MaskMode.PORTRAIT)
        assertEquals(bmp1, scene.photo)
        assertEquals(bmp2, scene.depth)
        assertEquals(bmp3, scene.mask)
        assertEquals(MaskMode.PORTRAIT, scene.detectedMode)
    }

    @Test
    fun testDepthEstimatorNormalize() {
        val estimator = DepthEstimator(context)
        val raw = FloatArray(100) { it.toFloat() } // 0..99
        val normalized = estimator.normalize(raw)
        assertEquals(100, normalized.size)
        // 2nd percentile is around 2, 98th percentile is around 97
        assertTrue("Min normalized should be clamped to 0f", normalized.first() <= 0.01f)
        assertTrue("Max normalized should be clamped to 255f", normalized.last() >= 254.9f)
        // Monotonicity check
        for (i in 1 until normalized.size) {
            assertTrue(normalized[i] >= normalized[i - 1])
        }
    }

    @Test
    fun testDepthEstimatorUpsample() {
        val estimator = DepthEstimator(context)
        // 2x2 source
        val src = floatArrayOf(
            0f, 100f,
            50f, 150f
        )
        // Upsample to 4x4
        val dst = estimator.upsample(src, 2, 2, 4, 4)
        assertEquals(16, dst.size)
        // Bilinear interpolation bounds check
        for (v in dst) {
            assertTrue(v in 0f..150f)
        }
        // Top-left should be close to 0f, bottom-right should be close to 150f
        assertTrue(dst[0] < dst[15])
    }

    @Test
    fun testDepthEstimatorBoxBlur() {
        val estimator = DepthEstimator(context)
        val w = 10
        val h = 10
        // Uniform array should remain identical after blur
        val uniform = FloatArray(w * h) { 42f }
        val blurred = estimator.boxBlur(uniform, w, h, 2)
        assertEquals(w * h, blurred.size)
        for (v in blurred) {
            assertEquals(42f, v, 0.01f)
        }

        // Single peak impulse should be smoothed/diffused
        val impulse = FloatArray(w * h)
        impulse[5 * w + 5] = 100f
        val blurredImpulse = estimator.boxBlur(impulse, w, h, 2)
        assertTrue("Peak should be attenuated", blurredImpulse[5 * w + 5] < 100f)
        assertTrue("Neighbors should receive diffused energy", blurredImpulse[5 * w + 4] > 0f)
    }

    @Test
    fun testDepthEstimatorNatureDepthMask() {
        val estimator = DepthEstimator(context)
        val w = 20
        val h = 20
        // Near ramp: depth increasing from 0 to 255
        val depth = FloatArray(w * h) { (it * 255f) / (w * h) }
        val mask = estimator.buildNatureDepthMask(depth, w, h)
        assertEquals(w * h, mask.size)
        for (v in mask) {
            assertTrue("Mask value should be strictly in 0..255 range, was $v", v in 0f..255f)
        }
    }

    @Test
    fun testRendererCoverCrop() {
        val renderer = LiquidGlassRenderer(context)
        val src = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        val dst16_10 = renderer.coverCrop(src, 160, 100)
        assertEquals(160, dst16_10.width)
        assertEquals(100, dst16_10.height)

        val dstPortrait = renderer.coverCrop(src, 100, 160)
        assertEquals(100, dstPortrait.width)
        assertEquals(160, dstPortrait.height)
    }

    @Test
    fun testRendererPanelRectInitial() {
        val renderer = LiquidGlassRenderer(context)
        val rect = renderer.getPanelRect()
        assertNotNull(rect)
        assertEquals(0f, rect.left, 0.001f)
        assertEquals(0f, rect.top, 0.001f)
    }

    @Test
    fun testSceneStoreDemoAndActivationFlow() {
        // Sample image was removed; loadDemoScene returns null
        val scene = SceneStore.loadDemoScene(context)
        assertEquals(null, scene)
    }

    @Test
    fun testMainViewModelSettingsMutation() {
        val app = RuntimeEnvironment.getApplication()
        val vm = MainViewModel(app)
        assertNotNull(vm.state.value)
        
        vm.updateSettings { it.copy(clockScale = 1.35f, showDate = false, clockInFront = true) }
        assertEquals(1.35f, vm.state.value.settings.clockScale, 0.001f)
        assertFalse(vm.state.value.settings.showDate)
        assertTrue(vm.state.value.settings.clockInFront)

        vm.selectMaskMode(MaskMode.NATURE)
        assertEquals(MaskMode.NATURE, vm.state.value.settings.maskMode)
    }

    @Test
    fun testLiquidGlassWallpaperServiceEngineCreation() {
        val controller = Robolectric.buildService(LiquidGlassWallpaperService::class.java)
        val service = controller.create().get()
        assertNotNull(service)

        val engine = service.onCreateEngine()
        assertNotNull(engine)
        controller.destroy()
    }

    @Test
    fun testParallaxControllerZeroDimensionGuard() {
        val controller = ParallaxController(context)
        // Calling onPointer with 0 width/height should be a safe no-op
        controller.onPointer(100f, 100f, 0f, 0f, active = true)
        controller.step(0.016f)
        assertEquals(0f, controller.x, 0.001f)
        assertEquals(0f, controller.y, 0.001f)
    }

    @Test
    fun testSceneStoreInvalidSettingsFallback() {
        val prefs = SceneStore.prefs(context)
        prefs.edit()
            .putString(SceneStore.KEY_MASK_MODE, "INVALID_NON_EXISTENT_MODE")
            .putString(SceneStore.KEY_CLOCK_FONT, "INVALID_NON_EXISTENT_FONT")
            .putString(SceneStore.KEY_CLOCK_COLOR_PRESET, "INVALID_NON_EXISTENT_PRESET")
            .commit()

        val loaded = SceneStore.loadSettings(context)
        val defaults = GlassSettings()
        // Should gracefully fall back to defaults
        assertEquals(defaults.maskMode, loaded.maskMode)
        assertEquals(defaults.clockFont, loaded.clockFont)
        assertEquals(defaults.clockColorPreset, loaded.clockColorPreset)
    }

    @Test
    fun testGeminiApiKeyPersistence() {
        assertEquals("", SceneStore.loadGeminiApiKey(context))
        SceneStore.saveGeminiApiKey(context, "  test-key-123  ")
        assertEquals("test-key-123", SceneStore.loadGeminiApiKey(context))
    }

    @Test
    fun testNanoBananaExpanderHelpers() {
        assertFalse(NanoBananaExpander.needsExpansionTo16x10(2560, 1600))
        assertTrue(NanoBananaExpander.needsExpansionTo16x10(1080, 1920))
        assertTrue(NanoBananaExpander.needsExpansionTo16x10(800, 500))
        assertFalse(NanoBananaExpander.needsExpansionTo16x10(0, 100))

        val req = NanoBananaExpander.buildOutpaintRequestJson("AQID", "Expand beach borders")
        assertTrue(req.contains("Expand beach borders"))
        assertTrue(req.contains("16:9"))
        assertTrue(req.contains("AQID"))

        val okResponse = """
            {"candidates":[{"content":{"parts":[{"inlineData":{"mimeType":"image/png","data":"BASE64_IMG"}}]}}]}
        """.trimIndent()
        assertEquals("BASE64_IMG", NanoBananaExpander.extractImageBase64FromResponse(okResponse))
    }
}
