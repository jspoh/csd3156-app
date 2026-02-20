package com.example.csd3156_app.data.local.codec

import org.junit.Assert.assertEquals
import org.junit.Test

class BoardStateJsonCodecTest {

    @Test
    fun encodeAndDecode_roundTripsBoardState() {
        val board = listOf(
            2, 0, 4, 0,
            8, 16, 0, 0,
            32, 64, 128, 0,
            256, 512, 1024, 2048
        )

        val encoded = BoardStateJsonCodec.encode(board)
        val decoded = BoardStateJsonCodec.decode(encoded)

        assertEquals("[2,0,4,0,8,16,0,0,32,64,128,0,256,512,1024,2048]", encoded)
        assertEquals(board, decoded)
    }

    @Test(expected = IllegalArgumentException::class)
    fun decode_throwsWhenBoardSizeIsInvalid() {
        BoardStateJsonCodec.decode("[2,4,8]")
    }
}
