package com.github.opscalehub.avacore

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvStatus)
        val btnOpenSettings = findViewById<Button>(R.id.btnOpenSettings)
        val btnBatteryOptimization = findViewById<Button>(R.id.btnBatteryOptimization)

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
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun updateStatus() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        val isIgnoringBattery = pm.isIgnoringBatteryOptimizations(packageName)
        
        // Efficient check for default TTS engine without binding to the service
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
}
