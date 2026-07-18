package com.skysoft.config.discovery

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName
import io.github.notenoughupdates.moulconfig.annotations.Accordion
import io.github.notenoughupdates.moulconfig.annotations.Category
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorDropdown
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption
import io.github.notenoughupdates.moulconfig.Config
import io.github.notenoughupdates.moulconfig.processor.MoulConfigProcessor
import io.github.notenoughupdates.moulconfig.processor.ProcessedOption
import java.lang.reflect.GenericArrayType
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.WildcardType
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Collections
import java.util.IdentityHashMap

internal data class NewSettingDescriptor(
    val id: String,
    val path: String,
    val signature: String,
    val option: ProcessedOption,
    val serializedPaths: Set<String>,
)

internal data class NewSettingsSchema(
    val descriptors: List<NewSettingDescriptor>,
) {
    val byId: Map<String, NewSettingDescriptor> = descriptors.associateBy(NewSettingDescriptor::id)
    val signatures: Map<String, String> = descriptors.associate { it.id to it.signature }

    companion object {
        fun from(processor: MoulConfigProcessor<*>): NewSettingsSchema {
            val serializedPaths = serializedOptionPaths(processor.configObject)
            val descriptors = processor.allCategories.values
                .flatMap { it.options }
                .mapNotNull { descriptorFor(it, serializedPaths) }
            require(descriptors.map(NewSettingDescriptor::id).distinct().size == descriptors.size) {
                "SoftConfig contains duplicate persistent setting identities"
            }
            return NewSettingsSchema(descriptors)
        }

        private fun descriptorFor(
            option: ProcessedOption,
            serializedPaths: Map<String, Set<String>>,
        ): NewSettingDescriptor? {
            val field = (option as? ProcessedOption.HasField)?.field ?: return null
            val expose = field.getAnnotation(Expose::class.java) ?: return null
            if (!expose.serialize || field.isAnnotationPresent(Accordion::class.java)) return null
            val editorNames = editorNames(field)
            if (editorNames.isEmpty() || editorNames.any(EXCLUDED_EDITOR_NAMES::contains)) return null

            val identity = "${field.declaringClass.name}#${field.name}"
            return NewSettingDescriptor(
                id = identity,
                path = option.path,
                signature = discoverySignature(field),
                option = option,
                serializedPaths = requireNotNull(serializedPaths[identity]) {
                    "Persistent SoftConfig option is missing a serialized config path: $identity"
                },
            )
        }
    }
}

internal fun serializedOptionPaths(config: Config): Map<String, Set<String>> {
    val paths = linkedMapOf<String, MutableSet<String>>()
    val visited = Collections.newSetFromMap(IdentityHashMap<Any, Boolean>())
    collectSerializedOptionPaths(config, setOf(""), paths, visited)
    return paths
}

private fun collectSerializedOptionPaths(
    container: Any,
    prefixes: Set<String>,
    paths: MutableMap<String, MutableSet<String>>,
    visited: MutableSet<Any>,
) {
    if (!visited.add(container)) return
    allFields(container.javaClass).forEach { field ->
        val expose = field.getAnnotation(Expose::class.java) ?: return@forEach
        if (!expose.serialize) return@forEach
        val fieldPaths = prefixes.flatMapTo(linkedSetOf()) { prefix ->
            serializedNames(field).map { name -> if (prefix.isEmpty()) name else "$prefix.$name" }
        }
        if (field.isAnnotationPresent(ConfigOption::class.java)) {
            val identity = "${field.declaringClass.name}#${field.name}"
            paths.getOrPut(identity, ::linkedSetOf).addAll(fieldPaths)
        }
        if (!field.isAnnotationPresent(Category::class.java) && !field.isAnnotationPresent(Accordion::class.java)) {
            return@forEach
        }
        require(field.trySetAccessible()) { "Cannot read serialized config structure field: $field" }
        val child = requireNotNull(field.get(container)) { "Serialized config structure field is null: $field" }
        collectSerializedOptionPaths(child, fieldPaths, paths, visited)
    }
}

