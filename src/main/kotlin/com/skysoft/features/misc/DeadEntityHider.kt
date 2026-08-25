package com.skysoft.features.misc

import com.skysoft.config.SkysoftConfigGui
import java.util.IdentityHashMap
import net.minecraft.client.Minecraft
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.decoration.ArmorStand

object DeadEntityHider {
    private var linkedDyingCacheTick = Long.MIN_VALUE
    private val linkedDyingCache = IdentityHashMap<ArmorStand, Boolean>()

    @JvmStatic
    fun shouldHide(entity: Entity): Boolean {
        if (!SkysoftConfigGui.config().misc.hideDeadEntities) return false
        return when {
            entity is LivingEntity && entity.isDeadOrDying -> true
            entity is ArmorStand -> entity.hasLinkedDyingEntity()
            else -> false
        }
    }

    private fun ArmorStand.hasLinkedDyingEntity(): Boolean {
        val tick = Minecraft.getInstance().level?.gameTime ?: return false
        if (tick != linkedDyingCacheTick) {
            linkedDyingCacheTick = tick
            linkedDyingCache.clear()
        }
        return linkedDyingCache.getOrPut(this) { linkedDyingEntity() != null }
    }

    private fun ArmorStand.linkedDyingEntity(): LivingEntity? {
        if (!isInvisible || !isCustomNameVisible || customName == null) return null
        val level = Minecraft.getInstance().level ?: return null
        return (1..MAX_NAMEPLATE_ENTITY_ID_OFFSET).firstNotNullOfOrNull { offset ->
            val candidate = level.getEntity(id - offset) as? LivingEntity ?: return@firstNotNullOfOrNull null
            candidate.takeIf { entity -> entity !is ArmorStand && entity.isDeadOrDying && isNameplateFor(entity) }
        }
    }

    private fun ArmorStand.isNameplateFor(entity: LivingEntity): Boolean {
        val dx = x - entity.x
        val dz = z - entity.z
        val verticalOffset = y - entity.y
        return dx * dx + dz * dz <= NAMEPLATE_HORIZONTAL_DISTANCE_SQUARED &&
            verticalOffset in 0.0..NAMEPLATE_MAX_VERTICAL_DISTANCE
    }

    private const val MAX_NAMEPLATE_ENTITY_ID_OFFSET = 4
    private const val NAMEPLATE_HORIZONTAL_DISTANCE_SQUARED = 1.0
    private const val NAMEPLATE_MAX_VERTICAL_DISTANCE = 4.0
}
