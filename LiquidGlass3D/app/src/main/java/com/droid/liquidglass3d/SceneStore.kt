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
)

/** Decoded source image + depth map + subject mask, as cached in `filesDir`. */
class SceneBitmaps(
    val photo: Bitmap,
    val depth: Bitmap,
    val mask: Bitmap,
    val detectedMode: MaskMode = MaskMode.OBJECT,
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

    const val SOURCE_FILE = "source.jpg"
    const val DEPTH_FILE = "depth.png"
    const val MASK_FILE = "mask.png"
    const val MASK_PORTRAIT_FILE = "mask_portrait.png"
    const val MASK_OBJECT_FILE = "mask_object.png"
    const val MASK_NATURE_FILE = "mask_nature.png"

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
            .apply()
    }

    fun saveDetectedMode(context: Context, mode: MaskMode) {
        prefs(context).edit().putString(KEY_DETECTED_MODE, mode.name).apply()
    }

    fun loadDetectedMode(context: Context): MaskMode {
        val name = prefs(context).getString(KEY_DETECTED_MODE, MaskMode.OBJECT.name) ?: MaskMode.OBJECT.name
        return runCatching { MaskMode.valueOf(name) }.getOrDefault(MaskMode.OBJECT)
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
        val selected = loadSettings(context).maskMode
        val maskFile = File(dir, maskFileFor(selected, detected)).takeIf { it.exists() } ?: File(dir, MASK_FILE)
        val photo = BitmapFactory.decodeFile(File(dir, SOURCE_FILE).path) ?: return null
        val depth = BitmapFactory.decodeFile(File(dir, DEPTH_FILE).path) ?: return null
        val mask = BitmapFactory.decodeFile(maskFile.path) ?: return null
        return SceneBitmaps(photo, depth, mask, detected)
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
            saveDetectedMode(context, MaskMode.PORTRAIT)
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
