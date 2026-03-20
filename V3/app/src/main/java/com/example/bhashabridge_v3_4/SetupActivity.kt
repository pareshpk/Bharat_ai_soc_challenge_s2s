package com.example.bhashabridge_v3_4

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class SetupActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)

        // Apply tricolour title
        // setupTitle is ImageView — no text needed

        // English button
        findViewById<Button>(R.id.btnEnglish).setOnClickListener {
            saveLanguage("en")
        }

        // Hindi button
        findViewById<Button>(R.id.btnHindi).setOnClickListener {
            saveLanguage("hi")
        }
    }

    private var pendingLang = "en"

    private fun saveLanguage(lang: String) {
        pendingLang = lang
        val check = android.content.Intent(
            android.speech.tts.TextToSpeech.Engine.ACTION_CHECK_TTS_DATA)
        startActivityForResult(check, 3001)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 3001) {
            if (resultCode == android.speech.tts.TextToSpeech.Engine.CHECK_VOICE_DATA_PASS) {
                commitLanguage(pendingLang)
            } else {
                androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("Hindi Voice Required")
                    .setMessage("BhashaBridge needs Hindi text-to-speech voice data to speak translations aloud.\n\nThis is a one-time install from Google (~15MB).")
                    .setPositiveButton("Install Now") { _, _ ->
                        startActivity(android.content.Intent(
                            android.speech.tts.TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA))
                        commitLanguage(pendingLang)
                    }
                    .setNegativeButton("Skip (text only)") { _, _ ->
                        commitLanguage(pendingLang)
                    }
                    .setCancelable(false)
                    .show()
            }
        }
    }

    private fun commitLanguage(lang: String) {
        getSharedPreferences("bb_prefs", MODE_PRIVATE)
            .edit()
            .putString("ui_language", lang)
            .apply()
        setResult(RESULT_OK, Intent().putExtra("ui_language", lang))
        finish()
    }

}
