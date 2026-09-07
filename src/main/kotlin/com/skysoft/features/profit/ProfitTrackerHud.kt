package com.skysoft.features.profit

import com.skysoft.config.SkysoftConfigGui
import com.skysoft.data.hypixel.HypixelLocationState
import com.skysoft.features.inventory.InventoryOverlayInput
import com.skysoft.gui.GuiOverlay
import com.skysoft.gui.GuiOverlayContextType
import com.skysoft.gui.GuiOverlayLayer
import com.skysoft.gui.GuiOverlayRegistry
import com.skysoft.gui.HudEditorElement
import com.skysoft.gui.HudEditorRegistry
import com.skysoft.gui.OverlayControlArea
import com.skysoft.gui.OverlayControlMouse
import com.skysoft.gui.transform
import com.skysoft.utils.input.InputHandlingResult
import com.skysoft.utils.input.InputUtilities
import com.skysoft.gui.tooltip.SkysoftNativeTooltip
import com.skysoft.utils.MinecraftClient
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent

private var hoveredControl: ProfitTrackerHoveredControl? = null
private var hoveredTracker: ProfitTrackerTarget? = null
private var itemPanelTarget: ProfitTrackerTarget? = null
private var isTrackerHovered = false
private val itemPanel = ProfitTrackerItemPanel()
private val hudControls = ProfitTrackerHudControls(itemPanel)
private val hudContent = ProfitTrackerHudContent(hudControls)
object ProfitTrackerHudInput {
    @JvmStatic
    fun handleKeyPress(event: KeyEvent): InputHandlingResult {
        val target = itemPanelTarget?.takeIf { it.isVisible() } ?: return InputHandlingResult.IGNORED
        return if (hudControls.wasKeyPressHandled(target, event)) {
            InputHandlingResult.CONSUMED
        } else {
            InputHandlingResult.IGNORED
        }
    }

    @JvmStatic
    fun handleCharTyped(event: CharacterEvent): InputHandlingResult =
        if (itemPanelTarget?.takeIf { it.isVisible() } != null && hudControls.wasCharTypedHandled(event)) {
            InputHandlingResult.CONSUMED
        } else {
            InputHandlingResult.IGNORED
        }
}

internal fun registerProfitTrackerHud() {
    registerMouseCapture()
    ProfitTrackerPreset.entries.map(ProfitTrackerTarget::preset).forEach { target ->
        HudEditorRegistry.register(profitTrackerHudEditorElement(target))
    }
    HudEditorRegistry.registerProvider("custom_profit_trackers") {
        customTrackerTargets().map(::profitTrackerHudEditorElement)
    }
    GuiOverlayRegistry.register(
        GuiOverlay(
            id = "profit_tracker",
            layer = GuiOverlayLayer.BELOW_SCREEN,
            contexts = GuiOverlayContextType.entries.toSet(),
            screenForegroundContexts = GuiOverlayContextType.INVENTORIES,
            render = { context, _ -> renderProfitTracker(context) },
        ),
    )
}

private fun profitTrackerHudEditorElement(target: ProfitTrackerTarget): HudEditorElement {
    val config = target.config
    return object : HudEditorElement {
        override val id: String = "profit_tracker_${target.storageKey.lowercase()}"
        override val label: String get() = "${target.displayName} Profit Tracker"
        override val position get() = config.position
        override val hasEditorBackground: Boolean get() = !config.details.showBackground
        override fun width(): Int = currentEditorRenderable()?.width ?: 0
        override fun height(): Int = currentEditorRenderable()?.height ?: 0
        override fun isVisible(): Boolean = isProfitTrackerHudVisible() && target.isVisible()
        override fun renderEditor(context: GuiGraphicsExtractor) {
            currentEditorRenderable()?.render(context)
        }
        override fun openConfig() = target.customId?.let(CustomProfitTrackerConfigScreen::open)
            ?: SkysoftConfigGui.open(target.displayName)

        private fun currentEditorRenderable(): ProfitTrackerRenderable? =
            if (isVisible()) hudContent.build(target, false) else null
    }
}

private fun isProfitTrackerHudVisible(): Boolean =
    HypixelLocationState.inSkyBlock && !MinecraftClient.isGuiHidden(Minecraft.getInstance())

private fun renderProfitTracker(context: GuiGraphicsExtractor) {
    val minecraft = Minecraft.getInstance()
    if (!isProfitTrackerHudVisible()) {
        clearProfitTrackerInteraction()
        return
    }
    val targets = visibleProfitTrackerTargets()
    if (targets.isEmpty()) {
        clearProfitTrackerInteraction()
        return
    }
    val inventoryScreen = MinecraftClient.screen(minecraft) as? AbstractContainerScreen<*>
    val inventoryOpen = inventoryScreen != null
    if (!inventoryOpen) clearProfitTrackerInteraction()
    if (itemPanelTarget?.takeIf { it.isVisible() } == null) {
        itemPanelTarget = null
        itemPanel.clear()
    }
    hoveredControl = null
    hoveredTracker = null
    isTrackerHovered = false
    val (mouseX, mouseY) = InputUtilities.scaledMousePosition(minecraft)
    val (normalMouseX, normalMouseY) = OverlayControlMouse.normalPoint(mouseX, mouseY)
    val (screenMouseX, screenMouseY) = OverlayControlMouse.screenPoint(mouseX, mouseY)
    val interactive = inventoryScreen != null &&
        !InventoryOverlayInput.isPointCovered(inventoryScreen, screenMouseX.toDouble(), screenMouseY.toDouble())
    targets.forEach { target ->
        context.nextStratum()
        renderPositioned(
            context,
            hudContent.build(target, inventoryOpen),
            target,
            interactive,
            normalMouseX,
            normalMouseY,
        )
    }
    if (interactive) {
        context.nextStratum()
        hoveredControl?.area?.let { area ->
            val managedItem = area.action as? ProfitTrackerControl.ManageItem
            val pestBreakdown = area.action as? ProfitTrackerControl.PestBreakdown
            if (managedItem != null) {
                SkysoftNativeTooltip.setItemActionForNextFrame(
                    context,
                    managedItem.stack,
                    "Manage",
                    managedItem.formattedName,
                    screenMouseX,
                    screenMouseY,
                )
            } else if (pestBreakdown != null && pestBreakdown.rows.isNotEmpty()) {
                SkysoftNativeTooltip.setItemRowsForNextFrame(
                    context,
                    "§ePests Vacuumed",
                    pestBreakdown.rows,
                    screenMouseX,
                    screenMouseY,
                )
            } else {
                SkysoftNativeTooltip.setForNextFrame(
                    context,
                    area.tooltipLines,
                    screenMouseX,
                    screenMouseY,
                    scrollable = false,
                )
            }
        }
    }
}

