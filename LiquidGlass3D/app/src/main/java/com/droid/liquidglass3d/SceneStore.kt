package com.droid.liquidglass3d

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Typeface
import java.io.File

enum class MaskMode(val label: String) {
    AUTO("Auto (AI)"),
    PORTRAIT("Person"),
    OBJECT("Object"),
    NATURE("Nature"),
}

enum class LivingMotionMode(val label: String, val shaderIndex: Float) {
    AUTO("Auto (AI)", -1f),
    CLOUDS("Clouds & Sky", 0f),
    OCEAN("Ocean & Water", 1f),
    BREEZE("Nature Breeze", 2f),
    AURA("Living Aura", 3f),
}

enum class ClockFont(val label: String, val family: String, val style: Int) {
    LIGHT("Light", "sans-serif-light", Typeface.NORMAL),
    MODERN("Modern", "sans-serif-medium", Typeface.NORMAL),
    BOLD("Bold", "sans-serif-black", Typeface.BOLD),
    THIN("Thin", "sans-serif-thin", Typeface.NORMAL),
    SERIF("Editorial", "serif", Typeface.NORMAL),
    MONO("Tech Mono", "monospace", Typeface.BOLD),
}

enum class ClockColorPreset(
    val label: String,
    val colors: IntArray,
    val isGradient: Boolean = false,
) {
    // Solid Glass Tones (16)
    WHITE("Pure White", intArrayOf(0xFFFFFFFF.toInt())),
    CYAN("Ice Cyan", intArrayOf(0xFFB3E5FC.toInt())),
    GOLD("Soft Gold", intArrayOf(0xFFFFE082.toInt())),
    ROSE("Rose Quartz", intArrayOf(0xFFF8BBD0.toInt())),
    MINT("Mint Glass", intArrayOf(0xFFA7FFEB.toInt())),
    VIOLET("Electric Violet", intArrayOf(0xFFE1BEE7.toInt())),
    CORAL("Neon Coral", intArrayOf(0xFFFF8A80.toInt())),
    OBSIDIAN("Obsidian", intArrayOf(0xFF1E2430.toInt())),
    CRIMSON("Crimson Ruby", intArrayOf(0xFFE53935.toInt())),
    COBALT("Cobalt Blue", intArrayOf(0xFF1E88E5.toInt())),
    EMERALD_SOLID("Emerald Green", intArrayOf(0xFF43A047.toInt())),
    TANGERINE("Tangerine", intArrayOf(0xFFFB8C00.toInt())),
    LAVENDER("Soft Lavender", intArrayOf(0xFFB39DDB.toInt())),
    TEAL("Deep Teal", intArrayOf(0xFF00897B.toInt())),
    AMBER("Warm Amber", intArrayOf(0xFFFFB300.toInt())),
    SLATE("Cool Slate", intArrayOf(0xFF90A4AE.toInt())),

    // Mixed & Liquid Gradients (28 Total: 8 Original + 20 New)
    CYBERPUNK("Cyberpunk", intArrayOf(0xFF00F5D4.toInt(), 0xFFF72585.toInt()), true),
    SUNSET("Sunset Glow", intArrayOf(0xFFFF512F.toInt(), 0xFFDD2476.toInt()), true),
    AURORA("Aurora", intArrayOf(0xFF00F260.toInt(), 0xFF0575E6.toInt()), true),
    LIQUID_GOLD("Liquid Gold", intArrayOf(0xFFFFE259.toInt(), 0xFFFFA751.toInt()), true),
    COSMIC("Cosmic Nebula", intArrayOf(0xFF9D4EDD.toInt(), 0xFFFF5400.toInt()), true),
    ARCTIC("Arctic Frost", intArrayOf(0xFFE0F7FA.toInt(), 0xFF00E5FF.toInt()), true),
    SYNTHWAVE("Miami Synth", intArrayOf(0xFFFF007F.toInt(), 0xFF00F5FF.toInt()), true),
    EMERALD("Emerald Sky", intArrayOf(0xFF70E000.toInt(), 0xFF00B4D8.toInt()), true),

    // 20 Brand-New Mixed Tones
    SOLAR_FLARE("Solar Flare", intArrayOf(0xFFFFE600.toInt(), 0xFFFF5E00.toInt(), 0xFFD00000.toInt()), true),
    TWILIGHT("Twilight Lavender", intArrayOf(0xFF3F2B96.toInt(), 0xFFA8C0FF.toInt()), true),
    BIOLUMINESCENT("Bioluminescent Abyss", intArrayOf(0xFF001220.toInt(), 0xFF00F5D4.toInt(), 0xFF00BBF9.toInt()), true),
    CHERRY_BLOSSOM("Sakura Blossom", intArrayOf(0xFFFF9A9E.toInt(), 0xFFFAD0C4.toInt(), 0xFFFED6E3.toInt()), true),
    ARCADE("Retro Arcade", intArrayOf(0xFF7209B7.toInt(), 0xFF4CC9F0.toInt(), 0xFFF72585.toInt()), true),
    NORTHERN_LIGHTS("Polar Aurora", intArrayOf(0xFF43E97B.toInt(), 0xFF38F9D7.toInt(), 0xFF667EEA.toInt()), true),
    DESERT_DUNE("Desert Dune", intArrayOf(0xFFFFD194.toInt(), 0xFFD1913C.toInt(), 0xFFE07A5F.toInt()), true),
    MATRIX("Cyber Matrix", intArrayOf(0xFF00FF87.toInt(), 0xFF60EFFF.toInt()), true),
    VELVET_PLUM("Velvet Sunset", intArrayOf(0xFF654EA3.toInt(), 0xFFEAAFC8.toInt()), true),
    GLACIAL_TEAL("Glacial Spring", intArrayOf(0xFF89F7FE.toInt(), 0xFF66A6FF.toInt()), true),
    HYPERDRIVE("Hyperdrive Beam", intArrayOf(0xFF00C6FF.toInt(), 0xFF0072FF.toInt()), true),
    FLAMINGO("Flamingo Dawn", intArrayOf(0xFFFF0844.toInt(), 0xFFFFB199.toInt()), true),
    MAGMA("Molten Magma", intArrayOf(0xFFFF4E50.toInt(), 0xFFF9D423.toInt()), true),
    ORCHID("Mystic Orchid", intArrayOf(0xFFB92B27.toInt(), 0xFF1565C0.toInt()), true),
    VINTAGE_SEPIA("Vintage Bronze", intArrayOf(0xFFE6DADA.toInt(), 0xFF274046.toInt()), true),
    OCEAN_TRENCH("Mariana Trench", intArrayOf(0xFF13547A.toInt(), 0xFF80D0C7.toInt()), true),
    COTTON_CANDY("Cotton Candy", intArrayOf(0xFFFEE140.toInt(), 0xFFFA709A.toInt()), true),
    JADE_EMPEROR("Imperial Jade", intArrayOf(0xFF0BA360.toInt(), 0xFF3CBA92.toInt()), true),
    PRISM("Prism Spectrum", intArrayOf(0xFFFF0055.toInt(), 0xFFFF9900.toInt(), 0xFF00E5FF.toInt(), 0xFF9900FF.toInt()), true),
    CHAMPAGNE("Platinum Rose", intArrayOf(0xFFE2D1C3.toInt(), 0xFFFDFBFB.toInt(), 0xFFC9CCD3.toInt()), true),
}

