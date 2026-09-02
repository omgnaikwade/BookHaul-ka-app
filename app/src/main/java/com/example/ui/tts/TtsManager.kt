package com.example.ui.tts

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.os.Bundle
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import com.example.ui.tts.engine.AndroidSystemTtsEngine
import com.example.ui.tts.engine.LocalNeuralTtsEngine
import com.example.ui.tts.kokoro.KokoroModelManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

data class TtsLanguage(
    val code: String,        // e.g. "hi-IN", "en-US", "mr-IN", "ta-IN"
    val displayName: String, // e.g. "Hindi (हिन्दी)", "Marathi (मराठी)"
    val nativeName: String,  // e.g. "हिन्दी", "मराठी"
    val locale: Locale,
    val isAvailable: Boolean = true,
    val isMissingData: Boolean = false
)

enum class TtsPlaybackState {
    IDLE,
    EXTRACTING_TEXT,
    PLAYING,
    PAUSED,
    STOPPED,
    COMPLETED,
    ERROR
}

data class TtsUiState(
    val isReady: Boolean = false,
    val playbackState: TtsPlaybackState = TtsPlaybackState.IDLE,
    val engineType: TtsEngineType = TtsEngineType.NATURAL_NEURAL,
    val currentPageIndex: Int = 0,
    val extractedText: String = "",
    val sentences: List<String> = emptyList(),
    val currentSentenceIndex: Int = 0,
    val selectedLanguage: TtsLanguage = TtsLanguage(
        code = "hi-IN",
        displayName = "Hindi (हिन्दी)",
        nativeName = "हिन्दी",
        locale = Locale("hi", "IN"),
        isAvailable = true
    ),
    val selectedNaturalVoice: NaturalVoice = NaturalVoiceCatalog.getDefaultVoiceForLanguage("hi-IN"),
    val availableLanguages: List<TtsLanguage> = emptyList(),
    val availableVoicesForSelectedLanguage: List<NaturalVoice> = NaturalVoiceCatalog.getVoicesForLanguage("hi-IN"),
    val speechRate: Float = 1.0f,
    val isBufferingSentence: Boolean = false,
    val isModelDownloaded: Boolean = false,
    val errorMessage: String? = null,
    val isScannedPage: Boolean = false,
    val isTranslated: Boolean = false,
    val customLabel: String? = null
)

/**
 * Hybrid TTS Manager for PDF and Book Reading.
 *
 * Architecture:
 * - HINDI → Local Offline Neural TTS (Sherpa-ONNX / Kokoro)
 * - ENGLISH → Local Offline Neural TTS (Sherpa-ONNX / Kokoro)
 * - ALL OTHER LANGUAGES → Android System TextToSpeech (Marathi, Tamil, Telugu, etc.)
 */
class TtsManager(private val context: Context) : TextToSpeech.OnInitListener {

    private val TAG = "TtsManager"
    private var systemTts: TextToSpeech? = null
    private var mediaPlayer: MediaPlayer? = null
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    private val modelManager = KokoroModelManager.getInstance(context)
    private val neuralEngine = LocalNeuralTtsEngine.getInstance(context)

    private val _state = MutableStateFlow(TtsUiState())
    val state: StateFlow<TtsUiState> = _state.asStateFlow()

    private var activeUtteranceId: String? = null
    private var activePlaybackJob: Job? = null

    var onPageCompleted: ((pageIndex: Int) -> Unit)? = null

