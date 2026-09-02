package com.example.ui.tts

import android.content.Context
import android.util.Log
import com.example.ui.tts.engine.LocalNeuralTtsEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/**
 * High-performance audio synthesizer for Local Offline Neural Voices.
 *
 * Caches generated audio files to avoid redundant inference on identical text chunks.
 * Contains ZERO cloud requests, ZERO WebSocket connections, and ZERO token hacks.
 */
object NaturalAudioSynthesizer {
    private const val TAG = "NaturalAudioSynth"

    suspend fun synthesizeToAudioFile(
        context: Context,
        text: String,
        voice: NaturalVoice,
        speechRate: Float = 1.0f
    ): Result<File> = withContext(Dispatchers.IO) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Text is blank"))
        }

        val cacheDir = File(context.cacheDir, "neural_tts_cache").apply { if (!exists()) mkdirs() }
        val hashKey = md5("${voice.voiceId}_${voice.gender.name}_${voice.languageCode}_${speechRate}_$trimmed")
        val cachedFile = File(cacheDir, "$hashKey.wav")

        // Return cached audio if already generated and valid
        if (cachedFile.exists() && cachedFile.length() > 500) {
            Log.d(TAG, "Reusing cached neural audio: ${cachedFile.absolutePath} (${cachedFile.length()} bytes)")
            return@withContext Result.success(cachedFile)
        }

        val engine = LocalNeuralTtsEngine.getInstance(context)
        val result = engine.synthesize(trimmed, voice, speechRate)

        result.onSuccess { generatedFile ->
            if (generatedFile != cachedFile) {
                try {
                    generatedFile.copyTo(cachedFile, overwrite = true)
                    return@withContext Result.success(cachedFile)
                } catch (e: Exception) {
                    Log.w(TAG, "Could not copy to cache: ${e.message}")
                    return@withContext Result.success(generatedFile)
                }
            }
        }

        result
    }

    private fun md5(input: String): String {
        return try {
            val md = MessageDigest.getInstance("MD5")
            val bytes = md.digest(input.toByteArray())
            bytes.joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            input.hashCode().toString()
        }
    }
}
