package com.example.ui.tts.kokoro

import android.util.Log
import org.json.JSONObject
import java.io.File
import java.util.Locale

object KokoroTokenizer {

    private const val TAG = "KokoroTokenizer"

    // In-memory cached dynamic vocabulary from config.json (the REAL, official
    // Kokoro-82M vocab). This is the only vocab that matches what the model
    // was actually trained on.
    private var dynamicVocab: Map<String, Long>? = null
    private var loggedFallbackWarning = false

    // WARNING: This is NOT the official Kokoro-82M vocabulary. The real vocab
    // is a fixed, pinned mapping shipped inside the model's config.json
    // (see https://huggingface.co/hexgrad/Kokoro-82M/blob/main/config.json,
    // "vocab" key -> 178 entries). This map below assigns IDs in an arbitrary
    // order and WILL NOT match the model's trained embeddings.
    //
    // Using this fallback is why pronunciation is wrong (especially Hindi,
    // which relies on many IPA symbols whose invented IDs here are almost
    // certainly different from what the model expects).
    //
    // ACTION REQUIRED: download config.json from the same repo you got the
    // .onnx model from, and make sure KokoroModelManager downloads/bundles it
    // and passes it into tokenize() via configFile, so dynamicVocab loads
    // instead of this fallback.
    private val DEFAULT_VOCAB: Map<String, Long> by lazy {
        val map = mutableMapOf<String, Long>()
        val punct = listOf(";", ":", ",", ".", "!", "?", "-", "'", "\"", "(", ")", "[", "]", "{", "}", "/", "\\", "@", "#", "$", "%", "^", "&", "*", "_", "+", "=", "<", ">", "~", "`", "|", " ")
        punct.forEachIndexed { idx, p -> map[p] = (idx + 1).toLong() }

        for (d in '0'..'9') {
            if (!map.containsKey(d.toString())) {
                map[d.toString()] = (map.size + 1).toLong()
            }
        }

        for (c in 'A'..'Z') {
            if (!map.containsKey(c.toString())) map[c.toString()] = (map.size + 1).toLong()
        }
        for (c in 'a'..'z') {
            if (!map.containsKey(c.toString())) map[c.toString()] = (map.size + 1).toLong()
        }

        val ipaSymbols = listOf(
            "ɐ", "ɑ", "ɒ", "ɓ", "ɔ", "ɕ", "ɖ", "ɗ", "ɘ", "ə", "ɚ", "ɛ", "ɜ", "ɝ", "ɞ",
            "ɟ", "ɠ", "ɡ", "ɢ", "ɣ", "ɤ", "ɥ", "ɦ", "ɧ", "ɨ", "ɩ", "ɪ", "ɫ", "ɬ", "ɭ",
            "ɮ", "ɯ", "ɰ", "ɱ", "ɲ", "ɳ", "ɴ", "ɵ", "ɶ", "ɷ", "ɸ", "ɹ", "ɺ", "ɻ", "ɽ",
            "ɾ", "ʀ", "ʁ", "ʂ", "ʃ", "ʄ", "ʅ", "ʆ", "ʇ", "ʈ", "ʉ", "ʊ", "ʋ", "ʌ", "ʍ",
            "ʎ", "ʏ", "ʐ", "ʑ", "ʒ", "ʓ", "ʔ", "ʕ", "ʖ", "ʗ", "ʘ", "ʙ", "ʚ", "ʛ", "ʜ",
            "ʝ", "ʞ", "ʟ", "ʠ", "ʡ", "ʢ", "ʣ", "ʤ", "ʥ", "ʦ", "ʧ", "ʨ", "ʰ", "ʱ", "ʲ",
            "ʳ", "ʷ", "ʸ", "ˈ", "ˌ", "ː", "ˑ", "̃", "̩", "̯", "̪", "θ", "ð"
        )
        ipaSymbols.forEach { sym ->
            if (!map.containsKey(sym)) {
                map[sym] = (map.size + 1).toLong()
            }
        }
        map
    }

