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

        when (action) {
            TextToSpeech.Engine.ACTION_CHECK_TTS_DATA -> {
                // Reporting ONLY the specific locale that matches the service
                val availableVoices = arrayListOf("fa-IR")
                resultIntent.putStringArrayListExtra(TextToSpeech.Engine.EXTRA_AVAILABLE_VOICES, availableVoices)
                resultIntent.putStringArrayListExtra(TextToSpeech.Engine.EXTRA_UNAVAILABLE_VOICES, arrayListOf<String>())
                setResult(TextToSpeech.Engine.CHECK_VOICE_DATA_PASS, resultIntent)
            }
            "android.speech.tts.engine.GET_SAMPLE_TEXT" -> {
                val sampleText = "این یک آزمایش از موتور بازگو کننده آوا است."
                resultIntent.putExtra("sampleText", sampleText)
                setResult(RESULT_OK, resultIntent)
            }
            TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA -> {
                setResult(RESULT_OK)
            }
        }

        finish()
    }
}
