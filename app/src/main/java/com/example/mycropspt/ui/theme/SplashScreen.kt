package com.example.mycropspt.ui.theme

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.mycropspt.R
import android.content.Intent
import android.os.Handler
import android.os.Looper
import com.example.mycropspt.MainActivity


class SplashScreen : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash_screen)

        // Delay launching the main activity with a Handler
        val handler = Handler(Looper.getMainLooper())
        handler.postDelayed({
            val intent = Intent(this, MainActivity::class.java) // Replace with your main activity
            startActivity(intent)
            finish()
        }, 3000) // Delay in milliseconds (3 seconds here)
    }
}