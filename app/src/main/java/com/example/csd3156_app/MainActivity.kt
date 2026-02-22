package com.example.csd3156_app

import com.example.csd3156_app.ui.game.MenuScreen
import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.csd3156_app.ui.game.SettingsScreen
import com.example.csd3156_app.ui.theme.AppTheme
import com.example.csd3156_app.ui.game.Tilt2048Route
import com.example.csd3156_app.ui.game.Tilt2048ViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        enableEdgeToEdge()
        setContent {
            AppTheme {
                val navController = rememberNavController()
                val viewModel: Tilt2048ViewModel = viewModel()
                val uiState by viewModel.uiState.collectAsState()

                NavHost(navController = navController, startDestination = "menu") {
                    composable("menu") {
                        MenuScreen(
                            onStartGame = { username ->
                                viewModel.setPlayerName(username)
                                navController.navigate("game")
                            },
                            onSettings = { navController.navigate("settings") }
                        )
                    }
                    composable("game") {
                        Tilt2048Route(
                            viewModel = viewModel,
                            onBackToMenu = { navController.popBackStack() }
                        )
                    }
                    composable("settings") {
                        SettingsScreen(
                            uiState = uiState,
                            onTiltControlsEnabledChanged = viewModel::onTiltControlsEnabledChanged,
                            onTiltSensitivityChanged = viewModel::onTiltSensitivityChanged,
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}
