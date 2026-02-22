package com.example.csd3156_app.ui.game

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.csd3156_app.game.Direction
import com.example.csd3156_app.game.GameMode
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.abs
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType

@Composable
fun Tilt2048Route(
    viewModel: Tilt2048ViewModel = viewModel(),
    onBackToMenu: () -> Unit
) {
    // For vibration
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(Unit) {
        viewModel.mergeHapticEvents.collectLatest {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val tiltSensorDataSource = remember(context) {
        TiltSensorDataSource(context.applicationContext)
    }
    val uiState by viewModel.uiState.collectAsState()

    // Initialize sensors
    LaunchedEffect(tiltSensorDataSource) {
        viewModel.onTiltSensorStatus(
            isAvailable = tiltSensorDataSource.isAvailable,
            label = tiltSensorDataSource.sensorLabel
        )
    }

    // Collect sensor data
    LaunchedEffect(tiltSensorDataSource, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            tiltSensorDataSource.samples().collectLatest { sample ->
                viewModel.onTiltSample(sample)
            }
        }
    }

    // Handle backgrounding
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                viewModel.onAppBackgrounded()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Tilt2048Screen(
        uiState = uiState,
        onSwipe = viewModel::onSwipe,
        onNewGame = viewModel::onNewGame,
        onModeSelected = viewModel::onModeSelected,
        onBackToMenu = onBackToMenu,
        onRefreshLeaderboard = viewModel::onRefreshLeaderboard,
        onSubmitDailyScore = viewModel::onSubmitDailyScore
    )
}

@Composable
fun Tilt2048Screen(
    uiState: Tilt2048UiState,
    onSwipe: (Direction) -> Unit,
    onNewGame: () -> Unit,
    onModeSelected: (GameMode) -> Unit,
    onBackToMenu: () -> Unit,
    onRefreshLeaderboard: () -> Unit,
    onSubmitDailyScore: () -> Unit
) {
    var dragDelta by remember { mutableStateOf(Offset.Zero) }
    val scrollState = rememberScrollState()

    // Centering Container
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center // This centers the Column inside the Box
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(20.dp),
            verticalArrangement = Arrangement.Center, // Centers items vertically if screen is taller than content
            horizontalAlignment = Alignment.CenterHorizontally // Centers items horizontally
        ) {
            // TOP BAR
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(onClick = onBackToMenu) { Text("Menu") }
                Text(
                    text = "Score: ${uiState.score}",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Button(onClick = onNewGame) { Text("Reset") }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // MODE SELECTOR
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { onModeSelected(GameMode.CLASSIC) },
                    colors = modeButtonColors(uiState.mode == GameMode.CLASSIC),
                    modifier = Modifier.weight(1f)
                ) { Text("Classic") }
                Button(
                    onClick = { onModeSelected(GameMode.DAILY) },
                    colors = modeButtonColors(uiState.mode == GameMode.DAILY),
                    modifier = Modifier.weight(1f)
                ) { Text("Daily") }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // GAME MESSAGES
            if (uiState.hasWon || uiState.isGameOver) {
                Text(
                    text = if (uiState.hasWon) "Victory! 2048!" else "Game Over — shake to reset!",
                    style = MaterialTheme.typography.headlineSmall,
                    color = if (uiState.hasWon) Color(0xFF2E7D32) else Color(0xFFB71C1C),
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }

            // THE BOARD
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .background(color = Color(0xFFBBADA0), shape = RoundedCornerShape(12.dp))
                    .pointerInput(uiState.board) {
                        detectDragGestures(
                            onDrag = { change, dragAmount ->
                                change.consume()
                                dragDelta += dragAmount
                            },
                            onDragEnd = {
                                val (dx, dy) = dragDelta
                                if (abs(dx) > abs(dy)) {
                                    if (abs(dx) > 50) onSwipe(if (dx > 0) Direction.RIGHT else Direction.LEFT)
                                } else {
                                    if (abs(dy) > 50) onSwipe(if (dy > 0) Direction.DOWN else Direction.UP)
                                }
                                dragDelta = Offset.Zero
                            }
                        )
                    }
                    .padding(8.dp)
            ) {
                val size = uiState.gridSize
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (r in 0 until size) {
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            for (c in 0 until size) {
                                val value = uiState.board[r * size + c]
                                TileCell(value = value, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            if (uiState.mode == GameMode.DAILY) {
                Spacer(modifier = Modifier.height(24.dp))
                DailyLeaderboardSection(uiState, onSubmitDailyScore, onRefreshLeaderboard)
            }

            // DEBUG TILT INFO - remove when controls feel good
            if (uiState.tiltSensorAvailable) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0x22000000), RoundedCornerShape(8.dp))
                        .padding(8.dp)
                ) {
                    Text(
                        text = "Sensor: ${uiState.tiltSensorLabel}",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                    Text(
                        text = "Direction: ${uiState.tiltDebugDirection}",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                    Text(
                        text = "Magnitude: ${"%.2f".format(uiState.tiltDebugMagnitude)}",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                    Text(
                        text = "Tilt enabled: ${uiState.tiltControlsEnabled}",
                        fontSize = 12.sp,
                        color = if (uiState.tiltControlsEnabled) Color(0xFF2E7D32) else Color(0xFFB71C1C)
                    )
                }
            }
        }
    }
}
/**
 * Fix: Helper function to define button colors based on selection
 */
@Composable
fun modeButtonColors(isSelected: Boolean): ButtonColors {
    return ButtonDefaults.buttonColors(
        containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
fun TileCell(value: Int, modifier: Modifier = Modifier) {
    val backgroundColor = when (value) {
        0 -> Color(0xFFCDC1B4)
        2 -> Color(0xFFEEE4DA)
        4 -> Color(0xFFEDE0C8)
        8 -> Color(0xFFF2B179)
        16 -> Color(0xFFF59563)
        32 -> Color(0xFFF67C5F)
        64 -> Color(0xFFF65E3B)
        128 -> Color(0xFFEDCF72)
        256 -> Color(0xFFEDCC61)
        512 -> Color(0xFFEDC850)
        1024 -> Color(0xFFEDC53F)
        2048 -> Color(0xFFEDC22E)
        else -> Color(0xFF3C3A32)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor, RoundedCornerShape(4.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (value > 0) {
            Text(
                text = value.toString(),
                fontWeight = FontWeight.Bold,
                fontSize = if (value < 100) 24.sp else 18.sp,
                color = if (value <= 4) Color(0xFF776E65) else Color.White
            )
        }
    }
}

@Composable
fun DailyLeaderboardSection(uiState: Tilt2048UiState, onSubmit: () -> Unit, onRefresh: () -> Unit) {
    // Implement your leaderboard UI here
    Column(modifier = Modifier.fillMaxWidth()) {
        Button(onClick = onSubmit, modifier = Modifier.fillMaxWidth()) { Text("Submit Daily Score") }
        Button(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) { Text("Refresh Leaderboard") }
    }
}