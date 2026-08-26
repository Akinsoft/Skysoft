package com.skysoft.features.misc

import com.mojang.authlib.GameProfile
import com.skysoft.config.SkysoftConfigGui
import com.skysoft.data.hypixel.HypixelLocationState
import com.skysoft.data.hypixel.HypixelPartyApi
import com.skysoft.data.hypixel.toTabListEntry
import com.skysoft.utils.SkysoftClientEvents
import com.skysoft.utils.chat.ChatEvents
import com.skysoft.utils.chat.ChatMessage
import com.skysoft.utils.chat.ChatMessageVisibility
import java.util.Locale
import java.util.Optional
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.function.Supplier
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.resources.Identifier
import net.minecraft.world.entity.player.PlayerSkin

object PartyDisplay {
    private val config get() = SkysoftConfigGui.config().gui.partyDisplay
    private val profileFutures = mutableMapOf<String, CompletableFuture<GameProfile?>>()
    private val skinLookups = mutableMapOf<UUID, Supplier<PlayerSkin>>()
    private val pendingInvites = linkedMapOf<String, PendingInvite>()
    private var displayedMembers: List<PartyDisplayMember> = emptyList()
    private var requestedParty: Set<UUID>? = null
    private var pendingPartyList: PendingPartyList? = null
    private var lastApiInParty: Boolean? = null
    private var optimisticPartyAtMillis = 0L
    private var wasEnabled = false

    fun register() {
        HypixelPartyApi.registerConsumer("Party Display") { config.enabled }
        SkysoftClientEvents.onEndTick(
            "Party Display update",
            isActive = { config.enabled || wasEnabled || pendingPartyList != null },
        ) { update() }
        SkysoftClientEvents.onDisconnect("Party Display reset", ::reset)
        ChatEvents.onVisibleMessage(
            "Party Display party messages",
            isActive = { config.enabled || pendingPartyList != null },
            listener = ::onChatMessage,
        )
        registerPartyDisplayHud()
    }

    private fun update() {
        val now = System.currentTimeMillis()
        if (pendingPartyList?.expiresAtMillis?.let { now > it } == true) pendingPartyList = null
        pendingInvites.values.removeAll { now >= it.expiresAtMillis }
        displayedMembers = displayedMembers.filterNot { member ->
            member.leavingAtMillis?.let { startedAt -> now >= startedAt + MEMBER_LEAVE_FADE_MILLIS } == true
        }
        if (!config.enabled) {
            clearPartyState()
            lastApiInParty = null
            wasEnabled = false
            return
        }
        wasEnabled = true
        if (!HypixelLocationState.inSkyBlock) {
            clearPartyState()
            lastApiInParty = null
            return
        }
        if (!HypixelPartyApi.isLoaded) return
        if (!HypixelPartyApi.isInParty) {
            if (HypixelPartyApi.state.updatedAtMillis > optimisticPartyAtMillis) {
                val memberIsFading = displayedMembers.any { member -> member.leavingAtMillis != null }
                if (lastApiInParty == true && pendingInvites.isEmpty() && !memberIsFading) clearPartyState()
                if (pendingInvites.isEmpty() && !memberIsFading) lastApiInParty = false
                requestedParty = null
            }
            return
        }
        lastApiInParty = true
        val party = HypixelPartyApi.memberUuids.toSet()
        if (party == requestedParty || pendingPartyList != null) return
        requestedParty = party
        pendingPartyList = PendingPartyList(expiresAtMillis = now + PARTY_LIST_TIMEOUT_MILLIS)
        // TODO Replace chat and /party list rank resolution with the Skysoft backend.
        if (Minecraft.getInstance().connection?.sendCommand("party list") == null) {
            pendingPartyList = null
            requestedParty = null
        }
    }

    private fun onChatMessage(message: ChatMessage): ChatMessageVisibility {
        val rawText = message.plainText
        val text = rawText.trim()
        updateFromPartyMessage(message.component, rawText, text)
        val pending = pendingPartyList ?: return ChatMessageVisibility.SHOW
        if (System.currentTimeMillis() > pending.expiresAtMillis) {
            pendingPartyList = null
            return ChatMessageVisibility.SHOW
        }
        return when {
            text in NOT_IN_PARTY_MESSAGES -> {
                clearPartyState()
                pendingPartyList = null
                ChatMessageVisibility.HIDE
            }
            text == PARTY_LIST_SEPARATOR -> {
                if (pending.started) {
                    if (!pending.discard) applyPartyList(pending.members)
                    pendingPartyList = null
                }
                ChatMessageVisibility.HIDE
            }
            PARTY_LIST_HEADER_PATTERN.matches(text) -> {
                pending.started = true
                pending.members.clear()
                ChatMessageVisibility.HIDE
            }
            pending.started -> parsePartyListSection(message.component, rawText, text, pending)
            else -> ChatMessageVisibility.SHOW
        }
    }

