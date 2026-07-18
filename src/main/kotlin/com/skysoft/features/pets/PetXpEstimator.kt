package com.skysoft.features.pets

import com.skysoft.data.StoredPetData
import com.skysoft.data.ProfileStorageApi
import com.skysoft.data.hypixel.SkyBlockProfileApi
import com.skysoft.data.skyblock.AccessoryBagData
import com.skysoft.data.skyblock.AttributeShardCatalog
import com.skysoft.data.skyblock.MayorPerkApi
import com.skysoft.utils.ChangeResult
import com.skysoft.utils.ElapsedTimeMark
import com.skysoft.utils.RegexUtilities.group
import com.skysoft.utils.SkysoftClientEvents
import com.skysoft.utils.TextUtilities.cleanSkyBlockText
import java.util.UUID
import net.minecraft.client.Minecraft
import kotlin.math.abs
import kotlin.math.roundToLong
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

object PetXpEstimator {
    private val storage get() = ProfileStorageApi.storage
    private val skillTracker = PetSkillGainTracker()
    private val recentEstimatePetUuids = mutableMapOf<UUID, ElapsedTimeMark>()
    private var pendingLevelUp: PendingPetLevelUp? = null
    private val recentEstimatedPetExp = mutableMapOf<UUID, ElapsedTimeMark>()

    fun register() {
        SkillExpGainApi.onSkillExpGain("Pet Experience estimation", PetFeatureDemand::isActive, ::onSkillExpGain)
        SkyBlockProfileApi.onProfileChange("Pet Experience profile reset", PetFeatureDemand::isActive) {
            resetProfileState()
        }
        SkysoftClientEvents.onDisconnect("Pet Experience disconnect reset") {
            resetWorldState()
        }
    }

    fun handleChat(message: String) {
        val cleanMessage = message.cleanSkyBlockText()
        val match = petLevelUpPattern.matchEntire(cleanMessage) ?: return
        val currentPet = ActivePetTracker.currentPet ?: return
        val petName = match.group("pet")
        if (!currentPet.matchesDisplayName(petName)) return

        val level = match.group("level").toInt()
        val levelExp = PetRepository.levelToXp(level, currentPet.fauxInternalName) ?: return
        val currentExp = currentPet.exp ?: 0.0
        if (levelExp > currentExp) {
            val previousExp = pendingLevelUp?.takeIf { it.matches(currentPet) }?.previousExp ?: currentExp
            pendingLevelUp = PendingPetLevelUp(
                currentPet.uuid,
                currentPet.fauxInternalName,
                previousExp,
                levelExp,
            )
        }
        if (ActivePetTracker.updateCurrentPetExp(levelExp) != null) PetStorageService.markDirty()
    }

    fun resyncFromPetDataRead(
        petData: StoredPetData,
        exact: Boolean,
        previousExp: Double? = null,
        appliedExp: Double? = null,
    ) {
        val appliedDelta = appliedExp?.let { applied -> previousExp?.let { applied - it } }
        if (exact) skillTracker.resyncSkillFractionFromExactRead(petData, appliedDelta)
    }

    fun shouldRecordPetMenuRead(uuid: UUID?): Boolean {
        val lastEstimate = recentEstimatePetUuids[uuid] ?: return false
        return lastEstimate.passedSince() <= 60.seconds
    }

    fun shouldIgnoreStalePetWidgetRead(
        petData: StoredPetData,
        readExp: Double,
        previousExp: Double?,
    ): Boolean {
        val uuid = petData.uuid ?: return false
        val previous = previousExp ?: return false
        val recentEstimate = recentEstimatedPetExp[uuid] ?: return false
        if (recentEstimate.passedSince() > WIDGET_STALE_READ_GRACE) return false
        return readExp + 1.0 < previous
    }

    fun recordAutopetSwap(petData: StoredPetData, previousPet: StoredPetData?, trigger: String?) {
        skillTracker.recordAutopetSwap(petData, previousPet, trigger)
    }

    private fun onSkillExpGain(event: SkillExpGainApi.SkillExpGain) {
        val currentPet = ActivePetTracker.currentPet
        skillTracker.readSkillGains(event, currentPet?.uuid).forEach { updatePetExp(event, it, currentPet) }
    }

