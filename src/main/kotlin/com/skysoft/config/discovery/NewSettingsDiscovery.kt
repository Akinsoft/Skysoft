package com.skysoft.config.discovery

import com.skysoft.SkysoftMod
import com.skysoft.config.SkysoftConfigFiles
import com.skysoft.config.SkysoftConfigGui
import com.skysoft.config.SkysoftMoulConfigGuis
import com.skysoft.utils.ChatDeliveryResult
import com.skysoft.utils.SkysoftChat
import com.skysoft.utils.SkysoftClientEvents
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent

object NewSettingsDiscovery {
    private var isRegistered = false
    private var runtime: NewSettingsRuntime? = null

    fun register() {
        if (isRegistered) return
        isRegistered = true
        SkysoftClientEvents.onClientStarted("New settings discovery initialization") { initialize() }
        SkysoftClientEvents.onJoin("New settings discovery announcement", ::announcePendingSettings)
    }

    fun hasPresentedSettings(): Boolean = runtime?.presentedIds()?.isNotEmpty() == true

    internal fun didOpenPresentedSettings(): Boolean {
        val optionIds = runtime?.presentedIds()?.takeIf(Set<String>::isNotEmpty) ?: return false
        return SkysoftConfigGui.didOpenNewSettings(optionIds)
    }

    private fun initialize() {
        try {
            initializeFromConfig()
        } catch (e: Exception) {
            runtime = null
            SkysoftMod.LOGGER.error("New-settings discovery is disabled for this run", e)
        }
    }

    private fun initializeFromConfig() {
        val config = SkysoftConfigGui.config()
        val configSource = NewSettingsConfigBootstrap.take()
        if (configSource is NewSettingsConfigSource.Unavailable) {
            SkysoftMod.LOGGER.warn("New-settings discovery is disabled for this run: {}", configSource.reason)
            return
        }

        val processor = SkysoftMoulConfigGuis.processConfig(config)
        val schema = NewSettingsSchema.from(processor)
        val store = NewSettingsStateStore(SkysoftConfigFiles.featureDiscovery)
        val storedState = store.load()

        val persistedSignatures = when (configSource) {
            NewSettingsConfigSource.Fresh -> emptyMap()
            is NewSettingsConfigSource.Loaded -> bootstrapKnownSignatures(schema, configSource.json)
            is NewSettingsConfigSource.Unavailable -> error("Unavailable config source passed initialization guard")
        }
        val currentState = updateNewSettingsState(schema, storedState, persistedSignatures)
        store.save(currentState)
        runtime = NewSettingsRuntime(schema, currentState, store)
    }

    private fun announcePendingSettings() {
        val activeRuntime = runtime ?: return
        val pendingIds = activeRuntime.currentIds(activeRuntime.state.pendingOptionIds)
        if (pendingIds.isEmpty()) return
        val delivery = SkysoftChat.chat(newSettingsAnnouncement())
        if (delivery != ChatDeliveryResult.DELIVERED) return

        val nextState = activeRuntime.state.copy(
            pendingOptionIds = emptyList(),
            lastPresentedOptionIds = orderedCurrentIds(activeRuntime.schema, pendingIds),
        )
        activeRuntime.state = nextState
        activeRuntime.store.save(nextState)
    }

    private data class NewSettingsRuntime(
        val schema: NewSettingsSchema,
        var state: NewSettingsDiscoveryState,
        val store: NewSettingsStateStore,
    ) {
        fun presentedIds(): Set<String> = currentIds(state.lastPresentedOptionIds)

        fun currentIds(ids: List<String>): Set<String> = ids.filterTo(linkedSetOf(), schema.byId::containsKey)
    }
}

private fun newSettingsAnnouncement(): Component =
    Component.literal("New Skysoft features are available! ").withStyle(ChatFormatting.WHITE)
        .append(
            Component.literal("[Configure them]").withStyle { style ->
                style.withColor(ChatFormatting.GREEN)
                    .withClickEvent(ClickEvent.RunCommand("/skysoft new"))
                    .withHoverEvent(
                        HoverEvent.ShowText(
                            Component.literal("Open new Skysoft settings").withStyle(ChatFormatting.GRAY),
                        ),
                    )
            },
        )
        .append(
            Component.literal(" Use /skysoft new to configure them later.").withStyle(ChatFormatting.WHITE),
        )
