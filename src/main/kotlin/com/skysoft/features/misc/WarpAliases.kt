package com.skysoft.features.misc

import com.skysoft.config.SkysoftConfigGui
import com.skysoft.data.SkyBlockIsland
import com.skysoft.data.hypixel.HypixelLocationState

object WarpAliases {
    @JvmStatic
    fun rewrite(command: String): String =
        if (SkysoftConfigGui.config().misc.shortWarpCommands && HypixelLocationState.inSkyBlock) {
            rewriteWarpAlias(command, HypixelLocationState.currentIsland) ?: command
        } else {
            command
        }
}

internal fun rewriteWarpAlias(command: String, island: SkyBlockIsland?): String? {
    val normalized = command.trim().lowercase()
    if (island == SkyBlockIsland.GARDEN) {
        if (normalized == "home") return "warp garden"
        if (normalized == "barn") return "plottp barn"
        if (normalized.startsWith("tp ")) {
            return normalized.removePrefix("tp ").trim().takeIf(String::isNotEmpty)?.let { "plottp $it" }
        }
    }
    if (normalized == "jerry" && island == SkyBlockIsland.PRIVATE_ISLANDS) return null
    return normalized.takeIf(WARP_ALIASES::contains)?.let { "warp $it" }
}

private val WARP_ALIASES = setOf(
    "arachne",
    "atoll",
    "backwater",
    "base",
    "basecamp",
    "barn",
    "bayou",
    "camp",
    "carnival",
    "castle",
    "ch",
    "ci",
    "cn",
    "crimson",
    "crypt",
    "crypts",
    "crystals",
    "da",
    "deep",
    "deeper",
    "desert",
    "dh",
    "dhub",
    "dmines",
    "drag",
    "dragons",
    "dungeons",
    "dungeon_hub",
    "dwarves",
    "elizabeth",
    "end",
    "farming",
    "foraging",
    "forge",
    "galatea",
    "garden",
    "glacite",
    "glowing",
    "gold",
    "gt",
    "hollows",
    "home",
    "howl",
    "howling_cave",
    "hub",
    "island",
    "isle",
    "jerry",
    "jungle",
    "kuudra",
    "loch",
    "lotus",
    "mines",
    "moby",
    "mound",
    "murk",
    "murkwater",
    "museum",
    "nest",
    "nether",
    "nuc",
    "nucleus",
    "park",
    "rift",
    "sepulture",
    "skull",
    "smold",
    "smoldering",
    "smoldering_tomb",
    "spider",
    "spiders",
    "stonks",
    "taylor",
    "the_rift",
    "top",
    "tower",
    "trap",
    "trapper",
    "trees",
    "tunnel",
    "tunnels",
    "village",
    "void",
    "winter",
    "wiz",
    "wizard",
    "wizard_tower",
    "workshop",
)