    private fun updatePetExp(event: SkillExpGainApi.SkillExpGain, skillRead: SkillGainRead, currentPet: StoredPetData?) {
        val targetPet = resolveGainTarget(skillRead, currentPet) ?: return
        val targetPetExp = targetPet.exp ?: return
        val skillGain = skillRead.gain.takeIf { it > 0.0 } ?: return
        val petXp = PetXpRules.estimatePetXp(targetPet, event.skill, skillGain) ?: return

        targetPet.uuid?.let { recentEstimatePetUuids[it] = ElapsedTimeMark.now() }
        skillTracker.rememberRecentSkillEstimate(event, targetPet, skillRead)
        val targetExp = targetPet.targetExpAfterGain(targetPetExp, petXp)
        val updatedPet = updateTargetPetExp(targetPet, targetExp)
        targetPet.uuid?.let { uuid -> recentEstimatedPetExp[uuid] = ElapsedTimeMark.now() }
        val expShareUpdate = updateExpSharePets(event.skill, petXp, targetPet.uuid)
        if (updatedPet != null || expShareUpdate == ChangeResult.CHANGED) PetStorageService.markDirty()
    }

    private fun resolveGainTarget(skillRead: SkillGainRead, currentPet: StoredPetData?): StoredPetData? =
        if (skillRead.petUuid == null) currentPet else getPetByUuid(skillRead.petUuid)

    private fun updateExpSharePets(skill: SkyBlockSkill, sourcePetXp: Double, currentPetUuid: UUID?): ChangeResult {
        val baseRate = PetXpRules.expShareBaseRate()
        val whyNotMoreRate = PetXpRules.whyNotMoreExpShareRate()
        var updated = false
        PetStorageService.getActiveExpSharePets().forEach { petData ->
            val currentExp = petData.exp ?: return@forEach
            if (petData.uuid == currentPetUuid) {
                return@forEach
            }
            val itemRate = if (petData.heldItemInternalName == EXP_SHARE) EXP_SHARE_ITEM_RATE else 0.0
            val rate = baseRate + whyNotMoreRate + itemRate
            if (rate <= 0.0) {
                return@forEach
            }

            val petType = PetRepository.getPetType(petData.fauxInternalName) ?: return@forEach
            val sharedPetBaseMultiplier = PetXpRules.skillBaseMultiplier(petType, skill) ?: return@forEach
            val gain = sourcePetXp * rate * sharedPetBaseMultiplier
            petData.exp = currentExp + gain
            petData.uuid?.let { recentEstimatePetUuids[it] = ElapsedTimeMark.now() }
            updated = true
        }
        return ChangeResult.from(updated)
    }

    private fun getPetByUuid(uuid: UUID): StoredPetData? =
        storage.pets.firstOrNull { it.uuid == uuid }

    private fun updateTargetPetExp(petData: StoredPetData, exp: Double): StoredPetData? {
        if (petData.uuid == ActivePetTracker.currentPet?.uuid) {
            return ActivePetTracker.updateCurrentPetExp(exp)
        }
        val currentExp = petData.exp ?: 0.0
        if (exp <= currentExp) return null
        petData.exp = exp
        return petData
    }

    private fun StoredPetData.targetExpAfterGain(currentExp: Double, petXp: Double): Double {
        val pending = pendingLevelUp ?: return currentExp + petXp
        pendingLevelUp = null
        return pending.expAfterGain(this, currentExp, petXp) ?: currentExp + petXp
    }

    private fun resetWorldState() {
        recentEstimatePetUuids.clear()
        recentEstimatedPetExp.clear()
        pendingLevelUp = null
        skillTracker.resetWorldState()
    }

    private fun resetProfileState() {
        skillTracker.resetProfileState()
        resetWorldState()
    }

    private data class PendingPetLevelUp(
        val uuid: UUID?,
        val internalName: String,
        val previousExp: Double,
        val levelExp: Double,
        val createdAt: ElapsedTimeMark = ElapsedTimeMark.now(),
    ) {
        fun matches(petData: StoredPetData): Boolean =
            uuid?.let { petData.uuid == it } ?: (petData.fauxInternalName == internalName)

        fun expAfterGain(petData: StoredPetData, currentExp: Double, gainedExp: Double): Double? {
            if (!matches(petData) || createdAt.passedSince() > 5.seconds) return null
            if (currentExp > levelExp) return currentExp + gainedExp
            return maxOf(previousExp + gainedExp, levelExp)
        }

    }
    private val WIDGET_STALE_READ_GRACE = 3.seconds

