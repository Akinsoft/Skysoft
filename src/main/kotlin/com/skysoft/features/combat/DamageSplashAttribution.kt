package com.skysoft.features.combat

import com.skysoft.utils.WorldVec
import java.util.UUID
import kotlin.math.abs

internal data class DamageSplash(
    val entityId: Int,
    val damage: Long,
    val location: WorldVec,
)

internal data class DamageSplashAttackContext(
    val atMillis: Long,
    val entityUuid: UUID,
    val targetLocation: WorldVec,
)

internal data class DamageSplashTargetView(
    val active: Boolean = true,
    val lastAttack: DamageSplashAttackContext?,
    val processedSplashIds: MutableSet<Int>,
    val attributionLocations: List<WorldVec>,
    val targetEntityUuids: Set<UUID> = emptySet(),
    val lastHealthChangeAtMillis: Long? = null,
)

internal object DamageSplashAttribution {
    fun <T> attribute(
        splash: DamageSplash,
        targets: Iterable<T>,
        now: Long,
        view: (T) -> DamageSplashTargetView,
    ): T? {
        val candidates = targets
            .asSequence()
            .mapNotNull { target ->
                val targetView = view(target)
                val score = targetView.score(splash, now) ?: return@mapNotNull null
                ScoredAttributionCandidate(target, targetView, score)
            }
            .toList()
        val attribution = candidates
            .minWithOrNull(
                compareBy<ScoredAttributionCandidate<T>> { candidate -> candidate.score.confidence }
                    .thenBy { candidate -> candidate.score.value },
            )
            ?: return null

        candidates.forEach { candidate -> candidate.targetView.processedSplashIds += splash.entityId }
        return attribution.target
    }

    private fun DamageSplashTargetView.score(
        splash: DamageSplash,
        now: Long,
    ): AttributionScore? {
        if (!active) return null
        if (splash.entityId in processedSplashIds) return null
        val attack = lastAttack ?: return null
        val attackAge = now - attack.atMillis
        if (attackAge !in 0..MAX_ATTACK_AGE_MILLIS) return null

        val targetDistance = attributionLocations.minOfOrNull { location -> splash.location.distance(location) }
            ?: Double.MAX_VALUE
        val attackDistance = splash.location.distance(attack.targetLocation)
        val bestDistance = minOf(targetDistance, attackDistance)
        if (bestDistance > MAX_SPLASH_DISTANCE) return null

        val exactEntity = attack.entityUuid in targetEntityUuids
        val healthConfirmed = lastHealthChangeAtMillis
            ?.let { changedAt -> abs(now - changedAt) <= HEALTH_CHANGE_WINDOW_MILLIS } == true
        val confidence = confidence(
            attackAge = attackAge,
            exactEntity = exactEntity,
            healthConfirmed = healthConfirmed,
            bestDistance = bestDistance,
            attackDistance = attackDistance,
            targetDistance = targetDistance,
        )
        return AttributionScore(
            confidence = confidence,
            value = scoreValue(attackAge, exactEntity, healthConfirmed, attackDistance, targetDistance),
        )
    }

    private fun confidence(
        attackAge: Long,
        exactEntity: Boolean,
        healthConfirmed: Boolean,
        bestDistance: Double,
        attackDistance: Double,
        targetDistance: Double,
    ): AttributionConfidence =
        if (
            attackAge <= HIGH_CONFIDENCE_ATTACK_AGE_MILLIS &&
            bestDistance <= HIGH_CONFIDENCE_SPLASH_DISTANCE &&
            (exactEntity || attackDistance <= HIGH_CONFIDENCE_SPLASH_DISTANCE) &&
            (healthConfirmed || targetDistance <= HIGH_CONFIDENCE_SPLASH_DISTANCE)
        ) {
            AttributionConfidence.LOCAL_DIRECT_HIGH
        } else {
            AttributionConfidence.LOCAL_DIRECT_MEDIUM
        }

    private fun scoreValue(
        attackAge: Long,
        exactEntity: Boolean,
        healthConfirmed: Boolean,
        attackDistance: Double,
        targetDistance: Double,
    ): Double {
        val entityBonus = if (exactEntity) EXACT_ENTITY_BONUS else 0.0
        val healthBonus = if (healthConfirmed) HEALTH_CONFIRMED_BONUS else 0.0
        return attackDistance + targetDistance + attackAge / MILLIS_PER_SECOND + entityBonus + healthBonus
    }

    private data class ScoredAttributionCandidate<T>(
        val target: T,
        val targetView: DamageSplashTargetView,
        val score: AttributionScore,
    )

    private data class AttributionScore(
        val confidence: AttributionConfidence,
        val value: Double,
    )

    private enum class AttributionConfidence {
        LOCAL_DIRECT_HIGH,
        LOCAL_DIRECT_MEDIUM,
    }

    private const val MAX_ATTACK_AGE_MILLIS = 900L
    private const val HIGH_CONFIDENCE_ATTACK_AGE_MILLIS = 400L
    private const val MAX_SPLASH_DISTANCE = 5.0
    private const val HIGH_CONFIDENCE_SPLASH_DISTANCE = 2.5
    private const val HEALTH_CHANGE_WINDOW_MILLIS = 500L
    private const val EXACT_ENTITY_BONUS = -8.0
    private const val HEALTH_CONFIRMED_BONUS = -2.0
    private const val MILLIS_PER_SECOND = 1_000.0
}
