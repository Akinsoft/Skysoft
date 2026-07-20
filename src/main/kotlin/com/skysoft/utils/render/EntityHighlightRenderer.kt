package com.skysoft.utils.render

import com.skysoft.utils.EntityUtilities.isVisibleToPlayer
import com.skysoft.utils.SkysoftClientEvents
import net.minecraft.world.entity.LivingEntity
import java.awt.Color
import java.util.concurrent.ConcurrentHashMap

object EntityHighlightRenderer {
    private val highlights = ConcurrentHashMap<LivingEntity, EntityHighlight>()

    fun register() {
        SkysoftClientEvents.onEndTick("Entity Highlight cleanup", { highlights.isNotEmpty() }) {
            highlights.keys.removeIf { entity -> !entity.isAlive }
        }
        SkysoftClientEvents.onDisconnect("Entity Highlight disconnect reset", highlights::clear)
    }

    @JvmStatic
    fun getEntityGlowColor(entity: LivingEntity): Int? {
        val highlight = highlights[entity] ?: return null
        return highlight.color.rgb.takeIf { entity.isVisibleToPlayer() && highlight.condition() }
    }

    fun setEntityColor(entity: LivingEntity, color: Color, condition: () -> Boolean) {
        highlights[entity] = EntityHighlight(color, condition)
    }

    fun removeEntityColor(entity: LivingEntity) {
        highlights.remove(entity)
    }

    private data class EntityHighlight(val color: Color, val condition: () -> Boolean)
}
