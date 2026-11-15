package com.droid.liquidglass3d

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.graphics.Typeface
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/** Cover-cropped, surface-sized copies of the scene bitmaps (built off the main thread). */
class PreparedScene(
    val photo: Bitmap,
    val depth: Bitmap,
    val mask: Bitmap,
    val width: Int,
    val height: Int,
    val detectedMotion: LivingMotionMode = LivingMotionMode.AURA,
    val motion: Bitmap = mask,
)

/**
 * Shared AGSL renderer used by BOTH the wallpaper engine and the Compose live preview.
 * Render on a hardware-accelerated canvas only (RuntimeShader requirement).
 */
class LiquidGlassRenderer(context: Context) {
    private val appContext = context.applicationContext
    private val shader = RuntimeShader(
        appContext.assets.open("LiquidGlassShader.agsl").bufferedReader().use { it.readText() }
    )
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply { this.shader = this@LiquidGlassRenderer.shader }
    private val fallbackPaint = Paint()

    private var surfaceW = 0
    private var surfaceH = 0
    private var sceneReady = false
    private var detectedMotion = LivingMotionMode.AURA

    // Clock layer (sandwiched between background and foreground or floating in front)
    private var clockBitmap: Bitmap? = null
    private var clockCanvas: Canvas? = null
    private var panelX = 0f; private var panelY = 0f; private var panelW = 0f; private var panelH = 0f
    private var lastClockMinute = -1L
    private var lastScale = -1f
    private var lastPosX = -1f
    private var lastPosY = -1f
    private var lastColorPreset: ClockColorPreset? = null
    private var lastFont: ClockFont? = null
    private var lastShowDate = true

