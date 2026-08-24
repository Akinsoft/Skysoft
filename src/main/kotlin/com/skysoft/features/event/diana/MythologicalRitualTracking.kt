package com.skysoft.features.event.diana

import com.skysoft.config.DianaRareMobOption
import com.skysoft.features.loot.RareLootChatParser
import com.skysoft.features.loot.RareLootChatDrop
import com.skysoft.features.loot.RareLootDropCount
import com.skysoft.features.loot.RareLootShareReceipt

internal class MythologicalRitualLootShareWindow {
    private var lastReceiptAtMillis = 0L

    fun recordReceipt(now: Long) {
        lastReceiptAtMillis = now
    }

    fun isActive(now: Long): Boolean =
        RareLootShareReceipt.isWithinWindow(lastReceiptAtMillis, now)

    fun clear() {
        lastReceiptAtMillis = 0L
    }
}

internal object MythologicalRitualMessageTracker {
    fun track(message: String, state: MythologicalRitualTrackerState, lootShareWindow: MythologicalRitualLootShareWindow, now: Long) {
        trackNonRareLoot(message, state, lootShareWindow, now)
        val chatDrop = RareLootChatParser.parse(message.trim()) ?: return
        val lootshare = lootShareWindow.isActive(now) || DianaRareMobSharing.likelyRemoteRareLoot
        trackRareLoot(chatDrop, state, lootshare)
    }

    fun trackNonRareLoot(
        message: String,
        state: MythologicalRitualTrackerState,
        lootShareWindow: MythologicalRitualLootShareWindow,
        now: Long,
    ) {
        val clean = message.trim()
        val receiptMob = DianaLootShareReceipt.parseMob(clean)
        if (receiptMob != null) {
            lootShareWindow.recordReceipt(now)
            state.addLootShareMob(receiptMob)
        } else if (RareLootShareReceipt.isReceipt(clean)) {
            lootShareWindow.recordReceipt(now)
        }
        when {
            isBurrowMessage(clean) -> state.addItem(MythologicalRitualItemKey.TOTAL_BURROWS)
            coinPattern.matchEntire(clean) != null -> state.addCoins(clean)
            else -> state.trackNonCoinMessage(clean)
        }
    }

    fun trackRareLoot(
        drop: RareLootChatDrop,
        state: MythologicalRitualTrackerState,
        lootshare: Boolean,
    ): RareLootDropCount? {
        val trackedDrop = MythologicalRitualDropMapping.fromChatDrop(drop, lootshare) ?: return null
        state.addTrackedDrop(trackedDrop, lootshare)
        return RareLootDropCount(state.event.item(trackedDrop.itemKey))
    }

    private fun MythologicalRitualTrackerState.trackNonCoinMessage(clean: String) {
        trackShard(clean)?.let { tracked ->
            addItem(tracked.itemKey, tracked.amount)
            return
        }
        DianaDugMobParser.parse(clean)
            ?.let(DianaRareMobOption::fromLabel)
            ?.let { mob -> addMob(mob) }
    }

    private fun MythologicalRitualTrackerState.addCoins(clean: String) {
        val coins = coinPattern.matchEntire(clean)
            ?.groups
            ?.get("coins")
            ?.value
            ?.replace(",", "")
            ?.toLongOrNull()
            ?: return
        addItem(MythologicalRitualItemKey.COINS, coins)
    }

    private fun trackShard(clean: String): MythologicalRitualTrackedDrop? {
        shardPatterns.forEach { pattern ->
            val match = pattern.matchEntire(clean) ?: return@forEach
            val mob = DianaRareMobOption.fromLabel(match.groups["mob"]?.value.orEmpty()) ?: return null
            val amount = match.groups["amount"]?.value?.toIntOrNull()?.coerceAtLeast(1) ?: 1
            return shardItemKey(mob)?.let { MythologicalRitualTrackedDrop(it, amount.toLong()) }
        }
        return null
    }

    fun isBurrowMessage(message: String): Boolean =
        burrowPattern.matches(message.trim())

    private val burrowPattern = Regex("""^You .* Griffin [Bb]urrow.*$""")
    private val coinPattern = Regex("""^Wow! You dug out (?<coins>[\d,]+) coins!$""", RegexOption.IGNORE_CASE)
    private val shardPatterns = listOf(
        Regex("""^CHARM! You charmed the .+? and received (?<amount>\d+) (?<mob>.+?) Shards?!$"""),
        Regex("""^You charmed a (?<mob>.+?) and captured (?<amount>\d+) Shards from it\.$"""),
        Regex("""^You charmed a (?<mob>.+?) and captured its Shard\.$"""),
        Regex("""^You caught (?<amount>\d+)x (?<mob>.+?) Shards.*$"""),
        Regex("""^You caught a (?<mob>.+?) Shard!$"""),
    )
}

internal fun MythologicalRitualTrackerState.addMob(mob: DianaRareMobOption, amount: Long = 1L) {
    val key = mob.name
    event.addMob(key, amount)
    total.addMob(key, amount)
    session.addMob(key, amount)
    event.addMob(MythologicalRitualMobKey.TOTAL_MOBS, amount)
    total.addMob(MythologicalRitualMobKey.TOTAL_MOBS, amount)
    session.addMob(MythologicalRitualMobKey.TOTAL_MOBS, amount)
    since.incrementForAnyMob(amount)
    since.incrementForMob(mob, amount)
}