private fun allFields(type: Class<*>): List<java.lang.reflect.Field> {
    val fields = mutableListOf<java.lang.reflect.Field>()
    var currentType: Class<*>? = type
    while (currentType != null && currentType != Any::class.java) {
        fields += currentType.declaredFields
        currentType = currentType.superclass
    }
    return fields
}

private fun serializedNames(field: java.lang.reflect.Field): List<String> {
    val serializedName = field.getAnnotation(SerializedName::class.java) ?: return listOf(field.name)
    return listOf(serializedName.value) + serializedName.alternate
}

internal fun discoverySignature(field: java.lang.reflect.Field): String {
    val dropdownChoices = field.getAnnotation(ConfigEditorDropdown::class.java)
        ?.values
        ?.sorted()
        ?.joinToString(separator = ",")
        .orEmpty()
    val enumChoices = enumChoices(field.genericType).sorted().joinToString(separator = ",")
    val signatureSource = listOf(
        field.genericType.typeName,
        editorNames(field).joinToString(separator = ","),
        dropdownChoices,
        enumChoices,
    ).joinToString(separator = "|")
    return MessageDigest.getInstance(SHA_256)
        .digest(signatureSource.toByteArray(StandardCharsets.UTF_8))
        .joinToString(separator = "") { byte ->
            (byte.toInt() and UNSIGNED_BYTE_MASK).toString(HEX_RADIX).padStart(HEX_BYTE_LENGTH, '0')
        }
}

private fun editorNames(field: java.lang.reflect.Field): List<String> =
    field.annotations
        .map { it.annotationClass.java }
        .filter { it.packageName == MOULCONFIG_ANNOTATION_PACKAGE }
        .map(Class<*>::getSimpleName)
        .filter { it.startsWith(CONFIG_EDITOR_PREFIX) }
        .sorted()

private fun enumChoices(type: Type): Set<String> =
    when (type) {
        is Class<*> -> if (type.isEnum) {
            type.enumConstants.mapTo(linkedSetOf()) { "${type.name}#${(it as Enum<*>).name}" }
        } else {
            emptySet()
        }
        is ParameterizedType -> type.actualTypeArguments.flatMapTo(linkedSetOf(), ::enumChoices)
        is GenericArrayType -> enumChoices(type.genericComponentType)
        is WildcardType -> (type.upperBounds + type.lowerBounds).flatMapTo(linkedSetOf(), ::enumChoices)
        else -> emptySet()
    }

private const val MOULCONFIG_ANNOTATION_PACKAGE = "io.github.notenoughupdates.moulconfig.annotations"
private const val CONFIG_EDITOR_PREFIX = "ConfigEditor"
private val EXCLUDED_EDITOR_NAMES = setOf("ConfigEditorButton", "ConfigEditorInfoText")
private const val SHA_256 = "SHA-256"
private const val UNSIGNED_BYTE_MASK = 0xFF
private const val HEX_RADIX = 16
private const val HEX_BYTE_LENGTH = 2

internal data class NewSettingsDetection(
    val addedIds: Set<String>,
    val changedIds: Set<String>,
) {
    val discoveredIds: Set<String> = addedIds + changedIds
}

internal fun detectNewSettings(
    previousSignatures: Map<String, String>,
    currentSignatures: Map<String, String>,
): NewSettingsDetection {
    val addedIds = currentSignatures.keys - previousSignatures.keys
    val changedIds = currentSignatures.keys
        .intersect(previousSignatures.keys)
        .filterTo(linkedSetOf()) { previousSignatures.getValue(it) != currentSignatures.getValue(it) }
    return NewSettingsDetection(addedIds, changedIds)
}

internal fun bootstrapKnownSignatures(
    schema: NewSettingsSchema,
    loadedJson: JsonObject,
): Map<String, String> =
    schema.descriptors
        .filter { descriptor -> descriptor.serializedPaths.any(loadedJson::hasPath) }
        .associate { it.id to it.signature }

private fun JsonObject.hasPath(path: String): Boolean {
    var current: JsonElement = this
    path.split('.').forEach { segment ->
        if (!current.isJsonObject) return false
        val child = current.asJsonObject.get(segment) ?: return false
        current = child
    }
    return true
}
