package com.whispercppdemo

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Keeps the APK small: the Levantine model is not bundled, it is downloaded
 * once on first launch into app storage and reused afterwards.
 *
 * The public edition ships the **q5_1** quantization (190 MB): fully functional
 * Levantine transcription at roughly 2/3 the accuracy of the private q8_0 build.
 * Swap MODEL_NAME/MODEL_URL below if you host a different quantization.
 */
object LevantineModel {
    private const val LOG_TAG = "LevantineModel"

    const val MODEL_NAME = "ggml-small-levantine-q5_1.bin"

    // Hugging Face "resolve/main" URL for the uploaded GGML file
    const val MODEL_URL =
        "https://huggingface.co/Laith05/whisper-levantine/resolve/main/$MODEL_NAME"

    /** Single source of truth for the on-device model location. */
    fun modelFile(context: Context): File =
        File(context.filesDir, "models/$MODEL_NAME")

    /**
     * Returns the model file, downloading it first when missing.
     * Returns null when the download fails so callers can surface an error.
     * [onProgress] is invoked with 0..100 on a background thread (log-only use).
     */
    suspend fun ensureModelFile(
        context: Context,
        onProgress: (percent: Int) -> Unit = {},
    ): File? = withContext(Dispatchers.IO) {
        val dest = modelFile(context)
        if (dest.isFile && dest.length() > 0) return@withContext dest

        dest.parentFile?.mkdirs()
        val tmp = File(dest.parentFile, "$MODEL_NAME.part")
        try {
            val conn = (URL(MODEL_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 60_000
                instanceFollowRedirects = true // HF redirects to its CDN
            }
            conn.connect()
            try {
                if (conn.responseCode !in 200..299) {
                    Log.e(LOG_TAG, "Download failed: HTTP ${conn.responseCode}")
                    return@withContext null
                }
                val total = conn.contentLengthLong
                conn.inputStream.use { input ->
                    tmp.outputStream().use { output ->
                        val buffer = ByteArray(1 shl 16)
                        var copied = 0L
                        var lastPct = -1
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            output.write(buffer, 0, n)
                            copied += n
                            if (total > 0) {
                                val pct = (100 * copied / total).toInt()
                                if (pct / 10 != lastPct / 10) {
                                    lastPct = pct
                                    Log.d(LOG_TAG, "download $pct%")
                                    onProgress(pct)
                                }
                            }
                        }
                    }
                }
            } finally {
                conn.disconnect()
            }

            if (dest.exists()) dest.delete()
            if (!tmp.renameTo(dest)) {
                Log.e(LOG_TAG, "could not move downloaded model into place")
                tmp.delete()
                return@withContext null
            }
            Log.d(LOG_TAG, "model ready: ${dest.length()} bytes at ${dest.absolutePath}")
            dest
        } catch (e: Exception) {
            Log.e(LOG_TAG, "model download failed", e)
            tmp.delete()
            null
        }
    }
}
