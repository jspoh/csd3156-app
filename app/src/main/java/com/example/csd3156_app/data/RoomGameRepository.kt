package com.example.csd3156_app.data

import com.example.csd3156_app.data.local.Tilt2048Database
import com.example.csd3156_app.data.local.codec.BoardStateJsonCodec
import com.example.csd3156_app.data.local.codec.CalibrationBaselineCodec
import com.example.csd3156_app.data.local.entity.DailySeedCacheEntity
import com.example.csd3156_app.data.local.entity.GameSessionEntity
import com.example.csd3156_app.data.local.entity.HighScoreEntity
import com.example.csd3156_app.data.local.entity.LeaderboardEntryCacheEntity
import com.example.csd3156_app.data.local.entity.MoveEventEntity
import com.example.csd3156_app.data.local.entity.PendingScoreUploadEntity
import com.example.csd3156_app.data.local.entity.SettingsEntity
import com.example.csd3156_app.data.network.DailyLeaderboardApi
import com.example.csd3156_app.game.Direction
import com.example.csd3156_app.game.GameMode
import java.io.IOException

class RoomGameRepository(
    private val database: Tilt2048Database,
    private val dailyApi: DailyLeaderboardApi?,
    private val nowMillis: () -> Long = { System.currentTimeMillis() }
) : GameRepository {

    override suspend fun loadSettings(): PersistedSettings {
        val existing = database.settingsDao().getById()
        if (existing != null) {
            val baseline = CalibrationBaselineCodec.decode(existing.calibrationBaseline)
            return PersistedSettings(
                tiltEnabled = existing.tiltEnabled,
                sensitivity = existing.sensitivity,
                calibrationBaselineX = baseline.first,
                calibrationBaselineY = baseline.second
            )
        }

        val defaults = PersistedSettings(
            tiltEnabled = false,
            sensitivity = 1f,
            calibrationBaselineX = 0f,
            calibrationBaselineY = 0f
        )
        saveSettings(defaults)
        return defaults
    }

    override suspend fun saveSettings(settings: PersistedSettings) {
        val entity = SettingsEntity(
            id = SettingsEntity.SETTINGS_SINGLETON_ID,
            tiltEnabled = settings.tiltEnabled,
            sensitivity = settings.sensitivity,
            calibrationBaseline = CalibrationBaselineCodec.encode(
                settings.calibrationBaselineX,
                settings.calibrationBaselineY
            ),
            updatedAt = nowMillis()
        )
        database.settingsDao().upsert(entity)
    }

    override suspend fun getLatestUnfinishedSession(): PersistedSession? {
        val entity = database.gameSessionDao().getLatestUnfinishedSession() ?: return null
        return PersistedSession(
            id = entity.id,
            board = BoardStateJsonCodec.decode(entity.boardState),
            score = entity.score,
            isFinished = entity.isFinished,
            mode = toGameMode(entity.mode),
            dailyDate = entity.dailyDate,
            dailySeed = entity.dailySeed
        )
    }

    override suspend fun startSession(
        board: List<Int>,
        score: Int,
        mode: GameMode,
        dailyDate: String?,
        dailySeed: Long?
    ): Long {
        val now = nowMillis()
        return database.gameSessionDao().insert(
            GameSessionEntity(
                boardState = BoardStateJsonCodec.encode(board),
                score = score,
                startedAt = now,
                lastUpdatedAt = now,
                isFinished = false,
                mode = mode.name,
                dailyDate = dailyDate,
                dailySeed = dailySeed
            )
        )
    }

    override suspend fun saveSessionSnapshot(
        sessionId: Long,
        board: List<Int>,
        score: Int,
        isFinished: Boolean
    ) {
        database.gameSessionDao().updateSnapshot(
            sessionId = sessionId,
            boardState = BoardStateJsonCodec.encode(board),
            score = score,
            lastUpdatedAt = nowMillis(),
            isFinished = isFinished
        )
    }

    override suspend fun markSessionFinished(sessionId: Long, score: Int) {
        database.gameSessionDao().markFinished(
            sessionId = sessionId,
            score = score,
            lastUpdatedAt = nowMillis()
        )
    }

    override suspend fun addHighScore(score: Int, mode: String) {
        database.highScoreDao().insert(
            HighScoreEntity(
                score = score,
                achievedAt = nowMillis(),
                mode = mode
            )
        )
    }

    override suspend fun addMoveEvent(sessionId: Long, direction: Direction, scoreAfter: Int) {
        database.moveEventDao().insert(
            MoveEventEntity(
                sessionId = sessionId,
                direction = direction.name,
                scoreAfter = scoreAfter,
                timestamp = nowMillis()
            )
        )
    }

    override suspend fun getOrFetchDailySeed(challengeDate: String): Long {
        val cached = database.dailyChallengeDao().getDailySeed(challengeDate)
        if (cached != null) {
            return cached.seed
        }

        val response = requireDailyApi().fetchDailySeed(challengeDate)
        database.dailyChallengeDao().upsertDailySeed(
            DailySeedCacheEntity(
                challengeDate = response.date,
                seed = response.seed,
                fetchedAt = nowMillis()
            )
        )
        return response.seed
    }

    override suspend fun fetchLeaderboard(challengeDate: String, limit: Int): List<LeaderboardEntry> {
        return try {
            val remoteEntries = requireDailyApi().fetchLeaderboard(challengeDate, limit)
            database.dailyChallengeDao().clearLeaderboard(challengeDate)
            database.dailyChallengeDao().insertLeaderboard(
                remoteEntries.map { remote ->
                    LeaderboardEntryCacheEntity(
                        challengeDate = challengeDate,
                        playerName = remote.playerName,
                        score = remote.score,
                        submittedAt = remote.submittedAt
                    )
                }
            )
            remoteEntries.map { LeaderboardEntry(it.playerName, it.score, it.submittedAt) }
        } catch (error: IOException) {
            val cached = database.dailyChallengeDao().getLeaderboard(challengeDate, limit)
            if (cached.isNotEmpty()) {
                cached.map { LeaderboardEntry(it.playerName, it.score, it.submittedAt) }
            } else {
                throw error
            }
        }
    }

    override suspend fun queueDailyScoreUpload(upload: DailyScoreUpload) {
        database.dailyChallengeDao().insertPendingUpload(
            PendingScoreUploadEntity(
                challengeDate = upload.challengeDate,
                seed = upload.seed,
                score = upload.score,
                playerName = upload.playerName,
                createdAt = nowMillis(),
                attempts = 0
            )
        )
    }

    override suspend fun uploadDailyScore(upload: DailyScoreUpload) {
        requireDailyApi().submitDailyScore(
            date = upload.challengeDate,
            seed = upload.seed,
            score = upload.score,
            playerName = upload.playerName
        )
    }

    override suspend fun retryPendingDailyUploads() {
        val uploads = database.dailyChallengeDao().getPendingUploads()
        for (pending in uploads) {
            try {
                uploadDailyScore(
                    DailyScoreUpload(
                        challengeDate = pending.challengeDate,
                        seed = pending.seed,
                        score = pending.score,
                        playerName = pending.playerName
                    )
                )
                database.dailyChallengeDao().deletePendingUpload(pending.id)
            } catch (_: Exception) {
                database.dailyChallengeDao().updatePendingAttempts(
                    id = pending.id,
                    attempts = pending.attempts + 1
                )
            }
        }
    }

    override fun close() {
        database.close()
    }

    private fun requireDailyApi(): DailyLeaderboardApi {
        return dailyApi ?: throw IOException("Leaderboard API is not configured.")
    }

    private fun toGameMode(value: String): GameMode {
        return runCatching { GameMode.valueOf(value) }.getOrDefault(GameMode.CLASSIC)
    }
}
