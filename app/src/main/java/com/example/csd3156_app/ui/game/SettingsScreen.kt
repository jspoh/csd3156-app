package com.example.csd3156_app.ui.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(
    uiState: Tilt2048UiState,
    onTiltControlsEnabledChanged: (Boolean) -> Unit,
    onTiltSensitivityChanged: (Float) -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Tilt Controls", modifier = Modifier.weight(1f))
            Switch(
                checked = uiState.tiltControlsEnabled,
                onCheckedChange = onTiltControlsEnabledChanged
            )
        }

        Text("Sensitivity: ${String.format("%.2f", uiState.tiltSensitivity)}")
        Slider(
            value = uiState.tiltSensitivity,
            onValueChange = onTiltSensitivityChanged,
            valueRange = 0.5f..5.0f
        )

        Spacer(modifier = Modifier.weight(1f))
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("Back to Menu")
        }
    }
}