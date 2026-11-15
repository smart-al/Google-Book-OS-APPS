package com.droid.liquidglass3d

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ImageDecoder
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class DepthModelMissingException(name: String) : Exception(
    "Model '$name' not found in app/src/main/assets."
)

/**
 * On-device multi-model AI pipeline via LiteRT:
 *  1. Depth Anything V2 Small (`depth_anything_v2_small_int8.tflite`) -> monocular depth map + nature depth mask
 *  2. MediaPipe Selfie Multiclass (`selfie_multiclass.tflite`) -> 6-class portrait detection & mask
 *  3. U²-Net (`u2net_fp16.tflite`) -> general salient-object segmentation (animals, objects, people edges)
 *
 * An AI scene router inspects the semantic class probabilities (face/hair/body/clothes) and U²-Net
 * saliency separation to automatically pick the best mask strategy:
 *  - Portrait (Person): blends Selfie Multiclass + U²-Net for crisp edges & fine hair
 *  - Object (Animal / Product / Vehicle): uses U²-Net salient object mask
 *  - Nature / Landscape: uses a smooth depth-foreground ramp from Depth Anything V2
 */
class DepthEstimator(
    private val context: Context,
    private val depthModelAsset: String = DEPTH_MODEL_ASSET,
    private val selfieModelAsset: String = SELFIE_MODEL_ASSET,
    private val u2netModelAsset: String = U2NET_MODEL_ASSET,
    private val maxImageSide: Int = 1280,
) {
    companion object {
        const val DEPTH_MODEL_ASSET = "depth_anything_v2_small_int8.tflite"
        const val SELFIE_MODEL_ASSET = "selfie_multiclass.tflite"
        const val U2NET_MODEL_ASSET = "u2net_fp16.tflite"
        private val MEAN = floatArrayOf(0.485f, 0.456f, 0.406f)
        private val STD = floatArrayOf(0.229f, 0.224f, 0.225f)
    }

    /** Runs the full AI depth + segmentation pipeline on `Dispatchers.Default` and caches results. */
    suspend fun process(uri: Uri, onStage: (String) -> Unit = {}): SceneBitmaps =
        withContext(Dispatchers.Default) {
            onStage("Decoding image…")
            val photo = decode(uri)
            processBitmap(photo, onStage)
        }

    /** Runs the full AI depth + segmentation pipeline on an already-decoded [photo] and caches results. */
    suspend fun processBitmap(photo: Bitmap, onStage: (String) -> Unit = {}): SceneBitmaps =
        withContext(Dispatchers.Default) {
            val w = photo.width
            val h = photo.height
            val photoPixels = IntArray(w * h).also { photo.getPixels(it, 0, w, 0, 0, w, h) }

            onStage("Estimating 3D depth…")
            val rawDepthGray = estimateDepth(photo)

            onStage("Analyzing scene (Person / Object / Nature)…")
            val (selfieMask, isPortrait) = estimateSelfie(photo)
            val (u2netRawMask, isObject) = estimateU2Net(photo)

            val detectedMode = when {
                isPortrait -> MaskMode.PORTRAIT
                isObject -> MaskMode.OBJECT
                else -> MaskMode.NATURE
            }

            // Portrait mask blends Selfie Multiclass (fine hair/head) with U²-Net (crisp garment edges)
            val rawPortraitMask = FloatArray(w * h) { i ->
                (0.35f * selfieMask[i] + 0.65f * u2netRawMask[i]).coerceIn(0f, 255f)
            }

            onStage("Refining foreground mask & depth layers…")
            val portraitMask = DepthOps.refineForegroundMask(rawPortraitMask, photoPixels, w, h)
            val u2netMask = DepthOps.refineForegroundMask(u2netRawMask, photoPixels, w, h)
            val primaryFgMask = when (detectedMode) {
                MaskMode.PORTRAIT -> portraitMask
                MaskMode.OBJECT -> u2netMask
                else -> u2netMask
            }
            val depthGray = DepthOps.postProcessDepth(rawDepthGray, primaryFgMask, w, h)
            val natureMask = buildNatureDepthMask(depthGray, w, h)

            onStage("Caching AI masks (${detectedMode.label})…")
            val depthBmp = grayToBitmap(depthGray, w, h)
            val portraitBmp = grayToBitmap(portraitMask, w, h)
            val objectBmp = grayToBitmap(u2netMask, w, h)
            val natureBmp = grayToBitmap(natureMask, w, h)

            save(photo, SceneStore.SOURCE_FILE, jpeg = true)
            save(depthBmp, SceneStore.DEPTH_FILE)
            save(portraitBmp, SceneStore.MASK_PORTRAIT_FILE)
            save(objectBmp, SceneStore.MASK_OBJECT_FILE)
            save(natureBmp, SceneStore.MASK_NATURE_FILE)

            SceneStore.saveDetectedMode(context, detectedMode)
            val selected = SceneStore.loadSettings(context).maskMode
            val effective = if (selected == MaskMode.AUTO) detectedMode else selected
            val activeBmp = when (effective) {
                MaskMode.PORTRAIT -> portraitBmp
                MaskMode.OBJECT -> objectBmp
                MaskMode.NATURE -> natureBmp
                MaskMode.AUTO -> objectBmp
            }
            save(activeBmp, SceneStore.MASK_FILE)
            val activeMaskArray = when (effective) {
                MaskMode.PORTRAIT -> portraitMask
                MaskMode.OBJECT -> u2netMask
                MaskMode.NATURE -> natureMask
                MaskMode.AUTO -> u2netMask
            }
            val motionPx = LivingMotionOps.buildMotionMaskChannels(photoPixels, depthGray, activeMaskArray, w, h)
            val motionBmp = Bitmap.createBitmap(motionPx, w, h, Bitmap.Config.ARGB_8888)
            save(motionBmp, SceneStore.MOTION_FILE)
            val detectedMotion = LivingMotionOps.detectFromBitmap(photo, activeBmp, detectedMode)
            SceneStore.saveDetectedMotion(context, detectedMotion)
            SceneStore.bumpSceneVersion(context)
            SceneBitmaps(photo, depthBmp, activeBmp, detectedMode, detectedMotion, motionBmp)
        }

    private fun loadModelBuffer(assetName: String): ByteBuffer {
        val model = try {
            context.assets.open(assetName).use { it.readBytes() }
        } catch (_: java.io.FileNotFoundException) {
            throw DepthModelMissingException(assetName)
        }
        return ByteBuffer.allocateDirect(model.size).order(ByteOrder.nativeOrder()).apply {
            put(model); rewind()
        }
    }

    private fun interpreterOptions(): Interpreter.Options =
        Interpreter.Options().apply { setNumThreads(max(2, Runtime.getRuntime().availableProcessors() / 2)) }

    private fun decode(uri: Uri): Bitmap {
        val src = ImageDecoder.createSource(context.contentResolver, uri)
        return ImageDecoder.decodeBitmap(src) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.isMutableRequired = false
            val longest = max(info.size.width, info.size.height)
            if (longest > maxImageSide) {
                val s = maxImageSide.toFloat() / longest
                decoder.setTargetSize((info.size.width * s).roundToInt(), (info.size.height * s).roundToInt())
            }
        }.let { if (it.config == Bitmap.Config.ARGB_8888) it else it.copy(Bitmap.Config.ARGB_8888, false) }
    }

    /** Returns a normalized depth map in 0..255 (255 = near) at the photo's resolution. */
    private fun estimateDepth(photo: Bitmap): FloatArray {
        val buffer = loadModelBuffer(depthModelAsset)
        Interpreter(buffer, interpreterOptions()).use { interp ->
            val inT = interp.getInputTensor(0)
            val shape = inT.shape()
            val nchw = shape.size == 4 && shape[1] == 3
            val inH = if (nchw) shape[2] else shape[1]
            val inW = if (nchw) shape[3] else shape[2]
            val floatInput = inT.dataType().name == "FLOAT32"

            val resized = Bitmap.createScaledBitmap(photo, inW, inH, true)
            val px = IntArray(inW * inH).also { resized.getPixels(it, 0, inW, 0, 0, inW, inH) }

            val input: ByteBuffer = if (floatInput) {
                ByteBuffer.allocateDirect(4 * inW * inH * 3).order(ByteOrder.nativeOrder()).also { b ->
                    if (nchw) {
                        for (c in 0 until 3) for (i in px.indices) {
                            val v = (px[i] shr (16 - 8 * c) and 0xFF) / 255f
                            b.putFloat((v - MEAN[c]) / STD[c])
                        }
                    } else {
                        for (i in px.indices) for (c in 0 until 3) {
                            val v = (px[i] shr (16 - 8 * c) and 0xFF) / 255f
                            b.putFloat((v - MEAN[c]) / STD[c])
                        }
                    }
                }
            } else {
                ByteBuffer.allocateDirect(inW * inH * 3).order(ByteOrder.nativeOrder()).also { b ->
                    if (nchw) {
                        for (c in 0 until 3) for (i in px.indices) b.put((px[i] shr (16 - 8 * c) and 0xFF).toByte())
                    } else {
                        for (i in px.indices) for (c in 0 until 3) b.put((px[i] shr (16 - 8 * c) and 0xFF).toByte())
                    }
                }
            }
            input.rewind()

            val outT = interp.getOutputTensor(0)
            val outShape = outT.shape()
            val count = outShape.fold(1) { a, b -> a * b }
            val dims = outShape.toList().let { l -> if (l.size > 2 && l.last() == 1) l.dropLast(1) else l }
            val outH = dims[dims.size - 2]
            val outW = dims[dims.size - 1]
            val quantized = outT.dataType().name != "FLOAT32"
            val out = ByteBuffer.allocateDirect(outT.numBytes()).order(ByteOrder.nativeOrder())

            interp.run(input, out)
            out.rewind()

            val raw = FloatArray(count)
            if (quantized) {
                val q = outT.quantizationParams()
                val unsigned = outT.dataType().name == "UINT8"
                for (i in 0 until count) {
                    val b = out.get().toInt().let { if (unsigned) it and 0xFF else it }
                    raw[i] = (b - q.zeroPoint) * (if (q.scale == 0f) 1f else q.scale)
                }
            } else {
                out.asFloatBuffer().get(raw)
            }
            return upsample(normalize(raw), outW, outH, photo.width, photo.height)
        }
    }

    /**
     * Runs MediaPipe Selfie Multiclass (`[1,256,256,3]` -> `[1,256,256,6]`).
     * Classes: 0=bg, 1=hair, 2=body, 3=face, 4=clothes, 5=other.
     * Returns (upsampled foreground mask 0..255, isPortraitDetected).
     */
    private fun estimateSelfie(photo: Bitmap): Pair<FloatArray, Boolean> {
        val buffer = loadModelBuffer(selfieModelAsset)
        Interpreter(buffer, interpreterOptions()).use { interp ->
            val inShape = interp.getInputTensor(0).shape() // [1, H, W, 3]
            val inH = inShape[1]
            val inW = inShape[2]
            val resized = Bitmap.createScaledBitmap(photo, inW, inH, true)
            val px = IntArray(inW * inH).also { resized.getPixels(it, 0, inW, 0, 0, inW, inH) }

            val input = ByteBuffer.allocateDirect(4 * inW * inH * 3).order(ByteOrder.nativeOrder())
            for (i in px.indices) {
                val p = px[i]
                input.putFloat(((p shr 16) and 0xFF) / 255f)
                input.putFloat(((p shr 8) and 0xFF) / 255f)
                input.putFloat((p and 0xFF) / 255f)
            }
            input.rewind()

            val outT = interp.getOutputTensor(0)
            val outShape = outT.shape() // [1, H, W, 6]
            val classes = outShape.last()
            val n = inW * inH
            val out = ByteBuffer.allocateDirect(outT.numBytes()).order(ByteOrder.nativeOrder())
            interp.run(input, out)
            out.rewind()
            val logits = FloatArray(n * classes)
            out.asFloatBuffer().get(logits)

            val fg = FloatArray(n)
            var coreCount = 0
            var fgCount = 0
            for (i in 0 until n) {
                val base = i * classes
                var m = logits[base]
                for (c in 1 until classes) if (logits[base + c] > m) m = logits[base + c]
                var sumExp = 0f
                val exps = FloatArray(classes)
                for (c in 0 until classes) {
                    val e = exp((logits[base + c] - m).toDouble()).toFloat()
                    exps[c] = e
                    sumExp += e
                }
                val bgProb = exps[0] / sumExp
                val fgProb = (1f - bgProb).coerceIn(0f, 1f)
                fg[i] = fgProb * 255f
                if (fgProb > 0.50f) fgCount++
                // face(3) + hair(1) + body(2)
                val coreProb = if (classes >= 4) (exps[1] + exps[2] + exps[3]) / sumExp else fgProb
                if (coreProb > 0.50f) coreCount++
            }
            val coreFrac = coreCount.toFloat() / n
            val fgFrac = fgCount.toFloat() / n
            val isPortrait = coreFrac >= 0.008f && fgFrac in 0.03f..0.88f
            return upsample(fg, inW, inH, photo.width, photo.height) to isPortrait
        }
    }

    /**
     * Runs U²-Net (`[1,3,320,320]` NCHW ImageNet-normalized -> `[1,1,320,320]`).
     * Returns (upsampled salient-object mask 0..255, isDecisiveObject).
     */
    private fun estimateU2Net(photo: Bitmap): Pair<FloatArray, Boolean> {
        val buffer = loadModelBuffer(u2netModelAsset)
        Interpreter(buffer, interpreterOptions()).use { interp ->
            val inShape = interp.getInputTensor(0).shape() // [1, 3, H, W]
            val nchw = inShape.size == 4 && inShape[1] == 3
            val inH = if (nchw) inShape[2] else inShape[1]
            val inW = if (nchw) inShape[3] else inShape[2]
            val resized = Bitmap.createScaledBitmap(photo, inW, inH, true)
            val px = IntArray(inW * inH).also { resized.getPixels(it, 0, inW, 0, 0, inW, inH) }

            val input = ByteBuffer.allocateDirect(4 * inW * inH * 3).order(ByteOrder.nativeOrder())
            if (nchw) {
                for (c in 0 until 3) for (i in px.indices) {
                    val v = ((px[i] shr (16 - 8 * c)) and 0xFF) / 255f
                    input.putFloat((v - MEAN[c]) / STD[c])
                }
            } else {
                for (i in px.indices) for (c in 0 until 3) {
                    val v = ((px[i] shr (16 - 8 * c)) and 0xFF) / 255f
                    input.putFloat((v - MEAN[c]) / STD[c])
                }
            }
            input.rewind()

            val outT = interp.getOutputTensor(0)
            val n = inW * inH
            val out = ByteBuffer.allocateDirect(outT.numBytes()).order(ByteOrder.nativeOrder())
            interp.run(input, out)
            out.rewind()
            val raw = FloatArray(n)
            out.asFloatBuffer().get(raw)

            var minV = Float.POSITIVE_INFINITY
            var maxV = Float.NEGATIVE_INFINITY
            for (v in raw) {
                if (v < minV) minV = v
                if (v > maxV) maxV = v
            }
            val needsSigmoid = minV < -0.01f || maxV > 1.01f
            val prob = FloatArray(n)
            var fgCount = 0
            var bgCount = 0
            for (i in 0 until n) {
                val p = if (needsSigmoid) (1f / (1f + exp(-raw[i].toDouble()).toFloat())) else raw[i].coerceIn(0f, 1f)
                prob[i] = p * 255f
                if (p > 0.65f) fgCount++
                if (p < 0.25f) bgCount++
            }
            val fgFrac = fgCount.toFloat() / n
            val bgFrac = bgCount.toFloat() / n
            val isObject = fgFrac in 0.03f..0.82f && bgFrac >= 0.15f && (fgFrac + bgFrac) >= 0.65f
            return upsample(prob, inW, inH, photo.width, photo.height) to isObject
        }
    }

    /** Robust min/max normalization (2nd–98th percentile) to 0..255. */
    internal fun normalize(raw: FloatArray): FloatArray = DepthOps.normalize(raw)

    internal fun upsample(src: FloatArray, sw: Int, sh: Int, dw: Int, dh: Int): FloatArray =
        DepthOps.upsample(src, sw, sh, dw, dh)

    /** Nature / Landscape fallback: smooth near-plane foreground ramp from the depth map. */
    internal fun buildNatureDepthMask(depth: FloatArray, w: Int, h: Int): FloatArray =
        DepthOps.buildNatureDepthMask(depth, w, h)

    internal fun boxBlur(src: FloatArray, w: Int, h: Int, r: Int): FloatArray =
        DepthOps.boxBlur(src, w, h, r)

    private fun grayToBitmap(gray: FloatArray, w: Int, h: Int): Bitmap {
        val px = IntArray(w * h) { val g = gray[it].roundToInt().coerceIn(0, 255); Color.rgb(g, g, g) }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }

    private fun save(bmp: Bitmap, name: String, jpeg: Boolean = false) {
        val target = File(context.filesDir, name)
        val tmp = File(context.filesDir, "$name.tmp")
        FileOutputStream(tmp).use {
            if (jpeg) bmp.compress(Bitmap.CompressFormat.JPEG, 94, it)
            else bmp.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        tmp.renameTo(target)
    }
}

