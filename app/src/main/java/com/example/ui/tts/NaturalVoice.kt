package com.example.ui.tts

import java.util.Locale

enum class VoiceGender {
    FEMALE,
    MALE
}

enum class VoiceProvider {
    LOCAL_NEURAL,     // On-device local neural TTS (Hindi & English offline)
    SYSTEM_TTS        // Android System TextToSpeech (All other languages)
}

enum class TtsEngineType {
    NATURAL_NEURAL,   // Hybrid Local Neural Engine (Hindi & English offline)
    SYSTEM_OFFLINE    // Android Device System TextToSpeech
}

data class NaturalVoice(
    val voiceId: String,
    val speakerName: String,
    val gender: VoiceGender,
    val provider: VoiceProvider = VoiceProvider.LOCAL_NEURAL,
    val languageCode: String,        // e.g. "hi-IN", "en-US", "mr-IN", "ta-IN"
    val languageDisplayName: String, // e.g. "Hindi", "English", "Marathi"
    val nativeLanguageName: String,  // e.g. "हिन्दी", "English", "मराठी"
    val locale: Locale,
    val description: String
) {
    val fullDisplayName: String
        get() = "$speakerName (${if (gender == VoiceGender.FEMALE) "Female" else "Male"}) [${when (provider) {
            VoiceProvider.LOCAL_NEURAL -> "Neural Offline"
            VoiceProvider.SYSTEM_TTS -> "System TTS"
        }}]"
}

object NaturalVoiceCatalog {

    // ==========================================
    // 1. LOCAL NEURAL HINDI VOICES (Offline)
    // ==========================================
    val HINDI_NEURAL_VOICES = listOf(
        NaturalVoice(
            voiceId = "hf_alpha",
            speakerName = "Alpha - अल्फा (Neural Female)",
            gender = VoiceGender.FEMALE,
            provider = VoiceProvider.LOCAL_NEURAL,
            languageCode = "hi-IN",
            languageDisplayName = "Hindi",
            nativeLanguageName = "हिन्दी",
            locale = Locale("hi", "IN"),
            description = "Offline on-device neural Hindi female voice (Soft & Expressive)"
        ),
        NaturalVoice(
            voiceId = "hf_beta",
            speakerName = "Beta - बीटा (Neural Female)",
            gender = VoiceGender.FEMALE,
            provider = VoiceProvider.LOCAL_NEURAL,
            languageCode = "hi-IN",
            languageDisplayName = "Hindi",
            nativeLanguageName = "हिन्दी",
            locale = Locale("hi", "IN"),
            description = "Offline on-device neural Hindi female voice (Bright & Clear)"
        ),
        NaturalVoice(
            voiceId = "hm_omega",
            speakerName = "Omega - ओमेगा (Neural Male)",
            gender = VoiceGender.MALE,
            provider = VoiceProvider.LOCAL_NEURAL,
            languageCode = "hi-IN",
            languageDisplayName = "Hindi",
            nativeLanguageName = "हिन्दी",
            locale = Locale("hi", "IN"),
            description = "Offline on-device neural Hindi male voice (Warm & Deep)"
        ),
        NaturalVoice(
            voiceId = "hm_psi",
            speakerName = "Psi - साई (Neural Male)",
            gender = VoiceGender.MALE,
            provider = VoiceProvider.LOCAL_NEURAL,
            languageCode = "hi-IN",
            languageDisplayName = "Hindi",
            nativeLanguageName = "हिन्दी",
            locale = Locale("hi", "IN"),
            description = "Offline on-device neural Hindi male voice (Narrator tone)"
        )
    )

