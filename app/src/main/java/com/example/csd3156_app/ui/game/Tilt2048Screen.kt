package com.example.csd3156_app.ui.game

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.csd3156_app.game.TileMovement
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
import androidx.compose.ui.zIndex

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
    // For vibration
    val haptic = LocalHapticFeedback.current

    var dragDelta by remember { mutableStateOf(Offset.Zero) }
    var showGameOverDialog by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    var showTutorial by remember {
        val prefs = context.getSharedPreferences("tilt2048_prefs", android.content.Context.MODE_PRIVATE)
        mutableStateOf(!prefs.getBoolean("tutorial_seen", false))
    }

    LaunchedEffect(uiState.isGameOver) {
        if (uiState.isGameOver) {
            showGameOverDialog = true
        }
    }

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
                Button(
                    onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onBackToMenu()
                }
                ) { Text("Menu") }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Score: ${uiState.score}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Best: ${maxOf(uiState.highestScore, uiState.score)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF6D4C41)
                    )
                }
                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onNewGame()
                    }
                ) { Text("Reset") }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // MODE SELECTOR
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onModeSelected(GameMode.CLASSIC)
                    },
                    colors = modeButtonColors(uiState.mode == GameMode.CLASSIC),
                    modifier = Modifier.weight(1f)
                ) { Text("Classic") }
                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onModeSelected(GameMode.DAILY)
                    },
                    colors = modeButtonColors(uiState.mode == GameMode.DAILY),
                    modifier = Modifier.weight(1f)
                ) { Text("Daily") }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // THE BOARD
            AnimatedGameBoard(
                board = uiState.board,
                tileMovements = uiState.tileMovements,
                mergedIndices = uiState.mergedIndices,
                moveKey = uiState.moveKey,
                gridSize = uiState.gridSize,
                tiltDirection = uiState.tiltDebugDirection,
                tiltMagnitude = uiState.tiltDebugMagnitude,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
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
            )

            if (uiState.mode == GameMode.DAILY) {
                Spacer(modifier = Modifier.height(24.dp))
                DailyLeaderboardSection(uiState, onSubmitDailyScore, onRefreshLeaderboard)
            }

            // DEBUG TILT INFO

//            if (uiState.tiltSensorAvailable) {
//                Column(
//                    modifier = Modifier
//                        .fillMaxWidth()
//                        .background(Color(0x22000000), RoundedCornerShape(8.dp))
//                        .padding(8.dp)
//                ) {
//                    Text(
//                        text = "Sensor: ${uiState.tiltSensorLabel}",
//                        fontSize = 12.sp,
//                        color = Color.Gray
//                    )
//                    Text(
//                        text = "Direction: ${uiState.tiltDebugDirection}",
//                        fontSize = 12.sp,
//                        color = Color.Gray
//                    )
//                    Text(
//                        text = "Magnitude: ${"%.2f".format(uiState.tiltDebugMagnitude)}",
//                        fontSize = 12.sp,
//                        color = Color.Gray
//                    )
//                    Text(
//                        text = "Tilt enabled: ${uiState.tiltControlsEnabled}",
//                        fontSize = 12.sp,
//                        color = if (uiState.tiltControlsEnabled) Color(0xFF2E7D32) else Color(0xFFB71C1C)
//                    )
//                }
//            }
        }

        // Game result overlay — shown on top of the board
        AnimatedVisibility(
            visible = uiState.hasWon || uiState.isGameOver,
            enter = fadeIn(tween(400)) + scaleIn(tween(400), initialScale = 0.85f)
        ) {
            GameResultOverlay(
                hasWon = uiState.hasWon,
                score = uiState.score,
                shakeEnabled = uiState.shakeToResetEnabled,
                onNewGame = onNewGame,
                onBackToMenu = onBackToMenu
            )
        }

        if (showTutorial) {
            TiltTutorialOverlay(
                onDismiss = {
                    showTutorial = false
                    context.getSharedPreferences("tilt2048_prefs", android.content.Context.MODE_PRIVATE)
                        .edit()
                        .putBoolean("tutorial_seen", true)
                        .apply()
                }
            )
        }
    }
}

