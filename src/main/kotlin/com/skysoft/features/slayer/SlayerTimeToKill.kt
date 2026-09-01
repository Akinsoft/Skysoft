package com.skysoft.features.slayer

import com.skysoft.config.SkysoftConfigGui
import com.skysoft.data.ProfileStorage
import com.skysoft.data.ProfileStorageApi
import com.skysoft.data.hypixel.HypixelPartyApi
import com.skysoft.data.hypixel.SkyBlockProfileApi
import com.skysoft.data.skyblock.SkyBlockPlayerDeathParser
import com.skysoft.data.skyblock.SkyBlockSlayerType
import com.skysoft.data.skyblock.SlayerQuestSnapshot
import com.skysoft.data.skyblock.SlayerQuestState
import com.skysoft.features.profit.ProfitTrackingPeriod
import com.skysoft.utils.SkysoftChat
import com.skysoft.utils.SkysoftClientEvents
import com.skysoft.utils.chat.ChatEvents
import com.skysoft.utils.chat.ChatMessageVisibility
import com.skysoft.utils.chat.SkysoftPartyShare
import java.time.LocalDate
import java.util.Locale
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component

object SlayerTimeToKill {
    private val config get() = SkysoftConfigGui.config().slayer.timeToKill
    private val sessionStats = mutableMapOf<SkyBlockSlayerType, MutableMap<Int, ProfileStorage.SlayerKillTimeStats>>()
    private var questStartedAtNanos: Long? = null
    private var activeBoss: ActiveSlayerBoss? = null

    fun register() {
        ProfileStorageApi.registerConsumer("Slayer Time to Kill") { config.enabled }
        HypixelPartyApi.registerConsumer("Slayer Personal Best Sharing") {
            config.enabled && config.settings.sharePersonalBests
        }
        SlayerQuestState.onBossSpawn("Slayer Time to Kill boss spawn", ::onBossSpawn)
        SlayerQuestState.onQuestStarted("Slayer Time to Kill quest start", ::onQuestStarted)
        SlayerQuestState.onQuestComplete("Slayer Time to Kill quest completion", ::onQuestComplete)
        ChatEvents.onVisibleMessage("Slayer Time to Kill player death", { activeBoss != null }) { message ->
            if (message.isSystemLike && SkyBlockPlayerDeathParser.isLocalDeath(message.cleanText)) activeBoss = null
            ChatMessageVisibility.SHOW
        }
        SkysoftClientEvents.onDisconnect("Slayer Time to Kill disconnect reset", ::clearTransientState)
        SkyBlockProfileApi.onProfileChange("Slayer Time to Kill profile reset", { true }) { clearTransientState() }
    }

    internal fun displayStats(
        slayerType: SkyBlockSlayerType,
        period: ProfitTrackingPeriod,
    ): SlayerKillTimeDisplay? {
        if (!config.enabled) return null
        val data = ProfileStorageApi.storage.slayerTimeToKill
        val tier = SlayerQuestState.tier.takeIf { SlayerQuestState.slayerType == slayerType }
            ?: data.lastTiers[slayerType]
        val periodStats = tier?.let {
            when (period) {
                ProfitTrackingPeriod.SESSION -> sessionStats
                ProfitTrackingPeriod.TODAY -> todayStats(data)
                ProfitTrackingPeriod.MAYOR -> error("Slayer trackers do not support the Mayor period")
                ProfitTrackingPeriod.TOTAL -> data.totals
            }[slayerType]?.get(it)
        }
        val totalStats = tier?.let { data.totals[slayerType]?.get(it) }
        return SlayerKillTimeDisplay(periodStats?.averageMillis, totalStats?.bestMillis?.takeIf { it > 0L })
    }

    internal fun reset(slayerType: SkyBlockSlayerType, period: ProfitTrackingPeriod) {
        when (period) {
            ProfitTrackingPeriod.SESSION -> sessionStats.remove(slayerType)
            ProfitTrackingPeriod.TODAY -> todayStats(ProfileStorageApi.storage.slayerTimeToKill).remove(slayerType)
            ProfitTrackingPeriod.MAYOR -> error("Slayer trackers do not support the Mayor period")
            ProfitTrackingPeriod.TOTAL -> ProfileStorageApi.storage.slayerTimeToKill.totals.remove(slayerType)
        }
        if (period != ProfitTrackingPeriod.SESSION) ProfileStorageApi.markDirty()
    }

    private fun onQuestStarted() {
        activeBoss = null
        questStartedAtNanos = System.nanoTime()
    }

    private fun onBossSpawn(quest: SlayerQuestSnapshot) {
        if (!config.enabled) return
        activeBoss = ActiveSlayerBoss(
            slayerType = quest.slayerType ?: return,
            tier = quest.tier ?: return,
            name = quest.bossName ?: return,
            spawnedAtNanos = System.nanoTime(),
            questStartedAtNanos = questStartedAtNanos,
        )
    }

