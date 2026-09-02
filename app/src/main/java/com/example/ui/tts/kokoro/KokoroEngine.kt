package com.example.ui.tts.kokoro

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.LongBuffer
import java.util.concurrent.atomic.AtomicBoolean

class KokoroEngine(private val context: Context, private val modelManager: KokoroModelManager) {

    private val TAG = "KokoroEngine"
    private val SAMPLE_RATE = 24000 // Kokoro output sample rate: 24,000 Hz

    private var ortEnvironment: OrtEnvironment? = null
    private var ortSession: OrtSession? = null
    private val isInitialized = AtomicBoolean(false)
    private var mediaPlayer: MediaPlayer? = null

    /**
     * Initializes the ONNX Runtime session using the downloaded quantized ONNX model.
     * Runs in the background and does not block the UI thread.
     *
     * FIX: Added NNAPI execution provider so inference runs on the device's
     * neural accelerator / GPU instead of pure CPU. This is very likely the
     * biggest reason synthesis was taking 10-15 minutes instead of seconds.
     * If NNAPI isn't available on a device/emulator, we safely fall back to CPU.
     */
    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        if (isInitialized.get() && ortSession != null) {
            return@withContext true
        }

        if (!modelManager.isModelValid()) {
            Log.w(TAG, "Kokoro ONNX model not found or invalid in local storage")
            return@withContext false
        }

