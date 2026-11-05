package com.skysoft.features.misc

import com.skysoft.config.SkysoftConfigGui
import com.skysoft.data.MinecraftProfileLookup
import com.skysoft.data.hypixel.HypixelLocationState
import com.skysoft.data.hypixel.HypixelPartyApi
import com.skysoft.data.hypixel.HypixelPartyState
import com.skysoft.data.hypixel.TabListApi
import com.skysoft.utils.MILLIS_PER_SECOND
import com.skysoft.utils.SkysoftClientEvents
import com.skysoft.utils.chat.ChatEvents
import com.skysoft.utils.chat.ChatMessage
import com.skysoft.utils.chat.ChatMessageVisibility
import com.skysoft.utils.chat.HYPIXEL_CHAT_SEPARATOR
import com.skysoft.utils.chat.styleAt
import java.util.UUID
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.resources.Identifier

object PartyDisplay {
    private val config get() = SkysoftConfigGui.config().gui.information.partyDisplay
    private val roster = PartyDisplayRoster()
    private var requestedParty: Set<UUID>? = null
    private var pendingPartyList: PendingPartyList? = null
    private var lastApiInParty: Boolean? = null
    private var optimisticPartyAtMillis = 0L
    private var wasEnabled = false

    fun register() {
        HypixelPartyApi.onChange(
            "Party Display",
            isActive = { config.enabled },
            listener = ::onPartyState,
        )
        HypixelLocationState.onChange(
            "Party Display location",
            isActive = { config.enabled || wasEnabled },
        ) { location ->
            if (!location.inSkyBlock) {
                clearDisplayedParty()
                lastApiInParty = null
            }
        }
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
        roster.update(now)
        if (!config.enabled) {
            clearPartyState()
            lastApiInParty = null
            wasEnabled = false
            return
        }
        wasEnabled = true
    }

    private fun onPartyState(state: HypixelPartyState) {
        if (!config.enabled || !HypixelLocationState.inSkyBlock || !state.isLoaded) return
        if (!state.isInParty) {
            if (state.updatedAtMillis > optimisticPartyAtMillis) {
                val memberIsFading = roster.hasLeavingMembers
                if (lastApiInParty == true && !roster.hasPendingInvites && !memberIsFading) clearDisplayedParty()
                if (!roster.hasPendingInvites && !memberIsFading) lastApiInParty = false
                requestedParty = null
            }
            return
        }
        lastApiInParty = true
        state.members.values
            .sortedBy { member -> member.uuid != state.leaderUuid }
            .forEach { member ->
                member.profileName?.let { name ->
                    roster.addPartyMember(apiPartyMember(member.uuid, name), first = member.uuid == state.leaderUuid)
                }
            }
        val party = state.memberUuids
        if (party == requestedParty || pendingPartyList != null) return
        requestedParty = party
        pendingPartyList = PendingPartyList(expiresAtMillis = System.currentTimeMillis() + PARTY_LIST_TIMEOUT_MILLIS)
        if (Minecraft.getInstance().connection?.sendCommand("party list") == null) {
            pendingPartyList = null
            requestedParty = null
        }
    }

