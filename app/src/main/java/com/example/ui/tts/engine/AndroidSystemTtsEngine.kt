package com.example.ui.tts.engine

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale

/**
 * Android System TextToSpeech Engine.
 *
 * Dedicated to all other languages (Marathi, Tamil, Telugu, Bengali, Gujarati, Kannada,
 * Malayalam, Punjabi, Urdu, French, German, Spanish, etc.) and explicit system fallback.
 */
class AndroidSystemTtsEngine(
    private val context: Context,
    private val onInitCallback: ((Boolean) -> Unit)? = null
) {
    private val TAG = "AndroidSystemTtsEngine"
    private var textToSpeech: TextToSpeech? = null
    private var isInitialized = false

    init {
        try {
            textToSpeech = TextToSpeech(context.applicationContext) { status ->
                isInitialized = status == TextToSpeech.SUCCESS
                if (isInitialized) {
                    Log.i(TAG, "Android System TTS engine initialized successfully.")
                } else {
                    Log.e(TAG, "Failed to initialize Android System TTS engine.")
                }
                onInitCallback?.invoke(isInitialized)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error instantiating TextToSpeech", e)
            onInitCallback?.invoke(false)
        }
    }

    fun isReady(): Boolean = isInitialized && textToSpeech != null

    fun setLanguage(locale: Locale): Int {
        return textToSpeech?.setLanguage(locale) ?: TextToSpeech.LANG_MISSING_DATA
    }

    fun setSpeechRate(rate: Float) {
        textToSpeech?.setSpeechRate(rate.coerceIn(0.5f, 2.5f))
    }

    fun setPitch(pitch: Float) {
        textToSpeech?.setPitch(pitch.coerceIn(0.5f, 2.0f))
    }

    fun setListener(listener: UtteranceProgressListener) {
        textToSpeech?.setOnUtteranceProgressListener(listener)
    }

    fun speak(text: String, utteranceId: String) {
        val tts = textToSpeech ?: return
        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
        }
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
    }

    fun stop() {
        try {
            textToSpeech?.stop()
        } catch (_: Exception) {}
    }

    fun shutdown() {
        try {
            textToSpeech?.stop()
            textToSpeech?.shutdown()
        } catch (_: Exception) {}
        textToSpeech = null
        isInitialized = false
    }
}
