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

interface SkyBlockSkillView {
    val level: Int
    val lastGain: String
    val totalXp: Long
    val currentXp: Long
    val currentXpMax: Long
    val overflowLevel: Int
    val overflowTotalXp: Long
    val overflowCurrentXp: Long
    val overflowCurrentXpMax: Long
}

data class SkyBlockSkillInfo(
    @Expose override var level: Int = 0,
    @Expose override var lastGain: String = "",
    @Expose override var totalXp: Long = 0,
    @Expose override var currentXp: Long = 0,
    @Expose override var currentXpMax: Long = 0,
    @Expose override var overflowLevel: Int = 0,
    @Expose override var overflowTotalXp: Long = 0,
    @Expose override var overflowCurrentXp: Long = 0,
    @Expose override var overflowCurrentXpMax: Long = 0,
) : SkyBlockSkillView
