package com.skysoft.features.inventory.sacks

import com.skysoft.config.SkysoftConfigGui
import com.skysoft.data.ProfileStorageApi
import com.skysoft.data.hypixel.HypixelLocationState
import com.skysoft.data.skyblock.SkyBlockDataRepository
import com.skysoft.data.skyblock.SkyBlockItemNames
import com.skysoft.data.skyblock.SkyBlockSackChangeBatch
import com.skysoft.data.skyblock.SkyBlockSackChanges
import com.skysoft.features.inventory.TrackedItemSelectionAction
import com.skysoft.gui.GuiOverlay
import com.skysoft.gui.GuiOverlayContextType
import com.skysoft.gui.GuiOverlayLayer
import com.skysoft.gui.GuiOverlayRegistry
import com.skysoft.gui.HudEditorElement
import com.skysoft.gui.HudEditorRegistry
import com.skysoft.gui.OverlayControlArea
import com.skysoft.utils.MinecraftClient
import com.skysoft.utils.SkysoftClientEvents
import com.skysoft.utils.animation.TimedHighlightTracker
import com.skysoft.utils.input.InputHandlingResult
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen

object SackHud {
    fun register() = registerSackHud()
}

internal val sackHudConfig get() = SkysoftConfigGui.config().inventory.sackHud
internal val sackHudItemPanel = SackHudItemPanel()
internal var sackHudScrollOffset = 0
internal var sackHudHoveredControl: OverlayControlArea<SackHudControl>? = null
internal var sackHudHovered = false
internal val sackHudChangeHighlights = TimedHighlightTracker<String>()

private fun registerSackHud() {
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
    GuiOverlayRegistry.register(
        GuiOverlay(
            id = "sack_hud",
            layer = GuiOverlayLayer.BELOW_SCREEN,
            contexts = GuiOverlayContextType.entries.toSet(),
            screenForegroundContexts = GuiOverlayContextType.INVENTORIES,
            render = { context, _ -> renderSackHud(context) },
        ),
    )
    HudEditorRegistry.register(object : HudEditorElement {
        override val id: String = "sack_hud"
        override val label: String = "Sacks Tracker"
        override val position get() = sackHudConfig.position
        override val hasEditorBackground: Boolean get() = !sackHudConfig.details.showBackground
        override fun width(): Int = buildSackHudRenderable(inventoryOpen = false).width
        override fun height(): Int = buildSackHudRenderable(inventoryOpen = false).height
        override fun isVisible(): Boolean = isSackHudVisible()
        override fun absoluteX(width: Int): Int = position.getAbsX0AllowingOverflow(0)
        override fun absoluteY(height: Int): Int = position.getAbsY0AllowingOverflow(0)
        override fun renderEditor(context: GuiGraphicsExtractor) =
            buildSackHudRenderable(inventoryOpen = false).render(context)
        override fun applyEditorDrag(deltaX: Int, deltaY: Int): InputHandlingResult {
            val targetX = position.getAbsX0AllowingOverflow(0) + deltaX
            val targetY = position.getAbsY0AllowingOverflow(0) + deltaY
            position.moveToAbsoluteAllowingOverflow(targetX, targetY, 0, 0)
            return InputHandlingResult.CONSUMED
        }
        override fun applyEditorScroll(scrollY: Double): InputHandlingResult {
            position.scale += if (scrollY > 0.0) SACK_HUD_EDITOR_SCALE_STEP else -SACK_HUD_EDITOR_SCALE_STEP
            return InputHandlingResult.CONSUMED
        }
        override fun openConfig() = SkysoftConfigGui.open("Sacks Tracker")
    })
}

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

internal fun sackHudMaximumScrollOffset(itemCount: Int): Int =
    (itemCount - sackHudConfig.settings.maximumItems.coerceIn(1, SACK_HUD_MAXIMUM_DISPLAY_ITEMS))
        .coerceAtLeast(0)

internal sealed interface SackHudControl {
    data object More : SackHudControl
    data object AddItems : SackHudControl
    data object RemoveItems : SackHudControl
    data class ItemSelection(val action: TrackedItemSelectionAction) : SackHudControl
    data class Item(val itemId: String) : SackHudControl
}

internal const val SACK_HUD_MAXIMUM_DISPLAY_ITEMS = 20
private const val SACK_HUD_EDITOR_SCALE_STEP = 0.1f
