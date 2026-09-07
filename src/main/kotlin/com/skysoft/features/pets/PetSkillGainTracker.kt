package com.skysoft.features.pets

import com.skysoft.data.skyblock.SkillExpGainApi
import com.skysoft.data.skyblock.SkyBlockSkill
import com.skysoft.data.StoredPetData
import com.skysoft.utils.ChangeResult
import com.skysoft.utils.ElapsedTimeMark
import java.util.UUID
import kotlin.math.abs
import kotlin.math.roundToLong
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

internal class PetSkillGainTracker(
    private val estimatePetXp: (StoredPetData, SkyBlockSkill, Double) -> Double?,
) {
    private val skillSamples = mutableMapOf<SkyBlockSkill, SkillProgressSample>()
    private val recentSkillEstimates = mutableMapOf<SkyBlockSkill, RecentSkillEstimate>()
    private val recentGainQuanta = mutableMapOf<SkyBlockSkill, MutableMap<Double, ElapsedTimeMark>>()
    private var pendingSpawnAutopetSwap: SpawnAutopetContext? = null

    fun recordAutopetSwap(petData: StoredPetData, previousPet: StoredPetData?, trigger: String?) {
        pendingSpawnAutopetSwap = if (trigger?.contains("spawn", ignoreCase = true) == true) {
            SpawnAutopetContext(petData.uuid, previousPet?.uuid)
        } else null
    }

    fun readSkillGain(event: SkillExpGainApi.SkillExpGain, currentPetUuid: UUID?): SkillGainRead {
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
                SkillGainRead(event.gained, totalXp, previousTotalXp, currentPetUuid),
            )
        }
    }

    fun rememberRecentSkillEstimate(
        event: SkillExpGainApi.SkillExpGain,
        targetPet: StoredPetData,
        skillRead: SkillGainRead,
    ) {
        if (skillRead.gain <= 0.0 || targetPet.uuid == null || skillRead.petUuid != targetPet.uuid) return
        estimatePetXp(targetPet, event.skill, skillRead.gain) ?: return
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
        val multiplier = estimatePetXp(petData, skill, 1.0)
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
    ): SkillGainRead {
        val totals = inferNonActionbarTotals(event, totalXp, previousTotalXp, previous)
        return rememberSkillSampleAndReturn(
            event.skill,
            totals.visible,
            totals.precise,
            currentPetUuid,
            SkillGainRead(event.gained, totals.visible, previousTotalXp, currentPetUuid),
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
    ): SkillGainRead = when {
        totalXp > previousTotalXp -> readIncreasingActionbarSkillGain(
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
            SkillGainRead(0.0, totalXp, previousTotalXp, currentPetUuid),
        )

        else -> rememberSkillSampleAndReturn(
            event.skill,
            totalXp,
            totalXp + ESTIMATED_TOTAL_FRACTION,
            currentPetUuid,
            SkillGainRead(event.gained, totalXp, previousTotalXp, currentPetUuid),
        )
    }

    private fun readIncreasingActionbarSkillGain(
        event: SkillExpGainApi.SkillExpGain,
        totalXp: Double,
        previousTotalXp: Double,
        previous: SkillProgressSample?,
        currentPetUuid: UUID?,
    ): SkillGainRead {
        val previousPreciseTotalXp = previous?.preciseTotalXp ?: previousTotalXp
        val displayedGain = totalXp - previousTotalXp
        val useVisibleGain = displayedGain.isRoundedVisibleGain(event.gained)
        val decomposedGain = if (useVisibleGain) null else decomposeDisplayedGain(event.skill, displayedGain)
        val totalGain = decomposedGain ?: if (useVisibleGain) event.gained else displayedGain
        val preciseTotalXp = (previousPreciseTotalXp + totalGain)
            .coerceIn(totalXp, totalXp + MAX_ESTIMATED_TOTAL_FRACTION)
        val routedPetUuid = petUuidForActionbarGain(event, previous, currentPetUuid)
        return rememberSkillSampleAndReturn(
            event.skill,
            totalXp,
            preciseTotalXp,
            currentPetUuid,
            SkillGainRead(preciseTotalXp - previousPreciseTotalXp, totalXp, previousTotalXp, routedPetUuid),
        )
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

    private fun rememberSkillSampleAndReturn(
        skill: SkyBlockSkill,
        totalXp: Double?,
        preciseTotalXp: Double?,
        currentPetUuid: UUID?,
        result: SkillGainRead,
    ): SkillGainRead {
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

internal data class SkillGainRead(
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

private val RECENT_GAIN_QUANTA_TTL = 10.minutes

private val SPAWN_AUTOPET_TTL = 5.seconds