private fun MythologicalRitualTrackerState.addLootShareMob(mob: DianaRareMobOption, amount: Long = 1L) {
    val key = lootShareMobKey(mob) ?: return
    event.addMob(key, amount)
    total.addMob(key, amount)
    session.addMob(key, amount)
    since.incrementForLootShareMob(mob, amount)
}

internal fun MythologicalRitualTrackerState.addItem(itemKey: String, amount: Long = 1L) {
    event.addItem(itemKey, amount)
    total.addItem(itemKey, amount)
    session.addItem(itemKey, amount)
}

private fun MythologicalRitualTrackerState.addTrackedDrop(drop: MythologicalRitualTrackedDrop, lootshare: Boolean) {
    addItem(drop.itemKey, drop.amount)
    since.resetForDrop(drop.itemKey)
    if (!lootshare && drop.magicFindKey != null) magicFind.record(drop.magicFindKey, drop.magicFind)
}

private fun MythologicalRitualSinceData.incrementForAnyMob(amount: Long) {
    mobsSinceInq += amount
    mobsSinceKing += amount
    mobsSinceManti += amount
    mobsSinceSphinx += amount
}

private fun MythologicalRitualSinceData.incrementForMob(mob: DianaRareMobOption, amount: Long) {
    when (mob) {
        DianaRareMobOption.KING_MINOS -> {
            kingSinceWool += amount
            mobsSinceKing = 0L
        }
        DianaRareMobOption.MANTICORE -> {
            mantiSinceCore += amount
            mantiSinceStinger += amount
            mobsSinceManti = 0L
        }
        DianaRareMobOption.MINOS_INQUISITOR -> {
            inqsSinceChim += amount
            mobsSinceInq = 0L
        }
        DianaRareMobOption.SPHINX -> {
            sphinxSinceFood += amount
            mobsSinceSphinx = 0L
        }
        DianaRareMobOption.MINOS_CHAMPION -> champsSinceRelic += amount
        DianaRareMobOption.MINOTAUR -> minotaursSinceStick += amount
        else -> Unit
    }
}

private fun MythologicalRitualSinceData.incrementForLootShareMob(mob: DianaRareMobOption, amount: Long) {
    when (mob) {
        DianaRareMobOption.MINOS_INQUISITOR -> inqsSinceLsChim += amount
        DianaRareMobOption.KING_MINOS -> kingSinceLsWool += amount
        DianaRareMobOption.MANTICORE -> {
            mantiSinceLsCore += amount
            mantiSinceLsStinger += amount
        }
        DianaRareMobOption.SPHINX -> sphinxSinceLsFood += amount
        else -> Unit
    }
}

private fun MythologicalRitualSinceData.resetForDrop(itemKey: String) {
    when (itemKey) {
        MythologicalRitualItemKey.CHIMERA -> inqsSinceChim = 0L
        MythologicalRitualItemKey.CHIMERA_LS -> inqsSinceLsChim = 0L
        MythologicalRitualItemKey.DAEDALUS_STICK -> minotaursSinceStick = 0L
        MythologicalRitualItemKey.MINOS_RELIC -> champsSinceRelic = 0L
        MythologicalRitualItemKey.MANTI_CORE -> mantiSinceCore = 0L
        MythologicalRitualItemKey.MANTI_CORE_LS -> mantiSinceLsCore = 0L
        MythologicalRitualItemKey.FATEFUL_STINGER -> mantiSinceStinger = 0L
        MythologicalRitualItemKey.FATEFUL_STINGER_LS -> mantiSinceLsStinger = 0L
        MythologicalRitualItemKey.SHIMMERING_WOOL -> kingSinceWool = 0L
        MythologicalRitualItemKey.SHIMMERING_WOOL_LS -> kingSinceLsWool = 0L
        MythologicalRitualItemKey.BRAIN_FOOD -> sphinxSinceFood = 0L
        MythologicalRitualItemKey.BRAIN_FOOD_LS -> sphinxSinceLsFood = 0L
    }
}

private fun lootShareMobKey(mob: DianaRareMobOption): String? =
    when (mob) {
        DianaRareMobOption.MINOS_INQUISITOR -> MythologicalRitualMobKey.MINOS_INQUISITOR_LS
        DianaRareMobOption.KING_MINOS -> MythologicalRitualMobKey.KING_MINOS_LS
        DianaRareMobOption.MANTICORE -> MythologicalRitualMobKey.MANTICORE_LS
        DianaRareMobOption.SPHINX -> MythologicalRitualMobKey.SPHINX_LS
        else -> null
    }

private fun shardItemKey(mob: DianaRareMobOption): String? =
    when (mob) {
        DianaRareMobOption.KING_MINOS -> MythologicalRitualItemKey.KING_MINOS_SHARD
        DianaRareMobOption.SPHINX -> MythologicalRitualItemKey.SPHINX_SHARD
        DianaRareMobOption.MINOTAUR -> MythologicalRitualItemKey.MINOTAUR_SHARD
        DianaRareMobOption.CRETAN_BULL -> MythologicalRitualItemKey.CRETAN_BULL_SHARD
        DianaRareMobOption.HARPY -> MythologicalRitualItemKey.HARPY_SHARD
        else -> null
    }