    private fun updateFromPartyMessage(component: Component, rawText: String, text: String) {
        val partyEnded = text == YOU_LEFT_PARTY_MESSAGE ||
            SELF_REMOVED_PATTERN.matches(text) ||
            PARTY_DISBANDED_PATTERN.matches(text) ||
            OTHER_DISBANDED_PATTERN.matches(text)
        val outgoingInvite = OUTGOING_INVITE_PATTERN.matchEntire(text)
        val inviteExpired = INVITE_EXPIRED_PATTERN.matchEntire(text)
        val youJoined = YOU_JOINED_PARTY_PATTERN.matchEntire(text)
        val partyingWith = PARTYING_WITH_PATTERN.matchEntire(text)
        val memberJoined = MEMBER_JOINED_PATTERNS.firstNotNullOfOrNull { it.matchEntire(text) }
        val memberRemoved = MEMBER_REMOVED_PATTERNS.firstNotNullOfOrNull { it.matchEntire(text) }
        val transfer = TRANSFER_PATTERN.matchEntire(text)
        when {
            text in NOT_IN_PARTY_MESSAGES || partyEnded -> {
                clearPartyState()
                if (partyEnded) HypixelPartyApi.requestPartyInfo()
            }
            outgoingInvite != null -> {
                lastApiInParty = true
                optimisticPartyAtMillis = System.currentTimeMillis()
                partyMember(component, rawText, text, outgoingInvite.groups["inviter"])?.let(::addPartyMember)
                partyMember(component, rawText, text, outgoingInvite.groups["invitee"])?.let { member ->
                    if (displayedMembers.none { samePlayer(it.name, member.name) }) {
                        val seconds = outgoingInvite.groups["seconds"]?.value?.toLongOrNull() ?: INVITE_SECONDS
                        pendingInvites[playerKey(member.name)] = PendingInvite(
                            member.copy(invited = true),
                            System.currentTimeMillis() + seconds * MILLIS_PER_SECOND,
                        )
                    }
                }
                HypixelPartyApi.requestPartyInfo()
            }
            inviteExpired != null -> playerName(inviteExpired.groups["invitee"])
                ?.let { pendingInvites.remove(playerKey(it)) }
            youJoined != null -> {
                clearPartyState()
                lastApiInParty = true
                optimisticPartyAtMillis = System.currentTimeMillis()
                partyMember(component, rawText, text, youJoined.groups["leader"])?.let(::addPartyMember)
                currentPlayerMember()?.let(::addPartyMember)
                HypixelPartyApi.requestPartyInfo()
            }
            partyingWith != null -> {
                lastApiInParty = true
                optimisticPartyAtMillis = System.currentTimeMillis()
                partyMembers(component, rawText, text, partyingWith.groups["members"]).forEach(::addPartyMember)
                currentPlayerMember()?.let(::addPartyMember)
            }
            memberJoined != null -> {
                lastApiInParty = true
                optimisticPartyAtMillis = System.currentTimeMillis()
                partyMember(component, rawText, text, memberJoined.groups["member"])?.let(::addPartyMember)
                currentPlayerMember()?.let(::addPartyMember)
                HypixelPartyApi.requestPartyInfo()
            }
            memberRemoved != null -> {
                playerName(memberRemoved.groups["member"])?.let(::removePartyMember)
                HypixelPartyApi.requestPartyInfo()
            }
            transfer != null -> {
                playerName(transfer.groups["member"])?.let(::removePartyMember)
                partyMember(component, rawText, text, transfer.groups["leader"])
                    ?.let { addPartyMember(it, first = true) }
                HypixelPartyApi.requestPartyInfo()
            }
        }
    }

    private fun parsePartyListSection(
        component: Component,
        rawText: String,
        text: String,
        pending: PendingPartyList,
    ): ChatMessageVisibility {
        val section = PARTY_LIST_SECTION_PATTERN.matchEntire(text) ?: return ChatMessageVisibility.SHOW
        val names = section.groups["names"] ?: return ChatMessageVisibility.HIDE
        val textOffset = rawText.indexOf(text).coerceAtLeast(0)
        for (match in PARTY_LIST_MEMBER_PATTERN.findAll(names.value)) {
            val name = match.groups["name"] ?: continue
            val index = textOffset + names.range.first + name.range.first
            pending.members += PartyDisplayMember(
                name = name.value,
                component = Component.literal(name.value).withStyle(component.styleAt(index)),
            )
        }
        return ChatMessageVisibility.HIDE
    }

    private fun partyMember(
        component: Component,
        rawText: String,
        text: String,
        group: MatchGroup?,
    ): PartyDisplayMember? {
        group ?: return null
        return partyMember(component, rawText, text, group.value, group.range.first)
    }

