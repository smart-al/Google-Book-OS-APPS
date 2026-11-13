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
}