    private val petLevelUpPattern = Regex("""Your (?<pet>.+) leveled up to level (?<level>\d+)!""")
}

private class PetSkillGainTracker {
    private val skillSamples = mutableMapOf<SkyBlockSkill, SkillProgressSample>()
    private val recentSkillEstimates = mutableMapOf<SkyBlockSkill, RecentSkillEstimate>()
    private val recentGainQuanta = mutableMapOf<SkyBlockSkill, MutableMap<Double, ElapsedTimeMark>>()
    private var pendingSpawnAutopetSwap: SpawnAutopetContext? = null

    fun recordAutopetSwap(petData: StoredPetData, previousPet: StoredPetData?, trigger: String?) {
        pendingSpawnAutopetSwap = if (trigger?.contains("spawn", ignoreCase = true) == true) {
            SpawnAutopetContext(petData.uuid, previousPet?.uuid)
        } else null
    }

    fun readSkillGains(event: SkillExpGainApi.SkillExpGain, currentPetUuid: UUID?): List<SkillGainRead> {
        val totalXp = event.totalXp?.takeIf { it > 0.0 }
        if (event.source == ACTION_BAR_EXP_SOURCE && event.gained > 0.0) rememberGainQuantum(event.skill, event.gained)
        val previous = skillSamples[event.skill]
        val previousTotalXp = previous?.totalXp ?: initialPreviousTotal(event, totalXp, previous)

        if (event.source != ACTION_BAR_EXP_SOURCE) {
            return readNonActionbarSkillGain(event, totalXp, previousTotalXp, previous, currentPetUuid)
        }

        return when {
            totalXp != null && previousTotalXp != null ->
                readActionbarSkillGain(event, totalXp, previousTotalXp, previous, currentPetUuid)

            else -> rememberSkillSampleAndReturn(
                event.skill,
                totalXp,
                totalXp?.plus(ESTIMATED_TOTAL_FRACTION),
                currentPetUuid,
                singleSkillGainRead(event.gained, totalXp, previousTotalXp, currentPetUuid),
            )
        }
    }

    fun rememberRecentSkillEstimate(
        event: SkillExpGainApi.SkillExpGain,
        targetPet: StoredPetData,
        skillRead: SkillGainRead,
    ) {
        if (skillRead.gain <= 0.0 || targetPet.uuid == null || skillRead.petUuid != targetPet.uuid) return
        PetXpRules.estimatePetXp(targetPet, event.skill, skillRead.gain) ?: return
        recentSkillEstimates[event.skill] = RecentSkillEstimate(targetPet.uuid)
    }

    fun resyncSkillFractionFromExactRead(petData: StoredPetData, appliedDelta: Double?): ChangeResult {
        val petUuid = petData.uuid ?: return ChangeResult.UNCHANGED
        val skill = recentSkillEstimates.asSequence()
            .filter { it.value.petUuid == petUuid && it.value.createdAt.passedSince() <= 10.minutes }
            .minByOrNull { it.value.createdAt.passedSince() }
            ?.key ?: return ChangeResult.UNCHANGED
        val sample = skillSamples[skill] ?: return ChangeResult.UNCHANGED
        val totalXp = sample.totalXp ?: return ChangeResult.UNCHANGED
        val precise = sample.preciseTotalXp ?: return ChangeResult.UNCHANGED
        val multiplier = PetXpRules.estimatePetXp(petData, skill, 1.0)
            ?.takeIf { it > 0.0 }
            ?: return ChangeResult.UNCHANGED
        val skillDelta = appliedDelta?.takeIf { it != 0.0 }?.let { it / multiplier } ?: return ChangeResult.UNCHANGED
        if (abs(skillDelta) >= 1.0) return ChangeResult.UNCHANGED

        val resynced = (precise + skillDelta).coerceIn(totalXp, totalXp + MAX_ESTIMATED_TOTAL_FRACTION)
        if (resynced == precise) return ChangeResult.UNCHANGED
        skillSamples[skill] = SkillProgressSample(totalXp, resynced, sample.petUuid)
        return ChangeResult.CHANGED
    }

    fun resetWorldState() {
        recentSkillEstimates.clear()
        pendingSpawnAutopetSwap = null
    }

    fun resetProfileState() {
        skillSamples.clear()
        recentGainQuanta.clear()
    }