    private fun onChatMessage(message: ChatMessage): ChatMessageVisibility {
        val partyText = PartyMessageText(message.component, message.plainText)
        val text = partyText.text
        updateFromPartyMessage(partyText)
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
            text == HYPIXEL_CHAT_SEPARATOR -> {
                if (pending.started) {
                    if (!pending.discard) roster.applyPartyList(pending.members)
                    pendingPartyList = null
                }
                ChatMessageVisibility.HIDE
            }
            PARTY_LIST_HEADER_PATTERN.matches(text) -> {
                pending.started = true
                pending.members.clear()
                ChatMessageVisibility.HIDE
            }
            pending.started -> parsePartyListSection(partyText, pending)
            else -> ChatMessageVisibility.SHOW
        }
    }

    private fun updateFromPartyMessage(partyText: PartyMessageText) {
        val text = partyText.text
        val partyEnded = text == YOU_LEFT_PARTY_MESSAGE ||
            SELF_REMOVED_PATTERN.matches(text) ||
            PARTY_DISBANDED_PATTERN.matches(text) ||
            OTHER_DISBANDED_PATTERN.matches(text)
        val outgoingInvite = OUTGOING_INVITE_PATTERN.matchEntire(text)
        val inviteExpired = INVITE_EXPIRED_PATTERN.matchEntire(text)
        val youJoined = YOU_JOINED_PARTY_PATTERN.matchEntire(text)
        val partyingWith = PARTYING_WITH_PATTERN.matchEntire(text)
        val memberJoined = MEMBER_JOINED_PATTERNS.firstNotNullOfOrNull { it.matchEntire(text) }
        val memberDisconnected = MEMBER_DISCONNECTED_PATTERN.matchEntire(text)
        val memberRejoined = MEMBER_REJOINED_PATTERN.matchEntire(text)
        val memberRemoved = MEMBER_REMOVED_PATTERNS.firstNotNullOfOrNull { it.matchEntire(text) }
        val transfer = TRANSFER_PATTERN.matchEntire(text)
        when {
            text in NOT_IN_PARTY_MESSAGES || partyEnded -> {
                clearPartyState()
                if (partyEnded) HypixelPartyApi.requestPartyInfo()
            }
            outgoingInvite != null -> {
                markOptimisticParty()
                partyText.member(outgoingInvite.groups["inviter"])?.let(roster::addPartyMember)
                partyText.member(outgoingInvite.groups["invitee"])?.let { member ->
                    if (!roster.hasMember(member.name)) {
                        val seconds = outgoingInvite.groups["seconds"]?.value?.toLongOrNull() ?: INVITE_SECONDS
                        roster.invite(
                            member,
                            System.currentTimeMillis() + seconds * MILLIS_PER_SECOND,
                        )
                    }
                }
                HypixelPartyApi.requestPartyInfo()
            }
            inviteExpired != null -> playerName(inviteExpired.groups["invitee"])
                ?.let(roster::removeInvite)
            youJoined != null -> {
                clearPartyState()
                markOptimisticParty()
                partyText.member(youJoined.groups["leader"])?.let(roster::addPartyMember)
                currentPlayerMember()?.let(roster::addPartyMember)
                HypixelPartyApi.requestPartyInfo()
            }
            partyingWith != null -> {
                markOptimisticParty()
                partyText.members(partyingWith.groups["members"]).forEach(roster::addPartyMember)
                currentPlayerMember()?.let(roster::addPartyMember)
            }
            memberJoined != null -> {
                markOptimisticParty()
                partyText.member(memberJoined.groups["member"])?.let(roster::addPartyMember)
                currentPlayerMember()?.let(roster::addPartyMember)
                HypixelPartyApi.requestPartyInfo()
            }
            memberDisconnected != null -> partyText.member(memberDisconnected.groups["member"])
                ?.let { member -> roster.setPartyMemberDisconnected(member, true) }
            memberRejoined != null -> partyText.member(memberRejoined.groups["member"])
                ?.let { member -> roster.setPartyMemberDisconnected(member, false) }
            memberRemoved != null -> {
                playerName(memberRemoved.groups["member"])?.let(roster::removePartyMember)
                HypixelPartyApi.requestPartyInfo()
            }
            transfer != null -> {
                playerName(transfer.groups["member"])?.let(roster::removePartyMember)
                partyText.member(transfer.groups["leader"])
                    ?.let { roster.addPartyMember(it, first = true) }
                HypixelPartyApi.requestPartyInfo()
            }
        }
    }

    private fun parsePartyListSection(partyText: PartyMessageText, pending: PendingPartyList): ChatMessageVisibility {
        val section = PARTY_LIST_SECTION_PATTERN.matchEntire(partyText.text) ?: return ChatMessageVisibility.SHOW
        val names = section.groups["names"] ?: return ChatMessageVisibility.HIDE
        for (match in PARTY_LIST_MEMBER_PATTERN.findAll(names.value)) {
            val name = match.groups["name"] ?: continue
            pending.members += partyText.styledMember(name.value, names.range.first + name.range.first)
        }
        return ChatMessageVisibility.HIDE
    }

    private fun markOptimisticParty() {
        lastApiInParty = true
        optimisticPartyAtMillis = System.currentTimeMillis()
    }

    private fun currentPlayerMember(): PartyDisplayMember? {
        val minecraft = Minecraft.getInstance()
        val player = minecraft.player ?: return null
        return apiPartyMember(player.uuid, player.gameProfile.name)
    }

    private fun apiPartyMember(uuid: UUID, name: String): PartyDisplayMember {
        val displayName = TabListApi.playerProfile(uuid)?.displayName
        val style = displayName?.styleAt(displayName.string.lastIndexOf(name)) ?: Style.EMPTY
        return PartyDisplayMember(name, Component.literal(name).withStyle(style), uuid = uuid)
    }

    private fun clearDisplayedParty() {
        roster.clearDisplayed()
        clearPartyTracking()
    }

    private fun clearPartyState() {
        roster.clear()
        clearPartyTracking()
    }

    private fun clearPartyTracking() {
        requestedParty = null
        pendingPartyList?.discard = true
        optimisticPartyAtMillis = 0L
    }

    internal fun currentMembers(): List<PartyDisplayMember> = roster.currentMembers()

    internal fun face(member: PartyDisplayMember): PartyDisplayFace? {
        val tabProfile = if (member.uuid != null) {
            TabListApi.playerProfile(member.uuid)
        } else {
            TabListApi.playerProfile(member.name)
        }
        if (tabProfile != null) return PartyDisplayFace(tabProfile.skin().body().texturePath(), tabProfile.showHat)
        val profile = if (member.uuid != null) {
            MinecraftProfileLookup.byId(member.uuid).getNow(null)
        } else {
            MinecraftProfileLookup.byName(member.name).getNow(null)
        } ?: return null
        val skin = MinecraftProfileLookup.skin(profile)
        return PartyDisplayFace(skin.body().texturePath(), true)
    }

    private fun reset() {
        roster.clear()
        requestedParty = null
        pendingPartyList = null
        lastApiInParty = null
        optimisticPartyAtMillis = 0L
        wasEnabled = false
    }

    private class PartyMessageText(private val component: Component, rawText: String) {
        val text = rawText.trim()
        private val textOffset = rawText.indexOf(text).coerceAtLeast(0)

        fun member(group: MatchGroup?): PartyDisplayMember? = group?.let { member(it.value, it.range.first) }

        fun members(group: MatchGroup?): List<PartyDisplayMember> {
            group ?: return emptyList()
            return PARTY_PLAYER_PATTERN.findAll(group.value).mapNotNull { match ->
                member(match.value, group.range.first + match.range.first)
            }.toList()
        }

        fun styledMember(name: String, textIndex: Int): PartyDisplayMember =
            PartyDisplayMember(name, Component.literal(name).withStyle(component.styleAt(textOffset + textIndex)))

        private fun member(value: String, rangeStart: Int): PartyDisplayMember? {
            val name = PLAYER_NAME_PATTERN.findAll(value).lastOrNull() ?: return null
            return styledMember(name.value, rangeStart + name.range.first)
        }
    }

    private data class PendingPartyList(
        val members: MutableList<PartyDisplayMember> = mutableListOf(),
        val expiresAtMillis: Long,
        var started: Boolean = false,
        var discard: Boolean = false,
    )

    private const val PARTY_PLAYER = """(?:\[[^]]+] )?[A-Za-z0-9_]{1,16}"""
    private const val PARTY_LIST_TIMEOUT_MILLIS = 3_000L
    private const val INVITE_SECONDS = 60L
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
    private val MEMBER_DISCONNECTED_PATTERN = Regex(
        """(?:The party leader, )?(?<member>$PARTY_PLAYER) has disconnected, they have \d+ minutes """ +
            """to rejoin before (?:they are removed from the party|the party is disbanded)\.""",
    )
    private val MEMBER_REJOINED_PATTERN = Regex("""(?<member>$PARTY_PLAYER) has rejoined\.""")
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
    val uuid: UUID? = null,
    val disconnected: Boolean = false,
    val leavingAtMillis: Long? = null,
) {
    val isActive: Boolean get() = !invited && leavingAtMillis == null
}

internal data class PartyDisplayFace(val texture: Identifier, val showHat: Boolean)

internal const val MEMBER_LEAVE_FADE_MILLIS = 750L

private val PLAYER_NAME_PATTERN = Regex("""[A-Za-z0-9_]{1,16}""")

private fun playerName(group: MatchGroup?): String? =
    group?.value?.let { PLAYER_NAME_PATTERN.findAll(it).lastOrNull()?.value }
