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

            onStage("Estimating 3D depth…")
            val depthGray = estimateDepth(photo)
            val natureMask = buildNatureDepthMask(depthGray, w, h)

            onStage("Analyzing scene (Person / Object / Nature)…")
            val (selfieMask, isPortrait) = estimateSelfie(photo)
            val (u2netMask, isObject) = estimateU2Net(photo)

            val detectedMode = when {
                isPortrait -> MaskMode.PORTRAIT
                isObject -> MaskMode.OBJECT
                else -> MaskMode.NATURE
            }

            // Portrait mask blends Selfie Multiclass (fine hair/head) with U²-Net (crisp garment edges)
            val portraitMask = FloatArray(w * h) { i ->
                (0.35f * selfieMask[i] + 0.65f * u2netMask[i]).coerceIn(0f, 255f)
            }

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
            SceneStore.bumpSceneVersion(context)
            SceneBitmaps(photo, depthBmp, activeBmp, detectedMode)
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
}