/**
 * Full-screen overlay displayed when the player wins or loses.
 */
@Composable
fun GameResultOverlay(
    hasWon: Boolean,
    score: Int,
    shakeEnabled: Boolean,
    onNewGame: () -> Unit,
    onBackToMenu: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val overlayBg = if (hasWon) Color(0xE8F9F1E8) else Color(0xE8201A18)
    val titleColor = if (hasWon) Color(0xFF776E65) else Color(0xFFEEE4DA)
    val subtitleColor = if (hasWon) Color(0xFF8B6914) else Color(0xFFBBB)
    val btnContainerColor = if (hasWon) Color(0xFFEDC22E) else Color(0xFFF65E3B)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(overlayBg),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Text(
                text = if (hasWon) "YOU WIN!" else "GAME OVER",
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                color = titleColor
            )

            if (hasWon) {
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .background(Color(0xFFEDC22E), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "2048",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            Text(
                text = "Score: $score",
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                color = subtitleColor
            )

            if (!hasWon) {
                Text(
                    text = "No more moves available",
                    fontSize = 14.sp,
                    color = subtitleColor
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onNewGame()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = btnContainerColor,
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .height(52.dp)
            ) {
                Text(
                    text = if (hasWon) "Play Again" else "Try Again",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            OutlinedButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onBackToMenu()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = btnContainerColor,
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .height(52.dp)
            ) {
                Text(
                    text = "Main Menu",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            if (shakeEnabled && !hasWon) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Shake phone to restart",
                        fontSize = 14.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Medium
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

fun tileColor(value: Int): Color = when (value) {
    0    -> Color(0xFFCDC1B4)
    2    -> Color(0xFFEEE4DA)
    4    -> Color(0xFFEDE0C8)
    8    -> Color(0xFFF2B179)
    16   -> Color(0xFFF59563)
    32   -> Color(0xFFF67C5F)
    64   -> Color(0xFFF65E3B)
    128  -> Color(0xFFEDCF72)
    256  -> Color(0xFFEDCC61)
    512  -> Color(0xFFEDC850)
    1024 -> Color(0xFFEDC53F)
    2048 -> Color(0xFFEDC22E)
    else -> Color(0xFF3C3A32)
}

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun AnimatedGameBoard(
    board: List<Int>,
    tileMovements: List<TileMovement>,
    mergedIndices: Set<Int>,
    moveKey: Int,
    gridSize: Int,
    tiltDirection: String,
    tiltMagnitude: Float,
    modifier: Modifier = Modifier
) {
    // ROOT CONTAINER
    BoxWithConstraints(
        modifier = modifier
    ) {
        val boardSizeDp = maxWidth
        val padding = 8.dp
        val gap = 8.dp

        // Exact tile size calculation: Total width minus padding on both sides,
        // minus all gaps, divided by the number of tiles per row.
        val tileSize = (boardSizeDp - (padding * 2) - gap * (gridSize - 1).toFloat()) / gridSize.toFloat()

        // SMOOTH ANIMATED SHADOW LAYER
        // Calculate target offsets
        val targetOffsetX = when {
            tiltDirection.contains("left", ignoreCase = true) -> -20.dp
            tiltDirection.contains("right", ignoreCase = true) -> 20.dp
            else -> 0.dp
        }
        val targetOffsetY = when {
            tiltDirection.contains("up", ignoreCase = true) -> -20.dp
            tiltDirection.contains("down", ignoreCase = true) -> 20.dp
            else -> 0.dp
        }

        // Animate the offset for smooth gliding
        val animatedOffsetX by androidx.compose.animation.core.animateDpAsState(
            targetValue = targetOffsetX * tiltMagnitude.coerceAtMost(1.5f),
            animationSpec = androidx.compose.animation.core.spring(stiffness = androidx.compose.animation.core.Spring.StiffnessLow)
        )
        val animatedOffsetY by androidx.compose.animation.core.animateDpAsState(
            targetValue = targetOffsetY * tiltMagnitude.coerceAtMost(1.5f),
            animationSpec = androidx.compose.animation.core.spring(stiffness = androidx.compose.animation.core.Spring.StiffnessLow)
        )

        val shadowAlpha = (tiltMagnitude * 0.4f).coerceIn(0f, 0.3f)

        Box(
            modifier = Modifier
                .size(boardSizeDp)
                .offset { IntOffset(animatedOffsetX.roundToPx(), animatedOffsetY.roundToPx()) }
                .background(
                    Color.Black.copy(alpha = shadowAlpha),
                    RoundedCornerShape(12.dp)
                )
        )


        // MAIN BOARD BACKGROUND
        Box(
            modifier = Modifier
                .size(boardSizeDp)
                .background(Color(0xFFBBADA0), RoundedCornerShape(12.dp))
        )

        // TILES CONTAINER
        Box(
            modifier = Modifier
                .size(boardSizeDp)
                .padding(padding) // Sets coordinate (0,0) inside the board bounds
        ) {
            fun indexXY(index: Int): Pair<Dp, Dp> {
                val row = index / gridSize
                val col = index % gridSize
                val step = tileSize + gap
                return (step * col.toFloat()) to (step * row.toFloat())
            }

            // Empty cell backgrounds
            for (i in 0 until gridSize * gridSize) {
                val (x, y) = indexXY(i)
                Box(
                    Modifier
                        .size(tileSize)
                        .offset(x = x, y = y)
                        .background(Color(0xFFCDC1B4), RoundedCornerShape(4.dp))
                )
            }

            if (tileMovements.isEmpty()) {
                // Static tiles
                for (i in board.indices) {
                    val v = board[i]
                    if (v == 0) continue
                    val (x, y) = indexXY(i)
                    Box(
                        modifier = Modifier
                            .size(tileSize)
                            .offset(x = x, y = y)
                            .background(tileColor(v), RoundedCornerShape(4.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = v.toString(),
                            fontWeight = FontWeight.Bold,
                            fontSize = when { v < 100 -> 24.sp; v < 1000 -> 18.sp; else -> 14.sp },
                            color = if (v <= 4) Color(0xFF776E65) else Color.White
                        )
                    }
                }
            } else {
                // Animated tiles
                val destIndices = tileMovements.map { it.toIndex }.toSet()

                tileMovements.forEach { movement ->
                    val v = board[movement.toIndex]
                    val (fromX, fromY) = indexXY(movement.fromIndex)
                    val (toX, toY) = indexXY(movement.toIndex)
                    key(moveKey, movement.fromIndex, movement.toIndex) {
                        SlidingTile(
                            value = v,
                            fromX = fromX, fromY = fromY,
                            toX = toX, toY = toY,
                            size = tileSize,
                            isMerged = movement.toIndex in mergedIndices,
                            isNew = false
                        )
                    }
                }

                // New tiles
                for (i in board.indices) {
                    if (board[i] != 0 && i !in destIndices) {
                        val (x, y) = indexXY(i)
                        key(moveKey, i) {
                            SlidingTile(
                                value = board[i],
                                fromX = x, fromY = y,
                                toX = x, toY = y,
                                size = tileSize,
                                isMerged = false,
                                isNew = true
                            )
                        }
                    }
                }
            }
        }
    }
}


@Composable
fun SlidingTile(
    value: Int,
    fromX: Dp, fromY: Dp,
    toX: Dp, toY: Dp,
    size: Dp,
    isMerged: Boolean,
    isNew: Boolean
) {
    val x = remember { Animatable(fromX.value) }
    val y = remember { Animatable(fromY.value) }
    val scale = remember { Animatable(if (isNew) 0f else 1f) }

    LaunchedEffect(Unit) {
        launch { x.animateTo(toX.value, tween(durationMillis = 120)) }
        launch { y.animateTo(toY.value, tween(durationMillis = 120)) }
        when {
            isNew -> launch {
                scale.animateTo(
                    1f,
                    spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh)
                )
            }
            isMerged -> launch {
                delay(120)
                scale.animateTo(1.18f, tween(60))
                scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
            }
        }
    }

    Box(
        modifier = Modifier
            .size(size)
            .offset { IntOffset(x.value.dp.roundToPx(), y.value.dp.roundToPx()) }
            .graphicsLayer { scaleX = scale.value; scaleY = scale.value }
            .background(tileColor(value), RoundedCornerShape(4.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = value.toString(),
            fontWeight = FontWeight.Bold,
            fontSize = when { value < 100 -> 24.sp; value < 1000 -> 18.sp; else -> 14.sp },
            color = if (value <= 4) Color(0xFF776E65) else Color.White
        )
    }
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
    var showModal by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (uiState.networkMessage.isNotBlank()) {
            Text(
                text = uiState.networkMessage,
                fontSize = 12.sp,
                color = Color.Gray,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
//            Button(
//                onClick = {
//                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
//                    onSubmit()
//                },
//                enabled = !uiState.isSubmittingDailyScore,
//                modifier = Modifier.weight(1f)
//            ) {
//                Text(if (uiState.isSubmittingDailyScore) "Submitting..." else "Submit Score")
//            }
            Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onRefresh()
                    showModal = true
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Leaderboard")
            }
        }
    }

    if (showModal) {
        AlertDialog(
            onDismissRequest = { showModal = false },
            title = {
                Text("Daily Leaderboard", fontWeight = FontWeight.Bold)
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = uiState.dailyDate,
                        fontSize = 12.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    if (uiState.leaderboardLoading) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                    } else if (uiState.leaderboard.isEmpty()) {
                        Text(
                            text = "No scores yet for today.",
                            color = Color.Gray,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    } else {
                        uiState.leaderboard.forEachIndexed { index, entry ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "#${index + 1}  ${entry.playerName}",
                                    fontWeight = if (index == 0) FontWeight.Bold else FontWeight.Normal
                                )
                                Text(
                                    text = entry.score.toString(),
                                    fontWeight = if (index == 0) FontWeight.Bold else FontWeight.Normal,
                                    color = if (index == 0) Color(0xFFEDC22E) else Color.Unspecified
                                )
                            }
                            if (index < uiState.leaderboard.lastIndex) {
                                HorizontalDivider()
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { onRefresh() }) { Text("Refresh") }
            },
            dismissButton = {
                TextButton(onClick = { showModal = false }) { Text("Close") }
            }
        )
    }
}

@Composable
fun TiltTutorialOverlay(onDismiss: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val infiniteTransition = rememberInfiniteTransition(label = "tilt_hint")
    val rockAngle by infiniteTransition.animateFloat(
        initialValue = -15f,
        targetValue = 15f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = androidx.compose.animation.core.EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "rock"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xCC000000)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF9F1E8))
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "How to Play",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF776E65)
                )

                // Animated rocking phone icon
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .graphicsLayer { rotationZ = rockAngle },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(44.dp)
                            .height(80.dp)
                            .background(Color(0xFF776E65), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .width(36.dp)
                                .height(64.dp)
                                .background(Color(0xFFBBADA0), RoundedCornerShape(4.dp))
                        )
                    }
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.Start,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TutorialRow(emoji = "📱", text = "Hold your phone flat, face up")
                    TutorialRow(emoji = "⬅️", text = "Tilt left to move tiles left")
                    TutorialRow(emoji = "➡️", text = "Tilt right to move tiles right")
                    TutorialRow(emoji = "⬆️", text = "Tilt away to move tiles up")
                    TutorialRow(emoji = "⬇️", text = "Tilt toward you to move tiles down")
                    TutorialRow(emoji = "👆", text = "Or swipe on the board to move tiles")
                }

                Spacer(modifier = Modifier.height(4.dp))

                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFEDC22E),
                        contentColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                ) {
                    Text("Got it, let's play!", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun TutorialRow(emoji: String, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(text = emoji, fontSize = 18.sp)
        Text(text = text, fontSize = 14.sp, color = Color(0xFF776E65))
    }
}

