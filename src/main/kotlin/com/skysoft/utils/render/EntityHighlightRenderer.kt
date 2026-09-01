package com.skysoft.utils.render

import com.skysoft.utils.ColorUtilities.toPackedArgb
import com.skysoft.utils.EntityUtilities.isVisibleToPlayer
import com.skysoft.utils.SkysoftClientEvents
import java.awt.Color
import java.util.concurrent.ConcurrentHashMap
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity

object EntityHighlightRenderer {
    private val highlights = ConcurrentHashMap<Entity, ConcurrentHashMap<Any, EntityHighlight>>()

    fun register() {
        SkysoftClientEvents.onEndTick("Entity Highlight cleanup", { highlights.isNotEmpty() }) {
            highlights.keys.removeIf { entity -> !entity.isAlive }
        }
        SkysoftClientEvents.onDisconnect("Entity Highlight disconnect reset", highlights::clear)
    }

    @JvmStatic
    fun getEntityGlowColor(entity: Entity): Int? = activeHighlight(entity)?.outlineColor

    @JvmStatic
    fun getEntityFillColor(entity: LivingEntity): Int? = activeHighlight(entity)?.fillColor

    fun setEntityColor(
        entity: Entity,
        color: Color,
        source: Any,
        fillOpacity: Float = 0f,
        priority: Int = 0,
        visibilityEntity: Entity = entity,
        condition: () -> Boolean,
    ) {
        val fillColor = color.toPackedArgb(fillOpacity.toDouble()).takeIf { fillOpacity > 0f }
        highlights.computeIfAbsent(entity) { ConcurrentHashMap() }[source] =
            EntityHighlight(color.rgb, fillColor, priority, visibilityEntity, condition)
    }

    fun removeEntityColor(entity: Entity, source: Any) {
        val entityHighlights = highlights[entity] ?: return
        entityHighlights.remove(source)
        if (entityHighlights.isEmpty()) highlights.remove(entity, entityHighlights)
    }

    private fun activeHighlight(entity: Entity): EntityHighlight? {
        val entityHighlights = highlights[entity] ?: return null
        return entityHighlights.values
            .asSequence()
            .filter { highlight -> highlight.visibilityEntity.isVisibleToPlayer() && highlight.condition() }
            .maxByOrNull(EntityHighlight::priority)
    }

    private data class EntityHighlight(
        val outlineColor: Int,
        val fillColor: Int?,
        val priority: Int,
        val visibilityEntity: Entity,
        val condition: () -> Boolean,
    )
}