    private fun readNonActionbarSkillGain(
        event: SkillExpGainApi.SkillExpGain,
        totalXp: Double?,
        previousTotalXp: Double?,
        previous: SkillProgressSample?,
        currentPetUuid: UUID?,
    ): List<SkillGainRead> {
        val totals = inferNonActionbarTotals(event, totalXp, previousTotalXp, previous)
        return rememberSkillSampleAndReturn(
            event.skill,
            totals.visible,
            totals.precise,
            currentPetUuid,
            singleSkillGainRead(event.gained, totals.visible, previousTotalXp, currentPetUuid),
        )
    }

    private fun inferNonActionbarTotals(
        event: SkillExpGainApi.SkillExpGain,
        totalXp: Double?,
        previousTotalXp: Double?,
        previous: SkillProgressSample?,
    ): NonActionbarTotals {
        val expectedVisible = previousTotalXp?.let { it + event.gained }
        val visible = listOfNotNull(totalXp, expectedVisible).maxOrNull()
        val precise = visible?.let { visibleTotal ->
            previous.preciseTotalAfterVisibleGain(visibleTotal, expectedVisible, event.gained)
                ?: visibleTotal + ESTIMATED_TOTAL_FRACTION
        }
        return NonActionbarTotals(visible, precise)
    }

    private fun SkillProgressSample?.preciseTotalAfterVisibleGain(
        visibleTotal: Double,
        expectedVisible: Double?,
        gained: Double,
    ): Double? {
        val previousPrecise = this?.preciseTotalXp ?: return null
        val expected = expectedVisible ?: return null
        return if (abs(visibleTotal - expected) <= VISIBLE_GAIN_TOLERANCE) previousPrecise + gained else null
    }

    private fun readActionbarSkillGain(
        event: SkillExpGainApi.SkillExpGain,
        totalXp: Double,
        previousTotalXp: Double,
        previous: SkillProgressSample?,
        currentPetUuid: UUID?,
    ): List<SkillGainRead> = when {
        totalXp > previousTotalXp -> readIncreasingActionbarSkillGains(
            event,
            totalXp,
            previousTotalXp,
            previous,
            currentPetUuid,
        )

        totalXp == previousTotalXp -> rememberSkillSampleAndReturn(
            event.skill,
            totalXp,
            previous?.preciseTotalXp ?: totalXp,
            currentPetUuid,
            singleSkillGainRead(0.0, totalXp, previousTotalXp, currentPetUuid),
        )

        else -> rememberSkillSampleAndReturn(
            event.skill,
            totalXp,
            totalXp + ESTIMATED_TOTAL_FRACTION,
            currentPetUuid,
            singleSkillGainRead(event.gained, totalXp, previousTotalXp, currentPetUuid),
        )
    }

    private fun readIncreasingActionbarSkillGains(
        event: SkillExpGainApi.SkillExpGain,
        totalXp: Double,
        previousTotalXp: Double,
        previous: SkillProgressSample?,
        currentPetUuid: UUID?,
    ): List<SkillGainRead> {
        val previousPreciseTotalXp = previous?.preciseTotalXp ?: previousTotalXp
        val displayedGain = totalXp - previousTotalXp
        val useVisibleGain = displayedGain.isRoundedVisibleGain(event.gained)
        val decomposedGain = if (useVisibleGain) null else decomposeDisplayedGain(event.skill, displayedGain)
        val totalGain = decomposedGain ?: if (useVisibleGain) event.gained else displayedGain
        val preciseTotalXp = (previousPreciseTotalXp + totalGain)
            .coerceIn(totalXp, totalXp + MAX_ESTIMATED_TOTAL_FRACTION)
        val reads = readIncreasingActionbarSkillGains(
            event,
            preciseTotalXp - previousPreciseTotalXp,
            totalXp,
            previousTotalXp,
            previous,
            currentPetUuid,
        )
        return rememberSkillSampleAndReturn(event.skill, totalXp, preciseTotalXp, currentPetUuid, reads)
    }

    private fun readIncreasingActionbarSkillGains(
        event: SkillExpGainApi.SkillExpGain,
        totalGain: Double,
        totalXp: Double,
        previousTotalXp: Double,
        previous: SkillProgressSample?,
        currentPetUuid: UUID?,
    ): List<SkillGainRead> {
        val routedPetUuid = petUuidForActionbarGain(event, previous, currentPetUuid)
        return singleSkillGainRead(totalGain, totalXp, previousTotalXp, routedPetUuid)
    }

