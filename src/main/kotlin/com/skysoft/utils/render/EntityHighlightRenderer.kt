package com.skysoft.utils.render

import com.skysoft.utils.ColorUtilities.toPackedArgb
import com.skysoft.utils.EntityUtilities.isVisibleToPlayer
import com.skysoft.utils.SkysoftClientEvents
import net.minecraft.world.entity.LivingEntity
import java.awt.Color
import java.util.concurrent.ConcurrentHashMap

object EntityHighlightRenderer {
    private val defaultSource = Any()
    private val highlights = ConcurrentHashMap<LivingEntity, ConcurrentHashMap<Any, EntityHighlight>>()

    fun register() {
        SkysoftClientEvents.onEndTick("Entity Highlight cleanup", { highlights.isNotEmpty() }) {
            highlights.keys.removeIf { entity -> !entity.isAlive }
        }
        SkysoftClientEvents.onDisconnect("Entity Highlight disconnect reset", highlights::clear)
    }

    @JvmStatic
    fun getEntityGlowColor(entity: LivingEntity): Int? = activeHighlight(entity)?.outlineColor

    @JvmStatic
    fun getEntityFillColor(entity: LivingEntity): Int? = activeHighlight(entity)?.fillColor

    fun setEntityColor(
        entity: LivingEntity,
        color: Color,
        source: Any = defaultSource,
        fillOpacity: Float = 0f,
        priority: Int = 0,
        condition: () -> Boolean,
    ) {
        val fillColor = color.toPackedArgb(fillOpacity.toDouble()).takeIf { fillOpacity > 0f }
        highlights.computeIfAbsent(entity) { ConcurrentHashMap() }[source] =
            EntityHighlight(color.rgb, fillColor, priority, condition)
    }

    fun removeEntityColor(entity: LivingEntity, source: Any = defaultSource) {
        val entityHighlights = highlights[entity] ?: return
        entityHighlights.remove(source)
        if (entityHighlights.isEmpty()) highlights.remove(entity, entityHighlights)
    }

    private fun activeHighlight(entity: LivingEntity): EntityHighlight? {
        val entityHighlights = highlights[entity] ?: return null
        if (!entity.isVisibleToPlayer()) return null
        return entityHighlights.values
            .asSequence()
            .filter { highlight -> highlight.condition() }
            .maxByOrNull(EntityHighlight::priority)
    }

    private data class EntityHighlight(
        val outlineColor: Int,
        val fillColor: Int?,
        val priority: Int,
        val condition: () -> Boolean,
    )
}
