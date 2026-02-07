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
                // فقط یک کد برمی‌گردانیم تا لیست تکراری نشود
                // fa-IR استانداردترین حالت برای اندروید است
                val availableVoices = arrayListOf("fa-IR")
                resultIntent.putStringArrayListExtra(TextToSpeech.Engine.EXTRA_AVAILABLE_VOICES, availableVoices)
                resultIntent.putStringArrayListExtra(TextToSpeech.Engine.EXTRA_UNAVAILABLE_VOICES, arrayListOf<String>())
                setResult(TextToSpeech.Engine.CHECK_VOICE_DATA_PASS, resultIntent)
            }
            "android.speech.tts.engine.GET_SAMPLE_TEXT" -> {
                val sampleText = "این یک آزمایش از موتور بازگو کننده آوا است."
                resultIntent.putExtra("sampleText", sampleText)
                resultIntent.putExtra(TextToSpeech.Engine.EXTRA_SAMPLE_TEXT, sampleText)
                setResult(RESULT_OK, resultIntent)
            }
            TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA -> {
                setResult(RESULT_OK)
            }
            else -> setResult(RESULT_CANCELED)
        }

        finish()
    }
}