object DepthOps {
    /** Robust min/max normalization (2nd–98th percentile) to 0..255. */
    fun normalize(raw: FloatArray): FloatArray {
        val sorted = raw.copyOf().also { it.sort() }
        val lo = sorted[(sorted.size * 0.02f).toInt().coerceIn(0, sorted.lastIndex)]
        val hi = sorted[(sorted.size * 0.98f).toInt().coerceIn(0, sorted.lastIndex)]
        val range = max(hi - lo, 1e-6f)
        return FloatArray(raw.size) { ((raw[it] - lo) / range).coerceIn(0f, 1f) * 255f }
    }

    fun upsample(src: FloatArray, sw: Int, sh: Int, dw: Int, dh: Int): FloatArray {
        val dst = FloatArray(dw * dh)
        for (y in 0 until dh) {
            val fy = ((y + 0.5f) * sh / dh - 0.5f).coerceIn(0f, sh - 1f)
            val y0 = fy.toInt(); val y1 = min(y0 + 1, sh - 1); val ty = fy - y0
            for (x in 0 until dw) {
                val fx = ((x + 0.5f) * sw / dw - 0.5f).coerceIn(0f, sw - 1f)
                val x0 = fx.toInt(); val x1 = min(x0 + 1, sw - 1); val tx = fx - x0
                val a = src[y0 * sw + x0] * (1 - tx) + src[y0 * sw + x1] * tx
                val b = src[y1 * sw + x0] * (1 - tx) + src[y1 * sw + x1] * tx
                dst[y * dw + x] = a * (1 - ty) + b * ty
            }
        }
        return dst
    }

