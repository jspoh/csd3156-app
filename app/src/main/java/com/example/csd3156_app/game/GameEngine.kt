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

data class TileMovement(
    val fromIndex: Int,
    val toIndex: Int
)

data class MoveResult(
    val state: GameState,
    val moved: Boolean,
    val scoreGained: Int,
    val mergedIndices: Set<Int> = emptySet(),
    val tileMovements: List<TileMovement> = emptyList()
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
        val allMovements = mutableListOf<TileMovement>()

        for (lineIndex in 0 until size) {
            val line = readLine(lineIndex, direction)
            val mergeResult = mergeLine(line)
            writeLine(lineIndex, direction, mergeResult.values)
            gainedScore += mergeResult.scoreGained
            mergeResult.mergedOffsets.forEach { offset ->
                mergedIndices.add(toBoardIndex(lineIndex, offset, direction))
            }
            mergeResult.movements.forEach { (fromOffset, toOffset) ->
                allMovements.add(
                    TileMovement(
                        fromIndex = toBoardIndex(lineIndex, fromOffset, direction),
                        toIndex = toBoardIndex(lineIndex, toOffset, direction)
                    )
                )
            }
        }

        val moved = !before.contentEquals(board)
        if (!moved) {
            return MoveResult(state = currentState(), moved = false, scoreGained = 0)
        }

        score += gainedScore
        spawnTile()

        return MoveResult(
            state = currentState(),
            moved = true,
            scoreGained = gainedScore,
            mergedIndices = mergedIndices,
            tileMovements = allMovements
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
        // Track (originalOffset, value) for non-zero tiles to map source → destination
        val nonZero = mutableListOf<Pair<Int, Int>>()
        for (i in values.indices) {
            if (values[i] != 0) nonZero.add(i to values[i])
        }

        val merged = mutableListOf<Int>()
        val mergedOffsets = mutableSetOf<Int>()
        val movements = mutableListOf<Pair<Int, Int>>() // fromOffset -> toOffset
        var scoreGained = 0
        var index = 0

        while (index < nonZero.size) {
            val (srcOffset, current) = nonZero[index]
            val nextPair = nonZero.getOrNull(index + 1)
            if (nextPair != null && current == nextPair.second) {
                val mergedValue = current * 2
                val destOffset = merged.size
                mergedOffsets.add(destOffset)
                merged.add(mergedValue)
                scoreGained += mergedValue
                movements.add(srcOffset to destOffset)
                movements.add(nextPair.first to destOffset)
                index += 2
            } else {
                val destOffset = merged.size
                merged.add(current)
                movements.add(srcOffset to destOffset)
                index += 1
            }
        }

        while (merged.size < size) {
            merged.add(0)
        }

        return MergeLineResult(
            values = merged.toIntArray(),
            scoreGained = scoreGained,
            mergedOffsets = mergedOffsets,
            movements = movements
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
        val mergedOffsets: Set<Int>,
        val movements: List<Pair<Int, Int>> = emptyList()
    )
}
