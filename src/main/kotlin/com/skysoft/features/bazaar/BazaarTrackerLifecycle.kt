package com.skysoft.features.bazaar

import com.skysoft.data.skyblock.BazaarOrderType
import com.skysoft.config.BazaarTrackerSound
import com.skysoft.config.SkysoftConfigGui
import com.skysoft.data.ProfileStorageApi
import com.skysoft.data.ProfileStorageView
import com.skysoft.data.hypixel.HypixelLocationState
import com.skysoft.data.hypixel.SkyBlockProfileApi
import com.skysoft.data.skyblock.SkyBlockDataRepository
import com.skysoft.data.skyblock.SkyBlockOpenInventoryApi
import com.skysoft.data.skyblock.SkyBlockOpenInventorySnapshot
import com.skysoft.features.inventory.InventoryOverlayInput
import com.skysoft.gui.GuiOverlay
import com.skysoft.gui.GuiOverlayContextType
import com.skysoft.gui.GuiOverlayLayer
import com.skysoft.gui.GuiOverlayRegistry
import com.skysoft.gui.HudEditorElement
import com.skysoft.utils.SkysoftClientEvents
import com.skysoft.utils.chat.ChatEvents
import com.skysoft.utils.chat.ChatMessageVisibility
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.sounds.SoundEvents

internal fun registerBazaarTracker() {
    ProfileStorageApi.registerConsumer("Bazaar Tracker") { config.enabled }
    SkyBlockDataRepository.Demand.register("Bazaar Tracker") { config.enabled }
    SkyBlockOpenInventoryApi.onChange(
        "Bazaar Tracker inventory",
        isActive = { config.enabled },
        listener = { snapshot -> openInventorySnapshot = snapshot },
    )
    registerChatListeners()
    registerMouseClickCapture()
    SkysoftClientEvents.onEndTick(
        "Bazaar Tracker tick",
        isActive = { config.enabled || wasBazaarTrackerEnabled },
    ) {
        wasBazaarTrackerEnabled = config.enabled
        onClientTick()
    }
    SkysoftClientEvents.onDisconnect("Bazaar Tracker disconnect reset", ::resetBazaarTrackerRuntime)
    SkyBlockProfileApi.onProfileChange("Bazaar Tracker profile reset", { config.enabled }) { resetBazaarTrackerRuntime() }
    GuiOverlayRegistry.registerHud(
        GuiOverlay(
            id = "bazaar_tracker",
            layer = GuiOverlayLayer.BELOW_SCREEN,
            contexts = GuiOverlayContextType.entries.toSet(),
            screenForegroundContexts = GuiOverlayContextType.INVENTORIES,
            render = { context, _ -> renderHud(context) },
        ),
        object : HudEditorElement {
            override val id: String = "bazaar_tracker"
            override val label: String = "Bazaar Tracker"
            override val position get() = config.position
            override val hasEditorBackground: Boolean get() = !config.details.showBackground
            override fun width(): Int = buildRenderable(false).width
            override fun height(): Int = buildRenderable(false).height
            override fun isVisible(): Boolean = config.enabled
            override fun renderEditor(context: GuiGraphicsExtractor) =
                buildRenderable(false).render(context)
            override fun openConfig() = SkysoftConfigGui.open("Bazaar Tracker")
        },
    )
}

internal fun resetBazaarTrackerDisplayedProfit() {
    if (BazaarDisplayState.mode == TrackerDisplayMode.SESSION) {
        BazaarSessionState.reset()
    } else {
        ProfileStorageApi.updateProfile { it.bazaarTracker.totalKnownProfit = 0.0 }
    }
}

internal fun resetBazaarTrackerRuntime() {
    BazaarTrackingState.reset()
    BazaarDisplayState.clearInteraction()
}

private fun registerChatListeners() {
    ChatEvents.onVisibleMessage("Bazaar Tracker chat", { config.enabled }) { message ->
        handleChat(message.plainText)
        ChatMessageVisibility.SHOW
    }
}

private fun registerMouseClickCapture() {
    InventoryOverlayInput.registerClickObserver("Bazaar Tracker mouse click", { config.enabled }) { screen, click ->
        recordClickedOrder(screen, click)
    }
}

private var openInventorySnapshot: SkyBlockOpenInventorySnapshot? = null
private var wasBazaarTrackerEnabled = false

