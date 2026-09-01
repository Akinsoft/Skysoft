package com.skysoft.config

import com.google.gson.JsonObject

internal fun JsonObject.getObjectOrNull(name: String): JsonObject? =
    get(name)?.takeIf { it.isJsonObject }?.asJsonObject

internal fun JsonObject.getOrCreateObject(name: String): JsonObject =
    getObjectOrNull(name) ?: JsonObject().also { add(name, it) }

internal fun JsonObject.moveFieldInto(
    target: JsonObject,
    fieldName: String,
    targetFieldName: String = fieldName,
) {
    val value = get(fieldName) ?: return
    if (!target.has(targetFieldName)) target.add(targetFieldName, value.deepCopy())
    remove(fieldName)
}

internal fun JsonObject.moveFieldsInto(targetName: String, fieldNames: Iterable<String>) {
    moveFieldsInto(getOrCreateObject(targetName), fieldNames)
}

internal fun JsonObject.moveFieldsInto(target: JsonObject, fieldNames: Iterable<String>) {
    fieldNames.forEach { fieldName -> moveFieldInto(target, fieldName) }
}

internal fun JsonObject.moveFieldsInto(targetName: String, vararg fieldNames: String) {
    moveFieldsInto(targetName, fieldNames.asIterable())
}

internal fun JsonObject.moveFieldsInto(target: JsonObject, vararg fieldNames: String) {
    moveFieldsInto(target, fieldNames.asIterable())
}