    private fun onQuestComplete(quest: SlayerQuestSnapshot) {
        questStartedAtNanos = null
        val boss = activeBoss.also { activeBoss = null } ?: return
        if (!config.enabled || boss.slayerType != quest.slayerType || boss.tier != quest.tier) return
        val completedAtNanos = System.nanoTime()
        val durationMillis = (completedAtNanos - boss.spawnedAtNanos) / NANOS_PER_MILLISECOND
        if (durationMillis <= 0L) return
        val spawnDurationMillis = boss.questStartedAtNanos
            ?.let { (boss.spawnedAtNanos - it) / NANOS_PER_MILLISECOND }
            ?.takeIf { it > 0L }
        val totalDurationMillis = boss.questStartedAtNanos
            ?.let { (completedAtNanos - it) / NANOS_PER_MILLISECOND }
            ?.takeIf { it > 0L }
        val previousBestMillis = record(boss.slayerType, boss.tier, durationMillis)
        val isPersonalBest = previousBestMillis == 0L || durationMillis < previousBestMillis
        SkysoftChat.chat(
            slayerKillMessage(
                boss.name,
                durationMillis,
                isPersonalBest,
                spawnDurationMillis.takeIf { config.settings.showSpawnTime },
                totalDurationMillis.takeIf { config.settings.showTotalTime },
            ),
        )
        if (isPersonalBest && config.settings.sharePersonalBests) {
            SkysoftPartyShare.sendParty(slayerKillPartyMessage(boss.name, durationMillis))
        }
    }

    private fun record(slayerType: SkyBlockSlayerType, tier: Int, durationMillis: Long): Long {
        val data = ProfileStorageApi.storage.slayerTimeToKill
        val previousBestMillis = data.totals.stats(slayerType, tier).record(durationMillis)
        sessionStats.stats(slayerType, tier).record(durationMillis)
        todayStats(data).stats(slayerType, tier).record(durationMillis)
        data.lastTiers[slayerType] = tier
        ProfileStorageApi.markDirty()
        return previousBestMillis
    }

    private fun todayStats(
        data: ProfileStorage.SlayerTimeToKillData,
    ): MutableMap<SkyBlockSlayerType, MutableMap<Int, ProfileStorage.SlayerKillTimeStats>> {
        val today = LocalDate.now().toEpochDay()
        if (data.todayEpochDay != today) {
            data.todayEpochDay = today
            data.today.clear()
            ProfileStorageApi.markDirty()
        }
        return data.today
    }

    private fun clearTransientState() {
        questStartedAtNanos = null
        activeBoss = null
    }
}

internal data class SlayerKillTimeDisplay(
    val averageMillis: Double?,
    val personalBestMillis: Long?,
)

private data class ActiveSlayerBoss(
    val slayerType: SkyBlockSlayerType,
    val tier: Int,
    val name: String,
    val spawnedAtNanos: Long,
    val questStartedAtNanos: Long?,
)

private fun MutableMap<SkyBlockSlayerType, MutableMap<Int, ProfileStorage.SlayerKillTimeStats>>.stats(
    slayerType: SkyBlockSlayerType,
    tier: Int,
): ProfileStorage.SlayerKillTimeStats =
    getOrPut(slayerType, ::mutableMapOf).getOrPut(tier) { ProfileStorage.SlayerKillTimeStats() }

internal fun formatSlayerKillTime(milliseconds: Number): String =
    String.format(Locale.ROOT, "%.2fs", milliseconds.toDouble() / MILLIS_PER_SECOND)

internal fun formatSlayerKillTimeForHud(milliseconds: Number?): String =
    milliseconds?.let { "§b${formatSlayerKillTime(it)}" } ?: "§8--"

internal fun slayerKillPartyMessage(bossName: String, durationMillis: Long): String =
    "$bossName defeated in ${formatSlayerKillTime(durationMillis)}! NEW PERSONAL BEST!"

internal fun slayerKillMessage(
    bossName: String,
    durationMillis: Long,
    isPersonalBest: Boolean,
    spawnDurationMillis: Long? = null,
    totalDurationMillis: Long? = null,
): Component = Component.empty()
    .append(Component.literal(bossName).withStyle(ChatFormatting.RED))
    .append(Component.literal(" defeated in "))
    .append(Component.literal(formatSlayerKillTime(durationMillis)).withColor(SkysoftChat.BRAND_BLUE))
    .append(Component.literal("!"))
    .apply {
        if (isPersonalBest) {
            append(Component.literal(" NEW PERSONAL BEST!").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD))
        }
        listOfNotNull(
            spawnDurationMillis?.let { "Spawn" to it },
            totalDurationMillis?.let { "Total" to it },
        ).forEachIndexed { index, (label, milliseconds) ->
            append(Component.literal(if (index == 0) " $label: " else ", $label: "))
            append(Component.literal(formatSlayerKillTime(milliseconds)).withColor(SkysoftChat.BRAND_BLUE))
        }
    }

private const val NANOS_PER_MILLISECOND = 1_000_000L
private const val MILLIS_PER_SECOND = 1_000.0
