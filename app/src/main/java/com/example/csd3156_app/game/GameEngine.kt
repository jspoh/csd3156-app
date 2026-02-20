package com.example.csd3156_app.game

import kotlin.random.Random

enum class Direction {
    UP,
    DOWN,
    LEFT,
    RIGHT
}

data class GameState(
    val board: List<Int>,
    val score: Int,
    val isGameOver: Boolean,
    val hasWon: Boolean
)

data class MoveResult(
    val state: GameState,
    val moved: Boolean,
    val scoreGained: Int,
    val mergedIndices: Set<Int> = emptySet()
)

data class TileSpawn(
    val row: Int,
    val column: Int,
    val value: Int
)

fun interface TileSpawner {
    fun nextSpawn(board: List<Int>, size: Int): TileSpawn?
}

class RandomTileSpawner(
    private val random: Random = Random.Default
) : TileSpawner {
    override fun nextSpawn(board: List<Int>, size: Int): TileSpawn? {
        val emptyIndices = board.indices.filter { board[it] == 0 }
        if (emptyIndices.isEmpty()) {
            return null
        }

        val chosenIndex = emptyIndices[random.nextInt(emptyIndices.size)]
        val row = chosenIndex / size
        val column = chosenIndex % size
        val value = if (random.nextInt(100) < 10) 4 else 2
        return TileSpawn(row = row, column = column, value = value)
    }
}

class GameEngine(
    private val size: Int = DEFAULT_SIZE,
    private var tileSpawner: TileSpawner = RandomTileSpawner()
) {
    private var board: IntArray = IntArray(size * size)
    private var score: Int = 0

    init {
        require(size >= 2) { "Board size must be at least 2." }
    }

    fun startNewGame(): GameState {
        board = IntArray(size * size)
        score = 0
        spawnTile()
        spawnTile()
        return currentState()
    }

    fun restoreState(boardValues: List<Int>, currentScore: Int): GameState {
        require(boardValues.size == size * size) {
            "Board size mismatch. Expected ${size * size}, got ${boardValues.size}."
        }
        require(currentScore >= 0) { "Score cannot be negative." }

        board = boardValues.toIntArray()
        score = currentScore
        return currentState()
    }

    fun getState(): GameState = currentState()

    fun setTileSpawner(spawner: TileSpawner) {
        tileSpawner = spawner
    }

    fun moveLeft(): MoveResult = move(Direction.LEFT)

    fun moveRight(): MoveResult = move(Direction.RIGHT)

    fun moveUp(): MoveResult = move(Direction.UP)

    fun moveDown(): MoveResult = move(Direction.DOWN)

    fun move(direction: Direction): MoveResult {
        val before = board.copyOf()
        var gainedScore = 0
        val mergedIndices = mutableSetOf<Int>()

        for (lineIndex in 0 until size) {
            val line = readLine(lineIndex, direction)
            val mergeResult = mergeLine(line)
            val movedLine = mergeResult.values
            val gained = mergeResult.scoreGained
            writeLine(lineIndex, direction, movedLine)
            gainedScore += gained
            mergeResult.mergedOffsets.forEach { offset ->
                mergedIndices.add(toBoardIndex(lineIndex, offset, direction))
            }
        }

        val moved = !before.contentEquals(board)
        if (!moved) {
            return MoveResult(
                state = currentState(),
                moved = false,
                scoreGained = 0
            )
        }

        score += gainedScore
        spawnTile()

        return MoveResult(
            state = currentState(),
            moved = true,
            scoreGained = gainedScore,
            mergedIndices = mergedIndices
        )
    }

    private fun readLine(lineIndex: Int, direction: Direction): IntArray {
        return IntArray(size) { offset ->
            val (row, column) = when (direction) {
                Direction.LEFT -> lineIndex to offset
                Direction.RIGHT -> lineIndex to (size - 1 - offset)
                Direction.UP -> offset to lineIndex
                Direction.DOWN -> (size - 1 - offset) to lineIndex
            }
            board[row * size + column]
        }
    }

    private fun writeLine(lineIndex: Int, direction: Direction, values: IntArray) {
        for (offset in 0 until size) {
            val rowColumn = toRowColumn(lineIndex, offset, direction)
            val row = rowColumn.first
            val column = rowColumn.second
            board[row * size + column] = values[offset]
        }
    }

    private fun mergeLine(values: IntArray): MergeLineResult {
        val nonZero = values.filter { it != 0 }
        val merged = mutableListOf<Int>()
        val mergedOffsets = mutableSetOf<Int>()
        var scoreGained = 0
        var index = 0

        while (index < nonZero.size) {
            val current = nonZero[index]
            val next = nonZero.getOrNull(index + 1)
            if (next != null && current == next) {
                val mergedValue = current * 2
                mergedOffsets.add(merged.size)
                merged.add(mergedValue)
                scoreGained += mergedValue
                index += 2
            } else {
                merged.add(current)
                index += 1
            }
        }

        while (merged.size < size) {
            merged.add(0)
        }

        return MergeLineResult(
            values = merged.toIntArray(),
            scoreGained = scoreGained,
            mergedOffsets = mergedOffsets
        )
    }

    private fun spawnTile() {
        val emptyIndices = board.indices.filter { board[it] == 0 }
        if (emptyIndices.isEmpty()) {
            return
        }

        val proposed = tileSpawner.nextSpawn(board.toList(), size)
        if (proposed != null && isSpawnValid(proposed)) {
            val value = if (proposed.value == 4) 4 else 2
            board[proposed.row * size + proposed.column] = value
            return
        }

        val fallbackIndex = emptyIndices.first()
        board[fallbackIndex] = 2
    }

    private fun isSpawnValid(spawn: TileSpawn): Boolean {
        if (spawn.row !in 0 until size || spawn.column !in 0 until size) {
            return false
        }
        if (spawn.value != 2 && spawn.value != 4) {
            return false
        }
        return board[spawn.row * size + spawn.column] == 0
    }

    private fun toBoardIndex(lineIndex: Int, offset: Int, direction: Direction): Int {
        val rowColumn = toRowColumn(lineIndex, offset, direction)
        return rowColumn.first * size + rowColumn.second
    }

    private fun toRowColumn(lineIndex: Int, offset: Int, direction: Direction): Pair<Int, Int> {
        return when (direction) {
            Direction.LEFT -> lineIndex to offset
            Direction.RIGHT -> lineIndex to (size - 1 - offset)
            Direction.UP -> offset to lineIndex
            Direction.DOWN -> (size - 1 - offset) to lineIndex
        }
    }

    private fun currentState(): GameState {
        val boardSnapshot = board.toList()
        val hasWon = boardSnapshot.any { it >= WIN_TILE }
        val isGameOver = isGameOver(boardSnapshot)
        return GameState(
            board = boardSnapshot,
            score = score,
            isGameOver = isGameOver,
            hasWon = hasWon
        )
    }

    private fun isGameOver(values: List<Int>): Boolean {
        if (values.any { it == 0 }) {
            return false
        }

        for (row in 0 until size) {
            for (column in 0 until size) {
                val value = values[row * size + column]
                if (column + 1 < size && values[row * size + column + 1] == value) {
                    return false
                }
                if (row + 1 < size && values[(row + 1) * size + column] == value) {
                    return false
                }
            }
        }

        return true
    }

    companion object {
        const val DEFAULT_SIZE: Int = 4
        private const val WIN_TILE: Int = 2048
    }

    private data class MergeLineResult(
        val values: IntArray,
        val scoreGained: Int,
        val mergedOffsets: Set<Int>
    )
}
