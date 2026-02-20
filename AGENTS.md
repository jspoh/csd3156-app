# AGENTS.md

## Project Overview
- App name: `Tilt2048`
- Platform: Android
- Language: Kotlin
- UI: Jetpack Compose

## Core Product Requirements
- Implement classic 2048 gameplay with swipe input.
- Add optional tilt controls using gyroscope/accelerometer:
  - User can toggle tilt controls on/off.
  - User can adjust sensitivity with a slider.
  - User can run calibration.
- Persist data with Room using multiple tables:
  - `GameSession`
  - `HighScore`
  - `Settings`
  - `MoveEvent`
- Implement networking for:
  - Daily-seed challenge
  - Leaderboard sync
- Use polished animations and haptics for tile merges.

## Architecture Rules
- Use MVVM + repository pattern.
- Use Kotlin coroutines and Flow for async/state streams.
- Keep game logic deterministic and testable.
- Keep board/game engine in a pure Kotlin module/class with no Android dependencies.
- Keep Compose UI layer separate from game engine logic.
- Do not use singletons that hide mutable state.
- Avoid overengineering; prefer clear, minimal, maintainable designs.

## Networking Decision (Chosen Approach)
- Use Firebase as the simplest reliable backend:
  - Firestore for daily challenge + leaderboard data.
  - Firebase Authentication (anonymous) for player identity.
- Keep networking behind repository interfaces so it is replaceable.
- Document full Firebase setup and required config in `README.md` when implementing.

## Quality Bar
- Deterministic board logic with unit tests (merge rules, move validity, spawn behavior, game-over conditions, scoring).
- Add tests for any non-trivial engine behavior and regression-prone logic.
- Keep UI tests focused and pragmatic; prioritize engine/unit test coverage first.
- Ensure no flaky tests.

## Delivery Requirements
- Gradle test tasks must pass before completion.
- App must build and run successfully.
- Changes should preserve clear module boundaries and readable code.

## Implementation Guidance
- Prefer small, composable classes with explicit dependencies.
- Use immutable UI state models where practical.
- Keep Room entities, DAOs, and repositories clearly separated by responsibility.
- Add concise comments only where logic is non-obvious.
- If a tradeoff is required, choose simplicity and testability first.
