package com.example.mycropspt.ui.theme

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.mycropspt.R
import android.util.Log
import kotlin.random.Random

class LoadingScreen : AppCompatActivity() {
    private lateinit var tipsTextView: TextView
    private lateinit var tips: Array<String>
    //private var currentTipIndex = 0
    private var randomIndex = 0
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_loading_screen)

        tipsTextView = findViewById(R.id.tips_rotation)
        tips = resources.getStringArray(R.array.tips_array)

        startTipRotation()
    }


    private fun startTipRotation() {
        handler.postDelayed({
            fadeOutAndChangeTip()
        }, 1500)
    }

    private fun fadeOutAndChangeTip() {
        tipsTextView.animate().alpha(0f).setDuration(500).withEndAction {
            randomIndex = Random.nextInt(tips.size)
            //currentTipIndex = (currentTipIndex + 1) % tips.size
            tipsTextView.text = tips[randomIndex]
            tipsTextView.animate().alpha(1f).setDuration(500)
            startTipRotation()
        }.start()

        handler.postDelayed({
            finish()
        }, 5000)
    }

}