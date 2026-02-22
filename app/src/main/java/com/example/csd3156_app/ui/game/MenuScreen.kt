package com.example.csd3156_app.ui.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

@Composable
fun MenuScreen(onStartGame: () -> Unit, onSettings: () -> Unit) {
    // For vibration
    val haptic = LocalHapticFeedback.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("2048 TILT", style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onStartGame()
        },
            modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)) {
            Text("PLAY GAME")
        }
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedButton(
            onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onSettings()
        }, modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)) {
            Text("SETTINGS")
        }
    }
}