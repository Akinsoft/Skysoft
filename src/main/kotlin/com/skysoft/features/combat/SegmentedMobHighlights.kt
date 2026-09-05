package com.skysoft.features.combat

import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Items

internal data class SkyBlockMobHighlight(
    val entity: Entity,
    val visibilityEntity: Entity,
)

internal object SegmentedMobHighlights {
    fun parts(nameplate: ArmorStand, entities: List<Entity>): List<SkyBlockMobHighlight> {
        if (!nameplate.isAlive) return emptyList()
        val entitiesById = entities.associateBy(Entity::getId)
        val physical = entitiesById[nameplate.id - 1] as? LivingEntity ?: return emptyList()
        if (!SkyBlockMobEntityMatcher.canPairWithNameplate(physical, nameplate)) return emptyList()
        return when {
            physical is ArmorStand && physical.isMobModelPart() && !physical.isMarker ->
                armorStandParts(physical, nameplate, entitiesById)
            physical.isPhysicalPart() -> physicalParts(physical, entitiesById)
            else -> emptyList()
        }
    }

    private fun physicalParts(tail: LivingEntity, entitiesById: Map<Int, Entity>): List<SkyBlockMobHighlight> {
        var physical = tail
        val highlights = mutableListOf<SkyBlockMobHighlight>()
        while (true) {
            val model = entitiesById[physical.id - 1] as? ArmorStand ?: break
            if (!model.isMobModelPart() || !model.isMarker) break
            val preceding = entitiesById[model.id - 1] as? LivingEntity ?: return emptyList()
            if (
                !preceding.isPhysicalPart() || model.distanceToSqr(physical) > MAX_PART_DISTANCE_SQ ||
                model.distanceToSqr(preceding) > MAX_PART_DISTANCE_SQ
            ) {
                return emptyList()
            }
            highlights.add(SkyBlockMobHighlight(model, preceding))
            physical = preceding
        }
        return highlights
    }

    private fun armorStandParts(
        head: ArmorStand,
        nameplate: ArmorStand,
        entitiesById: Map<Int, Entity>,
    ): List<SkyBlockMobHighlight> {
        val highlights = mutableListOf(SkyBlockMobHighlight(head, head))
        var previous = head
        var nextId = nameplate.id + 1
        while (true) {
            val part = entitiesById[nextId] as? ArmorStand ?: break
            if (!part.isMobModelPart() || part.isMarker || part.distanceToSqr(previous) > MAX_PART_DISTANCE_SQ) break
            highlights.add(SkyBlockMobHighlight(part, part))
            previous = part
            nextId++
        }
        return highlights
    }

    private fun LivingEntity.isPhysicalPart(): Boolean =
        isAlive && isInvisible && this !is ArmorStand && this !is Player

    private const val MAX_PART_DISTANCE_SQ = 16.0
}

internal fun ArmorStand.isMobModelPart(): Boolean =
    isAlive && isInvisible && !hasCustomName() && getItemBySlot(EquipmentSlot.HEAD).item == Items.PLAYER_HEAD
