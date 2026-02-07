package com.github.opscalehub.avacore

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val btnOpenSettings = findViewById<Button>(R.id.btnOpenSettings)
        btnOpenSettings.setOnClickListener {
            // Use the correct action string to open Text-to-Speech settings
            // 'Settings.ACTION_TTS_SETTINGS' is not a valid constant in android.provider.Settings
            val intent = Intent("com.android.settings.TTS_SETTINGS")
            startActivity(intent)
        }
    }
}
