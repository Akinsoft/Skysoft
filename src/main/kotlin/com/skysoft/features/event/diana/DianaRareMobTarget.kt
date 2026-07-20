package com.skysoft.features.event.diana

import com.skysoft.config.DianaRareMobOption
import com.skysoft.features.combat.DamageSplashAttackContext
import com.skysoft.features.combat.SkyBlockMobHealth
import com.skysoft.utils.WorldVec
import com.skysoft.utils.chat.ChatMessageSender
import com.skysoft.utils.toWorldVec
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.decoration.ArmorStand
import java.awt.Color

internal class DianaRareMobTarget(
    val targetId: Long,
    val key: String,
    val mob: DianaRareMobOption,
    var sharedBy: ChatMessageSender,
    val source: DianaRareMobTargetSource,
    val createdAtMillis: Long,
    expiresAtMillis: Long,
    location: WorldVec,
) {
    val sharedLocation: WorldVec = location.roundToBlock()
    var expiresAtMillis: Long = expiresAtMillis
        private set
    var location: WorldVec = location.roundToBlock()
        private set
    var entity: LivingEntity? = null
        private set
    var nameplate: ArmorStand? = null
        private set
    var currentHealth: Long? = null
        private set
    var maxHealth: Long? = null
        private set
    var localDamage: Long = 0
        private set
    var lootshareEligible = false
        private set
    var lastLocalAttack: DamageSplashAttackContext? = null
        private set
    private var lastLocalAttackCanDamage = false
    var lastHealthChangeAtMillis: Long? = null
        private set
    var lastSeenAtMillis: Long? = null
        private set
    var nearbyWithoutSignalSinceMillis: Long? = null
    var trackedEntityDeathSinceMillis: Long? = null
        private set
    private var pendingCocoonHatchUntilMillis: Long? = null
    var glowColor: Color? = null
    val processedDamageSplashIds = mutableSetOf<Int>()

    fun hasVisibleSignal(): Boolean =
        entity != null || nameplate != null

    fun updateFromSignal(signal: DianaRareMobSignal, now: Long) {
        location = signal.location.roundToBlock()
        entity = signal.entity
        nameplate = signal.nameplate
        lastSeenAtMillis = now
        nearbyWithoutSignalSinceMillis = null
        trackedEntityDeathSinceMillis = null
        pendingCocoonHatchUntilMillis = null
        signal.health?.let { health -> updateHealth(health, now) }
    }

    fun extendExpiry(expiresAtMillis: Long) {
        this.expiresAtMillis = maxOf(this.expiresAtMillis, expiresAtMillis)
    }

    fun markPendingCocoonHatch(untilMillis: Long) {
        pendingCocoonHatchUntilMillis = untilMillis
    }

    fun isAwaitingCocoonHatch(now: Long): Boolean =
        pendingCocoonHatchUntilMillis?.let { now < it } == true

    fun clearEntity(entityId: Int) {
        if (entity?.id == entityId) entity = null
        if (nameplate?.id == entityId) nameplate = null
    }

    fun lineLocation(): WorldVec =
        entity?.position()?.toWorldVec()
            ?: nameplate?.position()?.toWorldVec()
            ?: location.blockCenter()

    fun isSpawner(localName: String?): Boolean =
        localName != null && sharedBy.name.equals(localName, ignoreCase = true)

    fun shouldShowLootshare(localName: String?): Boolean =
        !isSpawner(localName) && hasVisibleSignal()

    fun addAttributedLocalDamage(damage: Long): LootshareEligibilityResult {
        if (!lastLocalAttackCanDamage) return LootshareEligibilityResult.UNCHANGED
        return addLocalDamage(damage)
    }

    private fun addLocalDamage(damage: Long): LootshareEligibilityResult {
        if (damage <= 0) return LootshareEligibilityResult.UNCHANGED
        localDamage += damage
        val threshold = lootshareThreshold() ?: return LootshareEligibilityResult.UNCHANGED
        val wasEligible = lootshareEligible
        lootshareEligible = localDamage >= threshold
        return if (!wasEligible && lootshareEligible) {
            LootshareEligibilityResult.BECAME_ELIGIBLE
        } else {
            LootshareEligibilityResult.UNCHANGED
        }
    }

    fun lootshareThreshold(): Long? =
        maxHealth?.let { (it * LOOTSHARE_DAMAGE_FRACTION).toLong().coerceAtLeast(1L) }

    fun recordLocalAttack(entity: Entity, playerLocation: WorldVec?, now: Long, canDamage: Boolean) {
        recordLocalAttack(
            entityId = entity.id,
            targetLocation = entity.position().toWorldVec(),
            playerLocation = playerLocation,
            now = now,
            canDamage = canDamage,
        )
    }

    fun recordLocalAttack(
        entityId: Int,
        targetLocation: WorldVec,
        playerLocation: WorldVec?,
        now: Long,
        canDamage: Boolean,
    ) {
        lastLocalAttack = DamageSplashAttackContext(
            atMillis = now,
            entityId = entityId,
            targetLocation = targetLocation,
            playerLocation = playerLocation,
        )
        lastLocalAttackCanDamage = canDamage
    }

    fun damageAttributionLocations(): List<WorldVec> =
        listOfNotNull(lineLocation(), lastLocalAttack?.targetLocation)

    fun targetEntityIds(): Set<Int> =
        listOfNotNull(entity?.id, nameplate?.id).toSet()

    fun markTrackedEntityDeath(now: Long) {
        trackedEntityDeathSinceMillis = trackedEntityDeathSinceMillis ?: now
    }

    fun hasConfirmedTrackedEntityDeath(now: Long, confirmationMillis: Long): Boolean =
        trackedEntityDeathSinceMillis?.let { since -> now - since >= confirmationMillis } == true

    private fun updateHealth(health: SkyBlockMobHealth, now: Long) {
        val oldHealth = currentHealth
        currentHealth = health.current
        maxHealth = health.max ?: maxOf(maxHealth ?: 0L, health.current)
        if (oldHealth != null && oldHealth != currentHealth) {
            lastHealthChangeAtMillis = now
        }
    }

    private companion object {
        const val LOOTSHARE_DAMAGE_FRACTION = 0.01
    }
}

internal enum class DianaRareMobTargetSource {
    LOCAL,
    REMOTE,
}

internal enum class LootshareEligibilityResult {
    BECAME_ELIGIBLE,
    UNCHANGED,
}
