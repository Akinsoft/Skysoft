package com.skysoft.data.skyblock

internal fun parseGardenPestKill(message: String): ParsedGardenPestKill? {
    val match = PEST_KILL_PATTERN.matchEntire(message) ?: return null
    val pest = match.groups["pest"]?.value ?: return null
    val itemName = match.groups["item"]?.value ?: return null
    val itemAmount = match.groups["amount"]?.value?.toIntOrNull() ?: return null
    val countsAsKill = when (pest) {
        "Field Mouse" -> itemName == "Dung"
        "Lunar Moth" -> itemName == "Enchanted Sunflower"
        else -> itemName != "Overclocker 3000"
    }
    return ParsedGardenPestKill(pest, itemName, itemAmount, countsAsKill)
}

internal data class ParsedGardenPestKill(
    val pest: String,
    val itemName: String,
    val itemAmount: Int,
    val countsAsKill: Boolean,
)

private val PEST_KILL_PATTERN = Regex(
    "^You received (?<amount>\\d+)x (?<item>.+) for killing an? (?<pest>.+)!$",
)
