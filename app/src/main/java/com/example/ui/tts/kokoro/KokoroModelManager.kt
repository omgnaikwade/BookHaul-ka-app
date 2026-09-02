package com.example.ui.tts.kokoro

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit

sealed class DownloadStatus {
    object Idle : DownloadStatus()
    data class Downloading(
        val item: String,
        val progress: Float, // 0.0f to 1.0f
        val bytesDownloaded: Long,
        val totalBytes: Long
    ) : DownloadStatus()
    data class Completed(val message: String) : DownloadStatus()
    data class Error(val message: String) : DownloadStatus()
}

data class KokoroModelState(
    val isModelDownloaded: Boolean = false,
    val modelSizeBytes: Long = 0L,
    val downloadedVoices: Set<String> = emptySet(),
    val downloadStatus: DownloadStatus = DownloadStatus.Idle,
    val isInitializing: Boolean = false,
    val isEngineReady: Boolean = false,
    val activeVoiceId: String = "hf_alpha"
)

class KokoroModelManager(private val context: Context) {

    private val TAG = "KokoroModelManager"

    // Primary & Mirror URLs
    // Exact official URL: onnx-community/Kokoro-82M-v1.0-ONNX / onnx / model_quantized.onnx
    val MODEL_PRIMARY_URL = "https://huggingface.co/onnx-community/Kokoro-82M-v1.0-ONNX/resolve/main/onnx/model_quantized.onnx"
    val MODEL_MIRROR_URL = "https://huggingface.co/hexgrad/Kokoro-82M/resolve/main/onnx/model_quantized.onnx"

    val CONFIG_PRIMARY_URL = "https://huggingface.co/onnx-community/Kokoro-82M-v1.0-ONNX/resolve/main/config.json"
    val CONFIG_MIRROR_URL = "https://huggingface.co/hexgrad/Kokoro-82M/resolve/main/config.json"

    // Path to the config.json bundled inside the app itself
    // (app/src/main/assets/kokoro/config.json)
    private val CONFIG_ASSET_PATH = "kokoro/config.json"

    // Official voices path
    val VOICE_PRIMARY_URL_TEMPLATE = "https://huggingface.co/onnx-community/Kokoro-82M-v1.0-ONNX/resolve/main/voices/%s.bin"
    val VOICE_MIRROR_URL_TEMPLATE = "https://huggingface.co/hexgrad/Kokoro-82M/resolve/main/voices/%s.bin"

    // Expected file sizes
    val MIN_VALID_MODEL_SIZE_BYTES = 70_000_000L // Quantized model is ~88.1 MB (~92.4 MB on disk)
    val EXPECTED_MODEL_SIZE_BYTES = 92_382_000L

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val _state = MutableStateFlow(KokoroModelState())
    val state: StateFlow<KokoroModelState> = _state.asStateFlow()

    private val kokoroDir: File by lazy {
        File(context.filesDir, "kokoro").apply {
            if (!exists()) mkdirs()
        }
    }

    private val voicesDir: File by lazy {
        File(kokoroDir, "voices").apply {
            if (!exists()) mkdirs()
        }
    }

    val modelFile: File
        get() = File(kokoroDir, "model_quantized.onnx")

    val configFile: File
        get() = File(kokoroDir, "config.json")

    init {
        refreshLocalStatus()
    }

    /**
     * Checks if the model and voices are present and valid in app's private storage.
     */
    fun refreshLocalStatus() {
        val exists = isModelValid()
        val size = if (exists) modelFile.length() else 0L

        val downloadedVoiceIds = mutableSetOf<String>()
        KokoroVoiceCatalog.ALL_VOICES.forEach { voice ->
            val voiceFile = File(voicesDir, voice.fileName)
            if (voiceFile.exists() && voiceFile.length() > 500) {
                downloadedVoiceIds.add(voice.id)
            }
        }

        _state.value = _state.value.copy(
            isModelDownloaded = exists,
            modelSizeBytes = size,
            downloadedVoices = downloadedVoiceIds,
            isEngineReady = exists
        )
    }

