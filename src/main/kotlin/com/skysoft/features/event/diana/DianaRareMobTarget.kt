package com.skysoft.features.event.diana

import com.skysoft.config.DianaRareMobOption
import com.skysoft.features.combat.DamageSplashAttackContext
import com.skysoft.features.combat.SkyBlockMob
import com.skysoft.utils.WorldVec
import com.skysoft.utils.chat.ChatMessageSender
import com.skysoft.utils.toWorldVec
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.decoration.ArmorStand
import java.awt.Color
import java.util.UUID

internal class DianaRareMobTarget(
    val targetId: Long,
    val key: String,
    val serverName: String,
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
    private var trackedMob: SkyBlockMob? = null
    private var trackedAttachmentVersion = Long.MIN_VALUE
    private var observedMaxHealth: Long? = null
    val entity: LivingEntity?
        get() = trackedMob?.entity
    val entityUuid: UUID?
        get() = trackedMob?.entityUuid
    val nameplate: ArmorStand?
        get() = trackedMob?.nameplate
    val nameplateUuid: UUID?
        get() = trackedMob?.nameplateUuid
    val currentHealth: Long?
        get() = trackedMob?.health?.current
    val maxHealth: Long?
        get() = trackedMob?.health?.max ?: observedMaxHealth
    val deathConfirmed: Boolean
        get() = trackedMob?.deathConfirmed == true
    var localDamage: Long = 0
        private set
    var lootshareEligible = false
        private set
    var lastLocalAttack: DamageSplashAttackContext? = null
        private set
    private var lastLocalAttackCanDamage = false
    val lastHealthChangeAtMillis: Long?
        get() = trackedMob?.lastHealthChangeAtMillis
    val lastSeenAtMillis: Long?
        get() = trackedMob?.lastSeenAtMillis
    var nearbyWithoutSignalSinceMillis: Long? = null
    private var pendingCocoonHatchUntilMillis: Long? = null
    var glowColor: Color? = null
    val processedDamageSplashIds = mutableSetOf<Int>()

    fun hasVisibleSignal(): Boolean =
        entity != null || nameplate != null

    fun updateFromSignal(signal: DianaRareMobSignal) {
        location = signal.location.roundToBlock()
        val nextMob = signal.trackedMob
        if (trackedMob !== nextMob || trackedAttachmentVersion != nextMob.attachmentVersion) glowColor = null
        trackedMob = nextMob
        trackedAttachmentVersion = nextMob.attachmentVersion
        signal.health?.let { health -> observedMaxHealth = health.max ?: maxOf(observedMaxHealth ?: 0L, health.current) }
        nearbyWithoutSignalSinceMillis = null
        pendingCocoonHatchUntilMillis = null
    }

    fun extendExpiry(expiresAtMillis: Long) {
        this.expiresAtMillis = maxOf(this.expiresAtMillis, expiresAtMillis)
    }

    fun prepareForCocoonHatch(untilMillis: Long) {
        trackedMob = null
        trackedAttachmentVersion = Long.MIN_VALUE
        observedMaxHealth = null
        localDamage = 0
        lootshareEligible = false
        lastLocalAttack = null
        lastLocalAttackCanDamage = false
        nearbyWithoutSignalSinceMillis = null
        pendingCocoonHatchUntilMillis = untilMillis
        glowColor = null
        processedDamageSplashIds.clear()
    }

    fun isAwaitingCocoonHatch(now: Long): Boolean =
        pendingCocoonHatchUntilMillis?.let { now < it } == true

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
            entityUuid = entity.uuid,
            targetLocation = entity.position().toWorldVec(),
            playerLocation = playerLocation,
            now = now,
            canDamage = canDamage,
        )
    }

    fun recordLocalAttack(
        entityUuid: UUID,
        targetLocation: WorldVec,
        playerLocation: WorldVec?,
        now: Long,
        canDamage: Boolean,
    ) {
        lastLocalAttack = DamageSplashAttackContext(
            atMillis = now,
            entityUuid = entityUuid,
            targetLocation = targetLocation,
            playerLocation = playerLocation,
        )
        lastLocalAttackCanDamage = canDamage
    }

    fun damageAttributionLocations(): List<WorldVec> =
        listOfNotNull(lineLocation(), lastLocalAttack?.targetLocation)

    fun targetEntityUuids(): Set<UUID> =
        listOfNotNull(entityUuid, nameplateUuid).toSet()

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
