package com.droid.liquidglass3d

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiquidGlassUnitTest {

    @Test
    fun testGlassSettingsDefaults() {
        val s = GlassSettings()
        assertTrue(s.spatialEnabled)
        assertEquals(0.8f, s.depthIntensity, 0.001f)
        assertEquals(0.7f, s.refraction, 0.001f)
        assertEquals(0.65f, s.glassBlend, 0.001f)
        assertEquals(MaskMode.AUTO, s.maskMode)
        assertEquals(1.0f, s.clockScale, 0.001f)
        assertEquals(0.5f, s.clockPosX, 0.001f)
        assertEquals(0.16f, s.clockPosY, 0.001f)
        assertFalse(s.clockInFront)
        assertEquals(ClockColorPreset.WHITE, s.clockColorPreset)
        assertEquals(ClockFont.LIGHT, s.clockFont)
        assertTrue(s.showDate)
    }

    @Test
    fun testGlassSettingsCopy() {
        val s = GlassSettings().copy(
            spatialEnabled = false,
            depthIntensity = 1.25f,
            glassBlend = 0.20f,
            clockScale = 1.40f,
            clockPosX = 0.85f,
            clockPosY = 0.45f,
            clockInFront = true,
            clockColorPreset = ClockColorPreset.CYBERPUNK,
            clockFont = ClockFont.BOLD,
            showDate = false,
        )
        assertFalse(s.spatialEnabled)
        assertEquals(1.25f, s.depthIntensity, 0.001f)
        assertEquals(0.20f, s.glassBlend, 0.001f)
        assertEquals(1.40f, s.clockScale, 0.001f)
        assertEquals(0.85f, s.clockPosX, 0.001f)
        assertEquals(0.45f, s.clockPosY, 0.001f)
        assertTrue(s.clockInFront)
        assertEquals(ClockColorPreset.CYBERPUNK, s.clockColorPreset)
        assertEquals(ClockFont.BOLD, s.clockFont)
        assertFalse(s.showDate)
    }

    @Test
    fun testClockColorPresetsComprehensive() {
        val presets = ClockColorPreset.entries
        // 8 solid tones + 28 liquid gradients = 36 presets total
        assertEquals(36, presets.size)

        val solids = presets.filter { !it.isGradient }
        val gradients = presets.filter { it.isGradient }

        assertEquals(8, solids.size)
        assertEquals(28, gradients.size)

        solids.forEach { preset ->
            assertTrue(preset.colors.isNotEmpty())
            assertFalse(preset.label.isBlank())
        }

        gradients.forEach { preset ->
            assertTrue("${preset.name} should have >= 2 gradient colors", preset.colors.size >= 2)
            assertFalse(preset.label.isBlank())
        }
    }

    @Test
    fun testClockFonts() {
        val fonts = ClockFont.entries
        assertEquals(6, fonts.size)
        fonts.forEach { font ->
            assertNotNull(font.family)
            assertFalse(font.label.isBlank())
        }
    }

    @Test
    fun testMaskFileRouting() {
        assertEquals(SceneStore.MASK_PORTRAIT_FILE, SceneStore.maskFileFor(MaskMode.PORTRAIT, MaskMode.OBJECT))
        assertEquals(SceneStore.MASK_OBJECT_FILE, SceneStore.maskFileFor(MaskMode.OBJECT, MaskMode.PORTRAIT))
        assertEquals(SceneStore.MASK_NATURE_FILE, SceneStore.maskFileFor(MaskMode.NATURE, MaskMode.OBJECT))

        // When AUTO is selected, it routes to detected mode
        assertEquals(SceneStore.MASK_PORTRAIT_FILE, SceneStore.maskFileFor(MaskMode.AUTO, MaskMode.PORTRAIT))
        assertEquals(SceneStore.MASK_OBJECT_FILE, SceneStore.maskFileFor(MaskMode.AUTO, MaskMode.OBJECT))
        assertEquals(SceneStore.MASK_NATURE_FILE, SceneStore.maskFileFor(MaskMode.AUTO, MaskMode.NATURE))
        assertEquals(SceneStore.MASK_FILE, SceneStore.maskFileFor(MaskMode.AUTO, MaskMode.AUTO))
    }

    @Test
    fun testMaskModes() {
        assertEquals(4, MaskMode.entries.size)
        assertEquals("Auto (AI)", MaskMode.AUTO.label)
        assertEquals("Person", MaskMode.PORTRAIT.label)
        assertEquals("Object", MaskMode.OBJECT.label)
        assertEquals("Nature", MaskMode.NATURE.label)
    }

    @Test
    fun testUiState() {
        val state = UiState()
        assertNotNull(state.settings)
        assertEquals(null, state.scene)
        assertEquals(0, state.sceneVersion)
        assertEquals(null, state.busyStage)
        assertEquals(null, state.error)

        val updated = state.copy(
            sceneVersion = 2,
            busyStage = "Processing",
            error = "Test Error"
        )
        assertEquals(2, updated.sceneVersion)
        assertEquals("Processing", updated.busyStage)
        assertEquals("Test Error", updated.error)
    }

    @Test
    fun testDepthOpsNormalize() {
        val raw = FloatArray(100) { it.toFloat() }
        val norm = DepthOps.normalize(raw)
        assertEquals(100, norm.size)
        assertTrue(norm.first() <= 0.01f)
        assertTrue(norm.last() >= 254.9f)
        for (i in 1 until norm.size) {
            assertTrue(norm[i] >= norm[i - 1])
        }

        // Constant input should not divide by zero or NaN
        val constant = FloatArray(50) { 10f }
        val normConst = DepthOps.normalize(constant)
        for (v in normConst) {
            assertFalse(v.isNaN())
            assertFalse(v.isInfinite())
        }
    }

    @Test
    fun testDepthOpsUpsample() {
        val src = floatArrayOf(
            0f, 100f,
            50f, 150f
        )
        val dst = DepthOps.upsample(src, 2, 2, 4, 4)
        assertEquals(16, dst.size)
        for (v in dst) {
            assertTrue("Value $v must be between 0 and 150", v in 0f..150f)
        }
        assertTrue(dst[0] < dst[15])
    }

    @Test
    fun testDepthOpsBoxBlur() {
        val w = 8
        val h = 8
        val uniform = FloatArray(w * h) { 50f }
        val blurred = DepthOps.boxBlur(uniform, w, h, 2)
        for (v in blurred) {
            assertEquals(50f, v, 0.01f)
        }

        val impulse = FloatArray(w * h)
        impulse[4 * w + 4] = 100f
        val blurredImpulse = DepthOps.boxBlur(impulse, w, h, 2)
        assertTrue(blurredImpulse[4 * w + 4] < 100f)
        assertTrue(blurredImpulse[4 * w + 3] > 0f)
    }

    @Test
    fun testDepthOpsBuildNatureDepthMask() {
        val w = 16
        val h = 16
        val depth = FloatArray(w * h) { (it * 255f) / (w * h) }
        val mask = DepthOps.buildNatureDepthMask(depth, w, h)
        assertEquals(w * h, mask.size)
        for (v in mask) {
            assertTrue("Mask value $v should be in 0..255 range", v in 0f..255f)
        }
    }

    @Test
    fun testLiquidGlassLayoutCalculations() {
        val w = 2560
        val h = 1600

        // Centered
        val center = LiquidGlassLayout.calculate(w, h, scale = 1.0f, posX = 0.5f, posY = 0.5f)
        assertTrue(center.width > 0f && center.width <= w)
        assertTrue(center.height > 0f && center.height <= h)
        assertTrue(center.right <= w.toFloat())
        assertTrue(center.bottom <= h.toFloat())

        // Top-left corner
        val topLeft = LiquidGlassLayout.calculate(w, h, scale = 1.0f, posX = 0f, posY = 0f)
        assertEquals(0f, topLeft.x, 0.001f)
        assertEquals(0f, topLeft.y, 0.001f)

        // Bottom-right corner
        val bottomRight = LiquidGlassLayout.calculate(w, h, scale = 1.0f, posX = 1f, posY = 1f)
        assertEquals(w.toFloat(), bottomRight.right, 0.01f)
        assertEquals(h.toFloat(), bottomRight.bottom, 0.01f)

        // Scale clamping
        val minScaled = LiquidGlassLayout.calculate(w, h, scale = 0.1f, posX = 0.5f, posY = 0.5f)
        val maxScaled = LiquidGlassLayout.calculate(w, h, scale = 5.0f, posX = 0.5f, posY = 0.5f)
        assertTrue("Min clamped scale should be strictly smaller than max scale", minScaled.width < maxScaled.width)
    }

    @Test
    fun testParallaxFilterPhysics() {
        val filter = ParallaxFilter()
        assertEquals(0f, filter.x, 0.001f)
        assertEquals(0f, filter.y, 0.001f)

        // Pointer input
        filter.onPointer(px = 1000f, py = 1000f, w = 1000f, h = 1000f, active = true)
        for (i in 0 until 15) {
            filter.step(0.016f)
        }
        assertTrue("X should be positive towards right edge", filter.x > 0.1f)
        assertTrue("Y should be positive towards bottom edge", filter.y > 0.1f)

        // Stop filter
        filter.stop()
        filter.onPointerExit()
        for (i in 0 until 10) {
            filter.step(0.016f)
        }

        // Page offset
        filter.onPageOffset(1.0f)
        filter.step(0.016f)
        assertTrue(filter.x > 0f)

        // Sensor target
        filter.onSensorTarget(-0.5f, -0.5f)
        filter.step(0.016f)
    }

    @Test
    fun testPanelBounds() {
        val bounds = PanelBounds(x = 10f, y = 20f, width = 100f, height = 50f)
        assertEquals(10f, bounds.x, 0.001f)
        assertEquals(20f, bounds.y, 0.001f)
        assertEquals(110f, bounds.right, 0.001f)
        assertEquals(70f, bounds.bottom, 0.001f)
    }
}
