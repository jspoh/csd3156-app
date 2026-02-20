package com.example.csd3156_app.data.local.codec

import org.junit.Assert.assertEquals
import org.junit.Test

class CalibrationBaselineCodecTest {

    @Test
    fun encodeAndDecode_roundTripsBaseline() {
        val encoded = CalibrationBaselineCodec.encode(1.25f, -0.75f)
        val decoded = CalibrationBaselineCodec.decode(encoded)

        assertEquals("[1.25,-0.75]", encoded)
        assertEquals(1.25f, decoded.first)
        assertEquals(-0.75f, decoded.second)
    }
}
