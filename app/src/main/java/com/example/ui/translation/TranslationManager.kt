package com.example.ui.translation

import android.util.Log
import com.example.ui.tts.TtsLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.TimeUnit

data class SupportedTranslationLanguage(
    val code: String,              // e.g. "hi", "mr", "gu", "bn", "ta", "te", "kn", "ml", "pa", "ur", "en"
    val displayName: String,       // e.g. "Hindi"
    val nativeName: String,        // e.g. "हिन्दी"
    val ttsLanguageCode: String,   // e.g. "hi-IN"
    val ttsLocale: Locale          // Locale("hi", "IN")
)

data class TranslationResult(
    val translatedText: String,
    val sourceLanguage: String,
    val targetLanguage: SupportedTranslationLanguage,
    val isOfflineModel: Boolean = false
)

object TranslationManager {
    private const val TAG = "TranslationManager"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    // Supported target languages
    val SUPPORTED_LANGUAGES = listOf(
        SupportedTranslationLanguage(
            code = "hi",
            displayName = "Hindi",
            nativeName = "हिन्दी",
            ttsLanguageCode = "hi-IN",
            ttsLocale = Locale("hi", "IN")
        ),
        SupportedTranslationLanguage(
            code = "mr",
            displayName = "Marathi",
            nativeName = "मराठी",
            ttsLanguageCode = "mr-IN",
            ttsLocale = Locale("mr", "IN")
        ),
        SupportedTranslationLanguage(
            code = "gu",
            displayName = "Gujarati",
            nativeName = "ગુજરાતી",
            ttsLanguageCode = "gu-IN",
            ttsLocale = Locale("gu", "IN")
        ),
        SupportedTranslationLanguage(
            code = "bn",
            displayName = "Bengali",
            nativeName = "বাংলা",
            ttsLanguageCode = "bn-IN",
            ttsLocale = Locale("bn", "IN")
        ),
        SupportedTranslationLanguage(
            code = "ta",
            displayName = "Tamil",
            nativeName = "தமிழ்",
            ttsLanguageCode = "ta-IN",
            ttsLocale = Locale("ta", "IN")
        ),
        SupportedTranslationLanguage(
            code = "te",
            displayName = "Telugu",
            nativeName = "తెలుగు",
            ttsLanguageCode = "te-IN",
            ttsLocale = Locale("te", "IN")
        ),
        SupportedTranslationLanguage(
            code = "kn",
            displayName = "Kannada",
            nativeName = "ಕನ್ನಡ",
            ttsLanguageCode = "kn-IN",
            ttsLocale = Locale("kn", "IN")
        ),
        SupportedTranslationLanguage(
            code = "ml",
            displayName = "Malayalam",
            nativeName = "മലയാളം",
            ttsLanguageCode = "ml-IN",
            ttsLocale = Locale("ml", "IN")
        ),
        SupportedTranslationLanguage(
            code = "pa",
            displayName = "Punjabi",
            nativeName = "ਪੰਜਾਬੀ",
            ttsLanguageCode = "pa-IN",
            ttsLocale = Locale("pa", "IN")
        ),
        SupportedTranslationLanguage(
            code = "ur",
            displayName = "Urdu",
            nativeName = "اردو",
            ttsLanguageCode = "ur-IN",
            ttsLocale = Locale("ur", "IN")
        ),
        SupportedTranslationLanguage(
            code = "en",
            displayName = "English",
            nativeName = "English",
            ttsLanguageCode = "en-IN",
            ttsLocale = Locale("en", "IN")
        ),
        SupportedTranslationLanguage(
            code = "es",
            displayName = "Spanish",
            nativeName = "Español",
            ttsLanguageCode = "es-ES",
            ttsLocale = Locale("es", "ES")
        ),
        SupportedTranslationLanguage(
            code = "fr",
            displayName = "French",
            nativeName = "Français",
            ttsLanguageCode = "fr-FR",
            ttsLocale = Locale("fr", "FR")
        ),
        SupportedTranslationLanguage(
            code = "de",
            displayName = "German",
            nativeName = "Deutsch",
            ttsLanguageCode = "de-DE",
            ttsLocale = Locale("de", "DE")
        ),
        SupportedTranslationLanguage(
            code = "ja",
            displayName = "Japanese",
            nativeName = "日本語",
            ttsLanguageCode = "ja-JP",
            ttsLocale = Locale("ja", "JP")
        ),
        SupportedTranslationLanguage(
            code = "ar",
            displayName = "Arabic",
            nativeName = "العربية",
            ttsLanguageCode = "ar-SA",
            ttsLocale = Locale("ar", "SA")
        )
    )