    /**
     * Validates that the model file exists, is non-empty, and exceeds the minimum valid size threshold.
     */
    fun isModelValid(): Boolean {
        return modelFile.exists() && modelFile.isFile && modelFile.length() >= MIN_VALID_MODEL_SIZE_BYTES
    }

    fun getVoiceFile(voiceId: String): File {
        val voice = KokoroVoiceCatalog.getVoiceById(voiceId)
        return File(voicesDir, voice.fileName)
    }

    fun isVoiceDownloaded(voiceId: String): Boolean {
        val vf = getVoiceFile(voiceId)
        return vf.exists() && vf.isFile && vf.length() > 500
    }

    /**
     * Downloads the Kokoro quantized model to app storage with progress notifications.
     *
     * FIX: previously, when the model was already downloaded (isModelValid() ==
     * true), this function returned early WITHOUT ever calling downloadConfig()
     * / ensureConfigAvailable(). That meant config.json (the real vocab) never
     * got set up for anyone who had already downloaded the model before this
     * logic existed - which is exactly what was happening here. Now both the
     * early-return path and the fresh-download path make sure config.json ends
     * up in place.
     */
    suspend fun downloadModel(onProgress: ((Float, Long, Long) -> Unit)? = null): Boolean = withContext(Dispatchers.IO) {
        if (isModelValid()) {
            Log.d(TAG, "Kokoro model already exists locally (${modelFile.length()} bytes)")
            ensureConfigAvailable()
            _state.value = _state.value.copy(
                isModelDownloaded = true,
                modelSizeBytes = modelFile.length(),
                downloadStatus = DownloadStatus.Completed("Model already cached on device.")
            )
            return@withContext true
        }

        _state.value = _state.value.copy(
            downloadStatus = DownloadStatus.Downloading(
                item = "Kokoro-82M Neural Model (~88 MB)",
                progress = 0f,
                bytesDownloaded = 0,
                totalBytes = EXPECTED_MODEL_SIZE_BYTES
            )
        )

        val tmpFile = File(kokoroDir, "model_quantized.onnx.tmp")
        if (tmpFile.exists()) tmpFile.delete()

        var success = false
        val urlsToTry = listOf(MODEL_PRIMARY_URL, MODEL_MIRROR_URL)

        for (url in urlsToTry) {
            try {
                Log.d(TAG, "Downloading Kokoro ONNX model from: $url")
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Android; Mobile; BookHaul-TTS)")
                    .build()

                val response = httpClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    Log.w(TAG, "Failed response code ${response.code} from $url")
                    response.close()
                    continue
                }

                val body = response.body ?: continue
                val contentLength = body.contentLength().let { if (it > 0) it else EXPECTED_MODEL_SIZE_BYTES }

                val inputStream: InputStream = body.byteStream()
                val outputStream = FileOutputStream(tmpFile)

                val buffer = ByteArray(64 * 1024)
                var bytesRead: Int
                var totalBytesRead = 0L
                var lastUpdate = 0L

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    totalBytesRead += bytesRead

                    val now = System.currentTimeMillis()
                    if (now - lastUpdate > 100 || totalBytesRead == contentLength) {
                        lastUpdate = now
                        val progress = if (contentLength > 0) (totalBytesRead.toFloat() / contentLength).coerceIn(0f, 1f) else 0f
                        _state.value = _state.value.copy(
                            downloadStatus = DownloadStatus.Downloading(
                                item = "Kokoro-82M Neural Model",
                                progress = progress,
                                bytesDownloaded = totalBytesRead,
                                totalBytes = contentLength
                            )
                        )
                        onProgress?.invoke(progress, totalBytesRead, contentLength)
                    }
                }

