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
        assertEquals(0xFFFFFFFF.toInt(), s.solidColor)
        assertEquals(0xFFFFFFFF.toInt(), s.mixedColorA)
        assertEquals(0xFFFFFFFF.toInt(), s.mixedColorB)
        assertEquals(0f, s.mixWeight, 0.001f)
        assertTrue(s.livingBackgroundEnabled)
        assertEquals(LivingMotionMode.AUTO, s.livingMotionMode)
        assertEquals(0.75f, s.livingMotionIntensity, 0.001f)
        assertTrue(s.popOut3d)
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
        // 16 solid tones + 28 liquid gradients = 44 presets total
        assertEquals(44, presets.size)

        val solids = presets.filter { !it.isGradient }
        val gradients = presets.filter { it.isGradient }

        assertEquals(16, solids.size)
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

    @Test
    fun testGlassColorOpsBlendAndTint() {
        val red = 0xFFFF0000.toInt()
        val blue = 0xFF0000FF.toInt()
        val green = 0xFF00FF00.toInt()

        assertEquals(red, GlassColorOps.blendArgb(red, blue, 0f))
        assertEquals(blue, GlassColorOps.blendArgb(red, blue, 1f))
        assertEquals(red, GlassColorOps.blendArgb(red, blue, -0.5f))
        assertEquals(blue, GlassColorOps.blendArgb(red, blue, 1.5f))

        val halfMix = GlassColorOps.blendArgb(red, blue, 0.5f)
        assertEquals(255, (halfMix ushr 24) and 0xFF)
        assertEquals(127, (halfMix ushr 16) and 0xFF)
        assertEquals(0, (halfMix ushr 8) and 0xFF)
        assertEquals(127, halfMix and 0xFF)

        val solidSettings = GlassSettings(solidColor = green, mixedColorA = red, mixedColorB = blue, mixWeight = 0f)
        val solidTint = GlassColorOps.effectiveGlassTint(solidSettings)
        assertEquals(0f, solidTint[0], 0.001f)
        assertEquals(1f, solidTint[1], 0.001f)
        assertEquals(0f, solidTint[2], 0.001f)
        assertEquals(1f, solidTint[3], 0.001f)

        val mixedSettings = GlassSettings(solidColor = green, mixedColorA = red, mixedColorB = blue, mixWeight = 0.5f)
        val mixedTint = GlassColorOps.effectiveGlassTint(mixedSettings)
        assertEquals(127f / 255f, mixedTint[0], 0.01f)
        assertEquals(0f, mixedTint[1], 0.001f)
        assertEquals(127f / 255f, mixedTint[2], 0.01f)
        assertEquals(1f, mixedTint[3], 0.001f)
    }

    @Test
    fun testParallaxFilterAnglesFromGravity() {
        val (p0, r0) = ParallaxFilter.anglesFromGravity(0f, 0f, 9.81f)
        assertEquals(0f, p0, 0.001f)
        assertEquals(0f, r0, 0.001f)

        val (p1, r1) = ParallaxFilter.anglesFromGravity(9.81f, 0f, 0f)
        assertEquals(0f, p1, 0.001f)
        assertTrue(r1 > 1.5f)
    }

    @Test
    fun testLivingMotionOpsDetectionAndResolution() {
        assertEquals(LivingMotionMode.OCEAN, LivingMotionOps.resolveEffectiveMode(LivingMotionMode.AUTO, LivingMotionMode.OCEAN))
        assertEquals(LivingMotionMode.CLOUDS, LivingMotionOps.resolveEffectiveMode(LivingMotionMode.CLOUDS, LivingMotionMode.OCEAN))
        assertEquals(LivingMotionMode.AURA, LivingMotionOps.resolveEffectiveMode(LivingMotionMode.AUTO, LivingMotionMode.AUTO))

        val w = 8
        val h = 8
        val noMask = FloatArray(w * h) { 0f }

        // Lower-half ocean blue -> OCEAN
        val oceanPixels = IntArray(w * h) { idx ->
            val y = idx / w
            if (y >= 4) 0xFF0077BE.toInt() else 0xFF222222.toInt()
        }
        assertEquals(LivingMotionMode.OCEAN, LivingMotionOps.detectFromSampleGrid(oceanPixels, noMask, w, h, MaskMode.NATURE))

        // Upper-half bright sky/clouds -> CLOUDS
        val skyPixels = IntArray(w * h) { idx ->
            val y = idx / w
            if (y < 4) 0xFFB0D8FF.toInt() else 0xFF332211.toInt()
        }
        assertEquals(LivingMotionMode.CLOUDS, LivingMotionOps.detectFromSampleGrid(skyPixels, noMask, w, h, MaskMode.NATURE))

        // Forest green -> BREEZE
        val greenPixels = IntArray(w * h) { 0xFF2E7D32.toInt() }
        assertEquals(LivingMotionMode.BREEZE, LivingMotionOps.detectFromSampleGrid(greenPixels, noMask, w, h, MaskMode.OBJECT))

        // Warm dark indoor -> AURA
        val warmPixels = IntArray(w * h) { 0xFF3E2723.toInt() }
        assertEquals(LivingMotionMode.AURA, LivingMotionOps.detectFromSampleGrid(warmPixels, noMask, w, h, MaskMode.PORTRAIT))

        // Gemini text parser
        assertEquals(LivingMotionMode.OCEAN, NanoBananaExpander.parseMotionModeFromText("OCEAN"))
        assertEquals(LivingMotionMode.CLOUDS, NanoBananaExpander.parseMotionModeFromText("Clouds"))
        assertEquals(LivingMotionMode.BREEZE, NanoBananaExpander.parseMotionModeFromText("breeze"))
        assertEquals(LivingMotionMode.AURA, NanoBananaExpander.parseMotionModeFromText("AURA"))
        assertEquals(null, NanoBananaExpander.parseMotionModeFromText("unknown"))
    }

    @Test
    fun testDepthOpsMorphCloseAndGuidedFilter() {
        val w = 16
        val h = 16
        val maskWithGap = FloatArray(w * h) { 0f }
        // Solid foreground block in center with a 1px hole at (8, 8) and an adjacent glowing cyan arm at (11, 8)
        for (y in 5..10) {
            for (x in 5..10) {
                maskWithGap[y * w + x] = 255f
            }
        }
        maskWithGap[8 * w + 8] = 0f // internal gap

        val closed = DepthOps.morphClose(maskWithGap, w, h, dilateRadius = 2, erodeRadius = 2)
        assertTrue("Internal hole should be closed to foreground", closed[8 * w + 8] > 240f)
        assertEquals("Distant background corner should stay 0", 0f, closed[0], 0.01f)

        // Glowing cyan pixel at (11, 8) attached to character vs distant sky at (1, 1)
        val pixels = IntArray(w * h) { 0xFF87CEEB.toInt() } // sky blue background
        pixels[8 * w + 11] = 0xFF00FFFF.toInt() // bright glowing cyan arm pixel attached to character

        val refined = DepthOps.refineForegroundMask(maskWithGap, pixels, w, h)
        assertTrue("Glowing cyan arm attached to character should fuse into mask", refined[8 * w + 11] > 180f)
        assertTrue("Internal hole should remain fused after refinement", refined[8 * w + 8] > 200f)
        assertTrue("Distant sky pixel should stay near zero", refined[1 * w + 1] < 15f)
    }

    @Test
    fun testDepthOpsPostProcessDepthLayers() {
        val w = 20
        val h = 20
        val rawDepth = FloatArray(w * h)
        val fgMask = FloatArray(w * h)

        for (y in 0 until h) {
            for (x in 0 until w) {
                val i = y * w + x
                if (y < 10) {
                    // Upper half: distant open sky (raw depth = 30)
                    rawDepth[i] = 30f
                } else {
                    // Lower half: continuous cliff/terrain around 102 (near ZERO_PLANE = 89.25)
                    rawDepth[i] = 102f
                }
            }
        }

        // Small isolated bird/leaf in the open sky at (5, 4) with raw depth = 95 (would otherwise stick to rock layer)
        rawDepth[4 * w + 5] = 95f

        // Foreground character at (10..13, 10..13) with high depth (235), plus glowing cyan arm at (10, 9)
        // where raw monocular depth mistakenly dropped to 22 (sky depth)
        for (y in 10..13) {
            for (x in 10..13) {
                val i = y * w + x
                rawDepth[i] = 235f
                fgMask[i] = 255f
            }
        }
        val glowingArmIdx = 9 * w + 10
        rawDepth[glowingArmIdx] = 22f // raw depth fell into sky
        fgMask[glowingArmIdx] = 245f  // closed foreground mask includes the glowing arm

        val processed = DepthOps.postProcessDepth(rawDepth, fgMask, w, h)

        // 1. Glowing arm must be fused to character's near Z-plane (>= 200f) instead of falling into sky (22f)
        assertTrue(
            "Glowing arm depth (${processed[glowingArmIdx]}) should fuse to character near plane",
            processed[glowingArmIdx] >= 200f
        )

        // 2. Distant open sky at (2, 2) should be compressed into deep parallax layer (< 20f)
        val skyDepth = processed[2 * w + 2]
        assertTrue("Distant sky ($skyDepth) should be in deep parallax layer", skyDepth < 20f)

        // 3. Isolated mid-sky bird/leaf at (5, 4) should be decoupled onto aerial mid-sky plane (~48f)
        val birdDepth = processed[4 * w + 5]
        assertEquals(48f, birdDepth, 1.0f)

        // 4. Main cliff/terrain at (2, 16) should be pulled toward ZERO_PLANE (89.25f)
        val cliffDepth = processed[16 * w + 2]
        assertTrue(
            "Cliff depth ($cliffDepth) should be stabilized closer to ZERO_PLANE (89.25)",
            kotlin.math.abs(cliffDepth - 89.25f) < kotlin.math.abs(102f - 89.25f)
        )
    }
}
