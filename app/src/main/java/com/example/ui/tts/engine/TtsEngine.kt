package com.example.ui.tts.engine

import com.example.ui.tts.NaturalVoice
import java.io.File

/**
 * Common interface for TTS synthesis engines.
 */
interface TtsEngine {
    suspend fun isReady(voice: NaturalVoice): Boolean
    suspend fun synthesize(text: String, voice: NaturalVoice, speechRate: Float): Result<File>
}
