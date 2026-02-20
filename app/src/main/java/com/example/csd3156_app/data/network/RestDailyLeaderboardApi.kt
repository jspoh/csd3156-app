package com.example.csd3156_app.data.network

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class RestDailyLeaderboardApi(
    private val baseUrl: String,
    private val connectTimeoutMs: Int = 5_000,
    private val readTimeoutMs: Int = 5_000
) : DailyLeaderboardApi {

    private val json = Json {
        ignoreUnknownKeys = true
    }

    override suspend fun fetchDailySeed(date: String): DailySeedResponse = withContext(Dispatchers.IO) {
        val url = "$baseUrl/daily-seed?date=${date.urlEncoded()}"
        val response = request(url = url, method = "GET", body = null)
        val payload = json.decodeFromString(DailySeedPayload.serializer(), response)
        DailySeedResponse(date = payload.date, seed = payload.seed)
    }

    override suspend fun fetchLeaderboard(
        date: String,
        limit: Int
    ): List<LeaderboardEntryDto> = withContext(Dispatchers.IO) {
        val url = "$baseUrl/leaderboard?date=${date.urlEncoded()}&limit=$limit"
        val response = request(url = url, method = "GET", body = null)
        val payload = json.decodeFromString(LeaderboardResponsePayload.serializer(), response)
        payload.entries.map { entry ->
            LeaderboardEntryDto(
                playerName = entry.playerName,
                score = entry.score,
                submittedAt = entry.submittedAt
            )
        }
    }

    override suspend fun submitDailyScore(
        date: String,
        seed: Long,
        score: Int,
        playerName: String
    ) = withContext(Dispatchers.IO) {
        val requestBody = json.encodeToString(
            SubmitScoreRequestPayload.serializer(),
            SubmitScoreRequestPayload(
                date = date,
                seed = seed,
                score = score,
                playerName = playerName
            )
        )
        request(
            url = "$baseUrl/leaderboard/submit",
            method = "POST",
            body = requestBody
        )
        Unit
    }

    private fun request(url: String, method: String, body: String?): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            setRequestProperty("Accept", "application/json")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
        }

        try {
            if (body != null) {
                connection.outputStream.use { output ->
                    output.write(body.toByteArray(Charsets.UTF_8))
                }
            }

            val responseCode = connection.responseCode
            val responseBody = if (responseCode in 200..299) {
                connection.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
            } else {
                val errorText = connection.errorStream?.use {
                    it.readBytes().toString(Charsets.UTF_8)
                }.orEmpty()
                throw IOException("HTTP $responseCode ${connection.responseMessage}: $errorText")
            }
            return responseBody
        } finally {
            connection.disconnect()
        }
    }

    private fun String.urlEncoded(): String {
        return URLEncoder.encode(this, Charsets.UTF_8.name())
    }
}

@Serializable
private data class DailySeedPayload(
    val date: String,
    val seed: Long
)

@Serializable
private data class LeaderboardResponsePayload(
    val entries: List<LeaderboardEntryPayload>
)

@Serializable
private data class LeaderboardEntryPayload(
    @SerialName("playerName")
    val playerName: String,
    val score: Int,
    val submittedAt: Long
)

@Serializable
private data class SubmitScoreRequestPayload(
    val date: String,
    val seed: Long,
    val score: Int,
    @SerialName("playerName")
    val playerName: String
)
