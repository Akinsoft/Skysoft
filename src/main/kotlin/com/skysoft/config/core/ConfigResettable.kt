package com.skysoft.config.core

import io.github.notenoughupdates.moulconfig.observer.Property
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Modifier

interface ConfigResettable {
    val resetConstructorScalar: Float? get() = null

    fun reset() {
        val defaults = defaultInstance()
            ?: error("Cannot reset ${javaClass.name}: no default constructor is available")
        for (field in resettableFields()) {
            field.isAccessible = true
            val current = field.get(this)
            val default = field.get(defaults)
            when {
                current is Property<*> && default is Property<*> -> resetProperty(current, default)

                current is ConfigResettable -> current.reset()

                current is MutableCollection<*> && default is Collection<*> -> resetCollection(current, default)

                current is MutableMap<*, *> && default is Map<*, *> -> resetMap(current, default)

                !Modifier.isFinal(field.modifiers) -> field.set(this, default)
            }
        }
    }

    private fun resetProperty(current: Property<*>, default: Property<*>) {
        Property::class.java.getMethod("set", Any::class.java).invoke(current, default.get())
    }

    private fun resetCollection(current: MutableCollection<*>, default: Collection<*>) {
        current.clear()
        current.javaClass.getMethod("addAll", Collection::class.java).invoke(current, default)
    }

    private fun resetMap(current: MutableMap<*, *>, default: Map<*, *>) {
        current.clear()
        current.javaClass.getMethod("putAll", Map::class.java).invoke(current, default)
    }

    private fun defaultInstance(): Any? {
        resetConstructorScalar?.let { scalar ->
            javaClass.singleFloatConstructor()?.let { constructor ->
                return constructor.newInstance(scalar)
            }
        }
        return javaClass.noArgConstructor()?.newInstance()
    }

    private fun Class<*>.noArgConstructor(): Constructor<*>? = declaredConstructors
        .firstOrNull { it.parameterCount == 0 }
        ?.also { it.isAccessible = true }

    private fun Class<*>.singleFloatConstructor(): Constructor<*>? = declaredConstructors
        .firstOrNull { constructor ->
            constructor.parameterTypes.singleOrNull()?.let {
                it == Float::class.javaPrimitiveType || it == Float::class.javaObjectType
            } == true
        }
        ?.also { it.isAccessible = true }

    private fun resettableFields(): Sequence<Field> = javaClass.hierarchyFields()
        .filterNot { field ->
            Modifier.isStatic(field.modifiers) || Modifier.isTransient(field.modifiers) || field.isSynthetic
        }
}