internal fun resetProfitTrackerHud() {
    clearProfitTrackerInteraction()
    hudContent.clear()
}

private fun clearProfitTrackerInteraction() {
    hoveredControl = null
    hoveredTracker = null
    itemPanelTarget = null
    isTrackerHovered = false
    itemPanel.clear()
    hudControls.clearResetConfirmation()
}

private fun renderPositioned(
    context: GuiGraphicsExtractor,
    renderable: ProfitTrackerRenderable,
    target: ProfitTrackerTarget,
    interactive: Boolean,
    mouseX: Int,
    mouseY: Int,
) {
    val transform = target.config.position.transform(renderable.width, renderable.height)
    val localMouseX = transform.localX(mouseX)
    val localMouseY = transform.localY(mouseY)
    val placePanelRight = transform.fitsRight(
        renderable.width, SIDE_PANEL_ESTIMATED_WIDTH, Minecraft.getInstance().window.guiScaledWidth,
    )
    val localControl = transform.render(context) {
        val trackerControl = renderable.renderInteractive(
            context,
            if (interactive) localMouseX else null,
            if (interactive) localMouseY else null,
        )
        val panelControl = if (itemPanelTarget == target) {
            itemPanel.render(
                context,
                target,
                renderable.width,
                placePanelRight,
                if (interactive) localMouseX else Int.MIN_VALUE,
                if (interactive) localMouseY else Int.MIN_VALUE,
            )
        } else {
            null
        }
        panelControl ?: trackerControl
    }
    val trackerHovered = interactive && localMouseX in 0..renderable.width && localMouseY in 0..renderable.height
    if (trackerHovered || localControl != null) {
        hoveredTracker = target
        isTrackerHovered = trackerHovered
    }
    localControl?.let { area ->
        hoveredControl = ProfitTrackerHoveredControl(
            target,
            area.copy(bounds = transform.screenBounds(area.bounds)),
        )
    }
}

private fun registerMouseCapture() {
    val isActive = { SkysoftConfigGui.config().profitTrackers.isAnyEnabled() }
    InventoryOverlayInput.registerClickHandler("Profit Tracker mouse click", isActive) { screen, click ->
        if (InventoryOverlayInput.isPointCovered(screen, click.x(), click.y())) {
            itemPanel.close()
            return@registerClickHandler InputHandlingResult.IGNORED
        }
        val hovered = hoveredControl
        val action = hovered?.area?.action
        val target = hovered?.target ?: itemPanelTarget
        val opensPanel = action.usesItemPanel()
        if (target != null && opensPanel) selectItemPanelTarget(target)
        val panelHovered = itemPanel.isHovered
        val handled = target?.takeIf { it.isVisible() }?.let {
            hudControls.wasClickHandled(screen, it, action, click.button())
        } == true
        if (!panelHovered && !opensPanel && (action != null || !handled)) itemPanel.close()
        if (handled || panelHovered) InputHandlingResult.CONSUMED else InputHandlingResult.IGNORED
    }
    InventoryOverlayInput.registerScrollHandler("Profit Tracker mouse scroll", isActive) {
            screen, mouseX, mouseY, verticalAmount ->
        when {
            InventoryOverlayInput.isPointCovered(screen, mouseX, mouseY) ->
                InputHandlingResult.IGNORED
            itemPanelTarget?.takeIf { it.isVisible() } == null && hoveredTracker == null ->
                InputHandlingResult.IGNORED
            itemPanelTarget?.let { itemPanel.wasSearchScrollHandled(it, verticalAmount) } == true ->
                InputHandlingResult.CONSUMED
            isTrackerHovered && hoveredTracker?.let { hudContent.wasScrollHandled(it, verticalAmount) } == true ->
                InputHandlingResult.CONSUMED
            else -> InputHandlingResult.IGNORED
        }
    }
}

private data class ProfitTrackerHoveredControl(
    val target: ProfitTrackerTarget,
    val area: OverlayControlArea<ProfitTrackerControl>,
)

private fun ProfitTrackerControl?.usesItemPanel(): Boolean =
    this == ProfitTrackerControl.More || this is ProfitTrackerControl.ManageItem

private fun selectItemPanelTarget(target: ProfitTrackerTarget) {
    if (itemPanelTarget == target) return
    itemPanel.clear()
    hudControls.clearResetConfirmation()
    itemPanelTarget = target
}

private const val SIDE_PANEL_ESTIMATED_WIDTH = 310
