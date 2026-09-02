package com.example.ui.tts.kokoro

enum class KokoroLanguage(val code: String, val displayName: String) {
    HINDI("hi-IN", "Hindi (हिन्दी)"),
    ENGLISH("en-US", "English")
}

data class KokoroVoice(
    val id: String,               // e.g. "hf_alpha", "hf_beta", "hm_omega", "hm_psi"
    val name: String,             // e.g. "Alpha (अल्फा)", "Beta (बीटा)"
    val language: KokoroLanguage,
    val gender: String,           // "Female" or "Male"
    val description: String,
    val isHindi: Boolean = language == KokoroLanguage.HINDI
) {
    val fileName: String
        get() = "$id.bin"
}

object KokoroVoiceCatalog {
    // Official Hindi voices for Kokoro-82M
    val HINDI_VOICES = listOf(
        KokoroVoice(
            id = "hf_alpha",
            name = "Alpha - अल्फा (Female)",
            language = KokoroLanguage.HINDI,
            gender = "Female",
            description = "Hindi female natural neural voice (Soft & Expressive)"
        ),
        KokoroVoice(
            id = "hf_beta",
            name = "Beta - बीटा (Female)",
            language = KokoroLanguage.HINDI,
            gender = "Female",
            description = "Hindi female natural neural voice (Bright & Clear)"
        ),
        KokoroVoice(
            id = "hm_omega",
            name = "Omega - ओमेगा (Male)",
            language = KokoroLanguage.HINDI,
            gender = "Male",
            description = "Hindi male natural neural voice (Warm & Deep)"
        ),
        KokoroVoice(
            id = "hm_psi",
            name = "Psi - साई (Male)",
            language = KokoroLanguage.HINDI,
            gender = "Male",
            description = "Hindi male natural neural voice (Narrator tone)"
        )
    )

    // Common English voices for Kokoro-82M
    val ENGLISH_VOICES = listOf(
        KokoroVoice(
            id = "af_heart",
            name = "Heart (Female)",
            language = KokoroLanguage.ENGLISH,
            gender = "Female",
            description = "American English female flagship voice"
        ),
        KokoroVoice(
            id = "af_bella",
            name = "Bella (Female)",
            language = KokoroLanguage.ENGLISH,
            gender = "Female",
            description = "American English female natural voice"
        ),
        KokoroVoice(
            id = "am_adam",
            name = "Adam (Male)",
            language = KokoroLanguage.ENGLISH,
            gender = "Male",
            description = "American English male natural voice"
        ),
        KokoroVoice(
            id = "am_michael",
            name = "Michael (Male)",
            language = KokoroLanguage.ENGLISH,
            gender = "Male",
            description = "American English male deep voice"
        ),
        KokoroVoice(
            id = "bf_emma",
            name = "Emma (British Female)",
            language = KokoroLanguage.ENGLISH,
            gender = "Female",
            description = "British English female voice"
        ),
        KokoroVoice(
            id = "bm_george",
            name = "George (British Male)",
            language = KokoroLanguage.ENGLISH,
            gender = "Male",
            description = "British English male voice"
        )
    )

    val ALL_VOICES = HINDI_VOICES + ENGLISH_VOICES

    fun getVoiceById(id: String): KokoroVoice {
        return ALL_VOICES.find { it.id.equals(id, ignoreCase = true) } ?: HINDI_VOICES.first()
    }
}