    /** Nature / Landscape fallback: smooth near-plane foreground ramp from the depth map. */
    fun buildNatureDepthMask(depth: FloatArray, w: Int, h: Int): FloatArray {
        val sorted = depth.copyOf().also { it.sort() }
        val thr = sorted[(sorted.size * 0.62f).toInt().coerceIn(0, sorted.lastIndex)]
        val soft = 24f
        val m = FloatArray(depth.size) {
            val t = ((depth[it] - (thr - soft)) / (2 * soft)).coerceIn(0f, 1f)
            t * t * (3 - 2 * t) * 255f
        }
        val radius = max(2, min(w, h) / 140)
        val blurred = boxBlur(boxBlur(m, w, h, radius), w, h, radius)
        return FloatArray(blurred.size) { blurred[it].coerceIn(0f, 255f) }
    }

    fun boxBlur(src: FloatArray, w: Int, h: Int, r: Int): FloatArray {
        if (w <= 0 || h <= 0 || r <= 0 || src.size < w * h) return src.copyOf()
        val tmp = FloatArray(src.size); val out = FloatArray(src.size)
        val k = 2 * r + 1
        for (y in 0 until h) {
            var acc = 0f
            for (x in -r..r) acc += src[y * w + x.coerceIn(0, w - 1)]
            for (x in 0 until w) {
                tmp[y * w + x] = acc / k
                acc += src[y * w + (x + r + 1).coerceIn(0, w - 1)] - src[y * w + (x - r).coerceIn(0, w - 1)]
            }
        }
        for (x in 0 until w) {
            var acc = 0f
            for (y in -r..r) acc += tmp[y.coerceIn(0, h - 1) * w + x]
            for (y in 0 until h) {
                out[y * w + x] = acc / k
                acc += tmp[(y + r + 1).coerceIn(0, h - 1) * w + x] - tmp[(y - r).coerceIn(0, h - 1) * w + x]
            }
        }
        return out
    }

