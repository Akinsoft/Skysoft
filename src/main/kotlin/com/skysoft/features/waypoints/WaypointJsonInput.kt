package com.skysoft.features.waypoints

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.google.gson.Strictness
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.StringReader

internal object WaypointJsonInput {
    fun parse(text: String): JsonElement {
        require(text.toByteArray(Charsets.UTF_8).size <= WaypointJson.MAX_JSON_BYTES) { "Waypoint JSON exceeds 8 MB." }
        JsonReader(StringReader(text)).use { reader ->
            reader.strictness = Strictness.STRICT
            val fields = ArrayDeque<MutableSet<String>?>()
            var tokens = 0
            while (reader.peek() != JsonToken.END_DOCUMENT) {
                require(++tokens <= MAX_TOKENS) { "Waypoint JSON contains too many values." }
                when (reader.peek()) {
                    JsonToken.BEGIN_OBJECT -> {
                        reader.beginObject()
                        fields.addLast(mutableSetOf())
                    }
                    JsonToken.BEGIN_ARRAY -> {
                        reader.beginArray()
                        fields.addLast(null)
                    }
                    JsonToken.END_OBJECT -> {
                        reader.endObject()
                        fields.removeLast()
                    }
                    JsonToken.END_ARRAY -> {
                        reader.endArray()
                        fields.removeLast()
                    }
                    JsonToken.NAME -> {
                        val name = reader.nextName()
                        require(fields.last()?.add(name) == true) { "Duplicate waypoint JSON field '$name'." }
                    }
                    JsonToken.STRING, JsonToken.NUMBER -> reader.nextString()
                    JsonToken.BOOLEAN -> reader.nextBoolean()
                    JsonToken.NULL -> reader.nextNull()
                    else -> error("Invalid waypoint JSON.")
                }
                require(fields.size <= MAX_DEPTH) { "Waypoint JSON is nested too deeply." }
            }
        }
        return JsonParser.parseString(text)
    }

    private const val MAX_TOKENS = 750000
    private const val MAX_DEPTH = 24
}
