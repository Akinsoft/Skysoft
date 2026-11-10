package com.skysoft.features.inventory.sacks

import com.skysoft.config.SkysoftConfigGui
import com.skysoft.data.ProfileStorageApi
import com.skysoft.data.hypixel.HypixelLocationState
import com.skysoft.data.skyblock.SkyBlockDataRepository
import com.skysoft.data.skyblock.SkyBlockItemNames
import com.skysoft.data.skyblock.SkyBlockSackChangeBatch
import com.skysoft.data.skyblock.SkyBlockSackChanges
import com.skysoft.gui.tracker.TrackedItemManagerPanel
import com.skysoft.gui.tracker.TrackedItemSelectionAction
import com.skysoft.gui.GuiOverlay
import com.skysoft.gui.GuiOverlayContextType
import com.skysoft.gui.GuiOverlayLayer
import com.skysoft.gui.GuiOverlayRegistry
import com.skysoft.gui.OverlayControlArea
import com.skysoft.gui.RenderableHudEditorElement
import com.skysoft.gui.TopLeftHudEditorElement
import com.skysoft.utils.MinecraftClient
import com.skysoft.utils.SkysoftClientEvents
import com.skysoft.utils.animation.TimedHighlightTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen

object SackHud {
    fun register() {
        ProfileStorageApi.registerConsumer("Sacks Tracker") { sackHudConfig.enabled }
        SkyBlockDataRepository.Demand.register("Sacks Tracker") { sackHudConfig.enabled }
        SkyBlockSackChanges.onChange(
            "Sacks Tracker changes",
            isActive = { sackHudConfig.enabled && sackHudConfig.details.highlightChanges },
            listener = ::highlightSackChanges,
        )
        SkysoftClientEvents.onDisconnect("Sacks Tracker reset") {
            sackHudScrollOffset = 0
            sackHudChangeHighlights.clear()
            sackHudItemPanel.clear()
            clearSackHudInteraction()
        }
        registerSackHudInput()
        GuiOverlayRegistry.registerHud(
            GuiOverlay(
                id = "sack_hud",
                layer = GuiOverlayLayer.BELOW_SCREEN,
                contexts = GuiOverlayContextType.entries.toSet(),
                screenForegroundContexts = GuiOverlayContextType.INVENTORIES,
                render = { context, _ -> renderSackHud(context) },
            ),
            object : TopLeftHudEditorElement, RenderableHudEditorElement {
                override val id: String = "sack_hud"
                override val label: String = "Sacks Tracker"
                override val position get() = sackHudConfig.position
                override val hasEditorBackground: Boolean get() = !sackHudConfig.details.showBackground
                override fun hudRenderable() = buildSackHudRenderable(inventoryOpen = false)
                override fun isVisible(): Boolean = isSackHudVisible()
                override fun openConfig() = SkysoftConfigGui.open("Sacks Tracker")
            },
        )
    }
}

internal val sackHudConfig get() = SkysoftConfigGui.config().storageFeatures.sackHud
internal val sackHudItemPanel = TrackedItemManagerPanel(
    overviewTitle = "Manage Tracked Items",
    addTitle = "Add Item",
    removeTitle = "Remove Item",
    removeInstruction = "Click an item to remove",
    isSelectable = { itemId -> itemId !in sackHudConfig.trackedItems },
    selectItem = ::addSackHudTrackedItem,
)
internal var sackHudScrollOffset = 0
internal var sackHudHoveredControl: OverlayControlArea<SackHudControl>? = null
internal var sackHudHovered = false
internal val sackHudChangeHighlights = TimedHighlightTracker<String>()

private fun highlightSackChanges(batch: SkyBlockSackChangeBatch) {
    val tracked = sackHudConfig.trackedItems.toSet()
    if (tracked.isEmpty()) return
    batch.changes.forEach { change ->
        val itemId = ProfileStorageApi.storage.sackContents.entries
            .singleOrNull { (_, data) -> data.displayName == change.displayName }
            ?.key
            ?: SkyBlockItemNames.itemId(change.displayName)
            ?: return@forEach
        if (itemId in tracked) {
            sackHudChangeHighlights.highlight(itemId)
        }
    }
}

internal fun isSackHudVisible(): Boolean {
    if (!sackHudConfig.enabled || !HypixelLocationState.inSkyBlock) return false
    val minecraft = Minecraft.getInstance()
    if (MinecraftClient.isGuiHidden(minecraft)) return false
    val inInventory = MinecraftClient.screen(minecraft) is AbstractContainerScreen<*>
    if (sackHudConfig.settings.hideWhenEmpty && sackHudConfig.trackedItems.isEmpty() && !inInventory) {
        return false
    }
    if (sackHudConfig.settings.isOnlyInMenus && !inInventory) {
        return false
    }
    return true
}

internal fun clearSackHudInteraction() {
    sackHudHoveredControl = null
    sackHudHovered = false
}

internal fun sackHudMaximumDisplayedItems(): Int =
    sackHudConfig.settings.maximumItems.coerceIn(1, SACK_HUD_MAXIMUM_DISPLAY_ITEMS)

internal fun sackHudMaximumScrollOffset(itemCount: Int): Int = (itemCount - sackHudMaximumDisplayedItems()).coerceAtLeast(0)

internal sealed interface SackHudControl {
    data object More : SackHudControl
    data object AddItems : SackHudControl
    data object RemoveItems : SackHudControl
    data class ItemSelection(val action: TrackedItemSelectionAction) : SackHudControl
    data class Item(val itemId: String) : SackHudControl
}

private const val SACK_HUD_MAXIMUM_DISPLAY_ITEMS = 20