    private fun partyMembers(
        component: Component,
        rawText: String,
        text: String,
        group: MatchGroup?,
    ): List<PartyDisplayMember> {
        group ?: return emptyList()
        return PARTY_PLAYER_PATTERN.findAll(group.value).mapNotNull { match ->
            partyMember(component, rawText, text, match.value, group.range.first + match.range.first)
        }.toList()
    }

    private fun partyMember(
        component: Component,
        rawText: String,
        text: String,
        value: String,
        rangeStart: Int,
    ): PartyDisplayMember? {
        val name = PLAYER_NAME_PATTERN.findAll(value).lastOrNull() ?: return null
        val index = rawText.indexOf(text).coerceAtLeast(0) + rangeStart + name.range.first
        return PartyDisplayMember(
            name = name.value,
            component = Component.literal(name.value).withStyle(component.styleAt(index)),
        )
    }

    private fun currentPlayerMember(): PartyDisplayMember? {
        val minecraft = Minecraft.getInstance()
        val player = minecraft.player ?: return null
        val name = player.gameProfile.name
        val displayName = minecraft.connection?.getPlayerInfo(player.uuid)?.toTabListEntry()?.displayName
        val index = displayName?.string?.lastIndexOf(name) ?: -1
        val style = if (displayName != null && index >= 0) displayName.styleAt(index) else Style.EMPTY
        return PartyDisplayMember(name, Component.literal(name).withStyle(style))
    }

    private fun addPartyMember(member: PartyDisplayMember, first: Boolean = false) {
        val key = playerKey(member.name)
        pendingInvites.remove(key)
        val index = displayedMembers.indexOfFirst { playerKey(it.name) == key }
        if (index < 0) {
            displayedMembers += member.copy(invited = false)
        } else if (displayedMembers[index].leavingAtMillis != null) {
            displayedMembers = displayedMembers.toMutableList().also { members ->
                members[index] = members[index].copy(leavingAtMillis = null)
            }
        }
        if (first) displayedMembers = displayedMembers.sortedBy { playerKey(it.name) != key }
    }

    private fun removePartyMember(name: String) {
        val key = playerKey(name)
        val now = System.currentTimeMillis()
        displayedMembers = displayedMembers.map { member ->
            if (playerKey(member.name) == key && member.leavingAtMillis == null) {
                member.copy(leavingAtMillis = now)
            } else {
                member
            }
        }
        pendingInvites.remove(key)
    }

    private fun applyPartyList(members: List<PartyDisplayMember>) {
        val latestMembers = members.associateBy { playerKey(it.name) }
        val existingNames = displayedMembers.mapTo(mutableSetOf()) { playerKey(it.name) }
        val now = System.currentTimeMillis()
        displayedMembers = displayedMembers.map { existing ->
            latestMembers[playerKey(existing.name)]
                ?: existing.takeIf { it.leavingAtMillis != null }
                ?: existing.copy(leavingAtMillis = now)
        } + members.filterNot { playerKey(it.name) in existingNames }
        members.firstOrNull()?.let { addPartyMember(it, first = true) }
        pendingInvites.keys.removeAll(latestMembers.keys)
    }

    private fun clearPartyState() {
        displayedMembers = emptyList()
        pendingInvites.clear()
        requestedParty = null
        pendingPartyList?.discard = true
        optimisticPartyAtMillis = 0L
    }

    internal fun currentMembers(): List<PartyDisplayMember> =
        displayedMembers + pendingInvites.values.map(PendingInvite::member)

    internal fun face(member: PartyDisplayMember): PartyDisplayFace? {
        val minecraft = Minecraft.getInstance()
        minecraft.connection?.getPlayerInfo(member.name)?.let { playerInfo ->
            return PartyDisplayFace(playerInfo.skin.body().texturePath(), playerInfo.showHat())
        }
        val profile = profileFutures.getOrPut(member.name.lowercase(Locale.ROOT)) {
            CompletableFuture.supplyAsync<GameProfile?> {
                runCatching {
                    minecraft.services().profileResolver().fetchByName(member.name).orElse(null)
                }.getOrNull()
            }
        }.getNow(null) ?: return null
        val skin = skinLookups.getOrPut(profile.id) {
            minecraft.skinManager.createLookup(profile, false)
        }.get()
        return PartyDisplayFace(skin.body().texturePath(), true)
    }

    private fun reset() {
        displayedMembers = emptyList()
        pendingInvites.clear()
        requestedParty = null
        pendingPartyList = null
        profileFutures.clear()
        skinLookups.clear()
        lastApiInParty = null
        optimisticPartyAtMillis = 0L
        wasEnabled = false
    }