    /**
     * Loads the official config.json vocab mapping if present.
     */
    fun loadVocabConfig(configFile: File?) {
        if (configFile == null || !configFile.exists() || configFile.length() == 0L) {
            if (!loggedFallbackWarning) {
                Log.e(TAG, "config.json missing or empty (path=${configFile?.absolutePath}). " +
                        "Falling back to INVENTED vocab IDs - pronunciation will be wrong. " +
                        "Download the real config.json from the model repo and pass its path here.")
                loggedFallbackWarning = true
            }
            return
        }
        try {
            val jsonStr = configFile.readText()
            val json = JSONObject(jsonStr)
            if (json.has("vocab")) {
                val vocabObj = json.getJSONObject("vocab")
                val loadedMap = mutableMapOf<String, Long>()
                val keys = vocabObj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    loadedMap[key] = vocabObj.getLong(key)
                }
                if (loadedMap.isNotEmpty()) {
                    dynamicVocab = loadedMap
                    Log.i(TAG, "Loaded ${loadedMap.size} tokens from config.json (REAL Kokoro vocab active)")
                }
            } else {
                Log.e(TAG, "config.json found but has no 'vocab' key - still using fallback (wrong) vocab")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse vocab from config.json: ${e.message}")
        }
    }

    private fun getVocab(): Map<String, Long> {
        val real = dynamicVocab
        if (real == null && !loggedFallbackWarning) {
            Log.e(TAG, "Using FALLBACK invented vocab (config.json never loaded successfully). " +
                    "Pronunciation accuracy cannot be fixed until this is resolved.")
            loggedFallbackWarning = true
        }
        return real ?: DEFAULT_VOCAB
    }

    /**
     * Main tokenization pipeline for Kokoro-82M.
     * Converts text -> phonemes -> token IDs wrapped with [0, ..., 0].
     */
    fun tokenize(text: String, languageCode: String = "en", configFile: File? = null): LongArray {
        loadVocabConfig(configFile)
        val vocab = getVocab()

        val phonemes = phonemize(text, languageCode)

        val tokens = mutableListOf<Long>()
        tokens.add(0L)

        for (ch in phonemes) {
            val s = ch.toString()
            val id = vocab[s] ?: when (ch) {
                ' ', '\n', '\t', '\r' -> vocab[" "] ?: 1L
                else -> vocab[s.lowercase(Locale.ROOT)] ?: 1L
            }
            tokens.add(id)
            if (tokens.size >= 508) break
        }

        tokens.add(0L)

        return tokens.toLongArray()
    }

    /**
     * Grapheme-to-Phoneme converter for English and Hindi.
     *
     * NOTE: The real Kokoro pipeline uses the "misaki" G2P library, which is
     * backed by espeak-ng, to produce phonemes - especially for Hindi, where
     * schwa deletion and consonant clusters follow rules that are hard to
     * replicate by hand. The phonemizeHindi() below is a reasonable manual
     * approximation but will diverge from what the model was trained on.
     * Once the vocab ID fix above is confirmed working, if Hindi is still
     * far off, the next step is integrating a real espeak-ng based G2P
     * (e.g. an Android NDK build of espeak-ng) instead of this hand-written
     * mapping.
     */
    fun phonemize(text: String, languageCode: String): String {
        val clean = text.trim()
        val lang = languageCode.lowercase()
        return if (lang.startsWith("hi")) {
            phonemizeHindi(clean)
        } else {
            phonemizeEnglish(clean)
        }
    }

