package com.example.ui.tts.engine

import android.content.Context
import android.util.Log
import com.example.ui.tts.NaturalVoice
import com.example.ui.tts.kokoro.KokoroEngine
import com.example.ui.tts.kokoro.KokoroModelManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Local Neural TTS Engine running on-device with ONNX Runtime.
 *
 * Dedicated strictly to Hindi and English local offline neural speech synthesis.
 * - Runs 100% offline without internet after models are installed.
 * - Requires no API keys or cloud credentials.
 * - Has no per-character limits or recurring costs.
 */
class LocalNeuralTtsEngine private constructor(private val context: Context) : TtsEngine {

    private val TAG = "LocalNeuralTtsEngine"
    private val modelManager = KokoroModelManager.getInstance(context)
    private val kokoroEngine = KokoroEngine.getInstance(context)

    val isModelInstalled: Boolean
        get() = modelManager.isModelValid()

    override suspend fun isReady(voice: NaturalVoice): Boolean {
        return modelManager.isModelValid() && modelManager.isVoiceDownloaded(voice.voiceId)
    }

    /**
     * Synthesizes the given text offline into a high-quality PCM/WAV audio file.
     */
    override suspend fun synthesize(
        text: String,
        voice: NaturalVoice,
        speechRate: Float
    ): Result<File> = withContext(Dispatchers.Default) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Text cannot be empty"))
        }

        if (!modelManager.isModelValid()) {
            return@withContext Result.failure(
                IllegalStateException("Local neural TTS model is not installed. Please download the offline neural model.")
            )
        }

        if (!modelManager.isVoiceDownloaded(voice.voiceId)) {
            return@withContext Result.failure(
                IllegalStateException("Voice style '${voice.speakerName}' is not installed locally. Please download the voice style.")
            )
        }

        try {
            val wavFile = kokoroEngine.synthesizeToWav(
                text = trimmed,
                voiceId = voice.voiceId,
                speed = speechRate,
                languageCode = voice.languageCode
            )

            if (wavFile != null && wavFile.exists() && wavFile.length() > 100) {
                Log.d(TAG, "Local neural synthesis succeeded: ${wavFile.absolutePath} (${wavFile.length()} bytes)")
                Result.success(wavFile)
            } else {
                Result.failure(Exception("Local neural engine produced an empty audio file."))
            }
        } catch (c: CancellationException) {
            throw c
        } catch (e: Exception) {
            Log.e(TAG, "Error in local neural synthesis: ${e.message}", e)
            Result.failure(e)
        }
    }

    companion object {
        @Volatile
        private var instance: LocalNeuralTtsEngine? = null

        fun getInstance(context: Context): LocalNeuralTtsEngine {
            return instance ?: synchronized(this) {
                instance ?: LocalNeuralTtsEngine(context.applicationContext).also { instance = it }
            }
        }
    }
}