/** User-tunable settings shared live between the Compose preview and the wallpaper engine. */
data class GlassSettings(
    val spatialEnabled: Boolean = true,
    val depthIntensity: Float = 0.8f,
    val refraction: Float = 0.7f,
    val glassBlend: Float = 0.65f, // 0 = seamless image blend, 1 = frosted
    val maskMode: MaskMode = MaskMode.AUTO,
    val clockScale: Float = 1.0f,
    val clockPosX: Float = 0.5f,
    val clockPosY: Float = 0.16f,
    val clockInFront: Boolean = false,
    val clockColorPreset: ClockColorPreset = ClockColorPreset.WHITE,
    val clockFont: ClockFont = ClockFont.LIGHT,
    val showDate: Boolean = true,
    // New color fields
    val solidColor: Int = 0xFFFFFFFF.toInt(),
    val mixedColorA: Int = 0xFFFFFFFF.toInt(),
    val mixedColorB: Int = 0xFFFFFFFF.toInt(),
    val mixWeight: Float = 0f,
    // Living Background (AI-guided real-time background animation)
    val livingBackgroundEnabled: Boolean = true,
    val livingMotionMode: LivingMotionMode = LivingMotionMode.AUTO,
    val livingMotionIntensity: Float = 0.75f,
    // 3DS-style pop-out depth boost (cast shadow, foreground lift, idle micro-orbit)
    val popOut3d: Boolean = true,
)

/** Decoded source image + depth map + subject mask + RGB semantic motion map, as cached in `filesDir`. */
class SceneBitmaps(
    val photo: Bitmap,
    val depth: Bitmap,
    val mask: Bitmap,
    val detectedMode: MaskMode = MaskMode.OBJECT,
    val detectedMotion: LivingMotionMode = LivingMotionMode.AURA,
    val motion: Bitmap = mask,
)