    private fun phonemizeHindi(text: String): String {
        val sb = StringBuilder()
        var i = 0
        val len = text.length

        val consonantMap = mapOf(
            'क' to "k", 'ख' to "kʰ", 'ग' to "ɡ", 'घ' to "ɡʱ", 'ङ' to "ŋ",
            'च' to "tʃ", 'छ' to "tʃʰ", 'ज' to "dʒ", 'झ' to "dʒʱ", 'ञ' to "ɲ",
            'ट' to "ʈ", 'ठ' to "ʈʰ", 'ड' to "ɖ", 'ढ' to "ɖʱ", 'ण' to "ɳ",
            'त' to "t̪", 'थ' to "t̪ʰ", 'द' to "d̪", 'ध' to "d̪ʱ", 'न' to "n",
            'प' to "p", 'फ' to "pʰ", 'ब' to "b", 'भ' to "bʱ", 'म' to "m",
            'य' to "j", 'र' to "r", 'ल' to "l", 'ळ' to "ɭ", 'व' to "ʋ",
            'श' to "ʃ", 'ष' to "ʂ", 'स' to "s", 'ह' to "h"
        )

        // Nukta consonants (Urdu/Persian loanwords, e.g. ज़रूर, फ़र्क़) are TWO
        // Unicode codepoints each (base letter + combining nukta U+093C), so
        // they can't be Char literals ('...') in Kotlin - that caused the
        // "too many characters in a character literal" build error. Using
        // String keys here instead and checking them before the single-char
        // consonantMap fixes it.
        val nuktaMap = mapOf(
            "क़" to "q", "ख़" to "x", "ग़" to "ɣ", "ज़" to "z",
            "ड़" to "ɽ", "ढ़" to "ɽʱ", "फ़" to "f"
        )

        val vowelMap = mapOf(
            'अ' to "ə", 'आ' to "aː", 'इ' to "ɪ", 'ई' to "iː",
            'उ' to "ʊ", 'ऊ' to "uː", 'ऋ' to "rɪ", 'ए' to "eː",
            'ऐ' to "ɛː", 'ओ' to "oː", 'औ' to "ɔː"
        )

        val matraMap = mapOf(
            'ा' to "aː", 'ि' to "ɪ", 'ी' to "iː",
            'ु' to "ʊ", 'ू' to "uː", 'ृ' to "rɪ",
            'े' to "eː", 'ै' to "ɛː", 'ो' to "oː", 'ौ' to "ɔː"
        )

        while (i < len) {
            val ch = text[i]
            val twoCharSlice = if (i + 1 < len) text.substring(i, i + 2) else null

            when {
                twoCharSlice != null && nuktaMap.containsKey(twoCharSlice) -> {
                    // Nukta consonant (2 codepoints) - consume both, then run
                    // the same virama/matra/schwa lookahead as a normal consonant.
                    val consIpa = nuktaMap[twoCharSlice]!!
                    sb.append(consIpa)
                    i += 2

                    val nextChar = if (i < len) text[i] else null
                    if (nextChar == '्') {
                        i += 1
                        continue
                    } else if (nextChar != null && matraMap.containsKey(nextChar)) {
                        sb.append(matraMap[nextChar])
                        i += 1
                        continue
                    } else {
                        val isEndOfWord = (nextChar == null || nextChar.isWhitespace() || nextChar in "।,!?.")
                        if (!isEndOfWord) {
                            sb.append("ə")
                        }
                        continue
                    }
                }
                consonantMap.containsKey(ch) -> {
                    val consIpa = consonantMap[ch]!!
                    sb.append(consIpa)

                    val nextChar = if (i + 1 < len) text[i + 1] else null
                    if (nextChar == '्') {
                        i += 2
                        continue
                    } else if (nextChar != null && matraMap.containsKey(nextChar)) {
                        sb.append(matraMap[nextChar])
                        i += 2
                        continue
                    } else {
                        val isEndOfWord = (nextChar == null || nextChar.isWhitespace() || nextChar in "।,!?.")
                        if (!isEndOfWord) {
                            sb.append("ə")
                        }
                    }
                }
                vowelMap.containsKey(ch) -> {
                    sb.append(vowelMap[ch])
                }
                matraMap.containsKey(ch) -> {
                    sb.append(matraMap[ch])
                }
                ch == 'ं' -> sb.append("̃")
                ch == 'ः' -> sb.append("h")
                ch == 'ँ' -> sb.append("̃")
                ch == 'ऽ' -> { /* Avagraha, prolong vowel */ }
                ch == '।' || ch == '॥' -> sb.append(".")
                else -> {
                    sb.append(ch)
                }
            }
            i++
        }
        return sb.toString()
    }

    private fun phonemizeEnglish(text: String): String {
        val expanded = expandEnglishAbbreviationsAndNumbers(text)
        val words = expanded.split(Regex("(?<=\\s)|(?=\\s)|(?<=[.,!?;:\"]) |(?=[.,!?;:\"])"))
        val sb = StringBuilder()

        for (token in words) {
            val clean = token.trim()
            if (clean.isEmpty()) {
                sb.append(token)
                continue
            }
            if (clean.length == 1 && !clean[0].isLetter()) {
                sb.append(clean)
                continue
            }
            val ipa = englishWordToIpa(clean.lowercase(Locale.ROOT))
            sb.append(ipa)
        }
        return sb.toString()
    }

    private fun expandEnglishAbbreviationsAndNumbers(text: String): String {
        var res = text
            .replace("Mr.", "Mister")
            .replace("Mrs.", "Missis")
            .replace("Dr.", "Doctor")
            .replace("Prof.", "Professor")
            .replace("St.", "Saint")
            .replace("&", " and ")
            .replace("%", " percent ")
            .replace("+", " plus ")
            .replace("=", " equals ")
            .replace("w/", "with")
            .replace("w/o", "without")
            .replace("can't", "kænt")
            .replace("won't", "woʊnt")
            .replace("don't", "doʊnt")
            .replace("didn't", "dɪdənt")
            .replace("it's", "ɪts")
            .replace("i'm", "aɪm")

        res = res.replace(Regex("\\b0\\b"), "zero")
            .replace(Regex("\\b1\\b"), "one")
            .replace(Regex("\\b2\\b"), "two")
            .replace(Regex("\\b3\\b"), "three")
            .replace(Regex("\\b4\\b"), "four")
            .replace(Regex("\\b5\\b"), "five")
            .replace(Regex("\\b6\\b"), "six")
            .replace(Regex("\\b7\\b"), "seven")
            .replace(Regex("\\b8\\b"), "eight")
            .replace(Regex("\\b9\\b"), "nine")
            .replace(Regex("\\b10\\b"), "ten")

        return res
    }