    private fun petUuidForActionbarGain(
        event: SkillExpGainApi.SkillExpGain,
        previous: SkillProgressSample?,
        currentPetUuid: UUID?,
    ): UUID? {
        val previousPetUuid = previous?.petUuid ?: currentPetUuid
        val pendingSpawn = pendingSpawnAutopetSwap
        if (pendingSpawn?.matchesCombatSpawnSwap(event, previousPetUuid, currentPetUuid) == true) {
            pendingSpawnAutopetSwap = null
            return previousPetUuid
        }
        if (pendingSpawn?.createdAt?.passedSince()?.let { it > SPAWN_AUTOPET_TTL } == true) {
            pendingSpawnAutopetSwap = null
        }
        return currentPetUuid
    }

    private fun SpawnAutopetContext.matchesCombatSpawnSwap(
        event: SkillExpGainApi.SkillExpGain,
        previousPetUuid: UUID?,
        currentPetUuid: UUID?,
    ): Boolean =
        event.skill == SkyBlockSkill.COMBAT &&
            previousPetUuid != null &&
            previousPetUuid != currentPetUuid &&
            petUuid == currentPetUuid &&
            matchesPreviousPet(previousPetUuid) &&
            createdAt.passedSince() <= SPAWN_AUTOPET_TTL

    private fun singleSkillGainRead(
        gain: Double,
        totalXp: Double?,
        previousTotalXp: Double?,
        petUuid: UUID?,
    ) = listOf(SkillGainRead(gain, totalXp, previousTotalXp, petUuid))

    private fun rememberSkillSampleAndReturn(
        skill: SkyBlockSkill,
        totalXp: Double?,
        preciseTotalXp: Double?,
        currentPetUuid: UUID?,
        result: List<SkillGainRead>,
    ): List<SkillGainRead> {
        skillSamples[skill] = SkillProgressSample(totalXp, preciseTotalXp, currentPetUuid)
        return result
    }

    private fun rememberGainQuantum(skill: SkyBlockSkill, gained: Double) {
        val quanta = recentGainQuanta.getOrPut(skill) { mutableMapOf() }
        quanta[gained] = ElapsedTimeMark.now()
        if (quanta.size > RECENT_GAIN_QUANTA_LIMIT) {
            quanta.maxByOrNull { it.value.passedSince() }?.let { quanta.remove(it.key) }
        }
    }

    private fun decomposeDisplayedGain(skill: SkyBlockSkill, displayedGain: Double): Double? {
        val quantaMap = recentGainQuanta[skill] ?: return null
        quantaMap.values.removeIf { it.passedSince() > RECENT_GAIN_QUANTA_TTL }
        val quanta = quantaMap.keys.sortedDescending()
        if (quanta.isEmpty()) return null
        return findDisplayedGainCandidate(displayedGain, quanta)
    }

    private fun Double.isRoundedVisibleGain(visibleGain: Double): Boolean =
        visibleGain > 0.0 && abs(this - visibleGain) <= VISIBLE_GAIN_TOLERANCE

    private fun SpawnAutopetContext.matchesPreviousPet(samplePreviousPetUuid: UUID?) =
        previousPetUuid == null || previousPetUuid == samplePreviousPetUuid
}

private fun findDisplayedGainCandidate(displayedGain: Double, quanta: List<Double>): Double? {
    val minQuantum = quanta.last()
    val candidates = mutableSetOf<Long>()
    collectGainCandidates(
        quanta = quanta,
        displayedGain = displayedGain,
        minimumQuantum = minQuantum,
        candidates = candidates,
    )
    return candidates.singleOrNull()?.div(VISIBLE_GAIN_DECIMAL_SCALE)
}

private fun initialPreviousTotal(
    event: SkillExpGainApi.SkillExpGain,
    visibleTotal: Double?,
    previous: SkillProgressSample?,
): Double? {
    if (previous != null || visibleTotal == null) return null
    return event.previousTotalXp?.takeIf { it < visibleTotal }
}