    private data class PendingInvite(val member: PartyDisplayMember, val expiresAtMillis: Long)

    private data class PendingPartyList(
        val members: MutableList<PartyDisplayMember> = mutableListOf(),
        val expiresAtMillis: Long,
        var started: Boolean = false,
        var discard: Boolean = false,
    )

    private fun Component.styleAt(index: Int): Style {
        var offset = 0
        var result = Style.EMPTY
        visit({ style: Style, segment: String ->
            val end = offset + segment.length
            if (index in offset until end) {
                result = style
                Optional.of(Unit)
            } else {
                offset = end
                Optional.empty()
            }
        }, Style.EMPTY)
        return result
    }

    private const val PARTY_PLAYER = """(?:\[[^]]+] )?[A-Za-z0-9_]{1,16}"""
    private const val PARTY_LIST_TIMEOUT_MILLIS = 3_000L
    private const val INVITE_SECONDS = 60L
    private const val MILLIS_PER_SECOND = 1_000L
    private const val PARTY_LIST_SEPARATOR = "-----------------------------------------------------"
    private const val YOU_LEFT_PARTY_MESSAGE = "You left the party."
    private val PARTY_PLAYER_PATTERN = Regex(PARTY_PLAYER)
    private val PARTY_LIST_HEADER_PATTERN = Regex("""Party Members \(\d+\)""")
    private val PARTY_LIST_SECTION_PATTERN = Regex("""Party (?:Leader|Moderators|Members): (?<names>.*)""")
    private val PARTY_LIST_MEMBER_PATTERN = Regex(
        """(?:^| ● )\s*(?:\[[^]]+] )*(?<name>[A-Za-z0-9_]{1,16})(?=\s*●)""",
    )
    private val OUTGOING_INVITE_PATTERN = Regex(
        """(?<inviter>$PARTY_PLAYER) invited (?<invitee>$PARTY_PLAYER) to the party! """ +
            """They have (?<seconds>\d+) seconds to accept\.""",
    )
    private val INVITE_EXPIRED_PATTERN = Regex(
        """The party invite to (?<invitee>$PARTY_PLAYER) has expired\.""",
    )
    private val YOU_JOINED_PARTY_PATTERN = Regex(
        """You have joined (?<leader>$PARTY_PLAYER)'s? party!""",
    )
    private val PARTYING_WITH_PATTERN = Regex("""You'll be partying with: (?<members>.+)""")
    private val MEMBER_JOINED_PATTERNS = listOf(
        Regex("""(?<member>$PARTY_PLAYER) joined the party\."""),
        Regex("""Party Finder > (?<member>$PARTY_PLAYER) joined the group! \(Combat Level \d+\)"""),
        Regex("""Party Finder > (?<member>$PARTY_PLAYER) joined the dungeon group! \(.+ Level \d+\)"""),
    )
    private val MEMBER_REMOVED_PATTERNS = listOf(
        Regex("""(?<member>$PARTY_PLAYER) has left the party\."""),
        Regex("""(?<member>$PARTY_PLAYER) has been removed from the party\."""),
        Regex("""Kicked (?<member>$PARTY_PLAYER) because they were offline\."""),
        Regex("""(?<member>$PARTY_PLAYER) was removed from your party because they disconnected\."""),
    )
    private val TRANSFER_PATTERN = Regex(
        """The party was transferred to (?<leader>$PARTY_PLAYER) """ +
            """(?:because (?<member>$PARTY_PLAYER) left|by $PARTY_PLAYER)\.?""",
    )
    private val SELF_REMOVED_PATTERN = Regex("""You have been (?:removed|kicked) from the party.*""")
    private val PARTY_DISBANDED_PATTERN = Regex("""The party was disbanded.*""")
    private val OTHER_DISBANDED_PATTERN = Regex("""$PARTY_PLAYER has disbanded the party!""")
    private val NOT_IN_PARTY_MESSAGES = setOf(
        "You are not currently in a party.",
        "You are not in a party.",
        "You are not in a party right now.",
    )
}

internal data class PartyDisplayMember(
    val name: String,
    val component: Component,
    val invited: Boolean = false,
    val leavingAtMillis: Long? = null,
)

internal data class PartyDisplayFace(val texture: Identifier, val showHat: Boolean)

internal const val MEMBER_LEAVE_FADE_MILLIS = 750L

private val PLAYER_NAME_PATTERN = Regex("""[A-Za-z0-9_]{1,16}""")

private fun playerName(group: MatchGroup?): String? =
    group?.value?.let { PLAYER_NAME_PATTERN.findAll(it).lastOrNull()?.value }

private fun samePlayer(first: String, second: String): Boolean =
    first.equals(second, ignoreCase = true)

private fun playerKey(name: String): String = name.lowercase(Locale.ROOT)