    /** Separable 2D morphological dilation (sliding window maximum) over a `(2r+1)x(2r+1)` kernel. */
    fun morphDilate(src: FloatArray, w: Int, h: Int, r: Int): FloatArray {
        if (w <= 0 || h <= 0 || r <= 0 || src.size < w * h) return src.copyOf()
        val tmp = FloatArray(w * h)
        val out = FloatArray(w * h)
        for (y in 0 until h) {
            val row = y * w
            for (x in 0 until w) {
                var m = Float.NEGATIVE_INFINITY
                val x0 = max(0, x - r)
                val x1 = min(w - 1, x + r)
                for (kx in x0..x1) {
                    val v = src[row + kx]
                    if (v > m) m = v
                }
                tmp[row + x] = m
            }
        }
        for (x in 0 until w) {
            for (y in 0 until h) {
                var m = Float.NEGATIVE_INFINITY
                val y0 = max(0, y - r)
                val y1 = min(h - 1, y + r)
                for (ky in y0..y1) {
                    val v = tmp[ky * w + x]
                    if (v > m) m = v
                }
                out[y * w + x] = m
            }
        }
        return out
    }

    /** Separable 2D morphological erosion (sliding window minimum) over a `(2r+1)x(2r+1)` kernel. */
    fun morphErode(src: FloatArray, w: Int, h: Int, r: Int): FloatArray {
        if (w <= 0 || h <= 0 || r <= 0 || src.size < w * h) return src.copyOf()
        val tmp = FloatArray(w * h)
        val out = FloatArray(w * h)
        for (y in 0 until h) {
            val row = y * w
            for (x in 0 until w) {
                var m = Float.POSITIVE_INFINITY
                val x0 = max(0, x - r)
                val x1 = min(w - 1, x + r)
                for (kx in x0..x1) {
                    val v = src[row + kx]
                    if (v < m) m = v
                }
                tmp[row + x] = m
            }
        }
        for (x in 0 until w) {
            for (y in 0 until h) {
                var m = Float.POSITIVE_INFINITY
                val y0 = max(0, y - r)
                val y1 = min(h - 1, y + r)
                for (ky in y0..y1) {
                    val v = tmp[ky * w + x]
                    if (v < m) m = v
                }
                out[y * w + x] = m
            }
        }
        return out
    }

