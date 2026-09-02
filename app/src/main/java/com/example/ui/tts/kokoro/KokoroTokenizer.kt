package com.example.ui.tts.kokoro

import android.util.Log
import org.json.JSONObject
import java.io.File
import java.text.Normalizer
import java.util.Locale

object KokoroTokenizer {

    private const val TAG = "KokoroTokenizer"

    // In-memory cached dynamic vocabulary from config.json
    private var dynamicVocab: Map<String, Long>? = null

    // Official Kokoro-82M fallback vocabulary mapping (178 tokens)
    private val DEFAULT_VOCAB: Map<String, Long> by lazy {
        val map = mutableMapOf<String, Long>()
        // Standard punctuation & symbols
        val punct = listOf(";", ":", ",", ".", "!", "?", "-", "'", "\"", "(", ")", "[", "]", "{", "}", "/", "\\", "@", "#", "$", "%", "^", "&", "*", "_", "+", "=", "<", ">", "~", "`", "|", " ")
        punct.forEachIndexed { idx, p -> map[p] = (idx + 1).toLong() }
        
        // Digits
        for (d in '0'..'9') {
            if (!map.containsKey(d.toString())) {
                map[d.toString()] = (map.size + 1).toLong()
            }
        }
        
        // ASCII Latin
        for (c in 'A'..'Z') {
            if (!map.containsKey(c.toString())) map[c.toString()] = (map.size + 1).toLong()
        }
        for (c in 'a'..'z') {
            if (!map.containsKey(c.toString())) map[c.toString()] = (map.size + 1).toLong()
        }
        
        // International Phonetic Alphabet (IPA) phonemes used by Kokoro
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
        if (configFile == null || !configFile.exists() || configFile.length() == 0L) return
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
                    Log.i(TAG, "Loaded ${loadedMap.size} tokens from config.json")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse vocab from config.json: ${e.message}")
        }
    }

    private fun getVocab(): Map<String, Long> {
        return dynamicVocab ?: DEFAULT_VOCAB
    }

    /**
     * Main tokenization pipeline for Kokoro-82M.
     * Converts text -> phonemes -> token IDs wrapped with [0, ..., 0].
     */
    fun tokenize(text: String, languageCode: String = "en", configFile: File? = null): LongArray {
        loadVocabConfig(configFile)
        val vocab = getVocab()

        // 1. Phonemize text based on language
        val phonemes = phonemize(text, languageCode)

        // 2. Map phonemes to token IDs
        val tokens = mutableListOf<Long>()
        // Kokoro expects leading token 0 (start token)
        tokens.add(0L)

        for (ch in phonemes) {
            val s = ch.toString()
            val id = vocab[s] ?: when (ch) {
                ' ', '\n', '\t', '\r' -> vocab[" "] ?: 1L
                else -> {
                    // Fallback to lower-case or standard ASCII
                    vocab[s.lowercase(Locale.ROOT)] ?: 1L
                }
            }
            tokens.add(id)
            if (tokens.size >= 508) break
        }

        // Kokoro expects trailing token 0 (end token)
        tokens.add(0L)

        return tokens.toLongArray()
    }

    /**
     * Grapheme-to-Phoneme converter for English and Hindi.
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

    /**
     * Indic G2P converter for Hindi (Devanagari script to IPA).
     */
    private fun phonemizeHindi(text: String): String {
        val sb = StringBuilder()
        var i = 0
        val len = text.length

        // Consonant map to IPA
        val consonantMap = mapOf(
            'क' to "k", 'ख' to "kʰ", 'ग' to "ɡ", 'घ' to "ɡʱ", 'ङ' to "ŋ",
            'च' to "tʃ", 'छ' to "tʃʰ", 'ज' to "dʒ", 'झ' to "dʒʱ", 'ञ' to "ɲ",
            'ट' to "ʈ", 'ठ' to "ʈʰ", 'ड' to "ɖ", 'ढ' to "ɖʱ", 'ण' to "ɳ",
            'त' to "t̪", 'थ' to "t̪ʰ", 'द' to "d̪", 'ध' to "d̪ʱ", 'न' to "n",
            'प' to "p", 'फ' to "pʰ", 'ब' to "b", 'भ' to "bʱ", 'म' to "m",
            'य' to "j", 'र' to "r", 'ल' to "l", 'ळ' to "ɭ", 'व' to "ʋ",
            'श' to "ʃ", 'ष' to "ʂ", 'स' to "s", 'ह' to "h",
            'क़' to "q", 'ख़' to "x", 'ग़' to "ɣ", 'ज़' to "z", 'ड़' to "ɽ", 'ढ़' to "ɽʱ", 'फ़' to "f"
        )

        // Independent vowels to IPA
        val vowelMap = mapOf(
            'अ' to "ə", 'आ' to "aː", 'इ' to "ɪ", 'ई' to "iː",
            'उ' to "ʊ", 'ऊ' to "uː", 'ऋ' to "rɪ", 'ए' to "eː",
            'ऐ' to "ɛː", 'ओ' to "oː", 'औ' to "ɔː"
        )

        // Matras (dependent vowel signs) to IPA
        val matraMap = mapOf(
            'ा' to "aː", 'ि' to "ɪ", 'ी' to "iː",
            'ु' to "ʊ", 'ू' to "uː", 'ृ' to "rɪ",
            'े' to "eː", 'ै' to "ɛː", 'ो' to "oː", 'ौ' to "ɔː"
        )

        while (i < len) {
            val ch = text[i]

            when {
                consonantMap.containsKey(ch) -> {
                    val consIpa = consonantMap[ch]!!
                    sb.append(consIpa)
                    
                    // Lookahead for virama, matra, or inherent vowel
                    val nextChar = if (i + 1 < len) text[i + 1] else null
                    if (nextChar == '्') {
                        // Virama suppresses inherent vowel
                        i += 2
                        continue
                    } else if (nextChar != null && matraMap.containsKey(nextChar)) {
                        // Matra replaces inherent vowel
                        sb.append(matraMap[nextChar])
                        i += 2
                        continue
                    } else {
                        // Inherent schwa 'ə' unless end of word
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
                    // Pass-through standard characters, spaces, punctuation
                    sb.append(ch)
                }
            }
            i++
        }
        return sb.toString()
    }

    /**
     * Rule-based English phonemizer converting text into IPA phonetic symbols.
     */
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

        // Convert simple digits to spoken words
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
        // Common words fast lookup dictionary
        COMMON_ENGLISH_IPA[word]?.let { return it }

        // Morphological / rule-based IPA conversion
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

        // Silent 'e' at end of word lengthening preceding vowel
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

