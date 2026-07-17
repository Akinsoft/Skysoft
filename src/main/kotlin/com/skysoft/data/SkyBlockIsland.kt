package com.skysoft.data

import com.skysoft.data.hypixel.HypixelLocationState

enum class SkyBlockIsland(
    val displayName: String,
    private vararg val apiNames: String,
) {
    PRIVATE_ISLANDS("Private Islands", "dynamic"),
    THE_END("The End", "combat_3"),
    KUUDRA("Kuudra", "kuudra"),
    DWARVEN_MINES("Dwarven Mines", "mining_3"),
    GLACITE_TUNNELS("Glacite Tunnels"),
    DUNGEON_HUB("Dungeon Hub", "dungeon_hub"),
    DUNGEONS("Dungeons", "dungeon"),
    HUB("Hub", "hub"),
    THE_FARMING_ISLANDS("The Farming Islands", "farming_1"),
    CRYSTAL_HOLLOWS("Crystal Hollows", "crystal_hollows"),
    THE_PARK("The Park", "foraging_1"),
    DEEP_CAVERNS("Deep Caverns", "mining_2"),
    GOLD_MINE("Gold Mine", "mining_1"),
    GARDEN("Garden", "garden"),
    SPIDERS_DEN("Spider's Den", "combat_1"),
    JERRYS_WORKSHOP("Jerry's Workshop", "winter"),
    THE_RIFT("The Rift", "rift"),
    GLACITE_MINESHAFTS("Glacite Mineshafts", "mineshaft"),
    CRIMSON_ISLE("Crimson Isle", "crimson_isle"),
    BACKWATER_BAYOU("Backwater Bayou", "fishing_1", "backwater_bayou"),
    GALATEA("Galatea", "foraging_2"),
    LOTUS_ATOLL("Lotus Atoll", "lotus_atoll"),
    ;

    fun isInIsland(): Boolean = HypixelLocationState.inSkyBlock && HypixelLocationState.currentIsland == this

    override fun toString(): String = displayName

    companion object {
        fun getByLocation(mode: String?, map: String?): SkyBlockIsland? {
            entries.firstOrNull { map.equals(it.displayName, ignoreCase = true) }?.let { return it }
            map?.legacyMapIsland()?.let { return it }
            return entries.firstOrNull { island ->
                island.apiNames.any { mode.equalsIslandId(it) } || mode.equalsIslandId(island.displayName)
            }
        }

        fun getByConditionValue(value: String): SkyBlockIsland? = when (value) {
            "PRIVATE_ISLAND", "PRIVATE_ISLAND_GUEST" -> PRIVATE_ISLANDS
            "CATACOMBS" -> DUNGEONS
            "GARDEN_GUEST" -> GARDEN
            "MINESHAFT" -> GLACITE_MINESHAFTS
            "DARK_AUCTION" -> null
            else -> runCatching { valueOf(value) }.getOrNull()
        }

        private fun String.legacyMapIsland(): SkyBlockIsland? = when {
            equals("Private Island", ignoreCase = true) || equals("Private Island Guest", ignoreCase = true) ->
                PRIVATE_ISLANDS
            equals("Garden Guest", ignoreCase = true) -> GARDEN
            equals("Catacombs", ignoreCase = true) || startsWith("The Catacombs", ignoreCase = true) -> DUNGEONS
            equals("Mineshaft", ignoreCase = true) || equals("Glacite Mineshaft", ignoreCase = true) ->
                GLACITE_MINESHAFTS
            else -> null
        }

        private fun String?.equalsIslandId(other: String): Boolean =
            this?.replace("-", "_")?.replace(" ", "_")?.equals(other.replace(" ", "_"), ignoreCase = true) == true
    }
}
