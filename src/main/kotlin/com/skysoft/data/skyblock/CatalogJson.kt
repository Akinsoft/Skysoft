package com.skysoft.data.skyblock

import com.google.gson.JsonObject

internal object CatalogJson {
    fun JsonObject.string(name: String): String = get(name)?.takeUnless { it.isJsonNull }?.asString.orEmpty()
    fun JsonObject.long(name: String): Long = get(name)?.takeUnless { it.isJsonNull }?.asLong ?: 0L
    fun JsonObject.obj(name: String): JsonObject? = get(name)?.takeIf { it.isJsonObject }?.asJsonObject
    fun JsonObject.array(name: String) = get(name)?.takeIf { it.isJsonArray }?.asJsonArray
    fun JsonObject.intOrNull(name: String): Int? = get(name)?.takeUnless { it.isJsonNull }?.asInt
    fun JsonObject.doubleOrNull(name: String): Double? = get(name)?.takeUnless { it.isJsonNull }?.asDouble
    fun JsonObject.double(name: String): Double = doubleOrNull(name) ?: 0.0
    inline fun <reified E : Enum<E>> JsonObject.enumOrNull(name: String): E? =
        string(name).let { value -> enumValues<E>().firstOrNull { it.name == value } }

    fun String.readableName(): String = replace('_', ' ').lowercase().replaceFirstChar(Char::uppercase)

    fun JsonObject.hasLinkedSources(minimumCount: Int, isValid: (JsonObject) -> Boolean = { true }): Boolean {
        val sources = array("sources")?.toList().orEmpty()
        return sources.size >= minimumCount && sources.all { element ->
            val source = element.takeIf { it.isJsonObject }?.asJsonObject ?: return@all false
            source.string("name").isNotBlank() && source.string("url").startsWith("https://") && isValid(source)
        }
    }

    const val MAXIMUM_TEXT_LENGTH = 128
    val catalogIdPattern = Regex("[A-Z0-9_;.\\-]+")
}
