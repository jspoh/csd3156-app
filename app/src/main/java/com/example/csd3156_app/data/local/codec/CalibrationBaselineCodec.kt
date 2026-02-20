package com.example.csd3156_app.data.local.codec

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

object CalibrationBaselineCodec {
    private val json = Json {
        prettyPrint = false
        isLenient = false
        ignoreUnknownKeys = false
    }

    fun encode(x: Float, y: Float): String {
        return json.encodeToString(
            ListSerializer(Float.serializer()),
            listOf(x, y)
        )
    }

    fun decode(serialized: String): Pair<Float, Float> {
        val values = json.decodeFromString(
            ListSerializer(Float.serializer()),
            serialized
        )
        require(values.size == 2) {
            "Expected calibration baseline to contain exactly 2 values."
        }
        return values[0] to values[1]
    }
}