    private fun englishWordToIpa(word: String): String {
        COMMON_ENGLISH_IPA[word]?.let { return it }

        var w = word
            .replace("th", "θ")
            .replace("sh", "ʃ")
            .replace("ch", "tʃ")
            .replace("ph", "f")
            .replace("gh", "f")
            .replace("ng", "ŋ")
            .replace("ck", "k")
            .replace("qu", "kw")
            .replace("wh", "w")
            .replace("ee", "iː")
            .replace("oo", "uː")
            .replace("ea", "iː")
            .replace("ai", "eɪ")
            .replace("ay", "eɪ")
            .replace("oa", "oʊ")
            .replace("ow", "aʊ")
            .replace("ou", "aʊ")
            .replace("oi", "ɔɪ")
            .replace("oy", "ɔɪ")

        if (w.length > 2 && w.endsWith("e") && !w.endsWith("ee")) {
            w = w.dropLast(1)
        }

        return "ˈ$w"
    }

    private val COMMON_ENGLISH_IPA = mapOf(
        "the" to "ðə", "a" to "ə", "an" to "æn", "and" to "ænd", "to" to "tuː",
        "of" to "ʌv", "in" to "ɪn", "is" to "ɪz", "you" to "juː", "that" to "ðæt",
        "it" to "ɪt", "he" to "hiː", "was" to "wʌz", "for" to "fɔːr", "on" to "ɑːn",
        "are" to "ɑːr", "as" to "æz", "with" to "wɪð", "his" to "hɪz", "they" to "ðeɪ",
        "i" to "aɪ", "at" to "æt", "be" to "biː", "this" to "ðɪs", "have" to "hæv",
        "from" to "frʌm", "or" to "ɔːr", "one" to "wʌn", "had" to "hæd", "by" to "baɪ",
        "word" to "wɜːrd", "but" to "bʌt", "not" to "nɑːt", "what" to "wʌt", "all" to "ɔːl",
        "were" to "wɜːr", "we" to "wiː", "when" to "wɛn", "your" to "jɔːr", "can" to "kæn",
        "said" to "sɛd", "there" to "ðɛr", "use" to "juːz", "each" to "iːtʃ", "which" to "wɪtʃ",
        "she" to "ʃiː", "do" to "duː", "how" to "haʊ", "their" to "ðɛr", "if" to "ɪf",
        "will" to "wɪl", "up" to "ʌp", "other" to "ʌðər", "about" to "əbaʊt", "out" to "aʊt",
        "many" to "mɛni", "then" to "ðɛn", "them" to "ðɛm", "these" to "ðiːz", "so" to "soʊ",
        "some" to "sʌm", "her" to "hɜːr", "would" to "wʊd", "make" to "meɪk", "like" to "laɪk",
        "him" to "hɪm", "into" to "ɪntuː", "time" to "taɪm", "has" to "hæz", "look" to "lʊk",
        "two" to "tuː", "more" to "mɔːr", "write" to "raɪt", "go" to "ɡoʊ", "see" to "siː",
        "number" to "nʌmbər", "no" to "noʊ", "way" to "weɪ", "could" to "kʊd", "people" to "piːpəl",
        "my" to "maɪ", "than" to "ðæn", "first" to "fɜːrst", "water" to "wɔːtər", "been" to "bɪn",
        "call" to "kɔːl", "who" to "huː", "oil" to "ɔɪl", "its" to "ɪts", "now" to "naʊ",
        "find" to "faɪnd", "long" to "lɔːŋ", "down" to "daʊn", "day" to "deɪ", "did" to "dɪd",
        "get" to "ɡɛt", "come" to "kʌm", "made" to "meɪd", "may" to "meɪ", "part" to "pɑːrt",
        "hello" to "həˈloʊ", "world" to "wɜːrld", "read" to "riːd", "reading" to "riːdɪŋ",
        "book" to "bʊk", "books" to "bʊks", "audio" to "ˈɔːdioʊ", "speech" to "spiːtʃ",
        "voice" to "vɔɪs", "player" to "ˈpleɪər", "page" to "peɪdʒ", "chapter" to "ˈtʃæptər"
    )
}
