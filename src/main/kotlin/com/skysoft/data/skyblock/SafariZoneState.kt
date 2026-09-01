package com.skysoft.data.skyblock

import com.skysoft.data.SkyBlockIsland
import com.skysoft.utils.SkysoftClientEvents
import net.minecraft.client.Minecraft
import net.minecraft.resources.Identifier

object SafariZoneState {
    var currentZone: SafariZone? = null
        private set

    var currentBiome: SafariBiome? = null
        private set

    var currentBiomeId: Identifier? = null
        private set

    fun register() {
        SkysoftClientEvents.onEndTick(
            "Safari zone state",
            isActive = { SkyBlockIsland.SAFARI.isInIsland() || currentBiomeId != null },
            action = ::update,
        )
        SkysoftClientEvents.onDisconnect("Safari zone state reset") { setBiome(null) }
    }

    private fun update(minecraft: Minecraft) {
        val level = minecraft.level
        val player = minecraft.player
        val biomeId = if (SkyBlockIsland.SAFARI.isInIsland() && level != null && player != null) {
            level.getBiome(player.blockPosition()).unwrapKey().orElse(null)?.identifier()
        } else {
            null
        }
        setBiome(biomeId)
    }

    private fun setBiome(biomeId: Identifier?) {
        currentBiomeId = biomeId
        currentBiome = SafariBiome.fromId(biomeId)
        currentZone = currentBiome?.zone
    }
}

enum class SafariZone {
    SPAWN,
    CAVERN,
    FOREST,
    HAUNTED,
    ICY,
}

enum class SafariBiome(
    val biomeId: Identifier,
    val zone: SafariZone,
) {
    TORRHUS(safariBiome("torrhus"), SafariZone.SPAWN),
    CAVERN(safariBiome("cavern"), SafariZone.CAVERN),
    FOREST(safariBiome("forest"), SafariZone.FOREST),
    HAUNTED(safariBiome("haunted"), SafariZone.HAUNTED),
    ICY(safariBiome("icy"), SafariZone.ICY),
    ICY_CAVES(safariBiome("icy_caves"), SafariZone.ICY),
    ;

    companion object {
        private val BY_ID = entries.associateBy(SafariBiome::biomeId)

        fun fromId(biomeId: Identifier?): SafariBiome? = BY_ID[biomeId]
    }
}

private fun safariBiome(path: String): Identifier = Identifier.fromNamespaceAndPath("hypixel", path)
