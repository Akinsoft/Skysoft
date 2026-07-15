package com.skysoft.data.hypixel

import com.skysoft.SkysoftMod
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.hypixel.modapi.HypixelModAPI
import net.hypixel.modapi.packet.impl.clientbound.ClientboundHelloPacket
import net.hypixel.modapi.packet.impl.clientbound.ClientboundPartyInfoPacket
import net.hypixel.modapi.packet.impl.clientbound.ClientboundPartyInfoPacket.PartyRole
import net.hypixel.modapi.packet.impl.serverbound.ServerboundPartyInfoPacket
import java.util.UUID

object HypixelPartyApi {
    private val listeners = mutableListOf<(HypixelPartyState) -> Unit>()
    private var isRegistered = false
    private var lastRequestAtMillis = 0L
    private var nextRefreshAtMillis = 0L

    var state: HypixelPartyState = HypixelPartyState.EMPTY
        private set

    var version: Long = 0
        private set

    val isLoaded: Boolean
        get() = state.isLoaded

    val isInParty: Boolean
        get() = state.isInParty

    val leaderUuid: UUID?
        get() = state.leaderUuid

    val memberUuids: Set<UUID>
        get() = state.memberUuids

    fun register() {
        if (isRegistered) return
        isRegistered = true

        val modApi = HypixelModAPI.getInstance()
        modApi.createHandler(ClientboundHelloPacket::class.java) {
            requestPartyInfo(force = true)
        }
        modApi.createHandler(ClientboundPartyInfoPacket::class.java, ::onPartyInfoPacket)

        ClientTickEvents.END_CLIENT_TICK.register {
            onTick()
        }
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ -> reset() }
    }

    fun refresh() {
        requestPartyInfo(force = true)
    }

    fun onChange(listener: (HypixelPartyState) -> Unit) {
        listeners += listener
    }

    fun member(uuid: UUID): HypixelPartyMember? =
        state.members[uuid]

    private fun onTick() {
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
        acceptPartyInfo(packet, System.currentTimeMillis())
    }

    internal fun acceptPartyInfo(packet: ClientboundPartyInfoPacket, now: Long) {
        val members = if (packet.isInParty) {
            packet.memberMap.values.associate { member ->
                member.uuid to HypixelPartyMember(member.uuid, member.role.toSkysoftRole())
            }
        } else {
            emptyMap()
        }
        updateState(
            HypixelPartyState(
                isInParty = packet.isInParty,
                members = members,
                updatedAtMillis = now,
            ),
        )
    }

    private fun reset() {
        updateState(HypixelPartyState.EMPTY)
        lastRequestAtMillis = 0L
        nextRefreshAtMillis = 0L
    }

    private fun updateState(nextState: HypixelPartyState) {
        val hasChanged = state.isInParty != nextState.isInParty || state.members != nextState.members
        state = nextState
        if (!hasChanged) return
        version++
        listeners.toList().forEach { listener -> listener(nextState) }
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
)

enum class HypixelPartyRole {
    LEADER,
    MOD,
    MEMBER,
}
