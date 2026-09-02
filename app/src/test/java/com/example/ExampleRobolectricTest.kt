package com.example

import com.example.ui.tts.LanguageRouter
import com.example.ui.tts.TtsRouteTarget
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun testLanguageRouter_HindiRouting() {
        val hindiText = "नमस्ते, आप कैसे हैं? यह एक पुस्तक है।"
        val target = LanguageRouter.routeText(hindiText)
        assertEquals(TtsRouteTarget.LOCAL_NEURAL_HINDI, target)
    }

    @Test
    fun testLanguageRouter_EnglishRouting() {
        val englishText = "The quick brown fox jumps over the lazy dog."
        val target = LanguageRouter.routeText(englishText)
        assertEquals(TtsRouteTarget.LOCAL_NEURAL_ENGLISH, target)
    }

    @Test
    fun testLanguageRouter_OtherLanguagesRouting() {
        val marathiText = "हे एक छान पुस्तक आहे."
        val target = LanguageRouter.routeText(marathiText, selectedLanguageCode = "mr-IN")
        assertEquals(TtsRouteTarget.ANDROID_SYSTEM_TTS, target)

        val tamilText = "வணக்கம், இது ஒரு புத்தகம்."
        val targetTamil = LanguageRouter.routeText(tamilText, selectedLanguageCode = "ta-IN")
        assertEquals(TtsRouteTarget.ANDROID_SYSTEM_TTS, targetTamil)
    }
}
