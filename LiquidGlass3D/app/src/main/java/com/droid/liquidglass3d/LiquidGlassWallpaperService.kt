package com.droid.liquidglass3d

import android.content.SharedPreferences
import android.service.wallpaper.WallpaperService
import android.view.Choreographer
import android.view.MotionEvent
import android.view.SurfaceHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Live wallpaper: AGSL RuntimeShader rendered into the wallpaper Surface through a
 * hardware-accelerated canvas, driven by Choreographer while visible.
 */
class LiquidGlassWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = LiquidGlassEngine()

    private inner class LiquidGlassEngine : Engine(), Choreographer.FrameCallback,
        SharedPreferences.OnSharedPreferenceChangeListener {

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        private val choreographer = Choreographer.getInstance()
        private val renderer = LiquidGlassRenderer(this@LiquidGlassWallpaperService)
        private val parallax = ParallaxController(this@LiquidGlassWallpaperService)
        private val prefs = SceneStore.prefs(this@LiquidGlassWallpaperService)

        private var settings = SceneStore.loadSettings(this@LiquidGlassWallpaperService)
        private var surfaceW = 0
        private var surfaceH = 0
        private var visible = false
        private var running = false
        private var lastFrameNs = 0L
        private var startNs = System.nanoTime()
        private var sceneJob: Job? = null

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            setTouchEventsEnabled(true) // pointer/hover/touch arrive via onTouchEvent
            prefs.registerOnSharedPreferenceChangeListener(this)
        }

        override fun onDestroy() {
            prefs.unregisterOnSharedPreferenceChangeListener(this)
            stopLoop()
            parallax.stop()
            scope.cancel()
            super.onDestroy()
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            surfaceW = width; surfaceH = height
            loadScene()
            drawFrame(0f)
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            stopLoop()
            sceneJob?.cancel()
            super.onSurfaceDestroyed(holder)
        }

        override fun onVisibilityChanged(visible: Boolean) {
            this.visible = visible
            if (visible) {
                settings = SceneStore.loadSettings(this@LiquidGlassWallpaperService)
                parallax.start()
                startLoop()
            } else {
                parallax.stop()
                stopLoop()
            }
        }

        // Laptop / desktop mode: mouse hover + click-drag + finger touch all feed the parallax.
        override fun onTouchEvent(event: MotionEvent) {
            when (event.actionMasked) {
                MotionEvent.ACTION_HOVER_ENTER,
                MotionEvent.ACTION_HOVER_MOVE,
                MotionEvent.ACTION_DOWN,
                MotionEvent.ACTION_MOVE -> parallax.onPointer(event.x, event.y, surfaceW.toFloat(), surfaceH.toFloat())
                MotionEvent.ACTION_HOVER_EXIT,
                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> parallax.onPointerExit()
            }
            super.onTouchEvent(event)
        }

        override fun onOffsetsChanged(
            xOffset: Float, yOffset: Float, xOffsetStep: Float, yOffsetStep: Float, xPixelOffset: Int, yPixelOffset: Int,
        ) {
            parallax.onPageOffset(xOffset)
        }

        override fun onSharedPreferenceChanged(sp: SharedPreferences, key: String?) {
            if (key == SceneStore.KEY_SCENE_VERSION) {
                loadScene()
            } else {
                settings = SceneStore.loadSettings(this@LiquidGlassWallpaperService)
            }
        }

        private fun loadScene() {
            if (surfaceW <= 0 || surfaceH <= 0) return
            val w = surfaceW; val h = surfaceH
            sceneJob?.cancel()
            sceneJob = scope.launch {
                val prepared = withContext(Dispatchers.Default) {
                    SceneStore.loadScene(this@LiquidGlassWallpaperService)?.let { renderer.prepare(it, w, h) }
                }
                if (prepared != null) renderer.apply(prepared) else renderer.clear()
                drawFrame(0f)
            }
        }

        private fun startLoop() {
            if (running) return
            running = true
            lastFrameNs = 0L
            choreographer.postFrameCallback(this)
        }

        private fun stopLoop() {
            running = false
            choreographer.removeFrameCallback(this)
        }

        override fun doFrame(frameTimeNanos: Long) {
            if (!running || !visible) return
            val dt = if (lastFrameNs == 0L) 0.016f else ((frameTimeNanos - lastFrameNs) / 1e9f).coerceIn(0.001f, 0.1f)
            lastFrameNs = frameTimeNanos
            drawFrame(dt)
            choreographer.postFrameCallback(this)
        }

        private fun drawFrame(dt: Float) {
            val holder = surfaceHolder ?: return
            if (!holder.surface.isValid) return
            parallax.step(dt)
            val canvas = try { holder.lockHardwareCanvas() } catch (_: Exception) { null } ?: return
            try {
                val t = (System.nanoTime() - startNs) / 1e9f
                renderer.draw(canvas, parallax.x, parallax.y, settings, t)
            } finally {
                holder.unlockCanvasAndPost(canvas)
            }
        }
    }
}
