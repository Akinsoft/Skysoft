package com.skysoft.features.bazaar

import com.skysoft.data.skyblock.BazaarOrderType
import com.skysoft.config.SkysoftConfigGui
import com.skysoft.data.ProfileStorageApi
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
            override fun width(): Int = currentEditorRenderable()?.width ?: 0
            override fun height(): Int = currentEditorRenderable()?.height ?: 0
            override fun isVisible(): Boolean = isBazaarTrackerVisible(Minecraft.getInstance())
            override fun renderEditor(context: GuiGraphicsExtractor) {
                currentEditorRenderable()?.render(context)
            }
            override fun openConfig() = SkysoftConfigGui.open("Bazaar Tracker")

            private fun currentEditorRenderable(): BazaarTrackerRenderable? =
                if (isVisible()) buildRenderable(false) else null
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
    BazaarTrackerAlerts.tick()
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
