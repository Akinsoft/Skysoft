package com.skysoft.features.pets

import com.skysoft.data.StoredPetData
import com.skysoft.utils.EasingUtilities
import com.skysoft.utils.ElapsedTimeMark
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds

class PetXpAnimationState {
    private var equippedAnimation: XpAnimation? = null
    private val expShareAnimations = mutableMapOf<UUID, XpAnimation>()

    val hasPendingAnimations: Boolean
        get() = equippedAnimation?.isPending == true || expShareAnimations.values.any { it.isPending }

    fun withAnimatedEquipped(petData: StoredPetData): StoredPetData = animate(petData, equippedAnimation) {
        equippedAnimation = it
    }

    fun withAnimatedExpShare(pets: List<StoredPetData>): List<StoredPetData> {
        val activeUuids = pets.mapNotNull { it.uuid }.toSet()
        expShareAnimations.keys.removeAll { it !in activeUuids }
        return pets.map { petData ->
            val uuid = petData.uuid ?: return@map petData
            animate(petData, expShareAnimations[uuid]) { expShareAnimations[uuid] = it }
        }
    }

    fun clear() {
        equippedAnimation = null
        expShareAnimations.clear()
    }

    private fun animate(
        petData: StoredPetData,
        currentAnimation: XpAnimation?,
        store: (XpAnimation) -> Unit,
    ): StoredPetData {
        val targetExp = petData.exp ?: return petData
        val petUuid = petData.uuid
        if (currentAnimation == null || currentAnimation.uuid != petUuid || targetExp < currentAnimation.targetExp) {
            store(XpAnimation(petUuid, targetExp, targetExp))
            return petData
        }
        if (targetExp > currentAnimation.targetExp) {
            store(XpAnimation(petUuid, currentAnimation.currentExp(), targetExp))
        }
        val displayedExp = currentAnimation.currentExp()
        if (displayedExp >= targetExp) {
            store(XpAnimation(petUuid, targetExp, targetExp))
            return petData
        }
        return petData.copy(exp = displayedExp)
    }

    private data class XpAnimation(
        val uuid: UUID?,
        val startExp: Double,
        val targetExp: Double,
        val startedAt: ElapsedTimeMark = ElapsedTimeMark.now(),
    ) {
        val isPending: Boolean get() = startExp != targetExp

        fun currentExp(): Double {
            if (startExp == targetExp) return targetExp
            val progress = (startedAt.passedSince() / XP_ANIMATION_DURATION).coerceIn(0.0, 1.0)
            return startExp + (targetExp - startExp) * EasingUtilities.easeOutCubic(progress)
        }
    }

    private companion object {
        private val XP_ANIMATION_DURATION = 750.milliseconds
    }
}
