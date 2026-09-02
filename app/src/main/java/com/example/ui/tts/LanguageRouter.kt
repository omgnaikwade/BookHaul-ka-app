package com.example.ui.tts

/**
 * Route targets for the hybrid TTS architecture.
 */
enum class TtsRouteTarget {
    LOCAL_NEURAL_HINDI,    // Local offline neural TTS (Hindi model)
    LOCAL_NEURAL_ENGLISH,  // Local offline neural TTS (English model)
    ANDROID_SYSTEM_TTS     // Android System TextToSpeech (All other languages & system mode)
}

data class RoutedSentence(
    val text: String,
    val target: TtsRouteTarget,
    val languageCode: String
)

/**
 * Hybrid Language Router.
 *
 * Routes sentences/paragraphs intelligently based on text language and script:
 * 1. HINDI → Local Offline Neural TTS
 * 2. ENGLISH → Local Offline Neural TTS
 * 3. ALL OTHER LANGUAGES → Android System TextToSpeech
 */
object LanguageRouter {

    private val DEVANAGARI_REGEX = Regex("[\\u0900-\\u097F]")
    private val TAMIL_REGEX = Regex("[\\u0B80-\\u0BFF]")
    private val TELUGU_REGEX = Regex("[\\u0C00-\\u0C7F]")
    private val BENGALI_REGEX = Regex("[\\u0980-\\u09FF]")
    private val GUJARATI_REGEX = Regex("[\\u0A80-\\u0AFF]")
    private val KANNADA_REGEX = Regex("[\\u0C80-\\u0CFF]")
    private val MALAYALAM_REGEX = Regex("[\\u0D00-\\u0D7F]")
    private val GURMUKHI_REGEX = Regex("[\\u0A00-\\u0A7F]")
    private val ARABIC_REGEX = Regex("[\\u0600-\\u06FF]")
    private val CJK_REGEX = Regex("[\\u4E00-\\u9FFF\\u3040-\\u30FF]")
    private val CYRILLIC_REGEX = Regex("[\\u0400-\\u04FF]")

    /**
     * Determines whether the given text segment should route to:
     * - Local Neural Hindi
     * - Local Neural English
     * - Android System TTS
     */
    fun routeText(text: String, selectedLanguageCode: String = "hi-IN"): TtsRouteTarget {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return TtsRouteTarget.LOCAL_NEURAL_ENGLISH

        // Explicit non-Hindi, non-English selected language (e.g. Marathi, Tamil, Bengali, French, etc.)
        val isExplicitOtherLanguage = !selectedLanguageCode.startsWith("hi", ignoreCase = true) &&
                !selectedLanguageCode.startsWith("en", ignoreCase = true)

        if (isExplicitOtherLanguage) {
            return TtsRouteTarget.ANDROID_SYSTEM_TTS
        }

        var devanagariCount = 0
        var otherIndicCount = 0
        var otherScriptCount = 0
        var latinCount = 0

        for (ch in trimmed) {
            val s = ch.toString()
            when {
                DEVANAGARI_REGEX.matches(s) -> devanagariCount++
                TAMIL_REGEX.matches(s) || TELUGU_REGEX.matches(s) ||
                BENGALI_REGEX.matches(s) || GUJARATI_REGEX.matches(s) ||
                KANNADA_REGEX.matches(s) || MALAYALAM_REGEX.matches(s) ||
                GURMUKHI_REGEX.matches(s) -> otherIndicCount++
                ARABIC_REGEX.matches(s) || CJK_REGEX.matches(s) || CYRILLIC_REGEX.matches(s) -> otherScriptCount++
                ch in 'a'..'z' || ch in 'A'..'Z' -> latinCount++
            }
        }

        // If non-Hindi/English scripts are detected -> Android System TTS
        if (otherIndicCount > 0 || otherScriptCount > 0) {
            return TtsRouteTarget.ANDROID_SYSTEM_TTS
        }

        // If Devanagari characters are present -> Local Neural Hindi
        if (devanagariCount > 0) {
            return TtsRouteTarget.LOCAL_NEURAL_HINDI
        }

        // If Latin characters or default -> Local Neural English
        return TtsRouteTarget.LOCAL_NEURAL_ENGLISH
    }

    /**
     * Routes a collection of text chunks/sentences, returning target destination and language code.
     */
    fun routeSentences(sentences: List<String>, selectedLanguageCode: String = "hi-IN"): List<RoutedSentence> {
        return sentences.map { sentence ->
            val target = routeText(sentence, selectedLanguageCode)
            val langCode = when (target) {
                TtsRouteTarget.LOCAL_NEURAL_HINDI -> "hi-IN"
                TtsRouteTarget.LOCAL_NEURAL_ENGLISH -> "en-US"
                TtsRouteTarget.ANDROID_SYSTEM_TTS -> selectedLanguageCode
            }
            RoutedSentence(
                text = sentence,
                target = target,
                languageCode = langCode
            )
        }
    }
}
