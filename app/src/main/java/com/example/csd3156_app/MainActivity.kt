package com.example.csd3156_app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.csd3156_app.ui.theme.CSD3156_APPTheme
import com.example.csd3156_app.ui.game.Tilt2048Route

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CSD3156_APPTheme {
                Tilt2048Route()
            }
        }
    }
}