    private val clockPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
        setShadowLayer(8f, 0f, 3f, 0x44000000)
    }
    private val datePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xE6FFFFFF.toInt()
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
        setShadowLayer(6f, 0f, 2f, 0x44000000)
    }

    val hasScene: Boolean get() = sceneReady

    /** Returns current screen bounding box of the Liquid Glass panel in surface pixels. */
    fun getPanelRect(): RectF = RectF(panelX, panelY, panelX + panelW, panelY + panelH)

    /** Heavy part: safe on Dispatchers.Default. */
    fun prepare(scene: SceneBitmaps, w: Int, h: Int): PreparedScene =
        PreparedScene(
            coverCrop(scene.photo, w, h),
            coverCrop(scene.depth, w, h),
            coverCrop(scene.mask, w, h),
            w,
            h,
            scene.detectedMotion,
            coverCrop(scene.motion, w, h),
        )

    /** Cheap part: call on the render thread. */
    fun apply(p: PreparedScene) {
        surfaceW = p.width; surfaceH = p.height
        detectedMotion = p.detectedMotion
        shader.setInputShader("photo", clampShader(p.photo))
        shader.setInputShader("depthTex", clampShader(p.depth))
        shader.setInputShader("maskTex", clampShader(p.mask))
        shader.setInputShader("motionTex", clampShader(p.motion))
        lastScale = -1f
        lastClockMinute = -1L
        sceneReady = true
    }

    fun clear() { sceneReady = false }

    private fun clampShader(b: Bitmap) = BitmapShader(b, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)

    private fun layoutPanel(w: Int, h: Int, settings: GlassSettings) {
        val bounds = LiquidGlassLayout.calculate(w, h, settings.clockScale, settings.clockPosX, settings.clockPosY)
        panelW = bounds.width
        panelH = bounds.height
        panelX = bounds.x
        panelY = bounds.y

        clockBitmap = Bitmap.createBitmap(max(1, panelW.toInt()), max(1, panelH.toInt()), Bitmap.Config.ARGB_8888)
        clockCanvas = Canvas(clockBitmap!!)
        clockPaint.textSize = panelH * if (settings.showDate) 0.50f else 0.62f
        datePaint.textSize = panelH * 0.14f
        clockPaint.typeface = Typeface.create(settings.clockFont.family, settings.clockFont.style)
        val dateFamily = when (settings.clockFont) {
            ClockFont.SERIF, ClockFont.SERIF_BOLD, ClockFont.SERIF_ITALIC -> "serif"
            ClockFont.MONO, ClockFont.RETRO_CODE -> "monospace"
            ClockFont.CASUAL -> "casual"
            ClockFont.CURSIVE -> "cursive"
            ClockFont.CONDENSED, ClockFont.CONDENSED_LIGHT, ClockFont.CONDENSED_MEDIUM -> "sans-serif-condensed"
            else -> "sans-serif-medium"
        }
        datePaint.typeface = Typeface.create(dateFamily, Typeface.NORMAL)

        val preset = settings.clockColorPreset
        if (preset.isGradient && preset.colors.size >= 2) {
            val grad = LinearGradient(0f, 0f, panelW, panelH, preset.colors, null, Shader.TileMode.CLAMP)
            clockPaint.shader = grad
            datePaint.shader = grad
        } else {
            clockPaint.shader = null
            datePaint.shader = null
            val c = preset.colors[0]
            clockPaint.color = c
            val dateAlpha = ((Color.alpha(c) * 0.88f).toInt()).coerceIn(0, 255)
            datePaint.color = Color.argb(dateAlpha, Color.red(c), Color.green(c), Color.blue(c))
        }
    }

    private fun refreshClock(now: Long, settings: GlassSettings) {
        val styleChanged = settings.clockScale != lastScale ||
            settings.clockPosX != lastPosX ||
            settings.clockPosY != lastPosY ||
            settings.clockColorPreset != lastColorPreset ||
            settings.clockFont != lastFont ||
            settings.showDate != lastShowDate

        if (styleChanged) {
            layoutPanel(surfaceW, surfaceH, settings)
            lastScale = settings.clockScale
            lastPosX = settings.clockPosX
            lastPosY = settings.clockPosY
            lastColorPreset = settings.clockColorPreset
            lastFont = settings.clockFont
            lastShowDate = settings.showDate
            lastClockMinute = -1L
        }

        val minute = now / 60_000L
        if (minute == lastClockMinute) return
        lastClockMinute = minute
        val c = clockCanvas ?: return
        val bmp = clockBitmap ?: return
        bmp.eraseColor(Color.TRANSPARENT)
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        val use24 = android.text.format.DateFormat.is24HourFormat(appContext)
        val h = if (use24) cal.get(Calendar.HOUR_OF_DAY) else (cal.get(Calendar.HOUR).let { if (it == 0) 12 else it })
        val time = String.format(Locale.getDefault(), if (use24) "%02d:%02d" else "%d:%02d", h, cal.get(Calendar.MINUTE))

        if (settings.showDate) {
            val date = DateFormat.getDateInstance(DateFormat.FULL, Locale.getDefault()).format(Date(now))
            c.drawText(time, bmp.width / 2f, bmp.height * 0.60f, clockPaint)
            c.drawText(date, bmp.width / 2f, bmp.height * 0.85f, datePaint)
        } else {
            val fm = clockPaint.fontMetrics
            val baseline = bmp.height / 2f - (fm.ascent + fm.descent) / 2f
            c.drawText(time, bmp.width / 2f, baseline, clockPaint)
        }
        shader.setInputShader("clockTex", clampShader(bmp))
    }

    /**
     * @param tiltX/tiltY smoothed parallax input in -1..1
     */
    fun draw(
        canvas: Canvas, tiltX: Float, tiltY: Float, settings: GlassSettings, timeSec: Float,
    ) {
        if (!sceneReady) { drawPlaceholder(canvas); return }
        refreshClock(System.currentTimeMillis(), settings)
        shader.setFloatUniform("res", surfaceW.toFloat(), surfaceH.toFloat())
        shader.setFloatUniform("tilt", tiltX, tiltY)
        shader.setFloatUniform("depthIntensity", settings.depthIntensity)
        shader.setFloatUniform("refraction", settings.refraction)
        shader.setFloatUniform("spatial", if (settings.spatialEnabled) 1f else 0f)
        shader.setFloatUniform("time", timeSec)
        shader.setFloatUniform("panel", panelX, panelY, panelW, panelH)
        shader.setFloatUniform("clockInFront", if (settings.clockInFront) 1f else 0f)
        shader.setFloatUniform("glassBlend", settings.glassBlend.coerceIn(0f, 1f))
        val baseColor = GlassColorOps.effectiveGlassTint(settings)
        shader.setFloatUniform("baseColor", baseColor[0], baseColor[1], baseColor[2], baseColor[3])
        val effectiveMotion = LivingMotionOps.resolveEffectiveMode(settings.livingMotionMode, detectedMotion)
        shader.setFloatUniform("livingEnabled", if (settings.livingBackgroundEnabled) 1f else 0f)
        shader.setFloatUniform("livingMode", effectiveMotion.shaderIndex)
        shader.setFloatUniform("livingIntensity", settings.livingMotionIntensity.coerceIn(0f, 1.5f))
        shader.setFloatUniform("popOut3d", if (settings.popOut3d) 1f else 0f)

        canvas.drawRect(0f, 0f, surfaceW.toFloat(), surfaceH.toFloat(), paint)
    }

    private fun drawPlaceholder(canvas: Canvas) {
        val w = canvas.width.toFloat(); val h = canvas.height.toFloat()
        fallbackPaint.shader = LinearGradient(0f, 0f, w, h, 0xFF0B0F2E.toInt(), 0xFF0A6C8F.toInt(), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, fallbackPaint)
    }

    /**
     * Crops and scales the source bitmap to fit the destination (w, h) while maintaining
     * center alignment and aspect ratio. For 16:10 Google Book laptops (e.g. 2560x1600),
     * this guarantees standard 16:10 frame rendering.
     */
    internal fun coverCrop(src: Bitmap, w: Int, h: Int): Bitmap {
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val s = max(w / src.width.toFloat(), h / src.height.toFloat())
        val m = Matrix().apply {
            postScale(s, s)
            postTranslate((w - src.width * s) / 2f, (h - src.height * s) / 2f)
        }
        Canvas(out).drawBitmap(src, m, Paint(Paint.FILTER_BITMAP_FLAG))
        return out
    }
}

data class PanelBounds(val x: Float, val y: Float, val width: Float, val height: Float) {
    val right: Float get() = x + width
    val bottom: Float get() = y + height
}

object LiquidGlassLayout {
    fun calculate(
        w: Int,
        h: Int,
        scale: Float,
        posX: Float,
        posY: Float
    ): PanelBounds {
        val unit = h / 1000f
        val clampedScale = scale.coerceIn(0.65f, 1.45f)
        val panelW = min(w * 0.92f, minOf(w * 0.72f, 640f * unit * 1.4f) * clampedScale)
        val panelH = min(h * 0.44f, 250f * unit * 1.1f * clampedScale)

        val maxPanelX = max(0f, w - panelW)
        val maxPanelY = max(0f, h - panelH)
        val panelX = maxPanelX * posX.coerceIn(0f, 1f)
        val panelY = maxPanelY * posY.coerceIn(0f, 1f)
        return PanelBounds(panelX, panelY, panelW, panelH)
    }
}