    // ==========================================
    // 2. LOCAL NEURAL ENGLISH VOICES (Offline)
    // ==========================================
    val ENGLISH_NEURAL_VOICES = listOf(
        NaturalVoice(
            voiceId = "af_heart",
            speakerName = "Heart (Neural Female)",
            gender = VoiceGender.FEMALE,
            provider = VoiceProvider.LOCAL_NEURAL,
            languageCode = "en-US",
            languageDisplayName = "English (US)",
            nativeLanguageName = "English",
            locale = Locale.US,
            description = "Offline on-device neural American English female voice"
        ),
        NaturalVoice(
            voiceId = "af_bella",
            speakerName = "Bella (Neural Female)",
            gender = VoiceGender.FEMALE,
            provider = VoiceProvider.LOCAL_NEURAL,
            languageCode = "en-US",
            languageDisplayName = "English (US)",
            nativeLanguageName = "English",
            locale = Locale.US,
            description = "Offline on-device neural American English natural female voice"
        ),
        NaturalVoice(
            voiceId = "am_adam",
            speakerName = "Adam (Neural Male)",
            gender = VoiceGender.MALE,
            provider = VoiceProvider.LOCAL_NEURAL,
            languageCode = "en-US",
            languageDisplayName = "English (US)",
            nativeLanguageName = "English",
            locale = Locale.US,
            description = "Offline on-device neural American English natural male voice"
        ),
        NaturalVoice(
            voiceId = "am_michael",
            speakerName = "Michael (Neural Male)",
            gender = VoiceGender.MALE,
            provider = VoiceProvider.LOCAL_NEURAL,
            languageCode = "en-US",
            languageDisplayName = "English (US)",
            nativeLanguageName = "English",
            locale = Locale.US,
            description = "Offline on-device neural American English deep male voice"
        ),
        NaturalVoice(
            voiceId = "bf_emma",
            speakerName = "Emma (British Neural Female)",
            gender = VoiceGender.FEMALE,
            provider = VoiceProvider.LOCAL_NEURAL,
            languageCode = "en-GB",
            languageDisplayName = "English (UK)",
            nativeLanguageName = "English",
            locale = Locale.UK,
            description = "Offline on-device neural British English female voice"
        ),
        NaturalVoice(
            voiceId = "bm_george",
            speakerName = "George (British Neural Male)",
            gender = VoiceGender.MALE,
            provider = VoiceProvider.LOCAL_NEURAL,
            languageCode = "en-GB",
            languageDisplayName = "English (UK)",
            nativeLanguageName = "English",
            locale = Locale.UK,
            description = "Offline on-device neural British English male voice"
        )
    )

    // ==========================================
    // 3. ANDROID SYSTEM VOICES (Other Languages)
    // ==========================================
    val SYSTEM_VOICES = listOf(
        NaturalVoice(
            voiceId = "mr-IN-system-female",
            speakerName = "Marathi Female (मराठी)",
            gender = VoiceGender.FEMALE,
            provider = VoiceProvider.SYSTEM_TTS,
            languageCode = "mr-IN",
            languageDisplayName = "Marathi",
            nativeLanguageName = "मराठी",
            locale = Locale("mr", "IN"),
            description = "Android System Marathi voice"
        ),
        NaturalVoice(
            voiceId = "mr-IN-system-male",
            speakerName = "Marathi Male (मराठी)",
            gender = VoiceGender.MALE,
            provider = VoiceProvider.SYSTEM_TTS,
            languageCode = "mr-IN",
            languageDisplayName = "Marathi",
            nativeLanguageName = "मराठी",
            locale = Locale("mr", "IN"),
            description = "Android System Marathi male voice"
        ),
        NaturalVoice(
            voiceId = "ta-IN-system-female",
            speakerName = "Tamil Female (தமிழ்)",
            gender = VoiceGender.FEMALE,
            provider = VoiceProvider.SYSTEM_TTS,
            languageCode = "ta-IN",
            languageDisplayName = "Tamil",
            nativeLanguageName = "தமிழ்",
            locale = Locale("ta", "IN"),
            description = "Android System Tamil voice"
        ),
        NaturalVoice(
            voiceId = "te-IN-system-female",
            speakerName = "Telugu Female (తెలుగు)",
            gender = VoiceGender.FEMALE,
            provider = VoiceProvider.SYSTEM_TTS,
            languageCode = "te-IN",
            languageDisplayName = "Telugu",
            nativeLanguageName = "తెలుగు",
            locale = Locale("te", "IN"),
            description = "Android System Telugu voice"
        ),
        NaturalVoice(
            voiceId = "bn-IN-system-female",
            speakerName = "Bengali Female (বাংলা)",
            gender = VoiceGender.FEMALE,
            provider = VoiceProvider.SYSTEM_TTS,
            languageCode = "bn-IN",
            languageDisplayName = "Bengali",
            nativeLanguageName = "বাংলা",
            locale = Locale("bn", "IN"),
            description = "Android System Bengali voice"
        ),
        NaturalVoice(
            voiceId = "gu-IN-system-female",
            speakerName = "Gujarati Female (ગુજરાતી)",
            gender = VoiceGender.FEMALE,
            provider = VoiceProvider.SYSTEM_TTS,
            languageCode = "gu-IN",
            languageDisplayName = "Gujarati",
            nativeLanguageName = "ગુજરાતી",
            locale = Locale("gu", "IN"),
            description = "Android System Gujarati voice"
        ),
        NaturalVoice(
            voiceId = "kn-IN-system-female",
            speakerName = "Kannada Female (ಕನ್ನಡ)",
            gender = VoiceGender.FEMALE,
            provider = VoiceProvider.SYSTEM_TTS,
            languageCode = "kn-IN",
            languageDisplayName = "Kannada",
            nativeLanguageName = "ಕನ್ನಡ",
            locale = Locale("kn", "IN"),
            description = "Android System Kannada voice"
        ),
        NaturalVoice(
            voiceId = "ml-IN-system-female",
            speakerName = "Malayalam Female (മലയാളം)",
            gender = VoiceGender.FEMALE,
            provider = VoiceProvider.SYSTEM_TTS,
            languageCode = "ml-IN",
            languageDisplayName = "Malayalam",
            nativeLanguageName = "മലയാളം",
            locale = Locale("ml", "IN"),
            description = "Android System Malayalam voice"
        ),
        NaturalVoice(
            voiceId = "pa-IN-system-female",
            speakerName = "Punjabi Female (ਪੰਜਾਬੀ)",
            gender = VoiceGender.FEMALE,
            provider = VoiceProvider.SYSTEM_TTS,
            languageCode = "pa-IN",
            languageDisplayName = "Punjabi",
            nativeLanguageName = "ਪੰਜਾਬੀ",
            locale = Locale("pa", "IN"),
            description = "Android System Punjabi voice"
        ),
        NaturalVoice(
            voiceId = "ur-IN-system-female",
            speakerName = "Urdu Female (اردو)",
            gender = VoiceGender.FEMALE,
            provider = VoiceProvider.SYSTEM_TTS,
            languageCode = "ur-IN",
            languageDisplayName = "Urdu",
            nativeLanguageName = "اردو",
            locale = Locale("ur", "IN"),
            description = "Android System Urdu voice"
        ),
        NaturalVoice(
            voiceId = "fr-FR-system-female",
            speakerName = "French Female (Français)",
            gender = VoiceGender.FEMALE,
            provider = VoiceProvider.SYSTEM_TTS,
            languageCode = "fr-FR",
            languageDisplayName = "French",
            nativeLanguageName = "Français",
            locale = Locale.FRANCE,
            description = "Android System French voice"
        ),
        NaturalVoice(
            voiceId = "de-DE-system-female",
            speakerName = "German Female (Deutsch)",
            gender = VoiceGender.FEMALE,
            provider = VoiceProvider.SYSTEM_TTS,
            languageCode = "de-DE",
            languageDisplayName = "German",
            nativeLanguageName = "Deutsch",
            locale = Locale.GERMANY,
            description = "Android System German voice"
        ),
        NaturalVoice(
            voiceId = "es-ES-system-female",
            speakerName = "Spanish Female (Español)",
            gender = VoiceGender.FEMALE,
            provider = VoiceProvider.SYSTEM_TTS,
            languageCode = "es-ES",
            languageDisplayName = "Spanish",
            nativeLanguageName = "Español",
            locale = Locale("es", "ES"),
            description = "Android System Spanish voice"
        ),
        NaturalVoice(
            voiceId = "hi-IN-system-female",
            speakerName = "Hindi System Voice (Android TTS)",
            gender = VoiceGender.FEMALE,
            provider = VoiceProvider.SYSTEM_TTS,
            languageCode = "hi-IN",
            languageDisplayName = "Hindi",
            nativeLanguageName = "हिन्दी",
            locale = Locale("hi", "IN"),
            description = "Android System default Hindi voice"
        ),
        NaturalVoice(
            voiceId = "en-US-system-female",
            speakerName = "English System Voice (Android TTS)",
            gender = VoiceGender.FEMALE,
            provider = VoiceProvider.SYSTEM_TTS,
            languageCode = "en-US",
            languageDisplayName = "English",
            nativeLanguageName = "English",
            locale = Locale.US,
            description = "Android System default English voice"
        )
    )

