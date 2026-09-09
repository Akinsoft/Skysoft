package com.skysoft.data.skyblock

import com.google.gson.JsonObject

internal object CatalogJson {
    fun JsonObject.string(name: String): String = get(name)?.takeUnless { it.isJsonNull }?.asString.orEmpty()
    fun JsonObject.long(name: String): Long = get(name)?.takeUnless { it.isJsonNull }?.asLong ?: 0L
    fun JsonObject.obj(name: String): JsonObject? = get(name)?.takeIf { it.isJsonObject }?.asJsonObject
    fun JsonObject.array(name: String) = get(name)?.takeIf { it.isJsonArray }?.asJsonArray
}
