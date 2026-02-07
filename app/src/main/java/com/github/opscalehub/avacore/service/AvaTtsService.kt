package com.github.opscalehub.avacore.service

import android.speech.tts.SynthesisCallback
import android.speech.tts.SynthesisRequest
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeechService
import android.speech.tts.Voice
import android.util.Log
import java.util.Locale

class AvaTtsService : TextToSpeechService() {

    private val persianLocale = Locale("fa", "IR")
    
    // Hardcode defaults to ensure the system always sees a valid language
    private var currentLanguage = "fa"
    private var currentCountry = "IR"

    companion object {
        private const val TAG = "AvaTtsService"
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate: AvaCore TTS Engine is initializing.")
    }

    private fun isPersian(lang: String?): Boolean {
        if (lang == null) return false
        return lang.equals("fa", ignoreCase = true) || 
               lang.equals("fas", ignoreCase = true) || 
               lang.equals("per", ignoreCase = true)
    }

    override fun onIsLanguageAvailable(lang: String?, country: String?, variant: String?): Int {
        Log.d(TAG, "onIsLanguageAvailable: lang=$lang, country=$country")
        
        // If the system asks for Persian, always say it's fully supported
        return if (isPersian(lang)) {
            TextToSpeech.LANG_COUNTRY_AVAILABLE
        } else {
            TextToSpeech.LANG_NOT_SUPPORTED
        }
    }

    override fun onGetLanguage(): Array<String> {
        // Always report Persian as the current language if nothing else is set
        Log.d(TAG, "onGetLanguage: returning $currentLanguage-$currentCountry")
        return arrayOf(currentLanguage, currentCountry, "")
    }

    override fun onLoadLanguage(lang: String?, country: String?, variant: String?): Int {
        Log.d(TAG, "onLoadLanguage: lang=$lang, country=$country")
        val result = onIsLanguageAvailable(lang, country, variant)
        if (result >= TextToSpeech.LANG_AVAILABLE) {
            currentLanguage = lang ?: "fa"
            currentCountry = country ?: "IR"
        }
        return result
    }

    override fun onGetVoices(): MutableList<Voice> {
        val voices = mutableListOf<Voice>()
        // No features = fully installed and ready
        voices.add(
            Voice(
                "fa-ir-ava-premium",
                persianLocale,
                Voice.QUALITY_VERY_HIGH,
                Voice.LATENCY_NORMAL,
                false, 
                mutableSetOf()
            )
        )
        return voices
    }

    override fun onGetDefaultVoiceNameFor(lang: String?, country: String?, variant: String?): String? {
        return if (isPersian(lang)) "fa-ir-ava-premium" else null
    }

    override fun onStop() {
        Log.d(TAG, "onStop: Stopping synthesis.")
    }

    override fun onSynthesizeText(request: SynthesisRequest?, callback: SynthesisCallback?) {
        val text = request?.charSequenceText ?: ""
        Log.d(TAG, "onSynthesizeText: synthesizing '$text'")
        
        if (callback == null) return
        
        // 16kHz, 16-bit Mono PCM
        callback.start(16000, android.media.AudioFormat.ENCODING_PCM_16BIT, 1)

        // Return 1 second of silence so the UI sees active playback
        val silenceBuffer = ByteArray(32000)
        callback.audioAvailable(silenceBuffer, 0, silenceBuffer.size)
        callback.done()
    }
}
