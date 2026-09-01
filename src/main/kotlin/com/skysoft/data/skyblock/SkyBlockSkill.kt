package com.skysoft.data.skyblock

import com.google.gson.annotations.Expose

enum class SkyBlockSkill(val displayName: String, val maxLevel: Int) {
    COMBAT("Combat", 60),
    FARMING("Farming", 60),
    FISHING("Fishing", 50),
    MINING("Mining", 60),
    FORAGING("Foraging", 54),
    ENCHANTING("Enchanting", 60),
    ALCHEMY("Alchemy", 50),
    CARPENTRY("Carpentry", 50),
    TAMING("Taming", 60),
    HUNTING("Hunting", 25),
    ;

    val uppercaseName: String = displayName.uppercase()

    companion object {
        fun getByNameOrNull(name: String): SkyBlockSkill? =
            entries.firstOrNull { it.displayName.equals(name, ignoreCase = true) }
    }
}

data class SkyBlockSkillInfo(
    @Expose var level: Int = 0,
    @Expose var lastGain: String = "",
    @Expose var totalXp: Long = 0,
    @Expose var currentXp: Long = 0,
    @Expose var currentXpMax: Long = 0,
    @Expose var overflowLevel: Int = 0,
    @Expose var overflowTotalXp: Long = 0,
    @Expose var overflowCurrentXp: Long = 0,
    @Expose var overflowCurrentXpMax: Long = 0,
)
