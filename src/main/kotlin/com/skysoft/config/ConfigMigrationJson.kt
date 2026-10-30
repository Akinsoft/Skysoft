package com.skysoft.config

import com.google.gson.JsonElement
import com.google.gson.JsonObject

internal fun JsonElement?.objectOrNull(): JsonObject? = this?.takeIf { it.isJsonObject }?.asJsonObject

internal fun JsonElement.isBooleanValue(): Boolean = isJsonPrimitive && asJsonPrimitive.isBoolean

internal fun JsonElement?.booleanOrNull(): Boolean? = this?.takeIf { it.isBooleanValue() }?.asBoolean

internal fun JsonObject.getObjectOrNull(vararg path: String): JsonObject? =
    path.fold<String, JsonObject?>(this) { parent, name -> parent?.get(name).objectOrNull() }

internal fun JsonObject.getOrCreateObject(vararg path: String): JsonObject =
    path.fold(this) { parent, name -> parent.get(name).objectOrNull() ?: JsonObject().also { parent.add(name, it) } }

internal fun JsonObject.wrapFieldAsEnabled(fieldName: String, shouldWrap: (JsonElement) -> Boolean) {
    val value = get(fieldName)?.takeIf(shouldWrap) ?: return
    add(fieldName, JsonObject().also { it.add("enabled", value.deepCopy()) })
}

internal fun JsonObject.copyFieldInto(
    target: () -> JsonObject,
    fieldName: String,
    targetFieldName: String = fieldName,
) {
    val value = get(fieldName) ?: return
    val targetJson = target()
    if (!targetJson.has(targetFieldName)) targetJson.add(targetFieldName, value.deepCopy())
}

internal fun JsonObject.moveFieldInto(
    target: () -> JsonObject,
    fieldName: String,
    targetFieldName: String = fieldName,
) {
    copyFieldInto(target, fieldName, targetFieldName)
    remove(fieldName)
}

internal fun JsonObject.moveFieldInto(
    target: JsonObject,
    fieldName: String,
    targetFieldName: String = fieldName,
) {
    moveFieldInto({ target }, fieldName, targetFieldName)
}

internal fun JsonObject.moveFieldsInto(targetName: String, fieldNames: Iterable<String>) {
    moveFieldsInto(getOrCreateObject(targetName), fieldNames)
}

internal fun JsonObject.moveFieldsInto(target: JsonObject, fieldNames: Iterable<String>) {
    fieldNames.forEach { fieldName -> moveFieldInto(target, fieldName) }
}

internal fun JsonObject.moveFieldsIntoSettingsAndDetails(settingsFields: Iterable<String>, detailsFields: Iterable<String>) {
    moveFieldsInto("settings", settingsFields)
    moveFieldsInto("details", detailsFields)
}

internal fun JsonObject.moveFieldsInto(targetName: String, vararg fieldNames: String) {
    moveFieldsInto(targetName, fieldNames.asIterable())
}

internal fun JsonObject.moveFieldsInto(target: JsonObject, vararg fieldNames: String) {
    moveFieldsInto(target, fieldNames.asIterable())
}