        try {
            Log.i(TAG, "Initializing ONNX Runtime session for Kokoro-82M...")
            val env = OrtEnvironment.getEnvironment()
            val sessionOptions = OrtSession.SessionOptions().apply {
                setIntraOpNumThreads(4)
                setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                setMemoryPatternOptimization(true)
            }

            val session = env.createSession(modelManager.modelFile.absolutePath, sessionOptions)
            ortEnvironment = env
            ortSession = session
            isInitialized.set(true)
            Log.i(TAG, "Kokoro-82M ONNX Runtime session successfully initialized! Input names: ${session.inputNames}")

            // Diagnostic: confirm whether the REAL Kokoro vocab (from config.json)
            // is available. If this logs a warning, token IDs sent to the model
            // are wrong and audio quality (especially Hindi) will be broken
            // regardless of any G2P fixes.
            if (modelManager.configFile == null || !modelManager.configFile!!.exists()) {
                Log.e(TAG, "config.json NOT FOUND - using invented fallback vocab IDs. " +
                        "This WILL cause wrong/garbled pronunciation. Download the official " +
                        "config.json from the same model repo and place it next to the .onnx file.")
            }

            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Kokoro ONNX session", e)
            isInitialized.set(false)
            false
        }
    }

    /**
     * Synthesizes text into a playable WAV audio file on-device using Kokoro-82M.
     */
    suspend fun synthesizeToWav(
        text: String,
        voiceId: String = "hf_alpha",
        speed: Float = 1.0f,
        languageCode: String = "en"
    ): File? = withContext(Dispatchers.Default) {
        if (!isInitialized.get() || ortSession == null) {
            val initialized = initialize()
            if (!initialized || ortSession == null) {
                Log.e(TAG, "Cannot synthesize: Kokoro engine is not initialized")
                throw Exception("Engine not initialized - model file missing/invalid, or ONNX session failed to load (check that model_quantized.onnx downloaded fully)")
            }
        }

        val session = ortSession ?: return@withContext null
        val env = ortEnvironment ?: return@withContext null

        try {
            val targetLang = languageCode

            // 1. Tokenize input text using real phonemizer and vocab
            val tokenizeStart = System.currentTimeMillis()
            val tokenIds = KokoroTokenizer.tokenize(text, targetLang, modelManager.configFile)
            Log.d(TAG, "Tokenization took ${System.currentTimeMillis() - tokenizeStart}ms for ${tokenIds.size} tokens")
            if (tokenIds.isEmpty()) return@withContext null

            // 2. Load voice style embedding
            val styleVector = loadVoiceStyle(voiceId, tokenIds.size)

            // 3. Prepare inputs based on session input names and tensor info
            val seqLen = tokenIds.size.toLong()
            val inputs = mutableMapOf<String, OnnxTensor>()
            val tensorsToClose = mutableListOf<OnnxTensor>()

            val inputInfoMap = session.inputInfo
            for ((inputName, nodeInfo) in inputInfoMap) {
                val tensorInfo = nodeInfo.info as? ai.onnxruntime.TensorInfo
                val shape = tensorInfo?.shape

                when {
                    inputName.contains("token", ignoreCase = true) || inputName.contains("input_ids", ignoreCase = true) -> {
                        val tokensBuffer = LongBuffer.wrap(tokenIds)
                        val t = OnnxTensor.createTensor(env, tokensBuffer, longArrayOf(1, seqLen))
                        inputs[inputName] = t
                        tensorsToClose.add(t)
                    }
                    inputName.contains("style", ignoreCase = true) || inputName.contains("ref", ignoreCase = true) -> {
                        val styleBuffer = FloatBuffer.wrap(styleVector)
                        val styleShape = if (shape != null && shape.size == 3) {
                            longArrayOf(1, (styleVector.size / 256).toLong().coerceAtLeast(1L), 256)
                        } else if (shape != null && shape.size == 1) {
                            longArrayOf(256)
                        } else {
                            if (styleVector.size == 256) longArrayOf(1, 256) else longArrayOf(1, (styleVector.size / 256).toLong(), 256)
                        }
                        val t = OnnxTensor.createTensor(env, styleBuffer, styleShape)
                        inputs[inputName] = t
                        tensorsToClose.add(t)
                    }
                    inputName.contains("speed", ignoreCase = true) -> {
                        val speedBuffer = FloatBuffer.wrap(floatArrayOf(speed.coerceIn(0.5f, 2.0f)))
                        val speedShape = if (shape != null && shape.size == 2) longArrayOf(1, 1) else longArrayOf(1)
                        val t = OnnxTensor.createTensor(env, speedBuffer, speedShape)
                        inputs[inputName] = t
                        tensorsToClose.add(t)
                    }
                    else -> {
                        val tokensBuffer = LongBuffer.wrap(tokenIds)
                        val t = OnnxTensor.createTensor(env, tokensBuffer, longArrayOf(1, seqLen))
                        inputs[inputName] = t
                        tensorsToClose.add(t)
                    }
                }
            }

            Log.d(TAG, "Running Kokoro inference for ${tokenIds.size} tokens with voice $voiceId on ${inputs.keys}...")
            val startTime = System.currentTimeMillis()
            val results = session.run(inputs)
            val elapsed = System.currentTimeMillis() - startTime
            Log.d(TAG, "Inference completed in ${elapsed}ms")
            if (elapsed > 5000) {
                Log.w(TAG, "Inference is unusually slow (${elapsed}ms). Check if NNAPI enabled successfully " +
                        "(see 'NNAPI execution provider enabled' log line at startup) and confirm the model " +
                        "file is the quantized (int8) version, not fp32.")
            }

            // Extract output audio samples
            val outputTensor = results.get(0)
            val rawOutput = outputTensor.value

            val audioFloats = when (rawOutput) {
                is Array<*> -> {
                    if (rawOutput.isNotEmpty() && rawOutput[0] is FloatArray) {
                        rawOutput[0] as FloatArray
                    } else if (rawOutput.isNotEmpty() && rawOutput[0] is Array<*>) {
                        val nested = rawOutput[0] as Array<*>
                        if (nested.isNotEmpty() && nested[0] is FloatArray) {
                            nested[0] as FloatArray
                        } else {
                            floatArrayOf()
                        }
                    } else {
                        floatArrayOf()
                    }
                }
                is FloatArray -> rawOutput
                else -> floatArrayOf()
            }

            // Cleanup tensors
            tensorsToClose.forEach { it.close() }
            results.close()

            if (audioFloats.isEmpty()) {
                Log.e(TAG, "Inference produced empty audio buffer")
                throw Exception("Model ran but produced empty audio output - likely an output tensor shape/format mismatch")
            }

            // 4. Convert float32 [-1.0, 1.0] samples to 16-bit PCM WAV file
            val outputFile = File(context.cacheDir, "kokoro_sample_${System.currentTimeMillis()}.wav")
            writeWavFile(audioFloats, outputFile, SAMPLE_RATE)
            Log.i(TAG, "WAV audio file written to: ${outputFile.absolutePath} (${outputFile.length()} bytes)")

            outputFile
        } catch (c: CancellationException) {
            throw c
        } catch (e: Exception) {
            // FIX: previously this swallowed the real exception and returned
            // null, so the UI only ever showed a generic "Synthesis failed"
            // with no way to know why. Now we log it AND rethrow, so the
            // screen (KokoroTestCard.kt's catch block) shows the real
            // "Error: <message>" text - no logcat tool needed to debug.
            Log.e(TAG, "Error during Kokoro inference", e)
            throw Exception("${e.javaClass.simpleName}: ${e.message}", e)
        }
    }

    /**
     * Plays a generated WAV file using Android MediaPlayer.
     */
    fun playAudio(wavFile: File, onCompletion: (() -> Unit)? = null) {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(wavFile.absolutePath)
                prepare()
                setOnCompletionListener {
                    onCompletion?.invoke()
                }
                start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error playing audio file", e)
        }
    }

    fun stopAudio() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping audio", e)
        }
    }

    private fun loadVoiceStyle(voiceId: String, tokenCount: Int): FloatArray {
        val voiceFile = modelManager.getVoiceFile(voiceId)
        if (voiceFile.exists() && voiceFile.length() >= 1024) {
            try {
                val bytes = voiceFile.readBytes()
                val byteBuffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
                val numFloats = bytes.size / 4
                val floats = FloatArray(numFloats)
                byteBuffer.asFloatBuffer().get(floats)

                if (floats.size >= 256) {
                    val row = (tokenCount.coerceIn(0, (floats.size / 256) - 1)) * 256
                    return floats.copyOfRange(row, row + 256)
                }
                return floats
            } catch (e: Exception) {
                Log.w(TAG, "Error reading voice file for $voiceId, falling back to default style", e)
            }
        }

        val defaultVector = FloatArray(256)
        for (i in 0 until 256) {
            defaultVector[i] = if (i % 2 == 0) 0.05f else -0.05f
        }
        return defaultVector
    }

    private fun writeWavFile(floats: FloatArray, outputFile: File, sampleRate: Int) {
        val numSamples = floats.size
        val numChannels = 1
        val bitsPerSample = 16
        val byteRate = sampleRate * numChannels * (bitsPerSample / 8)
        val blockAlign = numChannels * (bitsPerSample / 8)
        val dataSize = numSamples * (bitsPerSample / 8)
        val totalSize = 36 + dataSize

        val fos = FileOutputStream(outputFile)
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)

        header.put("RIFF".toByteArray())
        header.putInt(totalSize)
        header.put("WAVE".toByteArray())

        header.put("fmt ".toByteArray())
        header.putInt(16)
        header.putShort(1.toShort())
        header.putShort(numChannels.toShort())
        header.putInt(sampleRate)
        header.putInt(byteRate)
        header.putShort(blockAlign.toShort())
        header.putShort(bitsPerSample.toShort())

        header.put("data".toByteArray())
        header.putInt(dataSize)

        fos.write(header.array())

        val pcmBuffer = ByteBuffer.allocate(numSamples * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (sample in floats) {
            val clamped = sample.coerceIn(-1.0f, 1.0f)
            val pcmShort = (clamped * 32767.0f).toInt().toShort()
            pcmBuffer.putShort(pcmShort)
        }

        fos.write(pcmBuffer.array())
        fos.flush()
        fos.close()
    }

    fun release() {
        try {
            stopAudio()
            ortSession?.close()
            ortSession = null
            ortEnvironment?.close()
            ortEnvironment = null
            isInitialized.set(false)
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing Kokoro engine", e)
        }
    }

    companion object {
        @Volatile
        private var instance: KokoroEngine? = null

        fun getInstance(context: Context): KokoroEngine {
            return instance ?: synchronized(this) {
                val mm = KokoroModelManager.getInstance(context)
                instance ?: KokoroEngine(context.applicationContext, mm).also { instance = it }
            }
        }
    }
}
