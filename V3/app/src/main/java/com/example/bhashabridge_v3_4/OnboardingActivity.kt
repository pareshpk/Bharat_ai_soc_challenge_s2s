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

class OnboardingActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)

        // Apply tricolour title
        // onboardingTitle is ImageView — no text needed

        // Mark onboarding as seen
        getSharedPreferences("bb_prefs", MODE_PRIVATE)
            .edit()
            .putBoolean("seen_onboarding", true)
            .apply()

        // Get Started button → go to SetupActivity
        findViewById<Button>(R.id.btnGetStarted).setOnClickListener {
            startActivityForResult(
                Intent(this, SetupActivity::class.java),
                2001
            )
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        // Pass result back to MainActivity
        setResult(resultCode, data)
        finish()
    }

}
