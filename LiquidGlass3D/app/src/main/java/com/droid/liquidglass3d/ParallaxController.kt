package com.droid.liquidglass3d

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.display.DisplayManager
import android.view.Display
import android.view.Surface
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * Blends two parallax sources into one smoothed (x, y) tilt in -1..1:
 *  - device tilt via Sensor.TYPE_ROTATION_VECTOR (tablet / convertible tent & tablet modes)
 *  - pointer / hover / touch / wallpaper page offsets (laptop & desktop mode)
 *
 * While a pointer is active it dominates; when it goes idle it fades back to the sensor.
 * Not thread-safe: use from one thread (main).
 */
class ParallaxController(context: Context) : SensorEventListener {
    private val appContext = context.applicationContext
    private val sensorManager = appContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val activeSensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val displayManager = appContext.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager

    private val rot = FloatArray(9)
    private val remapped = FloatArray(9)
    private val orient = FloatArray(3)
    private var baseline: FloatArray? = null

    private val filter = ParallaxFilter()

    val x: Float get() = filter.x
    val y: Float get() = filter.y

    val hasSensor: Boolean get() = activeSensor != null

    fun start() {
        activeSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        baseline = null
    }

    fun stop() {
        sensorManager.unregisterListener(this)
        filter.stop()
    }

    /** Pointer position normalized to the view/surface: (px,py) in px, size in px. */
    fun onPointer(px: Float, py: Float, w: Float, h: Float, active: Boolean = true) {
        filter.onPointer(px, py, w, h, active)
    }

    fun onPointerExit() { filter.onPointerExit() }

    /** Launcher page scroll (onOffsetsChanged): 0..1 -> -1..1, added on top. */
    fun onPageOffset(xOffset: Float) { filter.onPageOffset(xOffset) }

    /** Advance smoothing; call once per frame. */
    fun step(dtSeconds: Float) { filter.step(dtSeconds) }

    override fun onSensorChanged(event: SensorEvent) {
        val displayRotation = displayManager.getDisplay(Display.DEFAULT_DISPLAY)?.rotation ?: Surface.ROTATION_0
        val pitch: Float
        val roll: Float
        when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR,
            Sensor.TYPE_GAME_ROTATION_VECTOR -> {
                SensorManager.getRotationMatrixFromVector(rot, event.values)
                val (ax, ay) = when (displayRotation) {
                    Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
                    Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
                    Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
                    else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
                }
                SensorManager.remapCoordinateSystem(rot, ax, ay, remapped)
                SensorManager.getOrientation(remapped, orient) // [azimuth, pitch, roll]
                pitch = orient[1]
                roll = orient[2]
            }
            Sensor.TYPE_GRAVITY,
            Sensor.TYPE_ACCELEROMETER -> {
                val (gx, gy) = when (displayRotation) {
                    Surface.ROTATION_90 -> -event.values[1] to event.values[0]
                    Surface.ROTATION_180 -> -event.values[0] to -event.values[1]
                    Surface.ROTATION_270 -> event.values[1] to -event.values[0]
                    else -> event.values[0] to event.values[1]
                }
                val (p, r) = ParallaxFilter.anglesFromGravity(gx, gy, event.values[2])
                pitch = p
                roll = r
            }
            else -> return
        }

        val b = baseline ?: floatArrayOf(pitch, roll).also { baseline = it }
        // Slowly re-center so the resting hold posture is "neutral".
        b[0] += (pitch - b[0]) * 0.0015f
        b[1] += (roll - b[1]) * 0.0015f

        val range = (PI / 12).toFloat() // ±15° = full deflection for responsive laptop/tablet tilt
        val targetX = ((roll - b[1]) / range).coerceIn(-1f, 1f)
        val targetY = ((pitch - b[0]) / range).coerceIn(-1f, 1f)
        filter.onSensorTarget(targetX, -targetY)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}

/**
 * Pure physics & smoothing filter for interactive 3D parallax.
 * Blends sensor attitude with cursor/touch interaction and desktop scroll offsets.
 */
class ParallaxFilter {
    var x = 0f; private set
    var y = 0f; private set

    private var sensorX = 0f
    private var sensorY = 0f
    private var pointerX = 0f
    private var pointerY = 0f
    private var pageOffsetX = 0f
    private var pointerWeight = 0f
    private var pointerWeightTarget = 0f
    private var lastPointerNs = 0L

    fun stop() {
        pointerWeightTarget = 0f
    }

    fun onPointer(px: Float, py: Float, w: Float, h: Float, active: Boolean = true) {
        if (w <= 0f || h <= 0f) return
        pointerX = ((px / w) * 2f - 1f).coerceIn(-1f, 1f)
        pointerY = ((py / h) * 2f - 1f).coerceIn(-1f, 1f)
        pointerWeightTarget = if (active) 1f else 0f
        lastPointerNs = System.nanoTime()
    }

    fun onPointerExit() {
        pointerWeightTarget = 0f
    }

    fun onPageOffset(xOffset: Float) {
        pageOffsetX = (xOffset * 2f - 1f).coerceIn(-1f, 1f)
    }

    fun onSensorTarget(targetX: Float, targetY: Float) {
        sensorX += (targetX - sensorX) * 0.25f
        sensorY += (targetY - sensorY) * 0.25f
    }

    fun step(dtSeconds: Float) {
        if (pointerWeightTarget > 0f && System.nanoTime() - lastPointerNs > 2_500_000_000L) {
            pointerWeightTarget = 0f
        }
        pointerWeight += (pointerWeightTarget - pointerWeight) * (1f - exp(-dtSeconds * 6f))

        val tx = sensorX * (1f - pointerWeight) + pointerX * pointerWeight + pageOffsetX * 0.35f
        val ty = sensorY * (1f - pointerWeight) + pointerY * pointerWeight
        val k = 1f - exp(-dtSeconds * 12f)
        x += (tx.coerceIn(-1f, 1f) - x) * k
        y += (ty.coerceIn(-1f, 1f) - y) * k
    }

    companion object {
        fun anglesFromGravity(gx: Float, gy: Float, gz: Float): Pair<Float, Float> {
            val pitch = atan2(gy, sqrt(gx * gx + gz * gz))
            val roll = atan2(gx, sqrt(gy * gy + gz * gz))
            return pitch to roll
        }
    }
}