object SceneStore {
    private const val PREFS = "liquid_glass_prefs"
    const val KEY_SPATIAL = "spatial"
    const val KEY_DEPTH = "depth_intensity"
    const val KEY_REFRACTION = "refraction"
    const val KEY_GLASS_BLEND = "glass_blend"
    const val KEY_MASK_MODE = "mask_mode"
    const val KEY_DETECTED_MODE = "detected_mask_mode"
    const val KEY_CLOCK_SCALE = "clock_scale"
    const val KEY_CLOCK_POS_X = "clock_pos_x"
    const val KEY_CLOCK_POS_Y = "clock_pos_y"
    const val KEY_CLOCK_IN_FRONT = "clock_in_front"
    const val KEY_CLOCK_COLOR_PRESET = "clock_color_preset"
    const val KEY_CLOCK_FONT = "clock_font"
    const val KEY_SHOW_DATE = "show_date"
    const val KEY_SCENE_VERSION = "scene_version"
    const val KEY_SOLID_COLOR = "solid_color"
    const val KEY_MIXED_COLOR_A = "mixed_color_a"
    const val KEY_MIXED_COLOR_B = "mixed_color_b"
    const val KEY_MIX_WEIGHT = "mix_weight"
    const val KEY_GEMINI_API_KEY = "gemini_api_key"
    const val KEY_LIVING_BG = "living_background"
    const val KEY_LIVING_MODE = "living_motion_mode"
    const val KEY_LIVING_INTENSITY = "living_motion_intensity"
    const val KEY_DETECTED_MOTION = "detected_living_motion"
    const val KEY_POP_OUT_3D = "pop_out_3d"

    const val SOURCE_FILE = "source.jpg"
    const val DEPTH_FILE = "depth.png"
    const val MASK_FILE = "mask.png"
    const val MASK_PORTRAIT_FILE = "mask_portrait.png"
    const val MASK_OBJECT_FILE = "mask_object.png"
    const val MASK_NATURE_FILE = "mask_nature.png"
    const val MOTION_FILE = "motion_mask.png"

    fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun loadSettings(context: Context): GlassSettings {
        val p = prefs(context)
        val d = GlassSettings()
        val modeName = p.getString(KEY_MASK_MODE, d.maskMode.name) ?: d.maskMode.name
        val mode = runCatching { MaskMode.valueOf(modeName) }.getOrDefault(d.maskMode)
        val fontName = p.getString(KEY_CLOCK_FONT, d.clockFont.name) ?: d.clockFont.name
        val font = runCatching { ClockFont.valueOf(fontName) }.getOrDefault(d.clockFont)
        val colorPresetName = p.getString(KEY_CLOCK_COLOR_PRESET, d.clockColorPreset.name) ?: d.clockColorPreset.name
        val colorPreset = runCatching { ClockColorPreset.valueOf(colorPresetName) }.getOrDefault(d.clockColorPreset)

        val solidColor = p.getInt(KEY_SOLID_COLOR, d.solidColor)
        val mixedColorA = p.getInt(KEY_MIXED_COLOR_A, d.mixedColorA)
        val mixedColorB = p.getInt(KEY_MIXED_COLOR_B, d.mixedColorB)
        val mixWeight = p.getFloat(KEY_MIX_WEIGHT, d.mixWeight)
        val livingModeName = p.getString(KEY_LIVING_MODE, d.livingMotionMode.name) ?: d.livingMotionMode.name
        val livingMode = runCatching { LivingMotionMode.valueOf(livingModeName) }.getOrDefault(d.livingMotionMode)

        return GlassSettings(
            spatialEnabled = p.getBoolean(KEY_SPATIAL, d.spatialEnabled),
            depthIntensity = p.getFloat(KEY_DEPTH, d.depthIntensity),
            refraction = p.getFloat(KEY_REFRACTION, d.refraction),
            glassBlend = p.getFloat(KEY_GLASS_BLEND, d.glassBlend).coerceIn(0f, 1f),
            maskMode = mode,
            clockScale = p.getFloat(KEY_CLOCK_SCALE, d.clockScale).coerceIn(0.65f, 1.45f),
            clockPosX = p.getFloat(KEY_CLOCK_POS_X, d.clockPosX).coerceIn(0f, 1f),
            clockPosY = p.getFloat(KEY_CLOCK_POS_Y, d.clockPosY).coerceIn(0f, 1f),
            clockInFront = p.getBoolean(KEY_CLOCK_IN_FRONT, d.clockInFront),
            clockColorPreset = colorPreset,
            clockFont = font,
            showDate = p.getBoolean(KEY_SHOW_DATE, d.showDate),
            solidColor = solidColor,
            mixedColorA = mixedColorA,
            mixedColorB = mixedColorB,
            mixWeight = mixWeight,
            livingBackgroundEnabled = p.getBoolean(KEY_LIVING_BG, d.livingBackgroundEnabled),
            livingMotionMode = livingMode,
            livingMotionIntensity = p.getFloat(KEY_LIVING_INTENSITY, d.livingMotionIntensity).coerceIn(0f, 1.5f),
            popOut3d = p.getBoolean(KEY_POP_OUT_3D, d.popOut3d),
        )
    }

