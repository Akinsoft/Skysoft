package com.skysoft.features.loot

import com.skysoft.config.RareLootShareChannel
import com.skysoft.config.SkysoftConfigGui
import com.skysoft.data.hypixel.HypixelLocationState
import com.skysoft.data.hypixel.HypixelPartyApi
import com.skysoft.data.skyblock.SkyBlockDataRepository
import com.skysoft.features.pets.PetRepository
import com.skysoft.utils.SkysoftClientEvents
import com.skysoft.utils.chat.ChatEvents
import com.skysoft.utils.chat.ChatMessage
import com.skysoft.utils.chat.ChatMessageVisibility
import com.skysoft.utils.chat.ChatMessageType
import com.skysoft.utils.chat.SkysoftPartyShare

internal object RareLootChatFeatures {
    private val miscConfig get() = SkysoftConfigGui.config().misc
    private val sharingConfig get() = miscConfig.rareLootSharing
    private val sharingThreshold = RareLootThresholdReader("rare loot value")
    private val isSharingEnabled: Boolean
        get() = sharingConfig.enabled && sharingConfig.settings.channels.get().isNotEmpty()
    private var lastLootShareAtMillis = 0L

    fun register() {
        HypixelPartyApi.registerConsumer("Rare Loot Sharing") { isSharingEnabled }
        SkyBlockDataRepository.Demand.register("Rare Loot Features") { miscConfig.isAnyRareLootFeatureEnabled() }
        PetRepository.registerConsumer("Rare Loot Features") { miscConfig.isAnyRareLootFeatureEnabled() }
        SkysoftClientEvents.onDisconnect("Rare Loot Features disconnect reset", ::clear)
        ChatEvents.onVisibleGameMessageModify(
            "Rare Loot party glyph rendering",
            isActive = { isSharingEnabled },
            modifier = RareLootPartyGlyphRenderer::render,
        )
        ChatEvents.onVisibleMessage(
            "Rare Loot chat",
            isActive = {
                miscConfig.isAnyRareLootFeatureEnabled() || RareLootContextRegistry.hasActiveContributors()
            },
        ) { message ->
            onMessage(message)
            ChatMessageVisibility.SHOW
        }
    }

    private fun onMessage(message: ChatMessage) {
        if (!HypixelLocationState.inSkyBlock) return
        val sharingEnabled = isSharingEnabled
        val cleanText = message.cleanText
        val now = System.currentTimeMillis()
        if (sharingEnabled && message.isSystemLike && RareLootShareReceipt.isReceipt(cleanText)) {
            lastLootShareAtMillis = now
        }
        if (!shouldConsiderRareLootMessage(message.type, cleanText)) return

        val chatDrop = RareLootChatParser.parse(cleanText) ?: return
        val lootshare = isLootShareDrop(now)
        val dropCount = RareLootContextRegistry.recordDrop(chatDrop, lootshare, now)
        val titlesEnabled = miscConfig.rareDropTitles.enabled
        if (!sharingEnabled && !titlesEnabled) return

        val drop = chatDrop.toRareLootDrop()
        val value = RareLootValueResolver.resolve(drop)
        if (titlesEnabled) RareDropTitles.show(drop, value)
        if (sharingEnabled) shareIfEligible(drop, value, lootshare, dropCount)
    }

    private fun shareIfEligible(
        drop: RareLootDrop,
        value: RareLootValue?,
        lootshare: Boolean,
        dropCount: RareLootDropCount?,
    ) {
        val threshold = sharingThreshold.read(sharingConfig.settings.rareLootValue) ?: return
        if (!RareLootEligibility.shouldShare(threshold, value)) return
        val message = RareLootPartyMessageFormatter.format(drop, value, lootshare, dropCount = dropCount)
        sharingConfig.settings.channels.get().forEach { channel ->
            when (channel) {
                RareLootShareChannel.PARTY -> SkysoftPartyShare.sendParty(message)
                RareLootShareChannel.GUILD -> SkysoftPartyShare.sendGuild(message)
            }
        }
    }

    private fun clear() {
        lastLootShareAtMillis = 0L
        sharingThreshold.clear()
        RareDropTitles.clear()
    }

    private fun isLootShareDrop(now: Long): Boolean =
        RareLootContextRegistry.hasLootShareEvidence(now) ||
            RareLootShareReceipt.isWithinWindow(lastLootShareAtMillis, now)
}

internal fun shouldConsiderRareLootMessage(
    messageType: ChatMessageType,
    cleanText: String,
): Boolean =
    (messageType == ChatMessageType.SYSTEM || messageType == ChatMessageType.UNKNOWN) &&
        !cleanText.startsWith(PARTY_PREFIX)

private const val PARTY_PREFIX = "Party >"
