package com.github.opscalehub.avacore

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log

class TtsDataCheckActivity : Activity() {
    
    companion object {
        private const val TAG = "TtsDataCheckActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val action = intent?.action
        val resultIntent = Intent()

        Log.d(TAG, "onCreate: action=$action")

        when (action) {
            TextToSpeech.Engine.ACTION_CHECK_TTS_DATA -> {
                // Return supported locales in different formats to ensure compatibility
                val availableVoices = arrayListOf("fa", "fa-IR", "fas-IRN")
                resultIntent.putStringArrayListExtra(TextToSpeech.Engine.EXTRA_AVAILABLE_VOICES, availableVoices)
                resultIntent.putStringArrayListExtra(TextToSpeech.Engine.EXTRA_UNAVAILABLE_VOICES, arrayListOf())
                
                // Some systems also look for these
                resultIntent.putStringArrayListExtra("availableVoices", availableVoices)
                
                setResult(TextToSpeech.Engine.CHECK_VOICE_DATA_PASS, resultIntent)
            }
            "android.speech.tts.engine.GET_SAMPLE_TEXT" -> {
                val lang = intent.getStringExtra("language")
                Log.d(TAG, "GET_SAMPLE_TEXT for language: $lang")
                
                val sampleText = "این یک آزمایش از موتور بازگو کننده آوا است."
                resultIntent.putExtra(TextToSpeech.Engine.EXTRA_SAMPLE_TEXT, sampleText)
                setResult(RESULT_OK, resultIntent)
            }
            TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA -> {
                // Since it's offline and included, we just say okay
                setResult(RESULT_OK)
            }
            else -> setResult(RESULT_CANCELED)
        }

        finish()
    }
}
