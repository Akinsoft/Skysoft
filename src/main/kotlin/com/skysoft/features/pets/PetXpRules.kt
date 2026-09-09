package com.skysoft.features.pets

import com.skysoft.data.skyblock.SkillExpGainApi
import com.skysoft.data.skyblock.SkyBlockSkill
import com.skysoft.data.skyblock.pets.PetRepository
import com.skysoft.data.StoredPetData
import com.skysoft.data.ProfileStorageApi
import com.skysoft.data.skyblock.AccessoryBagData
import com.skysoft.data.skyblock.AttributeShardCatalog
import com.skysoft.data.skyblock.MayorPerkApi
import net.minecraft.client.Minecraft

internal object PetXpRules {
    fun estimatePetXp(petData: StoredPetData, skill: SkyBlockSkill, skillXp: Double): Double? {
        val petType = PetRepository.getPetType(petData.fauxInternalName) ?: return null
        val baseMultiplier = skillBaseMultiplier(petType, skill) ?: return null
        val tamingMultiplier = 1.0 + tamingLevel() / PERCENT_DENOMINATOR
        val dianaMultiplier = if (MayorPerkApi.petXpBuffActive) DIANA_PET_XP_MULTIPLIER else 1.0
        val beastmasterMultiplier = getBeastmasterMultiplier()
        val itemMultiplier = petData.heldItemInternalName.petItemMultiplier(skill)
        val battleExperienceMultiplier = battleExperienceMultiplier(skill)
        val customMultiplier = PetRepository.getPetXpMultiplier(petData.fauxInternalName)
        return skillXp * baseMultiplier * tamingMultiplier * dianaMultiplier *
            beastmasterMultiplier * itemMultiplier * battleExperienceMultiplier * customMultiplier
    }

    fun skillBaseMultiplier(petType: String, skill: SkyBlockSkill): Double? = when (skill) {
        SkyBlockSkill.TAMING,
        SkyBlockSkill.CARPENTRY,
        -> null

        else -> when {
            petType.replace(" ", "_") in NON_SKILL_PET_TYPE_PREFIXES -> null
            petType == "ALL" -> 1.0
            petType == skill.uppercaseName -> matchingSkillMultiplier(skill)
            else -> nonMatchingSkillMultiplier(skill)
        }
    }

    fun expShareBaseRate(): Double {
        val dianaRate = if (MayorPerkApi.sharingIsCaringActive) SHARING_IS_CARING_EXP_SHARE_RATE else 0.0
        return tamingLevel() * TAMING_EXP_SHARE_RATE_PER_LEVEL + dianaRate
    }

    fun whyNotMoreExpShareRate(): Double =
        AttributeShardCatalog.getActiveLevelByAbilityName(WHY_NOT_MORE_ATTRIBUTE) / PERCENT_DENOMINATOR

    private fun tamingLevel(): Int = SkillExpGainApi.getSkillInfo(SkyBlockSkill.TAMING)
        ?.level
        ?.coerceIn(0, SkyBlockSkill.TAMING.maxLevel)
        ?: 0

    private fun matchingSkillMultiplier(skill: SkyBlockSkill): Double = when (skill) {
        SkyBlockSkill.MINING,
        SkyBlockSkill.FISHING,
        -> MATCHING_GATHERING_SKILL_MULTIPLIER

        else -> 1.0
    }

    private fun nonMatchingSkillMultiplier(skill: SkyBlockSkill): Double = when (skill) {
        SkyBlockSkill.MINING,
        SkyBlockSkill.FISHING,
        -> NON_MATCHING_GATHERING_SKILL_MULTIPLIER

        SkyBlockSkill.ENCHANTING,
        SkyBlockSkill.ALCHEMY,
        -> NON_MATCHING_MAGIC_SKILL_MULTIPLIER

        else -> NON_MATCHING_DEFAULT_SKILL_MULTIPLIER
    }

    private fun String?.petItemMultiplier(skill: SkyBlockSkill): Double {
        val internalName = this ?: return 1.0
        if (internalName == ALL_SKILLS_BOOST) return ALL_SKILLS_BOOST_MULTIPLIER
        if (internalName == ALL_SKILLS_SUPER_BOOST) return ALL_SKILLS_SUPER_BOOST_MULTIPLIER
        val prefix = "PET_ITEM_${skill.uppercaseName}_SKILL_BOOST_"
        if (!internalName.startsWith(prefix)) return 1.0
        return when (internalName.removePrefix(prefix)) {
            "COMMON" -> COMMON_SKILL_BOOST_MULTIPLIER
            "UNCOMMON" -> UNCOMMON_SKILL_BOOST_MULTIPLIER
            "RARE" -> RARE_SKILL_BOOST_MULTIPLIER
            "EPIC" -> EPIC_SKILL_BOOST_MULTIPLIER
            else -> 1.0
        }
    }

    private fun battleExperienceMultiplier(skill: SkyBlockSkill): Double =
        if (skill == SkyBlockSkill.COMBAT) {
            1.0 + AttributeShardCatalog.getActiveLevelByAbilityName(BATTLE_EXPERIENCE_ATTRIBUTE) /
                PERCENT_DENOMINATOR
        } else 1.0

    private fun getBeastmasterMultiplier(): Double =
        Minecraft.getInstance().player?.let { player ->
            (0 until player.inventory.getContainerSize())
                .asSequence()
                .map { player.inventory.getItem(it) }
                .mapNotNull { AccessoryBagData.readBeastmasterMultiplier(it) }
                .maxOrNull()
        } ?: ProfileStorageApi.storage.beastmasterPetXpMultiplier ?: 1.0
}


private const val PERCENT_DENOMINATOR = 100.0

private const val DIANA_PET_XP_MULTIPLIER = 1.35

private const val SHARING_IS_CARING_EXP_SHARE_RATE = 0.10

private const val TAMING_EXP_SHARE_RATE_PER_LEVEL = 0.002

private const val MATCHING_GATHERING_SKILL_MULTIPLIER = 1.5

private const val NON_MATCHING_GATHERING_SKILL_MULTIPLIER = 0.5

private const val NON_MATCHING_MAGIC_SKILL_MULTIPLIER = 1.0 / 12.0

private const val NON_MATCHING_DEFAULT_SKILL_MULTIPLIER = 1.0 / 3.0

private const val ALL_SKILLS_BOOST_MULTIPLIER = 1.1

private const val ALL_SKILLS_SUPER_BOOST_MULTIPLIER = 1.2

private const val COMMON_SKILL_BOOST_MULTIPLIER = 1.2

private const val UNCOMMON_SKILL_BOOST_MULTIPLIER = 1.3

private const val RARE_SKILL_BOOST_MULTIPLIER = 1.4

private const val EPIC_SKILL_BOOST_MULTIPLIER = 1.5

private val NON_SKILL_PET_TYPE_PREFIXES = setOf("GABAGOOL", "FRACTURED_SOUL")

private const val ALL_SKILLS_BOOST = "PET_ITEM_ALL_SKILLS_BOOST_COMMON"

private const val ALL_SKILLS_SUPER_BOOST = "ALL_SKILLS_SUPER_BOOST"

private const val BATTLE_EXPERIENCE_ATTRIBUTE = "Battle Experience"

private const val WHY_NOT_MORE_ATTRIBUTE = "Why Not More"
