package com.skysoft.features.misc

import java.util.Locale

internal class PartyDisplayRoster {
    private val pendingInvites = linkedMapOf<String, PendingInvite>()
    private val disconnectedMembers = mutableSetOf<String>()
    private var displayedMembers: List<PartyDisplayMember> = emptyList()

    val hasPendingInvites: Boolean get() = pendingInvites.isNotEmpty()
    val hasLeavingMembers: Boolean get() = displayedMembers.any { it.leavingAtMillis != null }

    fun hasMember(name: String): Boolean = displayedMembers.any { it.name.equals(name, ignoreCase = true) }

    fun invite(member: PartyDisplayMember, expiresAtMillis: Long) {
        pendingInvites[playerKey(member.name)] = PendingInvite(member.copy(invited = true), expiresAtMillis)
    }

    fun removeInvite(name: String) {
        pendingInvites.remove(playerKey(name))
    }

    fun update(now: Long) {
        pendingInvites.values.removeAll { now >= it.expiresAtMillis }
        displayedMembers = displayedMembers.filterNot { member ->
            member.leavingAtMillis?.let { startedAt -> now >= startedAt + MEMBER_LEAVE_FADE_MILLIS } == true
        }
    }

    fun addPartyMember(member: PartyDisplayMember, first: Boolean = false) {
        val key = playerKey(member.name)
        pendingInvites.remove(key)
        val index = displayedMembers.indexOfFirst { playerKey(it.name) == key }
        if (index < 0) {
            displayedMembers += member.copy(invited = false)
        } else {
            val existing = displayedMembers[index]
            displayedMembers = displayedMembers.toMutableList().also { members ->
                members[index] = member.copy(
                    invited = false,
                    uuid = member.uuid ?: existing.uuid,
                    leavingAtMillis = null,
                )
            }
        }
        if (first) displayedMembers = displayedMembers.sortedBy { playerKey(it.name) != key }
    }

    fun setPartyMemberDisconnected(member: PartyDisplayMember, disconnected: Boolean) {
        addPartyMember(member)
        val key = playerKey(member.name)
        if (disconnected) disconnectedMembers += key else disconnectedMembers -= key
    }

    fun removePartyMember(name: String) {
        val key = playerKey(name)
        disconnectedMembers -= key
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

    fun applyPartyList(members: List<PartyDisplayMember>) {
        val latestMembers = members.associateBy { playerKey(it.name) }
        val existingNames = displayedMembers.mapTo(mutableSetOf()) { playerKey(it.name) }
        val now = System.currentTimeMillis()
        displayedMembers = displayedMembers.map { existing ->
            latestMembers[playerKey(existing.name)]?.let { latest ->
                latest.copy(uuid = latest.uuid ?: existing.uuid)
            }
                ?: existing.takeIf { it.leavingAtMillis != null }
                ?: existing.copy(leavingAtMillis = now)
        } + members.filterNot { playerKey(it.name) in existingNames }
        members.firstOrNull()?.let { addPartyMember(it, first = true) }
        pendingInvites.keys.removeAll(latestMembers.keys)
    }

    fun clearDisplayed() {
        displayedMembers = emptyList()
        pendingInvites.clear()
    }

    fun clear() {
        clearDisplayed()
        disconnectedMembers.clear()
    }

    fun currentMembers(): List<PartyDisplayMember> =
        (displayedMembers + pendingInvites.values.map(PendingInvite::member)).map { member ->
            member.copy(disconnected = playerKey(member.name) in disconnectedMembers)
        }

    private data class PendingInvite(val member: PartyDisplayMember, val expiresAtMillis: Long)
}

private fun playerKey(name: String): String = name.lowercase(Locale.ROOT)