    fun saveSettings(context: Context, s: GlassSettings) {
        prefs(context).edit()
            .putBoolean(KEY_SPATIAL, s.spatialEnabled)
            .putFloat(KEY_DEPTH, s.depthIntensity)
            .putFloat(KEY_REFRACTION, s.refraction)
            .putFloat(KEY_GLASS_BLEND, s.glassBlend)
            .putString(KEY_MASK_MODE, s.maskMode.name)
            .putFloat(KEY_CLOCK_SCALE, s.clockScale)
            .putFloat(KEY_CLOCK_POS_X, s.clockPosX)
            .putFloat(KEY_CLOCK_POS_Y, s.clockPosY)
            .putBoolean(KEY_CLOCK_IN_FRONT, s.clockInFront)
            .putString(KEY_CLOCK_COLOR_PRESET, s.clockColorPreset.name)
            .putString(KEY_CLOCK_FONT, s.clockFont.name)
            .putBoolean(KEY_SHOW_DATE, s.showDate)
            .putInt(KEY_SOLID_COLOR, s.solidColor)
            .putInt(KEY_MIXED_COLOR_A, s.mixedColorA)
            .putInt(KEY_MIXED_COLOR_B, s.mixedColorB)
            .putFloat(KEY_MIX_WEIGHT, s.mixWeight)
            .putBoolean(KEY_LIVING_BG, s.livingBackgroundEnabled)
            .putString(KEY_LIVING_MODE, s.livingMotionMode.name)
            .putFloat(KEY_LIVING_INTENSITY, s.livingMotionIntensity)
            .putBoolean(KEY_POP_OUT_3D, s.popOut3d)
            .apply()
    }

    fun saveDetectedMode(context: Context, mode: MaskMode) {
        prefs(context).edit().putString(KEY_DETECTED_MODE, mode.name).apply()
    }

    fun loadDetectedMode(context: Context): MaskMode {
        val name = prefs(context).getString(KEY_DETECTED_MODE, MaskMode.OBJECT.name) ?: MaskMode.OBJECT.name
        return runCatching { MaskMode.valueOf(name) }.getOrDefault(MaskMode.OBJECT)
    }

    fun saveDetectedMotion(context: Context, mode: LivingMotionMode) {
        prefs(context).edit().putString(KEY_DETECTED_MOTION, mode.name).apply()
    }

    fun loadDetectedMotion(context: Context): LivingMotionMode {
        val name = prefs(context).getString(KEY_DETECTED_MOTION, LivingMotionMode.AURA.name) ?: LivingMotionMode.AURA.name
        return runCatching { LivingMotionMode.valueOf(name) }.getOrDefault(LivingMotionMode.AURA)
    }

    fun hasScene(context: Context): Boolean =
        File(context.filesDir, SOURCE_FILE).exists() &&
            File(context.filesDir, DEPTH_FILE).exists() &&
            File(context.filesDir, MASK_FILE).exists()

    fun maskFileFor(mode: MaskMode, detected: MaskMode): String {
        val effective = if (mode == MaskMode.AUTO) detected else mode
        return when (effective) {
            MaskMode.PORTRAIT -> MASK_PORTRAIT_FILE
            MaskMode.OBJECT -> MASK_OBJECT_FILE
            MaskMode.NATURE -> MASK_NATURE_FILE
            MaskMode.AUTO -> MASK_FILE
        }
    }

    /** Blocking; call from a background dispatcher. Returns null if nothing cached yet. */
    fun loadScene(context: Context): SceneBitmaps? {
        if (!hasScene(context)) return null
        val dir = context.filesDir
        val detected = loadDetectedMode(context)
        val detectedMotion = loadDetectedMotion(context)
        val selected = loadSettings(context).maskMode
        val maskFile = File(dir, maskFileFor(selected, detected)).takeIf { it.exists() } ?: File(dir, MASK_FILE)
        val photo = BitmapFactory.decodeFile(File(dir, SOURCE_FILE).path) ?: return null
        val depth = BitmapFactory.decodeFile(File(dir, DEPTH_FILE).path) ?: return null
        val mask = BitmapFactory.decodeFile(maskFile.path) ?: return null
        val motionFile = File(dir, MOTION_FILE)
        val motion = if (motionFile.exists()) {
            BitmapFactory.decodeFile(motionFile.path) ?: LivingMotionOps.buildMotionMaskBitmap(photo, depth, mask)
        } else {
            LivingMotionOps.buildMotionMaskBitmap(photo, depth, mask).also { saveMotionBitmap(context, it) }
        }
        return SceneBitmaps(photo, depth, mask, detected, detectedMotion, motion)
    }

