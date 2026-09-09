package com.skysoft.features.pets

import com.skysoft.data.skyblock.SkillExpGainApi
import com.skysoft.data.skyblock.SkyBlockSkill
import com.skysoft.data.skyblock.pets.PetRepository
import com.skysoft.data.StoredPetData
import com.skysoft.data.ProfileStorageApi
import com.skysoft.data.hypixel.SkyBlockProfileApi
import com.skysoft.utils.ElapsedTimeMark
import com.skysoft.utils.RegexUtilities.group
import com.skysoft.utils.SkysoftClientEvents
import com.skysoft.utils.TextUtilities.cleanSkyBlockText
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

object PetXpEstimator {
    private val storage get() = ProfileStorageApi.storage
    private val skillTracker = PetSkillGainTracker(PetXpRules::estimatePetXp)
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
        ActivePetTracker.updateCurrentPetExp(levelExp)
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
        val skillRead = skillTracker.readSkillGain(event, currentPet?.uuid)
        updatePetExp(event, skillRead, currentPet)
    }

    private fun updatePetExp(event: SkillExpGainApi.SkillExpGain, skillRead: SkillGainRead, currentPet: StoredPetData?) {
        val targetPet = resolveGainTarget(skillRead, currentPet) ?: return
        val targetPetExp = targetPet.exp ?: return
        val skillGain = skillRead.gain.takeIf { it > 0.0 } ?: return
        val petXp = PetXpRules.estimatePetXp(targetPet, event.skill, skillGain) ?: return

        targetPet.uuid?.let { recentEstimatePetUuids[it] = ElapsedTimeMark.now() }
        skillTracker.rememberRecentSkillEstimate(event, targetPet, skillRead)
        val targetExp = targetPet.targetExpAfterGain(targetPetExp, petXp)
        updateTargetPetExp(targetPet, targetExp)
        targetPet.uuid?.let { uuid -> recentEstimatedPetExp[uuid] = ElapsedTimeMark.now() }
        updateExpSharePets(event.skill, petXp, targetPet.uuid)
    }

    private fun resolveGainTarget(skillRead: SkillGainRead, currentPet: StoredPetData?): StoredPetData? =
        if (skillRead.petUuid == null) currentPet else getPetByUuid(skillRead.petUuid)

    private fun updateExpSharePets(skill: SkyBlockSkill, sourcePetXp: Double, currentPetUuid: UUID?) {
        val baseRate = PetXpRules.expShareBaseRate()
        val whyNotMoreRate = PetXpRules.whyNotMoreExpShareRate()
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
            PetStorageService.storePet(petData.copy(exp = currentExp + gain))
            petData.uuid?.let { recentEstimatePetUuids[it] = ElapsedTimeMark.now() }
        }
    }

    private fun getPetByUuid(uuid: UUID): StoredPetData? =
        storage.pets.firstOrNull { it.uuid == uuid }

    private fun updateTargetPetExp(petData: StoredPetData, exp: Double): StoredPetData? {
        if (petData.uuid == ActivePetTracker.currentPet?.uuid) {
            return ActivePetTracker.updateCurrentPetExp(exp)
        }
        val currentExp = petData.exp ?: 0.0
        if (exp <= currentExp) return null
        return petData.copy(exp = exp).also(PetStorageService::storePet)
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


private const val EXP_SHARE_ITEM_RATE = 0.15

private const val EXP_SHARE = "PET_ITEM_EXP_SHARE"