                outputStream.flush()
                outputStream.close()
                inputStream.close()
                response.close()

                // Validate downloaded file integrity before moving to final destination
                if (tmpFile.exists() && tmpFile.length() >= MIN_VALID_MODEL_SIZE_BYTES) {
                    if (modelFile.exists()) modelFile.delete()
                    if (tmpFile.renameTo(modelFile)) {
                        Log.i(TAG, "Kokoro ONNX model successfully saved and validated: ${modelFile.absolutePath} (${modelFile.length()} bytes)")
                        success = true
                        break
                    }
                } else {
                    Log.w(TAG, "Downloaded model file too small (${tmpFile.length()} bytes), discarded.")
                    if (tmpFile.exists()) tmpFile.delete()
                }
            } catch (e: CancellationException) {
                Log.w(TAG, "Download cancelled by user")
                if (tmpFile.exists()) tmpFile.delete()
                _state.value = _state.value.copy(
                    downloadStatus = DownloadStatus.Idle
                )
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Error downloading model from $url", e)
                if (tmpFile.exists()) tmpFile.delete()
            }
        }

        refreshLocalStatus()

        if (success) {
            ensureConfigAvailable()
            _state.value = _state.value.copy(
                isModelDownloaded = true,
                modelSizeBytes = modelFile.length(),
                downloadStatus = DownloadStatus.Completed("Kokoro-82M Model ready for offline inference.")
            )
            true
        } else {
            _state.value = _state.value.copy(
                downloadStatus = DownloadStatus.Error("Failed to download Kokoro-82M model. Please check internet connection and retry.")
            )
            false
        }
    }

    /**
     * Ensures config.json (the REAL Kokoro vocab mapping) is available in
     * internal storage. Tries the bundled app asset first (works fully
     * offline, matches the "100% on-device" design, no HuggingFace dependency
     * at runtime). Falls back to downloading from HuggingFace only if the
     * asset isn't bundled for some reason.
     */
    suspend fun ensureConfigAvailable(): Boolean = withContext(Dispatchers.IO) {
        if (configFile.exists() && configFile.length() > 100) {
            return@withContext true
        }

        // 1. Try the bundled asset: app/src/main/assets/kokoro/config.json
        try {
            context.assets.open(CONFIG_ASSET_PATH).use { input ->
                FileOutputStream(configFile).use { output ->
                    input.copyTo(output)
                }
            }
            if (configFile.exists() && configFile.length() > 100) {
                Log.i(TAG, "config.json copied from bundled app asset ($CONFIG_ASSET_PATH) -> ${configFile.absolutePath}")
                return@withContext true
            }
        } catch (e: Exception) {
            Log.w(TAG, "Bundled config.json asset not found or copy failed ($CONFIG_ASSET_PATH): ${e.message}")
        }

        // 2. Fall back to network download
        Log.w(TAG, "Falling back to downloading config.json from HuggingFace")
        return@withContext downloadConfig()
    }

    /**
     * Downloads official config.json containing vocabulary mapping if needed.
     * Kept as a network fallback - prefer ensureConfigAvailable() which tries
     * the bundled asset first.
     */
    suspend fun downloadConfig(): Boolean = withContext(Dispatchers.IO) {
        if (configFile.exists() && configFile.length() > 100) return@withContext true
        val urls = listOf(CONFIG_PRIMARY_URL, CONFIG_MIRROR_URL)
        for (url in urls) {
            try {
                val req = Request.Builder().url(url).build()
                val resp = httpClient.newCall(req).execute()
                if (resp.isSuccessful) {
                    val body = resp.body ?: continue
                    val tmp = File(kokoroDir, "config.json.tmp")
                    tmp.writeBytes(body.bytes())
                    resp.close()
                    if (tmp.exists() && tmp.length() > 100) {
                        tmp.renameTo(configFile)
                        Log.i(TAG, "Kokoro config.json downloaded: ${configFile.absolutePath}")
                        return@withContext true
                    }
                }
                resp.close()
            } catch (c: CancellationException) {
                throw c
            } catch (e: Exception) {
                Log.w(TAG, "Error fetching config.json from $url: ${e.message}")
            }
        }
        false
    }

    /**
     * Downloads a specific voice style embedding binary file if not already cached.
     */
    suspend fun downloadVoice(voiceId: String): Boolean = withContext(Dispatchers.IO) {
        val voice = KokoroVoiceCatalog.getVoiceById(voiceId)
        val targetFile = getVoiceFile(voiceId)

        if (targetFile.exists() && targetFile.length() > 500) {
            Log.d(TAG, "Voice $voiceId already cached locally")
            refreshLocalStatus()
            return@withContext true
        }

        val tmpFile = File(voicesDir, "${voice.fileName}.tmp")
        if (tmpFile.exists()) tmpFile.delete()

        val urls = listOf(
            String.format(VOICE_PRIMARY_URL_TEMPLATE, voice.id),
            String.format(VOICE_MIRROR_URL_TEMPLATE, voice.id)
        )

        var success = false
        for (url in urls) {
            try {
                Log.d(TAG, "Downloading voice file from $url")
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Android; Mobile; BookHaul-TTS)")
                    .build()

                val response = httpClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    response.close()
                    continue
                }

                val body = response.body ?: continue
                val inputStream = body.byteStream()
                val outputStream = FileOutputStream(tmpFile)

                val buffer = ByteArray(16 * 1024)
                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                }

                outputStream.flush()
                outputStream.close()
                inputStream.close()
                response.close()

                if (tmpFile.exists() && tmpFile.length() > 500) {
                    if (targetFile.exists()) targetFile.delete()
                    if (tmpFile.renameTo(targetFile)) {
                        Log.i(TAG, "Voice file ${voice.id} downloaded successfully (${targetFile.length()} bytes)")
                        success = true
                        break
                    }
                }
            } catch (c: CancellationException) {
                if (tmpFile.exists()) tmpFile.delete()
                throw c
            } catch (e: Exception) {
                Log.e(TAG, "Error downloading voice $voiceId from $url", e)
                if (tmpFile.exists()) tmpFile.delete()
            }
        }

        refreshLocalStatus()
        success
    }

    /**
     * Downloads both the base model and required initial voice files (e.g. Hindi voices).
     */
    suspend fun downloadModelAndInitialVoices(
        voiceIds: List<String> = listOf("hf_alpha", "hm_omega"),
        onOverallProgress: ((Float, String) -> Unit)? = null
    ): Boolean = withContext(Dispatchers.IO) {
        onOverallProgress?.invoke(0.1f, "Downloading Kokoro-82M Model...")
        val modelDownloaded = downloadModel { p, _, _ ->
            onOverallProgress?.invoke(0.1f + p * 0.7f, "Downloading Neural Model (${(p * 100).toInt()}%)")
        }

        if (!modelDownloaded) {
            return@withContext false
        }

        for ((index, voiceId) in voiceIds.withIndex()) {
            val voiceProgress = 0.8f + (index.toFloat() / voiceIds.size) * 0.2f
            onOverallProgress?.invoke(voiceProgress, "Downloading voice $voiceId...")
            downloadVoice(voiceId)
        }

        onOverallProgress?.invoke(1.0f, "Kokoro engine ready!")
        refreshLocalStatus()
        true
    }

    fun deleteLocalModel(): Boolean {
        return try {
            if (modelFile.exists()) modelFile.delete()
            voicesDir.listFiles()?.forEach { it.delete() }
            refreshLocalStatus()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting local model", e)
            false
        }
    }

    companion object {
        @Volatile
        private var instance: KokoroModelManager? = null

        fun getInstance(context: Context): KokoroModelManager {
            return instance ?: synchronized(this) {
                instance ?: KokoroModelManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