    fun saveMotionBitmap(context: Context, motion: Bitmap) {
        val target = File(context.filesDir, MOTION_FILE)
        val tmp = File(context.filesDir, "$MOTION_FILE.tmp")
        runCatching {
            tmp.outputStream().use { motion.compress(Bitmap.CompressFormat.PNG, 100, it) }
            tmp.renameTo(target)
        }
    }

    /** Switches active mask.png from cached variants and notifies wallpaper engine. */
    fun activateMaskMode(context: Context, mode: MaskMode): SceneBitmaps? {
        val dir = context.filesDir
        val detected = loadDetectedMode(context)
        val variant = File(dir, maskFileFor(mode, detected))
        if (variant.exists()) {
            variant.copyTo(File(dir, MASK_FILE), overwrite = true)
        }
        bumpSceneVersion(context)
        return loadScene(context)
    }

    /** Instantly extracts built-in 16:10 sample assets so users can test immediately. */
    fun loadDemoScene(context: Context): SceneBitmaps? {
        val dir = context.filesDir
        runCatching {
            context.assets.open("sample_source.jpg").use { src ->
                File(dir, SOURCE_FILE).outputStream().use { dst -> src.copyTo(dst) }
            }
            context.assets.open("sample_depth.png").use { src ->
                File(dir, DEPTH_FILE).outputStream().use { dst -> src.copyTo(dst) }
            }
            context.assets.open("sample_mask.png").use { src ->
                File(dir, MASK_FILE).outputStream().use { dst -> src.copyTo(dst) }
            }
            File(dir, MASK_FILE).copyTo(File(dir, MASK_PORTRAIT_FILE), overwrite = true)
            File(dir, MASK_FILE).copyTo(File(dir, MASK_OBJECT_FILE), overwrite = true)
            File(dir, MASK_FILE).copyTo(File(dir, MASK_NATURE_FILE), overwrite = true)
            File(dir, MOTION_FILE).delete()
            saveDetectedMode(context, MaskMode.PORTRAIT)
            saveDetectedMotion(context, LivingMotionMode.CLOUDS)
            bumpSceneVersion(context)
        }
        return loadScene(context)
    }

    fun bumpSceneVersion(context: Context) {
        val p = prefs(context)
        p.edit().putLong(KEY_SCENE_VERSION, p.getLong(KEY_SCENE_VERSION, 0) + 1).apply()
    }

    fun loadGeminiApiKey(context: Context): String =
        prefs(context).getString(KEY_GEMINI_API_KEY, "")?.trim().orEmpty()

    fun saveGeminiApiKey(context: Context, key: String) {
        prefs(context).edit().putString(KEY_GEMINI_API_KEY, key.trim()).apply()
    }
}

/** Pure color-blending utilities for custom solid and mixed glass tints. */
object GlassColorOps {
    val PALETTE: List<Int> = listOf(
        0xFFFFFFFF.toInt(), // Pure White
        0xFFB3E5FC.toInt(), // Ice Cyan
        0xFF00E5FF.toInt(), // Electric Cyan
        0xFF1E88E5.toInt(), // Cobalt Blue
        0xFF3F2B96.toInt(), // Deep Indigo
        0xFF9D4EDD.toInt(), // Cosmic Purple
        0xFFE1BEE7.toInt(), // Soft Violet
        0xFFF72585.toInt(), // Neon Pink
        0xFFF8BBD0.toInt(), // Rose Quartz
        0xFFE53935.toInt(), // Crimson Ruby
        0xFFFF8A80.toInt(), // Neon Coral
        0xFFFF5E00.toInt(), // Solar Orange
        0xFFFB8C00.toInt(), // Tangerine
        0xFFFFE082.toInt(), // Soft Gold
        0xFFFFB300.toInt(), // Warm Amber
        0xFFA7FFEB.toInt(), // Mint Glass
        0xFF00F5D4.toInt(), // Bio Teal
        0xFF43A047.toInt(), // Emerald Green
        0xFF00897B.toInt(), // Deep Teal
        0xFF90A4AE.toInt(), // Cool Slate
        0xFF1E2430.toInt(), // Obsidian
    )