    /**
     * Translates the given text to the target language.
     * Preserves paragraph formatting and structure.
     */
    suspend fun translateText(
        text: String,
        targetLang: SupportedTranslationLanguage,
        sourceLangCode: String = "auto"
    ): Result<TranslationResult> = withContext(Dispatchers.IO) {
        if (text.isBlank()) {
            return@withContext Result.failure(Exception("Cannot translate empty or blank page text."))
        }

        // If source and target are the same language
        if (sourceLangCode.equals(targetLang.code, ignoreCase = true)) {
            return@withContext Result.success(
                TranslationResult(
                    translatedText = text,
                    sourceLanguage = sourceLangCode,
                    targetLanguage = targetLang,
                    isOfflineModel = false
                )
            )
        }

        try {
            val translated = translateMultiTier(text, sourceLangCode, targetLang.code)
            if (translated.isNotBlank()) {
                return@withContext Result.success(
                    TranslationResult(
                        translatedText = translated,
                        sourceLanguage = sourceLangCode,
                        targetLanguage = targetLang,
                        isOfflineModel = false
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Translation error", e)
        }

        Result.failure(Exception("Unable to translate to ${targetLang.displayName}. Please verify your internet connection."))
    }

    private suspend fun translateMultiTier(
        text: String,
        sourceLang: String,
        targetLang: String
    ): String = withContext(Dispatchers.IO) {
        val paragraphs = text.split("\n\n")
        val translatedParagraphs = mutableListOf<String>()

        for (paragraph in paragraphs) {
            val trimmed = paragraph.trim()
            if (trimmed.isEmpty()) {
                translatedParagraphs.add("")
                continue
            }

            var translatedPart: String? = null

            // Tier 1: Google Translate GTX Endpoint
            try {
                val encodedText = URLEncoder.encode(trimmed, "UTF-8")
                val url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=$sourceLang&tl=$targetLang&dt=t&q=$encodedText"
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Android; Mobile; rv:109.0) Gecko/115.0 Firefox/115.0")
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string()
                        if (!body.isNullOrBlank()) {
                            val jsonArray = JSONArray(body)
                            val sentences = jsonArray.optJSONArray(0)
                            if (sentences != null && sentences.length() > 0) {
                                val sb = StringBuilder()
                                for (i in 0 until sentences.length()) {
                                    val part = sentences.optJSONArray(i)
                                    if (part != null && part.length() > 0) {
                                        sb.append(part.getString(0))
                                    }
                                }
                                val result = sb.toString()
                                if (result.isNotBlank()) {
                                    translatedPart = result
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Tier 1 translation failed: ${e.message}")
            }

            // Tier 2: Lingva Mirror fallback
            if (translatedPart.isNullOrBlank()) {
                try {
                    val sl = if (sourceLang == "auto") "en" else sourceLang
                    val encodedText = URLEncoder.encode(trimmed, "UTF-8")
                    val url = "https://lingva.ml/api/v1/$sl/$targetLang/$encodedText"
                    val request = Request.Builder()
                        .url(url)
                        .header("User-Agent", "BookHaul-Reader/1.0")
                        .build()

                    httpClient.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            val body = response.body?.string()
                            if (!body.isNullOrBlank()) {
                                val json = JSONObject(body)
                                if (json.has("translation")) {
                                    translatedPart = json.getString("translation")
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Tier 2 translation failed: ${e.message}")
                }
            }

            // Tier 3: MyMemory API fallback
            if (translatedPart.isNullOrBlank()) {
                try {
                    val sl = if (sourceLang == "auto") "en" else sourceLang
                    val encodedText = URLEncoder.encode(trimmed.take(500), "UTF-8")
                    val url = "https://api.mymemory.translated.net/get?q=$encodedText&langpair=$sl|$targetLang"
                    val request = Request.Builder()
                        .url(url)
                        .header("User-Agent", "BookHaul-Reader/1.0")
                        .build()

                    httpClient.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            val body = response.body?.string()
                            if (!body.isNullOrBlank()) {
                                val json = JSONObject(body)
                                val responseData = json.optJSONObject("responseData")
                                if (responseData != null && responseData.has("translatedText")) {
                                    val candidate = responseData.getString("translatedText")
                                    if (candidate.isNotBlank() && !candidate.startsWith("MYMEMORY WARNING")) {
                                        translatedPart = candidate
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Tier 3 translation failed: ${e.message}")
                }
            }

            translatedParagraphs.add(translatedPart ?: trimmed)
        }

        translatedParagraphs.joinToString("\n\n")
    }

    fun findTtsLanguage(targetLang: SupportedTranslationLanguage, availableTtsLanguages: List<TtsLanguage>): TtsLanguage {
        return availableTtsLanguages.firstOrNull { it.code.startsWith(targetLang.code, ignoreCase = true) }
            ?: availableTtsLanguages.firstOrNull { it.locale.language.equals(targetLang.code, ignoreCase = true) }
            ?: TtsLanguage(
                code = targetLang.ttsLanguageCode,
                displayName = targetLang.displayName,
                nativeName = targetLang.nativeName,
                locale = targetLang.ttsLocale,
                isAvailable = true
            )
    }
}
