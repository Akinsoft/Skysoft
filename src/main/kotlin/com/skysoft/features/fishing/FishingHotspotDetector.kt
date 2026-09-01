package com.skysoft.features.fishing

import com.skysoft.data.ClientEntitySnapshot
import com.skysoft.data.SkyBlockIsland
import com.skysoft.utils.EntityUtilities.cleanName
import com.skysoft.utils.WorldVec
import com.skysoft.utils.toWorldVec
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.decoration.ArmorStand

internal object FishingHotspotDetector {
    fun detect(): List<FishingHotspotObservation> {
        val armorStands = allEntities().filterIsInstance<ArmorStand>().filter { it.isAlive }
        val hotspotTags = armorStands.filter { it.cleanName() == HOTSPOT_NAME }
        val statTags = armorStands.filter { it.cleanName().isHotspotStatLine() }

        return hotspotTags.mapNotNull { hotspot ->
            val stat = statTags
                .filter { statTag -> statTag.distanceToSqr(hotspot) <= STAT_PAIR_DISTANCE_SQ }
                .minByOrNull { statTag -> statTag.distanceToSqr(hotspot) }
                ?.cleanName()
                ?: return@mapNotNull null
            FishingHotspotObservation(
                entityUuid = hotspot.uuid,
                share = FishingHotspotShare(stat, hotspot.blockLocationBelowTag()),
            )
        }
    }

    fun isLocationLoaded(location: WorldVec): Boolean {
        val level = Minecraft.getInstance().level ?: return false
        return level.isLoaded(BlockPos(location.x.toInt(), location.y.toInt(), location.z.toInt()))
    }

    fun playerLocation(): WorldVec? =
        Minecraft.getInstance().player?.position()?.toWorldVec()

    private fun Entity.blockLocationBelowTag(): WorldVec {
        val pos = blockPosition()
        return WorldVec(pos.x.toDouble(), (pos.y - 1).toDouble(), pos.z.toDouble())
    }

    private fun String.isHotspotStatLine(): Boolean =
        startsWith("+") && length <= MAX_STAT_LINE_LENGTH && any { it.isLetter() }

    private fun allEntities(): List<Entity> = ClientEntitySnapshot.entities()

    private const val HOTSPOT_NAME = "HOTSPOT"
    private const val STAT_PAIR_DISTANCE_SQ = 1.0
    private const val MAX_STAT_LINE_LENGTH = 64
}

internal val FISHING_HOTSPOT_ISLANDS = setOf(SkyBlockIsland.CRIMSON_ISLE, SkyBlockIsland.BACKWATER_BAYOU)