    fun blendArgb(colorA: Int, colorB: Int, weight: Float): Int {
        val w = weight.coerceIn(0f, 1f)
        val aA = (colorA ushr 24) and 0xFF
        val rA = (colorA ushr 16) and 0xFF
        val gA = (colorA ushr 8) and 0xFF
        val bA = colorA and 0xFF

        val aB = (colorB ushr 24) and 0xFF
        val rB = (colorB ushr 16) and 0xFF
        val gB = (colorB ushr 8) and 0xFF
        val bB = colorB and 0xFF

        val a = (aA * (1f - w) + aB * w).toInt().coerceIn(0, 255)
        val r = (rA * (1f - w) + rB * w).toInt().coerceIn(0, 255)
        val g = (gA * (1f - w) + gB * w).toInt().coerceIn(0, 255)
        val b = (bA * (1f - w) + bB * w).toInt().coerceIn(0, 255)
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    fun effectiveTintArgb(settings: GlassSettings): Int {
        return if (settings.mixWeight > 0f) {
            blendArgb(settings.mixedColorA, settings.mixedColorB, settings.mixWeight)
        } else {
            settings.solidColor
        }
    }

    fun effectiveGlassTint(settings: GlassSettings): FloatArray {
        val c = effectiveTintArgb(settings)
        return floatArrayOf(
            ((c ushr 16) and 0xFF) / 255f,
            ((c ushr 8) and 0xFF) / 255f,
            (c and 0xFF) / 255f,
            ((c ushr 24) and 0xFF) / 255f,
        )
    }
}

enum class MotionRegionType {
    CLOUDS,
    WATER,
    BIRDS_ANIMALS,
    FOLIAGE,
    STATIC,
}

data class MotionRegion(
    val type: MotionRegionType,
    val xmin: Float,
    val ymin: Float,
    val xmax: Float,
    val ymax: Float,
)

/**
 * AI scene-motion analyzer that builds both:
 *  - a 3-channel RGB semantic motion mask (`R = Clouds/Sky`, `G = Water/Ocean`,
 *    `B = Birds/Animals/Leaves/Foliage`, `0,0,0 = Static Solid Terrain/Structures & Subject`), and
 *  - a dominant [LivingMotionMode] classification.
 */
object LivingMotionOps {

    fun resolveEffectiveMode(selected: LivingMotionMode, detected: LivingMotionMode): LivingMotionMode =
        if (selected == LivingMotionMode.AUTO) {
            if (detected == LivingMotionMode.AUTO) LivingMotionMode.AURA else detected
        } else {
            selected
        }

    /**
     * Builds an RGB semantic motion mask [Bitmap] (`R=Clouds`, `G=Water`, `B=Birds/Animals/Foliage`,
     * `Black=Static Rigid Terrain/Buildings & Foreground Subject`).
     */
    fun buildMotionMaskBitmap(
        photo: Bitmap,
        depth: Bitmap,
        mask: Bitmap,
        aiRegions: List<MotionRegion> = emptyList(),
    ): Bitmap {
        val w = photo.width.coerceAtLeast(1)
        val h = photo.height.coerceAtLeast(1)
        val scaledDepth = if (depth.width == w && depth.height == h) depth else Bitmap.createScaledBitmap(depth, w, h, true)
        val scaledMask = if (mask.width == w && mask.height == h) mask else Bitmap.createScaledBitmap(mask, w, h, true)
        val n = w * h
        val photoPx = IntArray(n).also { photo.getPixels(it, 0, w, 0, 0, w, h) }
        val depthPx = IntArray(n).also { scaledDepth.getPixels(it, 0, w, 0, 0, w, h) }
        val maskPx = IntArray(n).also { scaledMask.getPixels(it, 0, w, 0, 0, w, h) }
        val depthGray = FloatArray(n) { ((depthPx[it] ushr 16) and 0xFF).toFloat() }
        val fgMask = FloatArray(n) { ((maskPx[it] ushr 16) and 0xFF).toFloat() }
        val outPx = buildMotionMaskChannels(photoPx, depthGray, fgMask, w, h, aiRegions)
        return Bitmap.createBitmap(outPx, w, h, Bitmap.Config.ARGB_8888)
    }