    /**
     * Morphological closing (`erode(dilate(src, dilateRadius), erodeRadius)`).
     * Bridges internal holes and fuses glowing limbs/weapons attached to the foreground silhouette.
     */
    fun morphClose(
        src: FloatArray,
        w: Int,
        h: Int,
        dilateRadius: Int,
        erodeRadius: Int = dilateRadius,
    ): FloatArray {
        val dilated = morphDilate(src, w, h, dilateRadius)
        return morphErode(dilated, w, h, erodeRadius)
    }

    /**
     * Fast O(N) edge-aware Guided Image Filter (He et al.).
     * Aligns [mask] (`0..255`) to structural boundaries in [guide] (`0..1` or `0..255`).
     */
    fun guidedFilter(
        mask: FloatArray,
        guide: FloatArray,
        w: Int,
        h: Int,
        r: Int,
        eps: Float = 1e-3f,
    ): FloatArray {
        val n = w * h
        if (w <= 0 || h <= 0 || r <= 0 || mask.size < n || guide.size < n) return mask.copyOf()
        var maxG = 0f
        for (i in 0 until n) if (guide[i] > maxG) maxG = guide[i]
        val gScale = if (maxG > 1.5f) 1f / 255f else 1f
        val normI = FloatArray(n) { (guide[it] * gScale).coerceIn(0f, 1f) }
        val p = FloatArray(n) { (mask[it] / 255f).coerceIn(0f, 1f) }

        val meanI = boxBlur(normI, w, h, r)
        val meanP = boxBlur(p, w, h, r)
        val ii = FloatArray(n) { normI[it] * normI[it] }
        val ip = FloatArray(n) { normI[it] * p[it] }
        val corrI = boxBlur(ii, w, h, r)
        val corrIP = boxBlur(ip, w, h, r)

        val a = FloatArray(n)
        val b = FloatArray(n)
        val safeEps = max(eps, 1e-6f)
        for (i in 0 until n) {
            val varI = max(0f, corrI[i] - meanI[i] * meanI[i])
            val covIP = corrIP[i] - meanI[i] * meanP[i]
            val ai = covIP / (varI + safeEps)
            a[i] = ai
            b[i] = meanP[i] - ai * meanI[i]
        }

        val meanA = boxBlur(a, w, h, r)
        val meanB = boxBlur(b, w, h, r)
        return FloatArray(n) { ((meanA[it] * normI[it] + meanB[it]) * 255f).coerceIn(0f, 255f) }
    }

