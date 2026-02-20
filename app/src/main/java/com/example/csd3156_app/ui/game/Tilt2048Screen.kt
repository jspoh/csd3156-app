package com.example.csd3156_app.ui.game

import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.csd3156_app.game.Direction
import com.example.csd3156_app.game.GameMode
import java.util.Locale
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.abs

@Composable
fun Tilt2048Route(
    viewModel: Tilt2048ViewModel = viewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val tiltSensorDataSource = remember(context) {
        TiltSensorDataSource(context.applicationContext)
    }
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(tiltSensorDataSource) {
        viewModel.onTiltSensorStatus(
            isAvailable = tiltSensorDataSource.isAvailable,
            label = tiltSensorDataSource.sensorLabel
        )
    }

    LaunchedEffect(tiltSensorDataSource, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            tiltSensorDataSource.samples().collectLatest { sample ->
                viewModel.onTiltSample(sample)
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                viewModel.onAppBackgrounded()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Tilt2048Screen(
        uiState = uiState,
        onSwipe = viewModel::onSwipe,
        onNewGame = viewModel::onNewGame,
        onModeSelected = viewModel::onModeSelected,
        onTiltControlsEnabledChanged = viewModel::onTiltControlsEnabledChanged,
        onTiltSensitivityChanged = viewModel::onTiltSensitivityChanged,
        onCalibrateTilt = viewModel::onCalibrateTilt,
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
    onTiltControlsEnabledChanged: (Boolean) -> Unit,
    onTiltSensitivityChanged: (Float) -> Unit,
    onCalibrateTilt: () -> Unit,
    onRefreshLeaderboard: () -> Unit,
    onSubmitDailyScore: () -> Unit
) {
    var dragDelta by remember { mutableStateOf(Offset.Zero) }
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Tilt2048",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Button(onClick = onNewGame) {
                Text("New Game")
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { onModeSelected(GameMode.CLASSIC) },
                colors = modeButtonColors(uiState.mode == GameMode.CLASSIC)
            ) {
                Text("Classic")
            }
            Button(
                onClick = { onModeSelected(GameMode.DAILY) },
                colors = modeButtonColors(uiState.mode == GameMode.DAILY)
            ) {
                Text("Daily")
            }
        }

        Text(
            text = "Score: ${uiState.score}",
            style = MaterialTheme.typography.titleMedium
        )

        if (uiState.mode == GameMode.DAILY) {
            Text(
                text = "Daily: ${uiState.dailyDate}  Seed: ${uiState.dailySeed ?: "-"}",
                style = MaterialTheme.typography.bodySmall
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Tilt Controls",
                style = MaterialTheme.typography.bodyLarge
            )
            Switch(
                checked = uiState.tiltControlsEnabled,
                onCheckedChange = onTiltControlsEnabledChanged,
                enabled = uiState.tiltSensorAvailable
            )
        }

        Text(
            text = "Sensitivity: ${
                String.format(Locale.US, "%.2f", uiState.tiltSensitivity)
            }",
            style = MaterialTheme.typography.bodyMedium
        )
        Slider(
            value = uiState.tiltSensitivity,
            onValueChange = onTiltSensitivityChanged,
            valueRange = Tilt2048ViewModel.MIN_SENSITIVITY..Tilt2048ViewModel.MAX_SENSITIVITY,
            enabled = uiState.tiltControlsEnabled && uiState.tiltSensorAvailable
        )

        Button(
            onClick = onCalibrateTilt,
            enabled = uiState.tiltControlsEnabled && uiState.tiltSensorAvailable
        ) {
            Text("Calibrate")
        }

        if (SHOW_TILT_DEBUG) {
            Text(
                text = "Tilt: ${uiState.tiltDebugDirection} " +
                    "(${String.format(Locale.US, "%.2f", uiState.tiltDebugMagnitude)}) " +
                    "- ${uiState.tiltSensorLabel}",
                style = MaterialTheme.typography.bodySmall
            )
        }

        if (uiState.networkMessage.isNotEmpty()) {
            Text(
                text = uiState.networkMessage,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF455A64)
            )
        }

        if (uiState.hasWon) {
            Text(
                text = "You reached 2048!",
                style = MaterialTheme.typography.bodyLarge,
                color = Color(0xFF2E7D32)
            )
        } else if (uiState.isGameOver) {
            Text(
                text = "Game Over",
                style = MaterialTheme.typography.bodyLarge,
                color = Color(0xFFB71C1C)
            )
        } else {
            Spacer(modifier = Modifier.height(0.dp))
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .background(
                    color = Color(0xFFBBADA0),
                    shape = RoundedCornerShape(16.dp)
                )
                .pointerInput(uiState.board) {
                    detectDragGestures(
                        onDragStart = { dragDelta = Offset.Zero },
                        onDragCancel = { dragDelta = Offset.Zero },
                        onDragEnd = {
                            val absX = abs(dragDelta.x)
                            val absY = abs(dragDelta.y)
                            if (maxOf(absX, absY) < SWIPE_THRESHOLD_PX) {
                                dragDelta = Offset.Zero
                                return@detectDragGestures
                            }
                            val direction = if (absX > absY) {
                                if (dragDelta.x > 0f) Direction.RIGHT else Direction.LEFT
                            } else {
                                if (dragDelta.y > 0f) Direction.DOWN else Direction.UP
                            }
                            onSwipe(direction)
                            dragDelta = Offset.Zero
                        },
                        onDrag = { change, amount ->
                            change.consume()
                            dragDelta += amount
                        }
                    )
                }
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (row in 0 until BOARD_SIZE) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for (column in 0 until BOARD_SIZE) {
                            val index = row * BOARD_SIZE + column
                            val value = uiState.board[index]
                            TileCell(
                                value = value,
                                isMerged = uiState.mergedIndices.contains(index),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        if (uiState.mode == GameMode.DAILY) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onSubmitDailyScore,
                    enabled = uiState.isGameOver && !uiState.isSubmittingDailyScore
                ) {
                    Text(if (uiState.isSubmittingDailyScore) "Submitting..." else "Submit Daily Score")
                }
                Button(
                    onClick = onRefreshLeaderboard,
                    enabled = !uiState.leaderboardLoading
                ) {
                    Text(if (uiState.leaderboardLoading) "Refreshing..." else "Refresh Leaderboard")
                }
            }

            Text(
                text = "Today's Leaderboard",
                style = MaterialTheme.typography.titleMedium
            )
            if (uiState.leaderboard.isEmpty()) {
                Text(
                    text = "No entries yet.",
                    style = MaterialTheme.typography.bodySmall
                )
            } else {
                uiState.leaderboard.forEachIndexed { index, entry ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("${index + 1}. ${entry.playerName}")
                        Text("${entry.score}")
                    }
                }
            }
        }
    }
}

@Composable
private fun TileCell(
    value: Int,
    isMerged: Boolean,
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(
        targetValue = if (isMerged) 1.12f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 500f),
        label = "tileScale"
    )

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .background(
                color = tileBackgroundColor(value),
                shape = RoundedCornerShape(10.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        if (value != 0) {
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.titleLarge,
                color = tileTextColor(value),
                fontWeight = FontWeight.Bold
            )
        }
    }
}

private fun tileBackgroundColor(value: Int): Color {
    return when (value) {
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
        else -> Color(0xFFEDC22E)
    }
}

private fun tileTextColor(value: Int): Color {
    return if (value <= 4) Color(0xFF776E65) else Color.White
}

@Composable
private fun modeButtonColors(isActive: Boolean) = ButtonDefaults.buttonColors(
    containerColor = if (isActive) Color(0xFF6D4C41) else Color(0xFFBCAAA4),
    contentColor = Color.White
)

private const val BOARD_SIZE = 4
private const val SWIPE_THRESHOLD_PX = 40f
private const val SHOW_TILT_DEBUG = true