    /**
     * Computes per-pixel RGB motion weights (`0xFFRRGGBB`):
     *  - `R` (`0..255`): Movable Clouds, Sky, Mist & Smoke
     *  - `G` (`0..255`): Movable Water, Ocean, Rivers & Ripples
     *  - `B` (`0..255`): Movable Birds, Animals, Floating Leaves & Swaying Foliage
     *  - `0, 0, 0`: Immovable Static Background (cliffs, rocks, buildings, ruins, ground) & Foreground Subject
     */
    fun buildMotionMaskChannels(
        photoPixels: IntArray,
        depthGray: FloatArray,
        fgMask: FloatArray,
        w: Int,
        h: Int,
        aiRegions: List<MotionRegion> = emptyList(),
    ): IntArray {
        val n = w * h
        if (w <= 0 || h <= 0 || photoPixels.size < n || depthGray.size < n || fgMask.size < n) {
            return IntArray(n.coerceAtLeast(0)) { 0xFF000000.toInt() }
        }

        val regionRadius = kotlin.math.max(2, kotlin.math.min(w, h) / 22)
        val regionalDepth = DepthOps.boxBlur(depthGray, w, h, regionRadius)
        val dilatedFg = DepthOps.morphDilate(fgMask, w, h, kotlin.math.max(2, kotlin.math.min(w, h) / 70))

        val rChan = FloatArray(n)
        val gChan = FloatArray(n)
        val bChan = FloatArray(n)

        for (y in 0 until h) {
            val ny = (y + 0.5f) / h.toFloat()
            val row = y * w
            for (x in 0 until w) {
                val nx = (x + 0.5f) / w.toFloat()
                val i = row + x

                // 1. Keep foreground subject & immediate silhouette guard completely solid in motionTex
                val fgWeight = (dilatedFg[i] / 255f).coerceIn(0f, 1f)
                if (fgWeight >= 0.85f) continue
                val bgFactor = 1f - fgWeight

                val c = photoPixels[i]
                val r = ((c ushr 16) and 0xFF) / 255f
                val g = ((c ushr 8) and 0xFF) / 255f
                val b = (c and 0xFF) / 255f
                val luma = 0.299f * r + 0.587f * g + 0.114f * b
                val maxC = kotlin.math.max(r, kotlin.math.max(g, b))
                val minC = kotlin.math.min(r, kotlin.math.min(g, b))
                val chroma = maxC - minC

                val d = depthGray[i].coerceIn(0f, 255f)
                val regD = regionalDepth[i].coerceIn(0f, 255f)

                // 2. Detect immovable static terrain / cliffs / stone / buildings
                // Continuous mid-depth masses (d in 62..165, regD >= 68) that aren't water or foliage stay solid
                val isWaterColor = ny >= 0.32f && b > r + 0.04f && (b + g) > r * 1.48f && luma in 0.08f..0.88f
                val isFoliageColor = g > r + 0.03f && g >= b - 0.02f && luma in 0.10f..0.82f
                val isIsolatedSkySprite = regD < 78f && (d - regD) > 8f && ny < 0.82f
                val isOpenSkyDepth = d < 66f && regD < 78f

                var isRigidTerrain = !isOpenSkyDepth && !isIsolatedSkySprite && !isWaterColor && !isFoliageColor &&
                    (d in 60f..175f || regD >= 66f || (ny > 0.65f && luma < 0.45f && chroma < 0.22f))

                var cloudWeight = 0f
                var waterWeight = 0f
                var spriteWeight = 0f

                // On-device semantic heuristics
                if (isIsolatedSkySprite) {
                    // Birds, floating leaves, petals in the sky
                    spriteWeight = 0.95f
                } else if (isWaterColor && d < 165f) {
                    // Ocean, lakes, rivers, waterfalls
                    waterWeight = 0.90f
                } else if (isFoliageColor && d < 170f) {
                    // Swaying trees, grass, bushes
                    spriteWeight = 0.78f
                } else if (isOpenSkyDepth && ny < 0.86f && (luma > 0.30f || b >= r - 0.05f)) {
                    // Clouds & sky
                    cloudWeight = ((72f - d) / 50f).coerceIn(0.35f, 1.0f)
                }

                // 3. Apply Gemini Vision AI region overrides when available
                var forcedStatic = false
                for (reg in aiRegions) {
                    if (nx in reg.xmin..reg.xmax && ny in reg.ymin..reg.ymax) {
                        when (reg.type) {
                            MotionRegionType.STATIC -> {
                                // Only force static on actual solid terrain/structures, not open sky or isolated birds
                                if (!isOpenSkyDepth && !isIsolatedSkySprite) {
                                    forcedStatic = true
                                }
                            }
                            MotionRegionType.CLOUDS -> if (!isRigidTerrain && !isIsolatedSkySprite) {
                                cloudWeight = kotlin.math.max(cloudWeight, 0.92f)
                            }
                            MotionRegionType.WATER -> if (d < 185f) {
                                waterWeight = kotlin.math.max(waterWeight, 0.92f)
                                isRigidTerrain = false
                            }
                            MotionRegionType.BIRDS_ANIMALS -> {
                                spriteWeight = kotlin.math.max(spriteWeight, 0.96f)
                                isRigidTerrain = false
                            }
                            MotionRegionType.FOLIAGE -> if (d < 185f) {
                                spriteWeight = kotlin.math.max(spriteWeight, 0.85f)
                                isRigidTerrain = false
                            }
                        }
                    }
                }

                if (forcedStatic || (isRigidTerrain && waterWeight == 0f && spriteWeight == 0f)) {
                    cloudWeight = 0f
                    waterWeight = 0f
                    spriteWeight = 0f
                }

                rChan[i] = (cloudWeight * bgFactor * 255f).coerceIn(0f, 255f)
                gChan[i] = (waterWeight * bgFactor * 255f).coerceIn(0f, 255f)
                bChan[i] = (spriteWeight * bgFactor * 255f).coerceIn(0f, 255f)
            }
        }

        // Smooth motion channel boundaries while strictly zeroing out the foreground subject core
        val smoothR = kotlin.math.max(1, kotlin.math.min(w, h) / 110)
        val rBlur = DepthOps.boxBlur(rChan, w, h, smoothR)
        val gBlur = DepthOps.boxBlur(gChan, w, h, smoothR)
        val bBlur = DepthOps.boxBlur(bChan, w, h, smoothR)

        return IntArray(n) { i ->
            val fg = (fgMask[i] / 255f).coerceIn(0f, 1f)
            val keep = (1f - fg * fg)
            val rv = (rBlur[i] * keep).toInt().coerceIn(0, 255)
            val gv = (gBlur[i] * keep).toInt().coerceIn(0, 255)
            val bv = (bBlur[i] * keep).toInt().coerceIn(0, 255)
            (0xFF shl 24) or (rv shl 16) or (gv shl 8) or bv
        }
    }