    /**
     * Applies morphological dilation/closing + glow-limb bridging + edge-aware guided filtering
     * so bright glowing areas attached to a character (e.g., Link's glowing cyan arm or bright
     * weapon aura) stay fused to the foreground subject mask instead of dropping into the sky.
     */
    fun refineForegroundMask(
        rawMask: FloatArray,
        pixels: IntArray?,
        w: Int,
        h: Int,
    ): FloatArray {
        val n = w * h
        if (w <= 0 || h <= 0 || rawMask.size < n) return rawMask.copyOf()
        val rDilate = max(2, min(w, h) / 68)
        val rErode = max(1, rDilate - 1)
        val dilated = morphDilate(rawMask, w, h, rDilate)
        val closed = morphErode(dilated, w, h, rErode)

        val guide = FloatArray(n)
        val bridged = closed.copyOf()

        if (pixels != null && pixels.size >= n) {
            for (i in 0 until n) {
                val c = pixels[i]
                val r = ((c ushr 16) and 0xFF) / 255f
                val g = ((c ushr 8) and 0xFF) / 255f
                val b = (c and 0xFF) / 255f
                val maxC = max(r, max(g, b))
                val minC = min(r, min(g, b))
                val chroma = maxC - minC
                val lum = 0.299f * r + 0.587f * g + 0.114f * b
                val cyanGlow = max(0f, min(g, b) - r * 0.65f)
                // Edge-aware guide combines luminance and chromatic structure
                guide[i] = (0.65f * lum + 0.35f * max(chroma, cyanGlow)).coerceIn(0f, 1f)

                // If a pixel sits inside the dilated character attachment halo and has strong
                // cyan/magic glow or luminous energy, fuse it onto the closed character mask.
                if (dilated[i] > 48f) {
                    val isGlowingAttachment = (cyanGlow > 0.18f && lum > 0.42f) ||
                        (lum > 0.58f && chroma > 0.22f) ||
                        (lum > 0.80f && dilated[i] > 110f)
                    if (isGlowingAttachment) {
                        bridged[i] = max(bridged[i], dilated[i] * 0.94f)
                    }
                }
            }
        } else {
            for (i in 0 until n) {
                guide[i] = (closed[i] / 255f).coerceIn(0f, 1f)
            }
        }

        val rGuide = max(2, min(w, h) / 110)
        val guided = guidedFilter(bridged, guide, w, h, rGuide, eps = 2.0e-3f)
        return FloatArray(n) { i ->
            // Retain strong closed core while using guided edges for smooth, tear-free boundaries
            max(closed[i] * 0.85f, guided[i]).coerceIn(0f, 255f)
        }
    }

