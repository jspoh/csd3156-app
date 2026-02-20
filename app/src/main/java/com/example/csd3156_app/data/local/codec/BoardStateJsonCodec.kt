package com.example.csd3156_app.data.local.codec

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

object BoardStateJsonCodec {
    private val json = Json {
        prettyPrint = false
        isLenient = false
        ignoreUnknownKeys = false
    }

    fun encode(board: List<Int>): String {
        return json.encodeToString(ListSerializer(Int.serializer()), board)
    }

    fun decode(serialized: String, expectedSize: Int = 16): List<Int> {
        val decoded = json.decodeFromString(ListSerializer(Int.serializer()), serialized)
        require(decoded.size == expectedSize) {
            "Expected board size $expectedSize, but was ${decoded.size}."
        }
        return decoded
    }
}
