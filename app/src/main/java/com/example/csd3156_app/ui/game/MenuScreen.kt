package com.example.csd3156_app.ui.game

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

private const val PREFS_NAME = "tilt2048_prefs"
private const val KEY_PLAYER_NAME = "player_name"

@Composable

fun MenuScreen(onStartGame: (String) -> Unit, onSettings: () -> Unit) {
    val context = LocalContext.current
    var showDialog by remember { mutableStateOf(false) }
    var username by remember {
        val saved = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_PLAYER_NAME, "") ?: ""
        mutableStateOf(saved)
    }
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
            if (username.isNotBlank()) {
                onStartGame(username)
            } else {
                showDialog = true
            }
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

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Enter Username") },
            text = {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val name = username.trim().ifBlank { "Player" }
                    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .edit().putString(KEY_PLAYER_NAME, name).apply()
                    showDialog = false
                    onStartGame(name)
                }) { Text("Play") }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text("Cancel") }
            }
        )
    }
}