    val ALL_VOICES = HINDI_NEURAL_VOICES + ENGLISH_NEURAL_VOICES + SYSTEM_VOICES

    fun getDefaultVoiceForLanguage(langCode: String): NaturalVoice {
        return ALL_VOICES.firstOrNull { it.languageCode.equals(langCode, ignoreCase = true) && it.provider == VoiceProvider.LOCAL_NEURAL }
            ?: ALL_VOICES.firstOrNull { it.languageCode.startsWith(langCode.take(2), ignoreCase = true) && it.provider == VoiceProvider.LOCAL_NEURAL }
            ?: ALL_VOICES.firstOrNull { it.languageCode.equals(langCode, ignoreCase = true) }
            ?: ALL_VOICES.firstOrNull { it.languageCode.startsWith(langCode.take(2), ignoreCase = true) }
            ?: HINDI_NEURAL_VOICES.first()
    }

    fun getVoicesForLanguage(langCode: String): List<NaturalVoice> {
        val langPrefix = langCode.take(2).lowercase()
        if (langPrefix == "hi") {
            return HINDI_NEURAL_VOICES + SYSTEM_VOICES.filter { it.languageCode.startsWith("hi", ignoreCase = true) }
        }
        if (langPrefix == "en") {
            return ENGLISH_NEURAL_VOICES + SYSTEM_VOICES.filter { it.languageCode.startsWith("en", ignoreCase = true) }
        }
        val matches = ALL_VOICES.filter { it.languageCode.startsWith(langPrefix, ignoreCase = true) }
        return if (matches.isNotEmpty()) matches else listOf(getDefaultVoiceForLanguage(langCode))
    }
}
