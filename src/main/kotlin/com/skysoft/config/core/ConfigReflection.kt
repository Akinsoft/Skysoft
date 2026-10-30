package com.skysoft.config.core

import com.google.gson.annotations.Expose
import io.github.notenoughupdates.moulconfig.processor.ProcessedCategory
import io.github.notenoughupdates.moulconfig.processor.ProcessedCategoryImpl
import io.github.notenoughupdates.moulconfig.processor.ProcessedOption
import java.lang.reflect.Field
import java.lang.reflect.Modifier

internal fun Class<*>.hierarchyFields(): Sequence<Field> = generateSequence(this) { it.superclass }
    .takeWhile { it != Any::class.java }
    .flatMap { it.declaredFields.asSequence() }

internal fun Class<*>.persistedFields(): Sequence<Field> = hierarchyFields()
    .filter { !Modifier.isStatic(it.modifiers) && it.isAnnotationPresent(Expose::class.java) }
    .onEach { it.isAccessible = true }

internal fun isSkysoftObject(value: Any): Boolean =
    value !is Enum<*> && value.javaClass.name.startsWith(SKYSOFT_PACKAGE_PREFIX)

internal fun ProcessedOption.fieldOrNull(): Field? = (this as? ProcessedOption.HasField)?.field

internal fun ProcessedCategory.implementation(): ProcessedCategoryImpl {
    require(this is ProcessedCategoryImpl) {
        "SoftConfig returned an unsupported category implementation: ${javaClass.name}"
    }
    return this
}

private const val SKYSOFT_PACKAGE_PREFIX = "com.skysoft."
