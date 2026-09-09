package com.skysoft.features.pets

import com.skysoft.data.ClientEntitySnapshot
import com.skysoft.data.StoredPetData
import com.skysoft.data.hypixel.HypixelLocationState
import com.skysoft.data.skyblock.SkyBlockItemUtilities.playerHeadTexture
import com.skysoft.utils.ActiveConsumerRegistry
import com.skysoft.utils.SkysoftClientEvents
import net.minecraft.client.Minecraft
import net.minecraft.world.entity.player.Player

internal object ActivePetEntityTracker {
    private val consumers = ActiveConsumerRegistry()
    private var observation: ActivePetEntityObservation? = null
    private var ticks = 0

    fun register() {
        SkysoftClientEvents.onEndTick(
            "Active Pet entity tracking",
            isActive = { consumers.hasActiveConsumers || observation != null },
        ) { tick() }
        SkysoftClientEvents.onDisconnect("Active Pet entity disconnect reset", ::clear)
    }

    fun registerConsumer(id: String, isActive: () -> Boolean) {
        consumers.register(id, isActive)
    }

    fun current(): ActivePetEntityObservation? = observation

    private fun tick() {
        if (!consumers.hasActiveConsumers) {
            clear()
            return
        }
        ticks++
        val context = trackingContext() ?: run {
            clear()
            return
        }
        val matcher = PetEntityMatcher(context.currentPet, context.expectedTextures, context.player)
        val identity = matcher.identity
        val previous = observation
        if (previous != null && previous.identity != identity) clear()
        val entities = ClientEntitySnapshot.entities()

        val trackedEntity = observation?.entity?.id?.let(context.level::getEntity)
        val trackedObservation = trackedEntity?.let { matcher.observe(it, entities) }
        if (trackedObservation != null) {
            observation = trackedObservation
            return
        }
        if (ticks % TARGET_SCAN_INTERVAL != 0) return

        observation = matcher.find(entities)
    }

    private fun trackingContext(): TrackingContext? {
        if (!HypixelLocationState.inSkyBlock) return null
        val minecraft = Minecraft.getInstance()
        val level = minecraft.level ?: return null
        val player = minecraft.player ?: return null
        val currentPet = ActivePetTracker.currentPet ?: return null
        return TrackingContext(
            level = level,
            player = player,
            currentPet = currentPet,
            expectedTextures = currentPet.expectedTextures(),
        )
    }

    private fun clear() {
        observation = null
    }

    private data class TrackingContext(
        val level: net.minecraft.client.multiplayer.ClientLevel,
        val player: Player,
        val currentPet: StoredPetData,
        val expectedTextures: Set<String>,
    )

    private const val TARGET_SCAN_INTERVAL = 2
}

private fun StoredPetData.expectedTextures(): Set<String> =
    getAnimatedItemStackSequence(firstFrameOnly = false)
        ?.mapNotNull { it.stack.playerHeadTexture() }
        ?.toSet()
        .orEmpty()
