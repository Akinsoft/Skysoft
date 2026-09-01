package com.skysoft.features.combat

import com.skysoft.data.ClientEntitySnapshot
import com.skysoft.data.hypixel.HypixelLocationState
import com.skysoft.events.entity.EntityLifecycleEvents
import com.skysoft.utils.SkysoftClientEvents
import com.skysoft.utils.WorldVec
import java.util.UUID
import net.minecraft.client.Minecraft
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.decoration.ArmorStand

internal class SkyBlockMob internal constructor(
    val serverName: String,
    val entityUuid: UUID,
    name: String,
    location: WorldVec,
    entity: LivingEntity,
    nameplate: ArmorStand?,
    health: SkyBlockMobHealth?,
    now: Long,
) {
    var name: String = name
        private set
    var location: WorldVec = location
        private set
    var entity: LivingEntity? = entity
        private set
    var nameplate: ArmorStand? = nameplate
        private set
    var nameplateUuid: UUID? = nameplate?.uuid
        private set
    var health: SkyBlockMobHealth? = health
        private set
    var lastHealthChangeAtMillis: Long? = null
        private set
    var lastSeenAtMillis: Long = now
        private set
    var attachmentVersion: Long = 0
        private set
    var deathConfirmed: Boolean = health?.current == 0L || entity.isDeadOrDying
        private set

    val isVisible: Boolean
        get() = entity != null || nameplate != null

    internal fun update(observation: DetectedSkyBlockMob, now: Long) {
        if (entity !== observation.entity || nameplate !== observation.nameplate) attachmentVersion++
        val previousHealth = health
        if (observation.nameplate != null || nameplateUuid == null) name = observation.name
        location = observation.location
        entity = observation.entity
        nameplate = observation.nameplate
        nameplateUuid = observation.nameplate?.uuid ?: nameplateUuid
        health = observation.health?.let { current ->
            SkyBlockMobHealth(current.current, current.max ?: previousHealth?.max)
        } ?: previousHealth
        if (previousHealth?.current != null && previousHealth.current != health?.current) {
            lastHealthChangeAtMillis = now
        }
        lastSeenAtMillis = now
        deathConfirmed = health?.current == 0L || observation.entity.isDeadOrDying
    }

    internal fun detach(unloadedEntity: Entity) {
        if (unloadedEntity.uuid == entityUuid && unloadedEntity is LivingEntity && unloadedEntity.isDeadOrDying) {
            deathConfirmed = true
        }
        if (health?.current == 0L) deathConfirmed = true
        var detached = false
        if (entity === unloadedEntity) {
            entity = null
            detached = true
        }
        if (nameplate === unloadedEntity) {
            nameplate = null
            detached = true
        }
        if (detached) attachmentVersion++
    }

    internal fun suspend() {
        if (!isVisible) return
        entity = null
        nameplate = null
        attachmentVersion++
    }
}

internal object SkyBlockMobTracker {
    private val mobs = mutableMapOf<SkyBlockMobKey, SkyBlockMob>()
    private var activeServerName: String? = null
    private var scannedTick = Long.MIN_VALUE

    fun register() {
        SkysoftClientEvents.onEndTick(
            "SkyBlock Mob Tracker tick",
            isActive = { HypixelLocationState.inSkyBlock || mobs.isNotEmpty() },
        ) { refresh() }
        SkysoftClientEvents.onDisconnect("SkyBlock Mob Tracker disconnect reset", ::clear)
        EntityLifecycleEvents.onLoad(
            "SkyBlock Mob Tracker entity loading",
            isActive = { HypixelLocationState.inSkyBlock },
        ) { scannedTick = Long.MIN_VALUE }
        EntityLifecycleEvents.onUnload(
            "SkyBlock Mob Tracker entity unloading",
            isActive = { mobs.isNotEmpty() },
            listener = ::onEntityUnload,
        )
    }

    fun visibleMobs(): List<SkyBlockMob> {
        refresh()
        val serverName = currentServerName() ?: return emptyList()
        return mobs.values.filter { mob ->
            mob.serverName == serverName && mob.isVisible && !mob.deathConfirmed
        }
    }

    private fun refresh() {
        val now = System.currentTimeMillis()
        val serverName = currentServerName()
        if (activeServerName != serverName) {
            activeServerName?.let(::suspendServer)
            activeServerName = serverName
            scannedTick = Long.MIN_VALUE
        }
        val level = Minecraft.getInstance().level
        if (serverName == null || level == null) {
            activeServerName?.let(::suspendServer)
            prune(now)
            return
        }
        if (scannedTick == level.gameTime) {
            prune(now)
            return
        }
        scannedTick = level.gameTime
        val observations = SkyBlockMobEntityMatcher.detectedMobs(ClientEntitySnapshot.entities())
        val seen = observations.mapTo(mutableSetOf()) { observation ->
            val key = SkyBlockMobKey(serverName, observation.entity.uuid)
            mobs.getOrPut(key) {
                SkyBlockMob(
                    serverName = serverName,
                    entityUuid = observation.entity.uuid,
                    name = observation.name,
                    location = observation.location,
                    entity = observation.entity,
                    nameplate = observation.nameplate,
                    health = observation.health,
                    now = now,
                )
            }.also { mob -> mob.update(observation, now) }
            key
        }
        mobs.forEach { (key, mob) ->
            if (key.serverName == serverName && key !in seen) mob.suspend()
        }
        prune(now)
    }

    private fun onEntityUnload(entity: Entity) {
        mobs.values.forEach { mob -> mob.detach(entity) }
        scannedTick = Long.MIN_VALUE
    }

    private fun suspendServer(serverName: String) {
        mobs.values.filter { mob -> mob.serverName == serverName }.forEach(SkyBlockMob::suspend)
    }

    private fun prune(now: Long) {
        mobs.entries.removeIf { (_, mob) ->
            if (now - mob.lastSeenAtMillis < RETENTION_MILLIS) return@removeIf false
            mob.suspend()
            true
        }
    }

    private fun clear() {
        mobs.values.forEach(SkyBlockMob::suspend)
        mobs.clear()
        activeServerName = null
        scannedTick = Long.MIN_VALUE
    }

    private fun currentServerName(): String? =
        HypixelLocationState.currentServerName?.takeIf { HypixelLocationState.inSkyBlock && it.isNotBlank() }

    private data class SkyBlockMobKey(
        val serverName: String,
        val entityUuid: UUID,
    )

    private const val RETENTION_MILLIS = 120_000L
}
