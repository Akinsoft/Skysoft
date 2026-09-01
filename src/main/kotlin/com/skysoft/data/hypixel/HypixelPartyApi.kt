package com.skysoft.data.hypixel

import com.skysoft.SkysoftMod
import com.skysoft.utils.ActiveConsumerRegistry
import com.skysoft.utils.ActiveStatePublisher
import com.skysoft.utils.SkysoftClientEvents
import net.hypixel.modapi.HypixelModAPI
import net.hypixel.modapi.packet.impl.clientbound.ClientboundHelloPacket
import net.hypixel.modapi.packet.impl.clientbound.ClientboundPartyInfoPacket
import net.hypixel.modapi.packet.impl.clientbound.ClientboundPartyInfoPacket.PartyRole
import net.hypixel.modapi.packet.impl.serverbound.ServerboundPartyInfoPacket
import java.util.UUID

object HypixelPartyApi {
    private val consumers = ActiveConsumerRegistry()
    private val publisher = ActiveStatePublisher("Hypixel Party API", HypixelPartyState.EMPTY)
    private var isRegistered = false
    private var isBackgroundActive = false
    private var lastRequestAtMillis = 0L
    private var nextRefreshAtMillis = 0L

    val isLoaded: Boolean
        get() = publisher.state.isLoaded

    val isInParty: Boolean
        get() = publisher.state.isInParty

    val leaderUuid: UUID?
        get() = publisher.state.leaderUuid

    val memberUuids: Set<UUID>
        get() = publisher.state.memberUuids

    fun register() {
        if (isRegistered) return
        isRegistered = true
        publisher.register()

        val modApi = HypixelModAPI.getInstance()
        modApi.createHandler(ClientboundHelloPacket::class.java) {
            if (hasActiveConsumers) requestPartyInfo(force = true)
        }
        modApi.createHandler(ClientboundPartyInfoPacket::class.java, ::onPartyInfoPacket)

        TabListApi.onChange(
            "Hypixel Party member identities",
            isActive = { hasActiveConsumers },
            listener = { enrichMemberIdentities() },
        )
        SkysoftClientEvents.onEndTick(
            "Hypixel Party refresh",
            isActive = { hasActiveConsumers || isBackgroundActive },
        ) {
            onTick()
        }
        SkysoftClientEvents.onDisconnect("Hypixel Party reset", ::reset)
    }

    val hasActiveConsumers: Boolean
        get() = consumers.hasActiveConsumers

    fun registerConsumer(id: String, isActive: () -> Boolean) {
        consumers.register(id, isActive)
    }

    fun onChange(
        boundary: String,
        isActive: () -> Boolean,
        listener: (HypixelPartyState) -> Unit,
    ) {
        registerConsumer(boundary, isActive)
        publisher.onChange(boundary, isActive, listener)
    }

    private fun onTick() {
        if (!hasActiveConsumers) {
            reset()
            return
        }
        if (!isBackgroundActive) {
            isBackgroundActive = true
            nextRefreshAtMillis = 0L
        }
        if (!HypixelLocationState.inSkyBlock) return
        val now = System.currentTimeMillis()
        if (now < nextRefreshAtMillis) return
        nextRefreshAtMillis = now + when (requestPartyInfo(now = now)) {
            PartyInfoRequestResult.FAILED -> REQUEST_RETRY_INTERVAL_MILLIS
            PartyInfoRequestResult.SENT,
            PartyInfoRequestResult.COOLDOWN,
            -> REFRESH_INTERVAL_MILLIS
        }
    }

    internal fun requestPartyInfo(
        force: Boolean = false,
        now: Long = System.currentTimeMillis(),
        sendPacket: () -> Unit = {
            HypixelModAPI.getInstance().sendPacket(ServerboundPartyInfoPacket())
        },
    ): PartyInfoRequestResult {
        if (!force && now - lastRequestAtMillis < REQUEST_COOLDOWN_MILLIS) {
            return PartyInfoRequestResult.COOLDOWN
        }
        return try {
            sendPacket()
            lastRequestAtMillis = now
            PartyInfoRequestResult.SENT
        } catch (e: Exception) {
            SkysoftMod.LOGGER.warn("Failed to request Hypixel party information", e)
            PartyInfoRequestResult.FAILED
        }
    }

    private fun onPartyInfoPacket(packet: ClientboundPartyInfoPacket) {
        if (!hasActiveConsumers) return
        acceptPartyInfo(packet, System.currentTimeMillis())
    }

    internal fun acceptPartyInfo(packet: ClientboundPartyInfoPacket, now: Long) {
        val names = TabListApi.playerProfiles.associate { profile -> profile.uuid to profile.profileName }
        val members = if (packet.isInParty) {
            packet.memberMap.values.associate { member ->
                member.uuid to HypixelPartyMember(member.uuid, member.role.toSkysoftRole(), names[member.uuid])
            }
        } else {
            emptyMap()
        }
        publisher.update(
            HypixelPartyState(
                isInParty = packet.isInParty,
                members = members,
                updatedAtMillis = now,
            ),
        )
    }

    private fun enrichMemberIdentities() {
        val current = publisher.state
        if (!current.isInParty) return
        val names = TabListApi.playerProfiles.associate { profile -> profile.uuid to profile.profileName }
        val updatedMembers = current.members.mapValues { (uuid, member) ->
            member.copy(profileName = names[uuid] ?: member.profileName)
        }
        if (updatedMembers != current.members) publisher.update(current.copy(members = updatedMembers))
    }

    private fun reset() {
        isBackgroundActive = false
        publisher.update(HypixelPartyState.EMPTY)
        lastRequestAtMillis = 0L
        nextRefreshAtMillis = 0L
    }

    private fun PartyRole.toSkysoftRole(): HypixelPartyRole =
        when (this) {
            PartyRole.LEADER -> HypixelPartyRole.LEADER
            PartyRole.MOD -> HypixelPartyRole.MOD
            PartyRole.MEMBER -> HypixelPartyRole.MEMBER
        }

    private const val REQUEST_COOLDOWN_MILLIS = 5_000L
    private const val REQUEST_RETRY_INTERVAL_MILLIS = 5_000L
    private const val REFRESH_INTERVAL_MILLIS = 30_000L
}

internal enum class PartyInfoRequestResult {
    SENT,
    COOLDOWN,
    FAILED,
}

data class HypixelPartyState(
    val isInParty: Boolean,
    val members: Map<UUID, HypixelPartyMember>,
    val updatedAtMillis: Long,
) {
    val isLoaded: Boolean
        get() = updatedAtMillis > 0L

    val leaderUuid: UUID?
        get() = members.values.firstOrNull { member -> member.role == HypixelPartyRole.LEADER }?.uuid

    val memberUuids: Set<UUID>
        get() = members.keys

    companion object {
        val EMPTY = HypixelPartyState(
            isInParty = false,
            members = emptyMap(),
            updatedAtMillis = 0L,
        )
    }
}

data class HypixelPartyMember(
    val uuid: UUID,
    val role: HypixelPartyRole,
    val profileName: String? = null,
)

enum class HypixelPartyRole {
    LEADER,
    MOD,
    MEMBER,
}