internal fun onClientTick() {
    if (!HypixelLocationState.inSkyBlock || !config.enabled) {
        resetBazaarTrackerRuntime()
        return
    }
    checkStatusAlerts()
    tickBazaarFillEstimator()
    val snapshot = openInventorySnapshot ?: run {
        BazaarTrackingState.resetOrderScan()
        return
    }
    when {
        snapshot.title == "Confirm Buy Order" -> readConfirmInventory(snapshot, BazaarOrderType.BUY)
        snapshot.title == "Confirm Sell Offer" -> readConfirmInventory(snapshot, BazaarOrderType.SELL)
        snapshot.title.contains("Bazaar Orders") -> readOrdersInventory(snapshot)
        snapshot.title == "Order options" -> readOrderOptionsInventory(snapshot)
        else -> BazaarTrackingState.resetOrderScan()
    }
}

private fun checkStatusAlerts() {
    if (BazaarTrackingState.statusAlertTick++ % STATUS_ALERT_INTERVAL_TICKS != 0) return
    val activeIds = storage.activeOrders.mapTo(mutableSetOf()) { it.id }
    BazaarTrackingState.lastAlertStatuses.keys.retainAll(activeIds)
    BazaarTrackingState.lastOutbidAlertMillis.keys.retainAll(activeIds)
    BazaarTrackingState.marketProofMillis.keys.retainAll(activeIds)

    val now = System.currentTimeMillis()
    for (order in storage.activeOrders) {
        val status = statusFor(order)
        val previous = BazaarTrackingState.lastAlertStatuses.put(order.id, status) ?: continue
        if (!status.isWarning || previous.isWarning) continue
        val lastAlert = BazaarTrackingState.lastOutbidAlertMillis[order.id] ?: 0L
        if (now - lastAlert < OUTBID_SOUND_COOLDOWN_MILLIS) continue
        BazaarTrackingState.lastOutbidAlertMillis[order.id] = now
        playAlertSound(BazaarTrackerSound.OUTBID_UNDERCUT)
    }
}

internal fun initializeOrderAlertState(order: ProfileStorageView.BazaarOrderData) {
    BazaarTrackingState.lastAlertStatuses[order.id] = statusFor(order)
}

internal fun playProgressAlert(order: ProfileStorageView.BazaarOrderData, previousFilledAmount: Long) {
    if (order.filledAmount <= previousFilledAmount) return
    if (order.amountOrdered > 0 && order.filledAmount >= order.maximumAmount()) {
        playAlertSound(BazaarTrackerSound.FILLED)
    } else if (order.filledAmount > order.claimedAmount) {
        playAlertSound(BazaarTrackerSound.PARTIAL)
    }
    BazaarTrackingState.lastAlertStatuses[order.id] = statusFor(order)
}

internal fun showEstimatedFillProgress(
    order: ProfileStorageView.BazaarOrderData,
    previousFilledAmount: Long,
    filledAmount: Long,
) {
    if (filledAmount <= previousFilledAmount) return
    markFillHighlight(order, filledAmount)
    if (isPartialFill(order, filledAmount)) {
        playAlertSound(BazaarTrackerSound.PARTIAL)
    }
    BazaarTrackingState.lastAlertStatuses[order.id] = statusFor(order)
}

private fun playAlertSound(sound: BazaarTrackerSound) {
    if (sound !in config.settings.sounds.get()) return
    val minecraft = Minecraft.getInstance()
    val instance = when (sound) {
        BazaarTrackerSound.FILLED ->
            SimpleSoundInstance.forUI(
                SoundEvents.NOTE_BLOCK_PLING.value(),
                FILLED_SOUND_VOLUME,
                FILLED_SOUND_PITCH,
            )
        BazaarTrackerSound.PARTIAL ->
            SimpleSoundInstance.forUI(
                SoundEvents.EXPERIENCE_ORB_PICKUP,
                PARTIAL_SOUND_VOLUME,
                PARTIAL_SOUND_PITCH,
            )
        BazaarTrackerSound.OUTBID_UNDERCUT ->
            SimpleSoundInstance.forUI(
                SoundEvents.NOTE_BLOCK_BASS.value(),
                OUTBID_SOUND_VOLUME,
                OUTBID_SOUND_PITCH,
            )
    }
    minecraft.soundManager.play(instance)
}

