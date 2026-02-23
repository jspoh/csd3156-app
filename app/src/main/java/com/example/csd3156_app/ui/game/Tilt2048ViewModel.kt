package com.example.csd3156_app.ui.game

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.csd3156_app.BuildConfig
import com.example.csd3156_app.data.DailyScoreUpload
import com.example.csd3156_app.data.GameRepository
import com.example.csd3156_app.data.PersistedSettings
import com.example.csd3156_app.data.RoomGameRepository
import com.example.csd3156_app.data.local.Tilt2048Database
import com.example.csd3156_app.data.network.RestDailyLeaderboardApi
import com.example.csd3156_app.game.Direction
import com.example.csd3156_app.game.GameEngine
import com.example.csd3156_app.game.GameMode
import com.example.csd3156_app.game.GameState
import com.example.csd3156_app.game.RandomTileSpawner
import com.example.csd3156_app.game.TileMovement
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.random.Random

class Tilt2048ViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    private val _mergeHapticEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val mergeHapticEvents = _mergeHapticEvents.asSharedFlow()

    private val gameEngine = GameEngine()
    private val repository: GameRepository = RoomGameRepository(
        database = Tilt2048Database.create(application.applicationContext),
        dailyApi = createDailyApi()
    )

    private val _uiState = MutableStateFlow(Tilt2048UiState())
    val uiState: StateFlow<Tilt2048UiState> = _uiState.asStateFlow()

    private var clearMergeJob: Job? = null
    private var autoSaveJob: Job? = null

    // Tilt Sensors
    private var neutralX: Float = 0f
    private var neutralY: Float = 0f
    private var latestX: Float = 0f
    private var latestY: Float = 0f
    private var waitingForNeutral: Boolean = false
    private var lastTiltDirection: Direction? = null
    private var lastMoveAtMillis: Long = 0L

    // Shake Sensor (LESS SENSITIVE: jerk-based on x/y changes)
    private var lastShakeTime: Long = 0L
    private val shakeHistory = ArrayDeque<Float>()
    private var lastShakeMagnitude: Float? = null

    private var currentSessionId: Long? = savedStateHandle.get(KEY_SESSION_ID)
    private var isCurrentSessionFinished: Boolean = false

    private var currentMode: GameMode = savedStateHandle.get<String>(KEY_MODE)
        ?.let { mode -> runCatching { GameMode.valueOf(mode) }.getOrDefault(GameMode.CLASSIC) }
        ?: GameMode.CLASSIC

    private var currentDailyDate: String? = savedStateHandle.get(KEY_DAILY_DATE)
    private var currentDailySeed: Long? = savedStateHandle.get(KEY_DAILY_SEED)
    private var dailyUploadHandledForSession: Boolean = false
    private var playerName: String = DEFAULT_PLAYER_NAME

    init {
        val restored = restoreFromSavedState()
        if (restored != null) {
            publishState(
                gameState = restored,
                mode = currentMode,
                dailyDate = currentDailyDate,
                dailySeed = currentDailySeed
            )
        }

        viewModelScope.launch {
            hydrateFromDatabase(hasSavedState = restored != null)
        }
        startAutoSaveLoop()
    }

    fun setPlayerName(name: String) {
        playerName = name.trim().ifBlank { DEFAULT_PLAYER_NAME }
    }

    fun onSwipe(direction: Direction) {
        applyMove(direction)
    }

    fun onNewGame() {
        viewModelScope.launch {
            startGame(currentMode)
        }
    }

    fun onModeSelected(mode: GameMode) {
        viewModelScope.launch {
            startGame(mode)
        }
    }

    fun onRefreshLeaderboard() {
        viewModelScope.launch {
            refreshLeaderboard(todayUtcDate())
        }
    }

    fun onSubmitDailyScore() {
        viewModelScope.launch {
            submitDailyScoreIfNeeded(force = true)
        }
    }

    fun onAppBackgrounded() {
        viewModelScope.launch {
            persistCurrentSession()
            repository.retryPendingDailyUploads()
        }
    }

    fun onTiltControlsEnabledChanged(enabled: Boolean) {
        _uiState.update { current ->
            current.copy(tiltControlsEnabled = enabled)
        }
        persistSettings()
    }

    fun onShakeToResetEnabledChanged(enabled: Boolean) {
        _uiState.update { current ->
            current.copy(shakeToResetEnabled = enabled)
        }
        persistSettings()
    }

    fun onTiltSensitivityChanged(sensitivity: Float) {
        val normalized = sensitivity.coerceIn(MIN_SENSITIVITY, MAX_SENSITIVITY)
        _uiState.update { current ->
            current.copy(tiltSensitivity = normalized)
        }
        persistSettings()
    }

    fun onCalibrateTilt() {
        neutralX = latestX
        neutralY = latestY
        waitingForNeutral = false
        lastTiltDirection = null
        persistSettings()
    }

    fun onTiltSensorStatus(isAvailable: Boolean, label: String) {
        _uiState.update { current ->
            current.copy(
                tiltSensorAvailable = isAvailable,
                tiltSensorLabel = label
            )
        }
    }

    fun onTiltSample(sample: TiltSample) {
        latestX = sample.x
        latestY = sample.y

        if (_uiState.value.shakeToResetEnabled) {
            detectShake(sample)
        }

        val currentState = _uiState.value
        val deltaX = sample.x - neutralX
        val deltaY = sample.y - neutralY
        val magnitude = hypot(deltaX.toDouble(), deltaY.toDouble()).toFloat()

        _uiState.update { state ->
            state.copy(
                tiltDebugDirection = debugDirectionFor(deltaX, deltaY),
                tiltDebugMagnitude = magnitude
            )
        }

        if (!currentState.tiltControlsEnabled || !currentState.tiltSensorAvailable) {
            return
        }

        val deadZone = DEAD_ZONE_BASE / currentState.tiltSensitivity
        val neutralResetZone = deadZone * NEUTRAL_RESET_MULTIPLIER

        if (magnitude <= neutralResetZone) {
            waitingForNeutral = false
        }

        if (magnitude < deadZone) {
            return
        }

        val direction = primaryDirection(deltaX, deltaY)

        // same direction as last move: require neutral reset before firing again
        if (direction == lastTiltDirection) {
            if (waitingForNeutral) return
        }
        // direction changed: allow immediately (skip neutral + cooldown)

        if (sample.timestampMillis - lastMoveAtMillis < MOVE_COOLDOWN_MS) {
            return
        }

        waitingForNeutral = true
        lastTiltDirection = direction
        lastMoveAtMillis = sample.timestampMillis
        applyMove(direction)
    }

    private suspend fun hydrateFromDatabase(hasSavedState: Boolean) {
        val settings = repository.loadSettings()
        applyPersistedSettings(settings)

        val highestScore = repository.getHighestScore()
        _uiState.update { current ->
            current.copy(highestScore = highestScore)
        }

        repository.retryPendingDailyUploads()

        if (!hasSavedState) {
            val unfinished = repository.getLatestUnfinishedSession()
            if (unfinished != null) {
                currentSessionId = unfinished.id
                savedStateHandle[KEY_SESSION_ID] = unfinished.id
                isCurrentSessionFinished = unfinished.isFinished
                currentMode = unfinished.mode
                currentDailyDate = unfinished.dailyDate
                currentDailySeed = unfinished.dailySeed

                publishState(
                    gameState = gameEngine.restoreState(unfinished.board, unfinished.score),
                    mode = currentMode,
                    dailyDate = currentDailyDate,
                    dailySeed = currentDailySeed
                )
            } else {
                startGame(GameMode.CLASSIC)
            }
        } else if (currentSessionId == null) {
            startSessionForState(gameEngine.getState(), currentMode, currentDailyDate, currentDailySeed)
        }

        val today = todayUtcDate()
        loadDailySeed(today)
        refreshLeaderboard(today)
    }

    private suspend fun startGame(mode: GameMode) {
        val previousSessionId = currentSessionId
        val previousFinished = isCurrentSessionFinished
        val previousScore = gameEngine.getState().score

        currentSessionId = null
        savedStateHandle[KEY_SESSION_ID] = null
        isCurrentSessionFinished = false
        dailyUploadHandledForSession = false

        currentMode = mode
        savedStateHandle[KEY_MODE] = mode.name

        // reset shake tracking between sessions
        shakeHistory.clear()
        lastShakeMagnitude = null
        lastShakeTime = 0L

        val gameState = if (mode == GameMode.DAILY) {
            val date = todayUtcDate()
            val seed = loadDailySeed(date)
            gameEngine.setTileSpawner(RandomTileSpawner(Random(seed)))
            currentDailyDate = date
            currentDailySeed = seed
            savedStateHandle[KEY_DAILY_DATE] = date
            savedStateHandle[KEY_DAILY_SEED] = seed
            gameEngine.startNewGame()
        } else {
            gameEngine.setTileSpawner(RandomTileSpawner())
            currentDailyDate = null
            currentDailySeed = null
            savedStateHandle[KEY_DAILY_DATE] = null
            savedStateHandle[KEY_DAILY_SEED] = null
            gameEngine.startNewGame()
        }

        publishState(
            gameState = gameState,
            mode = currentMode,
            dailyDate = currentDailyDate,
            dailySeed = currentDailySeed
        )

        if (previousSessionId != null && !previousFinished) {
            finishSession(previousSessionId, previousScore, previousMode = _uiState.value.mode)
        }

        startSessionForState(gameState, currentMode, currentDailyDate, currentDailySeed)

        if (currentMode == GameMode.DAILY) {
            refreshLeaderboard(currentDailyDate ?: todayUtcDate())
        }
    }

    private fun applyMove(direction: Direction) {
        // Block all input while the game result overlay is visible
        val currentUiState = _uiState.value
        if (currentUiState.isGameOver || currentUiState.hasWon) return

        val result = gameEngine.move(direction)
        if (!result.moved) {
            return
        }

        // If score updated (merged)
        if (result.scoreGained > 0){
            _mergeHapticEvents.tryEmit(Unit)
        }

        publishState(
            gameState = result.state,
            mergedIndices = result.mergedIndices,
            tileMovements = result.tileMovements,
            mode = currentMode,
            dailyDate = currentDailyDate,
            dailySeed = currentDailySeed
        )

        val gameEnded = result.state.isGameOver || result.state.hasWon

        viewModelScope.launch {
            ensureSessionExists(result.state)
            val sessionId = currentSessionId
            if (sessionId != null) {
                repository.addMoveEvent(sessionId, direction, result.state.score)
                repository.saveSessionSnapshot(
                    sessionId = sessionId,
                    board = result.state.board,
                    score = result.state.score,
                    isFinished = result.state.isGameOver
                )
            }
            if (gameEnded) {
                finishCurrentSessionIfNeeded(result.state.score)
                submitDailyScoreIfNeeded(force = false)
            }
        }
    }

    /**
     * Less-sensitive shake detection using only x/y + timestamp:
     * Detect "jerk" = rapid change in magnitude across samples, then require sustained variability.
     */
    private fun detectShake(sample: TiltSample) {
        // Only allow shake-to-reset after game over (your rule)
        if (!gameEngine.getState().isGameOver) return

        val now = sample.timestampMillis
        if (now - lastShakeTime < SHAKE_COOLDOWN_MS) return

        val mag = hypot(sample.x.toDouble(), sample.y.toDouble()).toFloat()
        val prev = lastShakeMagnitude
        lastShakeMagnitude = mag
        if (prev == null) return

        val jerk = abs(mag - prev)

        shakeHistory.addLast(jerk)
        if (shakeHistory.size > SHAKE_WINDOW_SIZE) shakeHistory.removeFirst()
        if (shakeHistory.size < SHAKE_WINDOW_SIZE) return

        val avg = shakeHistory.average().toFloat()
        val peak = shakeHistory.maxOrNull() ?: 0f
        val min = shakeHistory.minOrNull() ?: 0f
        val range = peak - min

        val passes =
            peak >= SHAKE_JERK_THRESHOLD &&
                    peak >= avg * SHAKE_PEAK_MULTIPLIER &&
                    range >= SHAKE_RANGE_THRESHOLD

        if (passes) {
            lastShakeTime = now
            shakeHistory.clear()
            lastShakeMagnitude = null
            viewModelScope.launch { startGame(currentMode) }
        }
    }

    private suspend fun loadDailySeed(challengeDate: String): Long {
        return try {
            val seed = repository.getOrFetchDailySeed(challengeDate)
            _uiState.update { state ->
                state.copy(dailyDate = challengeDate, dailySeed = seed, networkMessage = "")
            }
            seed
        } catch (error: Exception) {
            val fallbackSeed = fallbackSeedForDate(challengeDate)
            _uiState.update { state ->
                state.copy(
                    dailyDate = challengeDate,
                    dailySeed = fallbackSeed,
                    networkMessage = "Daily seed offline; using local fallback."
                )
            }
            fallbackSeed
        }
    }

    private suspend fun refreshLeaderboard(challengeDate: String) {
        _uiState.update { it.copy(leaderboardLoading = true) }
        try {
            val entries = repository.fetchLeaderboard(challengeDate, LEADERBOARD_LIMIT)
            _uiState.update { state ->
                state.copy(
                    leaderboard = entries,
                    leaderboardLoading = false,
                    networkMessage = ""
                )
            }
        } catch (error: Exception) {
            _uiState.update { state ->
                state.copy(
                    leaderboardLoading = false,
                    networkMessage = "Leaderboard unavailable: ${error.message.orEmpty()}"
                )
            }
        }
    }

    private suspend fun submitDailyScoreIfNeeded(force: Boolean) {
        if (currentMode != GameMode.DAILY) {
            return
        }
        if (!force && dailyUploadHandledForSession) {
            return
        }
        val engineState = gameEngine.getState()
        if (!force && !engineState.isGameOver && !engineState.hasWon) {
            return
        }

        val challengeDate = currentDailyDate ?: todayUtcDate()
        val seed = currentDailySeed ?: return
        val score = gameEngine.getState().score

        val upload = DailyScoreUpload(
            challengeDate = challengeDate,
            seed = seed,
            score = score,
            playerName = playerName
        )

        _uiState.update { it.copy(isSubmittingDailyScore = true) }
        try {
            repository.uploadDailyScore(upload)
            dailyUploadHandledForSession = true
            _uiState.update { state ->
                state.copy(
                    isSubmittingDailyScore = false,
                    networkMessage = "Daily score submitted."
                )
            }
            refreshLeaderboard(challengeDate)
        } catch (_: Exception) {
            repository.queueDailyScoreUpload(upload)
            dailyUploadHandledForSession = true
            _uiState.update { state ->
                state.copy(
                    isSubmittingDailyScore = false,
                    networkMessage = "Offline: daily score queued for retry."
                )
            }
        }
    }

    private fun restoreFromSavedState(): GameState? {
        val restoredBoard = savedStateHandle.get<IntArray>(KEY_BOARD)?.toList()
        val restoredScore = savedStateHandle.get<Int>(KEY_SCORE)
        if (restoredBoard == null || restoredBoard.size != BOARD_SIZE * BOARD_SIZE) {
            return null
        }
        if (restoredScore == null) {
            return null
        }
        return gameEngine.restoreState(restoredBoard, restoredScore)
    }

    private fun applyPersistedSettings(settings: PersistedSettings) {
        neutralX = settings.calibrationBaselineX
        neutralY = settings.calibrationBaselineY
        _uiState.update { current ->
            current.copy(
                tiltControlsEnabled = settings.tiltEnabled,
                shakeToResetEnabled = settings.shakeToResetEnabled,
                tiltSensitivity = settings.sensitivity.coerceIn(MIN_SENSITIVITY, MAX_SENSITIVITY)
            )
        }
    }

    private fun persistSettings() {
        val state = _uiState.value
        viewModelScope.launch {
            repository.saveSettings(
                PersistedSettings(
                    tiltEnabled = state.tiltControlsEnabled,
                    shakeToResetEnabled = state.shakeToResetEnabled,
                    sensitivity = state.tiltSensitivity,
                    calibrationBaselineX = neutralX,
                    calibrationBaselineY = neutralY
                )
            )
        }
    }

    private fun startAutoSaveLoop() {
        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch {
            while (isActive) {
                delay(AUTO_SAVE_INTERVAL_MS)
                persistCurrentSession()
            }
        }
    }

    private suspend fun persistCurrentSession() {
        val sessionId = currentSessionId ?: return
        val state = gameEngine.getState()
        repository.saveSessionSnapshot(
            sessionId = sessionId,
            board = state.board,
            score = state.score,
            isFinished = state.isGameOver || isCurrentSessionFinished
        )
        if (state.isGameOver) {
            finishCurrentSessionIfNeeded(state.score)
            submitDailyScoreIfNeeded(force = false)
        }
    }

    private suspend fun ensureSessionExists(state: GameState) {
        if (currentSessionId != null) return
        startSessionForState(state, currentMode, currentDailyDate, currentDailySeed)
    }

    private suspend fun startSessionForState(
        state: GameState,
        mode: GameMode,
        dailyDate: String?,
        dailySeed: Long?
    ) {
        val sessionId = repository.startSession(
            board = state.board,
            score = state.score,
            mode = mode,
            dailyDate = dailyDate,
            dailySeed = dailySeed
        )
        currentSessionId = sessionId
        savedStateHandle[KEY_SESSION_ID] = sessionId
        isCurrentSessionFinished = false
    }

    private suspend fun finishCurrentSessionIfNeeded(score: Int) {
        val sessionId = currentSessionId ?: return
        if (isCurrentSessionFinished) {
            return
        }
        finishSession(sessionId, score, currentMode)
        isCurrentSessionFinished = true
    }

    private suspend fun finishSession(sessionId: Long, score: Int, previousMode: GameMode) {
        repository.markSessionFinished(sessionId, score)
        if (score > 0) {
            repository.addHighScore(
                score = score,
                mode = previousMode.name.lowercase()
            )
            _uiState.update { current ->
                current.copy(highestScore = maxOf(current.highestScore, score))
            }
        }
    }

    private fun publishState(
        gameState: GameState,
        mergedIndices: Set<Int> = emptySet(),
        tileMovements: List<TileMovement> = emptyList(),
        mode: GameMode,
        dailyDate: String?,
        dailySeed: Long?
    ) {
        savedStateHandle[KEY_BOARD] = gameState.board.toIntArray()
        savedStateHandle[KEY_SCORE] = gameState.score
        savedStateHandle[KEY_MODE] = mode.name
        savedStateHandle[KEY_DAILY_DATE] = dailyDate
        savedStateHandle[KEY_DAILY_SEED] = dailySeed

        val previous = _uiState.value
        _uiState.value = previous.copy(
            board = gameState.board,
            score = gameState.score,
            hasWon = gameState.hasWon,
            isGameOver = gameState.isGameOver,
            mergedIndices = mergedIndices,
            tileMovements = tileMovements,
            moveKey = if (tileMovements.isNotEmpty()) previous.moveKey + 1 else previous.moveKey,
            mode = mode,
            dailyDate = dailyDate.orEmpty(),
            dailySeed = dailySeed
        )

        clearMergeJob?.cancel()
        if (mergedIndices.isNotEmpty() || tileMovements.isNotEmpty()) {
            clearMergeJob = viewModelScope.launch {
                delay(MERGE_ANIMATION_MS)
                _uiState.update { current ->
                    current.copy(mergedIndices = emptySet(), tileMovements = emptyList())
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        autoSaveJob?.cancel()
        repository.close()
    }

    private fun primaryDirection(deltaX: Float, deltaY: Float): Direction {
        return if (abs(deltaX) > abs(deltaY)) {
            if (deltaX > 0f) Direction.LEFT else Direction.RIGHT
        } else {
            if (deltaY > 0f) Direction.DOWN else Direction.UP
        }
    }

    private fun debugDirectionFor(deltaX: Float, deltaY: Float): String {
        if (abs(deltaX) < DEBUG_NEUTRAL_THRESHOLD && abs(deltaY) < DEBUG_NEUTRAL_THRESHOLD) {
            return "NEUTRAL"
        }
        return if (abs(deltaX) > abs(deltaY)) {
            if (deltaX > 0f) "LEFT" else "RIGHT"
        } else {
            if (deltaY > 0f) "DOWN" else "UP"
        }
    }

    private fun todayUtcDate(): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        formatter.timeZone = TimeZone.getTimeZone("UTC")
        return formatter.format(Date())
    }

    private fun createDailyApi(): RestDailyLeaderboardApi? {
        val baseUrl = BuildConfig.LEADERBOARD_BASE_URL.trim()
        if (baseUrl.isBlank()) {
            return null
        }
        return RestDailyLeaderboardApi(baseUrl.trimEnd('/'))
    }

    private fun fallbackSeedForDate(challengeDate: String): Long {
        return challengeDate.hashCode().toLong().and(0x0000_0000_FFFF_FFFFL)
    }

    companion object {
        private const val BOARD_SIZE = 4
        private const val KEY_BOARD = "tilt2048_board"
        private const val KEY_SCORE = "tilt2048_score"
        private const val KEY_SESSION_ID = "tilt2048_session_id"
        private const val KEY_MODE = "tilt2048_mode"
        private const val KEY_DAILY_DATE = "tilt2048_daily_date"
        private const val KEY_DAILY_SEED = "tilt2048_daily_seed"
        private const val MERGE_ANIMATION_MS = 300L
        private const val MOVE_COOLDOWN_MS = 220L
        private const val DEAD_ZONE_BASE = 1.35f
        private const val NEUTRAL_RESET_MULTIPLIER = 0.6f
        private const val DEBUG_NEUTRAL_THRESHOLD = 0.35f
        private const val AUTO_SAVE_INTERVAL_MS = 5_000L

        // Shake tuning (less sensitive, x/y-only jerk window)
        private const val SHAKE_WINDOW_SIZE = 10
        private const val SHAKE_COOLDOWN_MS = 2500L
        private const val SHAKE_JERK_THRESHOLD = 1.5f
        private const val SHAKE_PEAK_MULTIPLIER = 2.0f
        private const val SHAKE_RANGE_THRESHOLD = 1.2f

        private const val LEADERBOARD_LIMIT = 10
        private const val DEFAULT_PLAYER_NAME = "Player"
        const val MIN_SENSITIVITY = 0.6f
        const val MAX_SENSITIVITY = 2.0f
    }
}
