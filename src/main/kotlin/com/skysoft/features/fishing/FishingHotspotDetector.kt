package com.skysoft.features.fishing

import com.skysoft.data.ClientEntitySnapshot
import com.skysoft.data.SkyBlockIsland
import com.skysoft.data.hypixel.HypixelLocationState
import com.skysoft.utils.EntityUtilities.cleanName
import com.skysoft.utils.WorldVec
import com.skysoft.utils.toBlockPos
import com.skysoft.utils.toWorldVec
import net.minecraft.client.Minecraft
import net.minecraft.world.entity.decoration.ArmorStand

internal object FishingHotspotDetector {
    fun detect(): List<FishingHotspotObservation> {
        val armorStands = ClientEntitySnapshot.entities().filterIsInstance<ArmorStand>().filter { it.isAlive }
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
                share = FishingHotspotShare(stat, hotspot.blockPosition().below().toWorldVec()),
            )
        }
    }

    fun isLocationLoaded(location: WorldVec): Boolean {
        val level = Minecraft.getInstance().level ?: return false
        return level.isLoaded(location.toBlockPos())
    }

    private fun String.isHotspotStatLine(): Boolean =
        startsWith("+") && length <= MAX_STAT_LINE_LENGTH && any { it.isLetter() }

    private const val HOTSPOT_NAME = "HOTSPOT"
    private const val STAT_PAIR_DISTANCE_SQ = 1.0
    private const val MAX_STAT_LINE_LENGTH = 64
}

private val FISHING_HOTSPOT_ISLANDS = setOf(SkyBlockIsland.CRIMSON_ISLE, SkyBlockIsland.BACKWATER_BAYOU)

internal fun isOnFishingHotspotIsland(): Boolean =
    HypixelLocationState.inSkyBlock && HypixelLocationState.currentIsland in FISHING_HOTSPOT_ISLANDS