    /**
     * Post-processes the monocular depth map (`0..255`, 255 = near) with the refined foreground mask:
     * 1. Fuses the foreground subject (`fgMask`) to a unified near Z-plane (`>= 220f`) so glowing
     *    arms/weapons attached to the character never fall into the background sky or tear.
     * 2. Stabilizes the mid-ground cliff/terrain anchor plane around `ZERO_PLANE` (`89.25f`, `0.35`)
     *    so the main rock/terrain stays stationary (zero parallax drift).
     * 3. Pushes distant sky/clouds into a deep parallax layer (`0..32f`) while decoupling small
     *    isolated mid-sky elements (birds, floating leaves) onto an aerial layer (`~48f`) so they
     *    aren't frozen to the foreground rock plane.
     */
    fun postProcessDepth(
        depthGray: FloatArray,
        fgMask: FloatArray,
        w: Int,
        h: Int,
    ): FloatArray {
        val n = w * h
        if (w <= 0 || h <= 0 || depthGray.size < n || fgMask.size < n) return depthGray.copyOf()

        // 1. Determine the primary foreground character's near-depth anchor
        val fgSamples = ArrayList<Float>(n / 4)
        for (i in 0 until n) {
            if (fgMask[i] >= 128f) fgSamples.add(depthGray[i])
        }
        val fgAnchor = if (fgSamples.isNotEmpty()) {
            fgSamples.sort()
            val p75 = fgSamples[(fgSamples.size * 0.75f).toInt().coerceIn(0, fgSamples.lastIndex)]
            p75.coerceIn(220f, 248f)
        } else {
            228f
        }

        // 2. Regional depth context to separate broad cliff masses from isolated mid-sky particles
        val regionRadius = max(2, min(w, h) / 20)
        val regionalDepth = boxBlur(depthGray, w, h, regionRadius)
        val zeroPlane = 0.35f * 255f // 89.25f

        val out = FloatArray(n)
        for (i in 0 until n) {
            val d = depthGray[i].coerceIn(0f, 255f)
            val m = (fgMask[i] / 255f).coerceIn(0f, 1f)
            val mSmooth = m * m * (3f - 2f * m)
            val reg = regionalDepth[i]

            // Background / terrain / sky shaping (outside primary subject)
            val bgShaped = when {
                // Small isolated mid-sky element (bird / floating leaf) surrounded by open sky
                reg < 76f && (d - reg) > 10f -> {
                    48f // ~0.19 normalized aerial mid-sky plane (decoupled from 0.35 rock plane)
                }
                // Distant sky & clouds -> deep parallax layer (0..32f)
                d < 62f && reg < 78f -> {
                    d * 0.50f
                }
                // Main cliff / terrain anchor plane -> plateau-locked to ZERO_PLANE (89.25f)
                d in 62f..142f -> {
                    val dist = kotlin.math.abs(d - zeroPlane) / 53f
                    val lock = (1f - dist.coerceIn(0f, 1f))
                    val lockSmooth = lock * lock * (3f - 2f * lock)
                    d * (1f - 0.78f * lockSmooth) + zeroPlane * (0.78f * lockSmooth)
                }
                else -> d
            }

            // Foreground subject fusion: keep glowing limbs & character silhouette unified at near Z
            val fgDepth = max(d, fgAnchor)
            out[i] = (bgShaped * (1f - mSmooth) + fgDepth * mSmooth).coerceIn(0f, 255f)
        }
        return out
    }
}
