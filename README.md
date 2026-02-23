# Tilt2048 Backend Setup (Daily Challenge + Leaderboard)

This app uses a lightweight REST backend for daily challenge seed + leaderboard sync.

## 1) Configure Base URL

Set your backend base URL in `app/build.gradle.kts`:

```kotlin
defaultConfig {
    buildConfigField("String", "LEADERBOARD_BASE_URL", "\"https://your-api.example.com\"")
}
```

If this value is empty, the app still runs:
- Daily seed falls back to a deterministic local seed.
- Score uploads are queued locally and retried later.
- Leaderboard shows cached data only (if available).

## 2) Required API Endpoints

All responses are JSON.

### `GET /daily-seed`

Returns the seed for today's challenge. The date is determined server-side.

Response:

```json
{
  "date": "2026-02-20",
  "seed": 123456789
}
```

### `GET /leaderboard?date=YYYY-MM-DD&limit=10`

Response:

```json
{
  "entries": [
    { "playerName": "PlayerA", "score": 4096, "submittedAt": 1739980000000 },
    { "playerName": "PlayerB", "score": 3072, "submittedAt": 1739981000000 }
  ]
}
```

### `POST /leaderboard`

Request:

```json
{
  "date": "2026-02-20",
  "seed": 123456789,
  "score": 2048,
  "playerName": "Player"
}
```

The server validates that `seed` matches the expected seed for the given `date`. Mismatched seeds are rejected with `400`.

Response: `200` with any JSON body (or empty body).

## 3) Reliability Behavior in App

- Daily seed is cached in Room once fetched for a given day.
- Leaderboard entries are cached per day in Room.
- Failed score uploads are stored in a pending queue and retried:
  - on next launch
  - on app background events (pause/stop)

No WorkManager is required for this implementation.