    fun detectFromBitmap(photo: Bitmap, mask: Bitmap, detectedMaskMode: MaskMode): LivingMotionMode {
        val gridW = 24
        val gridH = 16
        val pixels = IntArray(gridW * gridH)
        val maskVals = FloatArray(gridW * gridH)
        for (gy in 0 until gridH) {
            val py = ((gy + 0.5f) / gridH * photo.height).toInt().coerceIn(0, photo.height - 1)
            val my = ((gy + 0.5f) / gridH * mask.height).toInt().coerceIn(0, mask.height - 1)
            for (gx in 0 until gridW) {
                val px = ((gx + 0.5f) / gridW * photo.width).toInt().coerceIn(0, photo.width - 1)
                val mx = ((gx + 0.5f) / gridW * mask.width).toInt().coerceIn(0, mask.width - 1)
                val idx = gy * gridW + gx
                pixels[idx] = photo.getPixel(px, py)
                maskVals[idx] = ((mask.getPixel(mx, my) ushr 16) and 0xFF) / 255f
            }
        }
        return detectFromSampleGrid(pixels, maskVals, gridW, gridH, detectedMaskMode)
    }

    fun detectFromSampleGrid(
        pixels: IntArray,
        maskVals: FloatArray,
        gridW: Int,
        gridH: Int,
        detectedMaskMode: MaskMode,
    ): LivingMotionMode {
        var skyScore = 0f
        var waterScore = 0f
        var greenScore = 0f
        var bgCount = 0

        for (y in 0 until gridH) {
            val ny = y.toFloat() / gridH.coerceAtLeast(1)
            for (x in 0 until gridW) {
                val i = y * gridW + x
                if (maskVals[i] > 0.5f) continue
                bgCount++
                val c = pixels[i]
                val r = ((c ushr 16) and 0xFF) / 255f
                val g = ((c ushr 8) and 0xFF) / 255f
                val b = (c and 0xFF) / 255f
                val luma = 0.299f * r + 0.587f * g + 0.114f * b

                // Upper-half bright/cool sky or soft clouds
                if (ny < 0.55f && (b >= r - 0.03f) && luma > 0.42f) {
                    skyScore += 1f
                }
                // Lower-half ocean/water (blue/cyan/teal dominant)
                if (ny >= 0.40f && b > r + 0.06f && (b + g) > r * 1.6f) {
                    waterScore += 1.2f
                }
                // Foliage / grass / forest green
                if (g > r + 0.04f && g >= b - 0.02f && luma in 0.12f..0.82f) {
                    greenScore += 1.1f
                }
            }
        }

        if (bgCount == 0) return LivingMotionMode.AURA
        val skyRatio = skyScore / bgCount
        val waterRatio = waterScore / bgCount
        val greenRatio = greenScore / bgCount

        return when {
            waterRatio >= 0.14f && waterRatio >= greenRatio -> LivingMotionMode.OCEAN
            skyRatio >= 0.20f && skyRatio >= waterRatio && skyRatio >= greenRatio -> LivingMotionMode.CLOUDS
            greenRatio >= 0.16f -> LivingMotionMode.BREEZE
            detectedMaskMode == MaskMode.NATURE -> LivingMotionMode.CLOUDS
            else -> LivingMotionMode.AURA
        }
    }
}
