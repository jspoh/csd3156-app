package com.example.csd3156_app.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameEngineTest {

    @Test
    fun startNewGame_spawnsExactlyTwoTiles_andZeroScore() {
        val spawner = QueueTileSpawner(
            listOf(
                TileSpawn(0, 0, 2),
                TileSpawn(1, 1, 4)
            )
        )
        val engine = GameEngine(tileSpawner = spawner)

        val state = engine.startNewGame()

        assertEquals(2, state.board.count { it != 0 })
        assertEquals(2, state.board[0])
        assertEquals(4, state.board[5])
        assertEquals(0, state.score)
        assertFalse(state.isGameOver)
        assertFalse(state.hasWon)
    }

    @Test
    fun moveLeft_mergesOnlyOncePerPair_andAddsScore() {
        val spawner = QueueTileSpawner(listOf(TileSpawn(3, 3, 2)))
        val engine = GameEngine(tileSpawner = spawner)
        engine.restoreState(
            boardValues = listOf(
                2, 2, 2, 0,
                0, 0, 0, 0,
                0, 0, 0, 0,
                0, 0, 0, 0
            ),
            currentScore = 0
        )

        val result = engine.moveLeft()

        assertTrue(result.moved)
        assertEquals(4, result.scoreGained)
        assertEquals(4, result.state.score)
        assertEquals(
            listOf(
                4, 2, 0, 0,
                0, 0, 0, 0,
                0, 0, 0, 0,
                0, 0, 0, 2
            ),
            result.state.board
        )
    }

    @Test
    fun moveWithoutBoardChange_doesNotSpawnTile() {
        val spawner = QueueTileSpawner(listOf(TileSpawn(3, 3, 2)))
        val engine = GameEngine(tileSpawner = spawner)
        val initialBoard = listOf(
            2, 4, 8, 16,
            0, 0, 0, 0,
            0, 0, 0, 0,
            0, 0, 0, 0
        )
        engine.restoreState(
            boardValues = initialBoard,
            currentScore = 10
        )

        val result = engine.moveLeft()

        assertFalse(result.moved)
        assertEquals(0, result.scoreGained)
        assertEquals(10, result.state.score)
        assertEquals(initialBoard, result.state.board)
    }

    @Test
    fun detectsGameOver_whenBoardFullAndNoValidMoves() {
        val engine = GameEngine(tileSpawner = QueueTileSpawner(emptyList()))

        val state = engine.restoreState(
            boardValues = listOf(
                2, 4, 2, 4,
                4, 2, 4, 2,
                2, 4, 2, 4,
                4, 2, 4, 2
            ),
            currentScore = 100
        )

        assertTrue(state.isGameOver)
    }

    @Test
    fun moveRight_mergesTowardRightEdge() {
        val engine = GameEngine(
            tileSpawner = QueueTileSpawner(
                listOf(TileSpawn(1, 0, 2))
            )
        )
        engine.restoreState(
            boardValues = listOf(
                0, 2, 2, 4,
                0, 0, 0, 0,
                0, 0, 0, 0,
                0, 0, 0, 0
            ),
            currentScore = 0
        )

        val result = engine.moveRight()

        assertTrue(result.moved)
        assertEquals(
            listOf(
                0, 0, 4, 4,
                2, 0, 0, 0,
                0, 0, 0, 0,
                0, 0, 0, 0
            ),
            result.state.board
        )
        assertEquals(4, result.state.score)
    }

    @Test
    fun moveUp_mergesColumnsCorrectly() {
        val engine = GameEngine(
            tileSpawner = QueueTileSpawner(
                listOf(TileSpawn(3, 3, 2))
            )
        )
        engine.restoreState(
            boardValues = listOf(
                2, 0, 0, 0,
                2, 0, 0, 0,
                4, 0, 0, 0,
                4, 0, 0, 0
            ),
            currentScore = 0
        )

        val result = engine.moveUp()

        assertTrue(result.moved)
        assertEquals(12, result.state.score)
        assertEquals(
            listOf(
                4, 0, 0, 0,
                8, 0, 0, 0,
                0, 0, 0, 0,
                0, 0, 0, 2
            ),
            result.state.board
        )
    }

    @Test
    fun moveDown_mergesColumnsTowardBottom() {
        val engine = GameEngine(
            tileSpawner = QueueTileSpawner(
                listOf(TileSpawn(0, 0, 4))
            )
        )
        engine.restoreState(
            boardValues = listOf(
                2, 0, 0, 0,
                2, 0, 0, 0,
                0, 0, 0, 0,
                0, 0, 0, 0
            ),
            currentScore = 0
        )

        val result = engine.moveDown()

        assertTrue(result.moved)
        assertEquals(4, result.state.score)
        assertEquals(
            listOf(
                4, 0, 0, 0,
                0, 0, 0, 0,
                0, 0, 0, 0,
                4, 0, 0, 0
            ),
            result.state.board
        )
    }

    @Test
    fun successfulMove_spawnsSingleTile_withValue2or4() {
        val spawner = QueueTileSpawner(listOf(TileSpawn(0, 3, 4)))
        val engine = GameEngine(tileSpawner = spawner)
        engine.restoreState(
            boardValues = listOf(
                2, 2, 0, 0,
                0, 0, 0, 0,
                0, 0, 0, 0,
                0, 0, 0, 0
            ),
            currentScore = 0
        )

        val result = engine.moveLeft()
        val nonZero = result.state.board.filter { it != 0 }

        assertEquals(2, nonZero.size)
        assertTrue(nonZero.contains(4))
        assertTrue(nonZero.all { it == 2 || it == 4 })
    }

    @Test
    fun detectsWin_whenTileReaches2048() {
        val engine = GameEngine(tileSpawner = QueueTileSpawner(emptyList()))

        val state = engine.restoreState(
            boardValues = listOf(
                2048, 2, 4, 8,
                16, 32, 64, 128,
                256, 512, 1024, 2,
                4, 8, 16, 32
            ),
            currentScore = 0
        )

        assertTrue(state.hasWon)
    }

    @Test
    fun randomTileSpawner_producesOnly2or4() {
        val board = List(16) { 0 }
        val spawner = RandomTileSpawner(kotlin.random.Random(1234))

        repeat(100) {
            val spawn = spawner.nextSpawn(board, 4)
            assertTrue(spawn != null)
            assertTrue(spawn!!.value == 2 || spawn.value == 4)
        }
    }

    private class QueueTileSpawner(
        spawns: List<TileSpawn>
    ) : TileSpawner {
        private val queue = ArrayDeque(spawns)

        override fun nextSpawn(board: List<Int>, size: Int): TileSpawn? {
            return if (queue.isEmpty()) null else queue.removeFirst()
        }
    }
}
