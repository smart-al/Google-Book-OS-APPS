package com.droid.liquidglass3d

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.abs
import kotlin.math.max

/**
 * Expands smaller or non-16:10 photos into seamless 16:10 widescreen scenes using
 * Gemini Nano Banana 2.1 (`gemini-nano-banana-2.1`) outpainting.
 */
object NanoBananaExpander {
    const val MODEL_ID = "gemini-nano-banana-2.1"
    const val ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL_ID:generateContent"
    const val TARGET_ASPECT = 16f / 10f
    const val DEFAULT_OUTPAINT_PROMPT =
        "Seamlessly expand and outpaint the borders of this image to fill a 16:10 widescreen frame. " +
            "Continue the existing scenery, lighting, textures, and colors naturally around the edges " +
            "without stretching, cropping, or altering the original subject."

    /** Returns true when an image is smaller than 1280px or differs noticeably from 16:10. */
    fun needsExpansionTo16x10(width: Int, height: Int): Boolean {
        if (width <= 0 || height <= 0) return false
        val ratio = width.toFloat() / height.toFloat()
        return abs(ratio - TARGET_ASPECT) > 0.08f || max(width, height) < 1280
    }

    /** Builds the Gemini `generateContent` JSON payload for 16:10 Nano Banana outpainting. */
    fun buildOutpaintRequestJson(base64Jpeg: String, prompt: String = DEFAULT_OUTPAINT_PROMPT): String {
        val textPart = JSONObject().put("text", prompt.ifBlank { DEFAULT_OUTPAINT_PROMPT })
        val imagePart = JSONObject().put(
            "inlineData",
            JSONObject()
                .put("mimeType", "image/jpeg")
                .put("data", base64Jpeg),
        )
        val content = JSONObject().put("parts", JSONArray().put(textPart).put(imagePart))
        val generationConfig = JSONObject()
            .put("responseModalities", JSONArray().put("IMAGE"))
            .put("imageConfig", JSONObject().put("aspectRatio", "16:10"))

        return JSONObject()
            .put("contents", JSONArray().put(content))
            .put("generationConfig", generationConfig)
            .toString()
    }

    /** Extracts the base64-encoded image string from a Gemini `generateContent` JSON response. */
    fun extractImageBase64FromResponse(responseJson: String): String {
        val root = JSONObject(responseJson)
        val error = root.optJSONObject("error")
        if (error != null) {
            throw IllegalStateException(error.optString("message", "Gemini API error"))
        }
        val candidates = root.optJSONArray("candidates")
            ?: throw IllegalStateException("No candidates returned by $MODEL_ID")
        for (i in 0 until candidates.length()) {
            val parts = candidates.optJSONObject(i)
                ?.optJSONObject("content")
                ?.optJSONArray("parts") ?: continue
            for (j in 0 until parts.length()) {
                val inlineData = parts.optJSONObject(j)?.optJSONObject("inlineData")
                    ?: parts.optJSONObject(j)?.optJSONObject("inline_data")
                val data = inlineData?.optString("data").orEmpty()
                if (data.isNotBlank()) return data
            }
        }
        throw IllegalStateException("$MODEL_ID did not return an expanded image")
    }

    /** Calls Nano Banana 2.1 on `Dispatchers.IO` and returns the expanded 16:10 [Bitmap]. */
    suspend fun expandTo16x10(
        apiKey: String,
        source: Bitmap,
        prompt: String = DEFAULT_OUTPAINT_PROMPT,
    ): Bitmap = withContext(Dispatchers.IO) {
        val trimmedKey = apiKey.trim()
        require(trimmedKey.isNotBlank()) { "Enter a Gemini API key first." }

        val jpegBytes = ByteArrayOutputStream().use { out ->
            source.compress(Bitmap.CompressFormat.JPEG, 92, out)
            out.toByteArray()
        }
        val b64In = Base64.encodeToString(jpegBytes, Base64.NO_WRAP)
        val body = buildOutpaintRequestJson(b64In, prompt)

        val conn = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = 90_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            setRequestProperty("x-goog-api-key", trimmedKey)
        }

        try {
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
            val responseText = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (conn.responseCode !in 200..299) {
                val msg = runCatching {
                    JSONObject(responseText).optJSONObject("error")?.optString("message")
                }.getOrNull().takeUnless { it.isNullOrBlank() } ?: "HTTP ${conn.responseCode}"
                throw IllegalStateException("Nano Banana error: $msg")
            }
            val b64Out = extractImageBase64FromResponse(responseText)
            val decoded = Base64.decode(b64Out, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(decoded, 0, decoded.size)
                ?: throw IllegalStateException("Failed to decode expanded image")
        } finally {
            conn.disconnect()
        }
    }
}
