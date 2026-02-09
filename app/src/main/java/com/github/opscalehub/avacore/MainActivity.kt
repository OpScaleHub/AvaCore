package com.github.opscalehub.avacore

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private var tts: TextToSpeech? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvStatus)
        val btnOpenSettings = findViewById<Button>(R.id.btnOpenSettings)
        val btnBatteryOptimization = findViewById<Button>(R.id.btnBatteryOptimization)
        val btnTestTts = findViewById<Button>(R.id.btnTestTts)

        btnOpenSettings.setOnClickListener {
            try {
                startActivity(Intent("com.android.settings.TTS_SETTINGS"))
            } catch (e: Exception) {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            }
        }

        btnBatteryOptimization.setOnClickListener {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = Uri.parse("package:$packageName")
            startActivity(intent)
        }

        btnTestTts.setOnClickListener {
            speakSample()
        }
        
        initializeTts()
    }

    private fun initializeTts() {
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale("fa", "IR")
            } else {
                Log.e("MainActivity", "TTS Initialization failed")
            }
        }
    }

    private fun speakSample() {
        val text = "این یک آزمایش از موتور بازگو کننده آوا است."
        val result = tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "sample_id")
        if (result == TextToSpeech.ERROR) {
            Toast.makeText(this, "Speech failed. Ensure AvaCore is selected.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun updateStatus() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        val isIgnoringBattery = pm.isIgnoringBatteryOptimizations(packageName)
        
        val defaultEngine = Settings.Secure.getString(contentResolver, Settings.Secure.TTS_DEFAULT_SYNTH)
        val isSelected = defaultEngine == packageName
        
        val statusText = StringBuilder()
        if (isSelected) {
            statusText.append("✅ AvaCore is the active TTS engine.\n")
        } else {
            statusText.append("❌ AvaCore is NOT the active engine.\n")
        }
        
        if (isIgnoringBattery) {
            statusText.append("✅ Battery restrictions are disabled.")
        } else {
            statusText.append("⚠️ Battery optimization is active.")
        }
        
        tvStatus.text = statusText.toString()
        tvStatus.setTextColor(if (isSelected && isIgnoringBattery) Color.GREEN else Color.YELLOW)
    }

    override fun onDestroy() {
        tts?.shutdown()
        super.onDestroy()
    }
}