private fun collectGainCandidates(
    quanta: List<Double>,
    displayedGain: Double,
    minimumQuantum: Double,
    candidates: MutableSet<Long>,
    startIndex: Int = 0,
    sum: Double = 0.0,
    parts: Int = 0,
) {
    if (candidates.size > 1) return
    if (sum > displayedGain - VISIBLE_GAIN_ERROR_MARGIN) {
        candidates += (sum * VISIBLE_GAIN_DECIMAL_SCALE).roundToLong()
    }
    if (
        parts >= GAIN_DECOMPOSITION_PART_LIMIT ||
        sum + minimumQuantum >= displayedGain + VISIBLE_GAIN_ERROR_MARGIN
    ) return
    for (index in startIndex until quanta.size) {
        val nextSum = sum + quanta[index]
        if (nextSum < displayedGain + VISIBLE_GAIN_ERROR_MARGIN) {
            collectGainCandidates(
                quanta,
                displayedGain,
                minimumQuantum,
                candidates,
                index,
                nextSum,
                parts + 1,
            )
        }
    }
}

private object PetXpRules {
    fun estimatePetXp(petData: StoredPetData, skill: SkyBlockSkill, skillXp: Double): Double? {
        val petType = PetRepository.getPetType(petData.fauxInternalName) ?: return null
        val baseMultiplier = skillBaseMultiplier(petType, skill) ?: return null
        val tamingLevel = SkillExpGainApi.getSkillInfo(SkyBlockSkill.TAMING)
            ?.level
            ?.coerceIn(0, SkyBlockSkill.TAMING.maxLevel)
            ?: 0
        val tamingMultiplier = 1.0 + tamingLevel / PERCENT_DENOMINATOR
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
        val tamingLevel = SkillExpGainApi.getSkillInfo(SkyBlockSkill.TAMING)
            ?.level
            ?.coerceIn(0, SkyBlockSkill.TAMING.maxLevel)
            ?: 0
        val dianaRate = if (MayorPerkApi.sharingIsCaringActive) SHARING_IS_CARING_EXP_SHARE_RATE else 0.0
        return tamingLevel * TAMING_EXP_SHARE_RATE_PER_LEVEL + dianaRate
    }

    fun whyNotMoreExpShareRate(): Double =
        AttributeShardCatalog.getActiveLevelByAbilityName(WHY_NOT_MORE_ATTRIBUTE) / PERCENT_DENOMINATOR

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

private data class SkillProgressSample(
    val totalXp: Double?,
    val preciseTotalXp: Double?,
    val petUuid: UUID?,
)

private data class NonActionbarTotals(
    val visible: Double?,
    val precise: Double?,
)

private data class RecentSkillEstimate(
    val petUuid: UUID?,
    val createdAt: ElapsedTimeMark = ElapsedTimeMark.now(),
)

private data class SkillGainRead(
    val gain: Double,
    val totalXp: Double?,
    val previousTotalXp: Double?,
    val petUuid: UUID?,
)

private data class SpawnAutopetContext(
    val petUuid: UUID?,
    val previousPetUuid: UUID?,
    val createdAt: ElapsedTimeMark = ElapsedTimeMark.now(),
)

private const val ACTION_BAR_EXP_SOURCE = "actionbar"
private const val VISIBLE_GAIN_TOLERANCE = 1.0000001
private const val ESTIMATED_TOTAL_FRACTION = 0.5
private const val MAX_ESTIMATED_TOTAL_FRACTION = 0.999
private const val RECENT_GAIN_QUANTA_LIMIT = 12
private const val GAIN_DECOMPOSITION_PART_LIMIT = 12
private const val VISIBLE_GAIN_DECIMAL_SCALE = 10.0
private const val VISIBLE_GAIN_ERROR_MARGIN = 1.0
private const val PERCENT_DENOMINATOR = 100.0
private const val EXP_SHARE_ITEM_RATE = 0.15
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
private val RECENT_GAIN_QUANTA_TTL = 10.minutes
private val SPAWN_AUTOPET_TTL = 5.seconds
private val NON_SKILL_PET_TYPE_PREFIXES = setOf("GABAGOOL", "FRACTURED_SOUL")
private const val EXP_SHARE = "PET_ITEM_EXP_SHARE"
private const val ALL_SKILLS_BOOST = "PET_ITEM_ALL_SKILLS_BOOST_COMMON"
private const val ALL_SKILLS_SUPER_BOOST = "ALL_SKILLS_SUPER_BOOST"
private const val BATTLE_EXPERIENCE_ATTRIBUTE = "Battle Experience"
private const val WHY_NOT_MORE_ATTRIBUTE = "Why Not More"
