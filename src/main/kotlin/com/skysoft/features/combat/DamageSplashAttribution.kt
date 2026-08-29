package com.skysoft.features.combat

import com.skysoft.utils.WorldVec
import java.util.UUID
import kotlin.math.abs

internal data class DamageSplash(
    val entityId: Int,
    val damage: Long,
    val location: WorldVec,
    val cleanName: String,
)

internal data class DamageSplashAttributionConfig(
    val maxAttackAgeMillis: Long = 900L,
    val highConfidenceAttackAgeMillis: Long = 400L,
    val maxSplashDistance: Double = 5.0,
    val highConfidenceSplashDistance: Double = 2.5,
    val healthChangeWindowMillis: Long = 500L,
) {
    companion object {
        val DEFAULT = DamageSplashAttributionConfig()
    }
}

internal data class DamageSplashAttackContext(
    val atMillis: Long,
    val entityUuid: UUID,
    val targetLocation: WorldVec,
    val playerLocation: WorldVec?,
)

internal data class DamageSplashTargetView(
    val active: Boolean = true,
    val lastAttack: DamageSplashAttackContext?,
    val processedSplashIds: MutableSet<Int>,
    val attributionLocations: List<WorldVec>,
    val targetEntityUuids: Set<UUID> = emptySet(),
    val lastHealthChangeAtMillis: Long? = null,
)

internal data class AttributedDamage<T>(
    val target: T,
    val splash: DamageSplash,
    val confidence: DamageSplashAttributionConfidence,
    val score: Double,
)

internal enum class DamageSplashAttributionConfidence {
    LOCAL_DIRECT_HIGH,
    LOCAL_DIRECT_MEDIUM,
}

internal object DamageSplashAttribution {
    fun <T> attribute(
        splash: DamageSplash,
        targets: Iterable<T>,
        now: Long,
        config: DamageSplashAttributionConfig = DamageSplashAttributionConfig.DEFAULT,
        view: (T) -> DamageSplashTargetView,
    ): AttributedDamage<T>? {
        val candidates = targets
            .asSequence()
            .map { target -> AttributionCandidate(target, view(target)) }
            .mapNotNull { candidate -> candidate.withScore(candidate.targetView.score(splash, now, config)) }
            .toList()
        val attribution = candidates
            .minWithOrNull(
                compareBy<ScoredAttributionCandidate<T>> { candidate -> candidate.score.rank }
                    .thenBy { candidate -> candidate.score.value },
            )
            ?: return null

        candidates.forEach { candidate -> candidate.targetView.processedSplashIds += splash.entityId }
        return AttributedDamage(
            target = attribution.target,
            splash = splash,
            confidence = attribution.score.confidence,
            score = attribution.score.value,
        )
    }

    private fun DamageSplashTargetView.score(
        splash: DamageSplash,
        now: Long,
        config: DamageSplashAttributionConfig,
    ): AttributionScore? {
        if (!active) return null
        if (splash.entityId in processedSplashIds) return null
        val attack = lastAttack ?: return null
        val attackAge = now - attack.atMillis
        if (attackAge !in 0..config.maxAttackAgeMillis) return null

        val targetDistance = attributionLocations
            .map { location -> splash.location.distance(location) }
            .minOrNull()
            ?: Double.MAX_VALUE
        val attackDistance = splash.location.distance(attack.targetLocation)
        val bestDistance = minOf(targetDistance, attackDistance)
        if (bestDistance > config.maxSplashDistance) return null

        val exactEntity = attack.entityUuid in targetEntityUuids
        val healthConfirmed = lastHealthChangeAtMillis
            ?.let { changedAt -> abs(now - changedAt) <= config.healthChangeWindowMillis }
            ?: false
        val confidence = confidence(
            attackAge = attackAge,
            exactEntity = exactEntity,
            healthConfirmed = healthConfirmed,
            bestDistance = bestDistance,
            attackDistance = attackDistance,
            targetDistance = targetDistance,
            config = config,
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
        config: DamageSplashAttributionConfig,
    ): DamageSplashAttributionConfidence =
        if (
            attackAge <= config.highConfidenceAttackAgeMillis &&
            bestDistance <= config.highConfidenceSplashDistance &&
            (exactEntity || attackDistance <= config.highConfidenceSplashDistance) &&
            (healthConfirmed || targetDistance <= config.highConfidenceSplashDistance)
        ) {
            DamageSplashAttributionConfidence.LOCAL_DIRECT_HIGH
        } else {
            DamageSplashAttributionConfidence.LOCAL_DIRECT_MEDIUM
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

    private data class AttributionCandidate<T>(
        val target: T,
        val targetView: DamageSplashTargetView,
    ) {
        fun withScore(score: AttributionScore?): ScoredAttributionCandidate<T>? =
            score?.let { ScoredAttributionCandidate(target, targetView, it) }
    }

    private data class ScoredAttributionCandidate<T>(
        val target: T,
        val targetView: DamageSplashTargetView,
        val score: AttributionScore,
    )

    private data class AttributionScore(
        val confidence: DamageSplashAttributionConfidence,
        val value: Double,
    ) {
        val rank: Int =
            when (confidence) {
                DamageSplashAttributionConfidence.LOCAL_DIRECT_HIGH -> 0
                DamageSplashAttributionConfidence.LOCAL_DIRECT_MEDIUM -> 1
            }
    }

    private const val EXACT_ENTITY_BONUS = -8.0
    private const val HEALTH_CONFIRMED_BONUS = -2.0
    private const val MILLIS_PER_SECOND = 1_000.0
}
