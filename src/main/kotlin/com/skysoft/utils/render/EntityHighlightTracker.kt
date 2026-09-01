package com.skysoft.utils.render

import net.minecraft.world.entity.Entity

internal class EntityHighlightTracker<E : Entity>(private val source: Any) {
    private val entities = mutableSetOf<E>()

    fun isNotEmpty(): Boolean = entities.isNotEmpty()

    operator fun contains(entity: E): Boolean = entity in entities

    fun replaceWith(next: Collection<E>): List<E> {
        val nextEntities = next.toSet()
        entities.filter { entity -> entity !in nextEntities }.forEach { entity ->
            EntityHighlightRenderer.removeEntityColor(entity, source)
            entities.remove(entity)
        }
        return nextEntities.filter { entity -> entities.add(entity) }
    }

    fun clear() {
        entities.forEach { entity -> EntityHighlightRenderer.removeEntityColor(entity, source) }
        entities.clear()
    }
}