    init {
        try {
            systemTts = TextToSpeech(context.applicationContext, this)
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Android System TextToSpeech", e)
        }

        val initialDefaultVoice = NaturalVoiceCatalog.getDefaultVoiceForLanguage("hi-IN")
        val isModelReady = modelManager.isModelValid()

        _state.update {
            it.copy(
                isReady = true,
                isModelDownloaded = isModelReady,
                selectedNaturalVoice = initialDefaultVoice,
                availableVoicesForSelectedLanguage = NaturalVoiceCatalog.getVoicesForLanguage("hi-IN")
            )
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val ttsEngine = systemTts ?: return
            ttsEngine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    scope.launch {
                        _state.update { it.copy(playbackState = TtsPlaybackState.PLAYING, errorMessage = null) }
                    }
                }

                override fun onDone(utteranceId: String?) {
                    scope.launch {
                        if (_state.value.engineType == TtsEngineType.SYSTEM_OFFLINE) {
                            handleSentenceFinished(utteranceId)
                        }
                    }
                }

                override fun onError(utteranceId: String?) {
                    scope.launch {
                        if (_state.value.engineType == TtsEngineType.SYSTEM_OFFLINE) {
                            _state.update {
                                it.copy(
                                    playbackState = TtsPlaybackState.ERROR,
                                    errorMessage = "Android System voice playback error."
                                )
                            }
                        }
                    }
                }
            })

            populateAvailableLanguages(ttsEngine)
        } else {
            Log.e(TAG, "System TTS initialization failed with status: $status")
        }
    }

    private fun populateAvailableLanguages(ttsEngine: TextToSpeech) {
        val supportedLocales = listOf(
            Locale("hi", "IN") to ("Hindi (हिन्दी)" to "हिन्दी"),
            Locale("en", "US") to ("English (US)" to "English"),
            Locale("en", "IN") to ("English (India)" to "English"),
            Locale("en", "GB") to ("English (UK)" to "English"),
            Locale("mr", "IN") to ("Marathi (मराठी)" to "मराठी"),
            Locale("ta", "IN") to ("Tamil (தமிழ்)" to "தமிழ்"),
            Locale("te", "IN") to ("Telugu (తెలుగు)" to "తెలుగు"),
            Locale("bn", "IN") to ("Bengali (বাংলা)" to "বাংলা"),
            Locale("gu", "IN") to ("Gujarati (ગુજરાતી)" to "ગુજરાતી"),
            Locale("kn", "IN") to ("Kannada (ಕನ್ನಡ)" to "ಕನ್ನಡ"),
            Locale("ml", "IN") to ("Malayalam (മലയാളം)" to "മലയാളം"),
            Locale("pa", "IN") to ("Punjabi (ਪੰਜਾਬੀ)" to "ਪੰਜਾਬੀ"),
            Locale("ur", "IN") to ("Urdu (اردو)" to "اردو"),
            Locale.FRANCE to ("French (Français)" to "Français"),
            Locale.GERMAN to ("German (Deutsch)" to "Deutsch"),
            Locale("es", "ES") to ("Spanish (Español)" to "Español"),
            Locale.ITALIAN to ("Italian (Italiano)" to "Italiano"),
            Locale("pt", "BR") to ("Portuguese (Português)" to "Português"),
            Locale("ru", "RU") to ("Russian (Русский)" to "Русский"),
            Locale.JAPANESE to ("Japanese (日本語)" to "日本語"),
            Locale("ar", "SA") to ("Arabic (العربية)" to "العربية")
        )

        val languages = supportedLocales.map { (locale, names) ->
            val availability = try {
                ttsEngine.isLanguageAvailable(locale)
            } catch (_: Exception) {
                TextToSpeech.LANG_NOT_SUPPORTED
            }
            val isAvail = availability >= TextToSpeech.LANG_AVAILABLE
            val isMissing = availability == TextToSpeech.LANG_MISSING_DATA

            TtsLanguage(
                code = locale.toLanguageTag(),
                displayName = names.first,
                nativeName = names.second,
                locale = locale,
                isAvailable = isAvail,
                isMissingData = isMissing
            )
        }

        val sortedLanguages = languages.sortedWith(
            compareByDescending<TtsLanguage> { it.code.startsWith("hi") }
                .thenByDescending { it.code.startsWith("en") }
                .thenByDescending { it.isAvailable }
        )

        val defaultLang = sortedLanguages.firstOrNull { it.code == "hi-IN" }
            ?: sortedLanguages.firstOrNull { it.code.startsWith("hi") }
            ?: sortedLanguages.firstOrNull { it.isAvailable }
            ?: sortedLanguages.first()

        val naturalVoice = NaturalVoiceCatalog.getDefaultVoiceForLanguage(defaultLang.code)
        val isModelReady = modelManager.isModelValid()

        _state.update {
            it.copy(
                availableLanguages = sortedLanguages,
                selectedLanguage = defaultLang,
                selectedNaturalVoice = naturalVoice,
                isModelDownloaded = isModelReady,
                availableVoicesForSelectedLanguage = NaturalVoiceCatalog.getVoicesForLanguage(defaultLang.code)
            )
        }
    }

    fun startReadingPage(pageIndex: Int, text: String, customLabel: String? = null) {
        activePlaybackJob?.cancel()
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        systemTts?.stop()

        val cleanText = text.trim()
        val sentences = splitTextIntoSentences(cleanText)
        val isScanned = cleanText.isBlank() || sentences.isEmpty()
        val isModelReady = modelManager.isModelValid()

        _state.update {
            it.copy(
                currentPageIndex = pageIndex,
                extractedText = cleanText,
                sentences = sentences,
                currentSentenceIndex = 0,
                isScannedPage = isScanned,
                isBufferingSentence = false,
                isModelDownloaded = isModelReady,
                errorMessage = if (isScanned) "This page contains no readable digital text (scanned image page)." else null,
                playbackState = if (isScanned) TtsPlaybackState.IDLE else TtsPlaybackState.PLAYING,
                isTranslated = false,
                customLabel = customLabel
            )
        }

        if (!isScanned) {
            playSentence(0)
        }
    }

    fun startReadingTranslated(pageIndex: Int, translatedText: String, language: TtsLanguage) {
        setLanguage(language)
        startReadingPage(pageIndex, translatedText, "Read Translated (${language.displayName})")
        _state.update { it.copy(isTranslated = true) }
    }

    fun setLanguage(language: TtsLanguage) {
        val voicesForLang = NaturalVoiceCatalog.getVoicesForLanguage(language.code)
        val defaultVoice = voicesForLang.firstOrNull() ?: NaturalVoiceCatalog.getDefaultVoiceForLanguage(language.code)

        _state.update {
            it.copy(
                selectedLanguage = language,
                selectedNaturalVoice = defaultVoice,
                availableVoicesForSelectedLanguage = voicesForLang
            )
        }

        configureSystemTtsVoice(defaultVoice)

        if (_state.value.playbackState == TtsPlaybackState.PLAYING) {
            playSentence(_state.value.currentSentenceIndex)
        }
    }

    fun setNaturalVoice(voice: NaturalVoice) {
        val matchingLang = _state.value.availableLanguages.firstOrNull {
            it.code.equals(voice.languageCode, ignoreCase = true) || it.locale == voice.locale
        } ?: _state.value.selectedLanguage

        _state.update {
            it.copy(
                selectedNaturalVoice = voice,
                selectedLanguage = matchingLang,
                availableVoicesForSelectedLanguage = NaturalVoiceCatalog.getVoicesForLanguage(voice.languageCode),
                errorMessage = null
            )
        }

        configureSystemTtsVoice(voice)

        if (_state.value.playbackState == TtsPlaybackState.PLAYING) {
            playSentence(_state.value.currentSentenceIndex)
        }
    }

    fun setEngineType(engineType: TtsEngineType) {
        _state.update { it.copy(engineType = engineType, errorMessage = null) }
        if (_state.value.playbackState == TtsPlaybackState.PLAYING) {
            playSentence(_state.value.currentSentenceIndex)
        }
    }

    fun setSpeechRate(rate: Float) {
        val clampedRate = rate.coerceIn(0.5f, 2.5f)
        _state.update { it.copy(speechRate = clampedRate) }

        systemTts?.setSpeechRate(clampedRate)

        mediaPlayer?.let { player ->
            try {
                if (player.isPlaying) {
                    val params = player.playbackParams
                    params.speed = clampedRate
                    player.playbackParams = params
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed setting MediaPlayer speed: ${e.message}")
            }
        }
    }

    fun play() {
        val currentIdx = _state.value.currentSentenceIndex
        val sentences = _state.value.sentences

        if (sentences.isEmpty()) return

        if (mediaPlayer != null && _state.value.playbackState == TtsPlaybackState.PAUSED) {
            try {
                mediaPlayer?.start()
                _state.update { it.copy(playbackState = TtsPlaybackState.PLAYING, errorMessage = null) }
                return
            } catch (e: Exception) {
                Log.w(TAG, "Resuming MediaPlayer failed: ${e.message}")
            }
        }

        if (currentIdx in sentences.indices) {
            playSentence(currentIdx)
        } else {
            playSentence(0)
        }
    }

    fun pause() {
        activePlaybackJob?.cancel()
        try {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.pause()
            }
        } catch (_: Exception) {}

        try {
            systemTts?.stop()
        } catch (_: Exception) {}

        _state.update { it.copy(playbackState = TtsPlaybackState.PAUSED, isBufferingSentence = false) }
    }

    fun stop() {
        activePlaybackJob?.cancel()
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (_: Exception) {}
        mediaPlayer = null

        try {
            systemTts?.stop()
        } catch (_: Exception) {}

        _state.update {
            it.copy(
                playbackState = TtsPlaybackState.STOPPED,
                currentSentenceIndex = 0,
                isBufferingSentence = false,
                errorMessage = null
            )
        }
    }

    fun seekSentence(index: Int) {
        val sentences = _state.value.sentences
        if (index in sentences.indices) {
            playSentence(index)
        }
    }

    private fun configureSystemTtsVoice(voice: NaturalVoice) {
        val ttsEngine = systemTts ?: return
        try {
            ttsEngine.language = voice.locale
            ttsEngine.setSpeechRate(_state.value.speechRate)

            val availableVoices = ttsEngine.voices
            if (!availableVoices.isNullOrEmpty()) {
                val isMale = voice.gender == VoiceGender.MALE
                val matchingVoice = availableVoices.firstOrNull { sysVoice ->
                    val matchesLang = sysVoice.locale.language.equals(voice.locale.language, ignoreCase = true)
                    val matchesGender = if (isMale) {
                        sysVoice.name.contains("male", ignoreCase = true) && !sysVoice.name.contains("female", ignoreCase = true)
                    } else {
                        sysVoice.name.contains("female", ignoreCase = true)
                    }
                    matchesLang && matchesGender
                } ?: availableVoices.firstOrNull { sysVoice ->
                    sysVoice.locale.language.equals(voice.locale.language, ignoreCase = true)
                }

                if (matchingVoice != null) {
                    ttsEngine.voice = matchingVoice
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error configuring system TTS voice: ${e.message}")
        }
    }

    private fun playSentence(index: Int) {
        activePlaybackJob?.cancel()

        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (_: Exception) {}
        mediaPlayer = null

        try {
            systemTts?.stop()
        } catch (_: Exception) {}

        val sentences = _state.value.sentences
        if (index !in sentences.indices) {
            _state.update {
                it.copy(
                    playbackState = TtsPlaybackState.COMPLETED,
                    currentSentenceIndex = sentences.size,
                    isBufferingSentence = false
                )
            }
            onPageCompleted?.invoke(_state.value.currentPageIndex)
            return
        }

        val sentenceToRead = sentences[index]
        val selectedVoice = _state.value.selectedNaturalVoice
        val currentRate = _state.value.speechRate
        val engineMode = _state.value.engineType

        _state.update {
            it.copy(
                currentSentenceIndex = index,
                playbackState = TtsPlaybackState.PLAYING,
                errorMessage = null
            )
        }

        if (engineMode == TtsEngineType.SYSTEM_OFFLINE) {
            // User explicitly requested device System TTS
            playViaSystemTts(sentenceToRead, index)
            return
        }

        // Hybrid Language Routing
        val routeTarget = LanguageRouter.routeText(sentenceToRead, selectedVoice.languageCode)

        when (routeTarget) {
            TtsRouteTarget.LOCAL_NEURAL_HINDI -> {
                // Route to Local Offline Neural Hindi
                val hindiVoice = if (selectedVoice.languageCode.startsWith("hi", ignoreCase = true) && selectedVoice.provider == VoiceProvider.LOCAL_NEURAL) {
                    selectedVoice
                } else {
                    NaturalVoiceCatalog.HINDI_NEURAL_VOICES.first()
                }
                playViaLocalNeural(sentenceToRead, hindiVoice, currentRate, index)
            }
            TtsRouteTarget.LOCAL_NEURAL_ENGLISH -> {
                // Route to Local Offline Neural English
                val englishVoice = if (selectedVoice.languageCode.startsWith("en", ignoreCase = true) && selectedVoice.provider == VoiceProvider.LOCAL_NEURAL) {
                    selectedVoice
                } else {
                    NaturalVoiceCatalog.ENGLISH_NEURAL_VOICES.first()
                }
                playViaLocalNeural(sentenceToRead, englishVoice, currentRate, index)
            }
            TtsRouteTarget.ANDROID_SYSTEM_TTS -> {
                // Route to Android System TTS for other languages (Marathi, Tamil, French, etc.)
                playViaSystemTts(sentenceToRead, index)
            }
        }
    }

    private fun playViaLocalNeural(
        sentenceToRead: String,
        voice: NaturalVoice,
        currentRate: Float,
        index: Int
    ) {
        activePlaybackJob = scope.launch {
            _state.update { it.copy(isBufferingSentence = true, errorMessage = null) }

            // Check if model and voice are installed locally
            if (!modelManager.isModelValid()) {
                _state.update {
                    it.copy(
                        isBufferingSentence = false,
                        playbackState = TtsPlaybackState.ERROR,
                        errorMessage = "Local offline neural model is not downloaded yet. Please download the neural model or switch to Android System TTS."
                    )
                }
                return@launch
            }

            if (!modelManager.isVoiceDownloaded(voice.voiceId)) {
                _state.update {
                    it.copy(
                        isBufferingSentence = false,
                        playbackState = TtsPlaybackState.ERROR,
                        errorMessage = "Offline voice '${voice.speakerName}' is not downloaded yet. Please download the voice style or switch to Android System TTS."
                    )
                }
                return@launch
            }

            // Synthesize offline on background thread
            val result = NaturalAudioSynthesizer.synthesizeToAudioFile(
                context = context,
                text = sentenceToRead,
                voice = voice,
                speechRate = currentRate
            )

            _state.update { it.copy(isBufferingSentence = false) }

            result.onSuccess { audioFile ->
                playAudioFile(audioFile, index)

                // Pre-buffer next sentence in background for gapless reading
                val sentences = _state.value.sentences
                if (index + 1 < sentences.size) {
                    launch(Dispatchers.IO) {
                        try {
                            val nextSentence = sentences[index + 1]
                            val nextRoute = LanguageRouter.routeText(nextSentence, voice.languageCode)
                            val nextVoice = when (nextRoute) {
                                TtsRouteTarget.LOCAL_NEURAL_HINDI -> if (voice.languageCode.startsWith("hi")) voice else NaturalVoiceCatalog.HINDI_NEURAL_VOICES.first()
                                TtsRouteTarget.LOCAL_NEURAL_ENGLISH -> if (voice.languageCode.startsWith("en")) voice else NaturalVoiceCatalog.ENGLISH_NEURAL_VOICES.first()
                                else -> null
                            }
                            if (nextVoice != null && modelManager.isVoiceDownloaded(nextVoice.voiceId)) {
                                NaturalAudioSynthesizer.synthesizeToAudioFile(
                                    context = context,
                                    text = nextSentence,
                                    voice = nextVoice,
                                    speechRate = currentRate
                                )
                            }
                        } catch (_: CancellationException) {
                        } catch (e: Exception) {
                            Log.d(TAG, "Pre-buffer ignored: ${e.message}")
                        }
                    }
                }
            }.onFailure { e ->
                if (e is CancellationException) return@onFailure
                Log.e(TAG, "Local neural TTS synthesis error", e)
                _state.update {
                    it.copy(
                        playbackState = TtsPlaybackState.ERROR,
                        errorMessage = "Neural TTS synthesis failed: ${e.message}. You can switch to Android System TTS in settings."
                    )
                }
            }
        }
    }

    private fun playAudioFile(audioFile: File, sentenceIndex: Int) {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null

            val mp = MediaPlayer()
            mp.setDataSource(audioFile.absolutePath)
            mp.setOnPreparedListener { player ->
                try {
                    val targetSpeed = _state.value.speechRate.coerceIn(0.5f, 2.5f)
                    val params = player.playbackParams
                    params.speed = targetSpeed
                    player.playbackParams = params
                } catch (e: Exception) {
                    Log.w(TAG, "Could not set playback params: ${e.message}")
                }
                player.start()
            }
            mp.setOnCompletionListener {
                scope.launch {
                    handleSentenceFinished(null)
                }
            }
            mp.setOnErrorListener { _, _, _ ->
                scope.launch {
                    _state.update {
                        it.copy(
                            playbackState = TtsPlaybackState.ERROR,
                            errorMessage = "Audio playback error for sentence ${sentenceIndex + 1}"
                        )
                    }
                }
                true
            }
            mp.prepareAsync()
            mediaPlayer = mp
        } catch (e: Exception) {
            Log.e(TAG, "Error playing audio file with MediaPlayer", e)
            _state.update {
                it.copy(
                    playbackState = TtsPlaybackState.ERROR,
                    errorMessage = "Audio player error: ${e.message}"
                )
            }
        }
    }

    private fun playViaSystemTts(sentenceToRead: String, index: Int) {
        val ttsEngine = systemTts ?: return
        configureSystemTtsVoice(_state.value.selectedNaturalVoice)
        val utteranceId = "bh_utt_${System.currentTimeMillis()}_$index"
        activeUtteranceId = utteranceId

        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
        }

        try {
            ttsEngine.speak(sentenceToRead, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
        } catch (e: Exception) {
            Log.e(TAG, "System TTS speak error", e)
            _state.update {
                it.copy(
                    playbackState = TtsPlaybackState.ERROR,
                    errorMessage = "Failed to speak via Android System TTS."
                )
            }
        }
    }

    private fun handleSentenceFinished(utteranceId: String?) {
        if (utteranceId != null && activeUtteranceId != utteranceId) return
        val currentIdx = _state.value.currentSentenceIndex
        val total = _state.value.sentences.size

        if (currentIdx + 1 < total) {
            playSentence(currentIdx + 1)
        } else {
            _state.update {
                it.copy(
                    playbackState = TtsPlaybackState.COMPLETED,
                    currentSentenceIndex = total,
                    isBufferingSentence = false
                )
            }
            onPageCompleted?.invoke(_state.value.currentPageIndex)
        }
    }

    private fun splitTextIntoSentences(text: String): List<String> {
        val rawChunks = text.split(Regex("(?<=[.!?।\\n])\\s+"))
        val result = mutableListOf<String>()

        for (chunk in rawChunks) {
            val trimmed = chunk.trim()
            if (trimmed.isNotBlank()) {
                if (trimmed.length > 400) {
                    val subChunks = trimmed.split(Regex("(?<=[,;])\\s+"))
                    for (sub in subChunks) {
                        val subTrimmed = sub.trim()
                        if (subTrimmed.isNotBlank()) {
                            result.add(subTrimmed)
                        }
                    }
                } else {
                    result.add(trimmed)
                }
            }
        }
        return result
    }

    fun openTtsSettings() {
        try {
            val intent = Intent("com.android.settings.TTS_SETTINGS").apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            try {
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Unable to open TTS settings", e)
            }
        }
    }

    fun refreshModelStatus() {
        modelManager.refreshLocalStatus()
        _state.update { it.copy(isModelDownloaded = modelManager.isModelValid()) }
    }

    fun release() {
        activePlaybackJob?.cancel()
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (_: Exception) {}
        mediaPlayer = null

        try {
            systemTts?.stop()
            systemTts?.shutdown()
        } catch (_: Exception) {}
        systemTts = null
        activeUtteranceId = null
    }
}
