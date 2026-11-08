package com.skysoft.gui

import com.mojang.blaze3d.platform.InputConstants
import com.skysoft.config.SkysoftConfigGui
import com.skysoft.features.inventory.InventoryButtonEditorActions
import com.skysoft.features.inventory.InventoryButtonManager
import com.skysoft.features.inventory.InventoryButtonResetShortcutResult
import com.skysoft.features.inventory.inventoryButtonEditorState
import com.skysoft.gui.scale.HudEditorGuiScale
import com.skysoft.gui.scale.InventoryScaledScreen
import com.skysoft.gui.tooltip.SkysoftNativeTooltip
import com.skysoft.gui.tooltip.TooltipScrollExcludedScreen
import com.skysoft.utils.MinecraftClient
import com.skysoft.utils.gui.Point
import com.skysoft.utils.gui.Rect
import com.skysoft.utils.input.InputHandlingResult
import com.skysoft.utils.input.InputUtilities
import com.skysoft.utils.render.ScreenTitleRenderer
import com.skysoft.utils.renderables.withIsolatedPose
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component

private typealias ButtonPlacement = InventoryButtonManager.ButtonPlacement

object SkysoftHudEditor {
    private const val EDITOR_BACKGROUND = 0x60000000
    private const val OLD_SCREEN_DIM = 0x30000000

    fun open() {
        val screen = MinecraftClient.screen()
        ScreenTitleRenderer.beginPositionEditing()
        MinecraftClient.setScreen(EditorScreen(screen as? AbstractContainerScreen<*>))
    }

    class EditorScreen(private val oldScreen: AbstractContainerScreen<*>? = null) :
        SkysoftEditorScreen(Component.literal("Skysoft Position Editor"), oldScreen),
        InventoryScaledScreen,
        TooltipScrollExcludedScreen {
        override fun usesInventoryScale(): Boolean = oldScreen != null

        private var elementDrag: HudEditorElementDrag? = null
        private val grabbedElement get() = elementDrag?.element
        private var hoveredElement: HudEditorElement? = null
        val isTabListVisible = Minecraft.getInstance().options.keyPlayerList.isDown
        private var selectedElement: HudEditorElement? = null
        private var grabbedInventoryButtonIndex: Int? = null
        private var grabbedInventoryButtonOffsetX = 0
        private var grabbedInventoryButtonOffsetY = 0
        private var hoveredInventoryButtonIndex: Int? = null
        private var selectedInventoryButtonIndex: Int? = null
        private var grabbedState: HudEditorSnapshot? = null
        private var oldScreenWidth = -1
        private var oldScreenHeight = -1
        private val editorScale = HudEditorGuiScale(oldScreen != null)
        private val registeredElements = HudEditorRegistry.editorElements(oldScreen != null)
        private var elements = registeredElements.filter(HudEditorElement::isVisible)
        private val snapper = HudEditorSnapper(
            { elements },
            editorScale::usesInventoryCoordinates,
            isSnapping = { Minecraft.getInstance().hasShiftDown() },
        )
        private val history = HudEditorHistory()

        override fun extractRenderState(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
            super.extractRenderState(context, mouseX, mouseY, delta)
            refreshVisibleElements()
            history.flushIdleScroll()
            renderBackground(context, mouseX, mouseY, delta)

            val placements = inventoryButtonPlacements(oldScreen)
            val hoveredButton = placements.placementAt(mouseX, mouseY)
            selectedInventoryButtonIndex = selectedInventoryButtonIndex?.takeIf { selected ->
                placements.any { it.index == selected }
            }
            val hovered = editorScale.elementAt(elements, mouseX, mouseY)
            val activeHoveredButton = hoveredButton?.takeIf { hovered == null }
            val activeButton = placements.placementFor(grabbedInventoryButtonIndex)
                ?: placements.placementFor(selectedInventoryButtonIndex)
                ?: activeHoveredButton
            val active = grabbedElement ?: selectedElement ?: hovered.takeIf { activeButton == null }
            hoveredInventoryButtonIndex = activeHoveredButton?.index
            hoveredElement = hovered

            val activeUsesInventoryCoordinates = when {
                activeButton != null -> true
                active != null -> editorScale.usesInventoryCoordinates(active)
                else -> oldScreen != null
            }
            val gridElement = active.takeIf { activeButton == null }
            val padding = if (snapper.gridEnabled) 0 else HUD_EDITOR_BORDER
            val highlighted = setOfNotNull(hovered, grabbedElement, selectedElement)
            val (inventoryElements, normalElements) = elements.partition(editorScale::usesInventoryCoordinates)
            if (snapper.gridEnabled && activeUsesInventoryCoordinates) renderEditorGrid(context, gridElement)
            renderInventoryButtons(context, placements, hoveredButton, padding)
            inventoryElements.forEach { renderEditorElement(context, it, it in highlighted, padding) }
            val grabbedUsesInventoryCoordinates = grabbedElement?.let(editorScale::usesInventoryCoordinates)
            renderSnapGuides(context, snapper, grabbedInventoryButtonIndex != null || grabbedUsesInventoryCoordinates == true)
            val tooltipLines = context.withIsolatedPose {
                context.pose().scale(editorScale.normalRenderScale(), editorScale.normalRenderScale())
                editorScale.withNormalGuiScale {
                    if (snapper.gridEnabled && !activeUsesInventoryCoordinates) renderEditorGrid(context, gridElement)
                    normalElements.forEach { renderEditorElement(context, it, it in highlighted, padding) }
                    renderSnapGuides(context, snapper, grabbedUsesInventoryCoordinates == false)
                    hudEditorTooltipLines(active, activeButton, snapper.gridEnabled)
                }
            }
            val tooltipFollowsMouse = SkysoftConfigGui.config().gui.positionEditor.details.doesTooltipFollowMouse
            SkysoftNativeTooltip.setForNextFrame(
                context,
                tooltipLines,
                mouseX,
                mouseY,
                scrollable = tooltipFollowsMouse,
                positioner = if (tooltipFollowsMouse) {
                    null
                } else {
                    HudEditorHelpPositioner(editorScale.activeEditorBounds(active, activeButton))
                },
            )
        }

        private fun renderBackground(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
            val window = Minecraft.getInstance().window
            val screenWidth = window.guiScaledWidth
            val screenHeight = window.guiScaledHeight
            val screen = oldScreen
            if (screen == null) {
                context.fill(0, 0, screenWidth, screenHeight, EDITOR_BACKGROUND)
                return
            }
            if (oldScreenWidth != screenWidth || oldScreenHeight != screenHeight) {
                screen.resize(screenWidth, screenHeight)
                oldScreenWidth = screenWidth
                oldScreenHeight = screenHeight
            }
            context.withIsolatedPose {
                screen.extractBackground(context, mouseX, mouseY, delta)
                screen.extractRenderState(context, mouseX, mouseY, delta)
            }
            context.fill(0, 0, screenWidth, screenHeight, OLD_SCREEN_DIM)
        }

        private fun renderInventoryButtons(
            context: GuiGraphicsExtractor,
            placements: List<ButtonPlacement>,
            hovered: ButtonPlacement?,
            padding: Int,
        ) {
            for (placement in placements) {
                val selected = placement.index == grabbedInventoryButtonIndex ||
                    placement.index == selectedInventoryButtonIndex ||
                    placement == hovered
                context.fillEditorPanel(placement.bounds, padding, selected)
                InventoryButtonManager.drawButton(
                    context = context,
                    x = placement.bounds.x,
                    y = placement.bounds.y,
                    button = placement.button,
                    active = placement.button.isActive(),
                    hovered = placement == hovered,
                    selected = selected,
                )
            }
        }

        override fun mouseClicked(click: MouseButtonEvent, doubled: Boolean): Boolean {
            refreshVisibleElements()
            val mouseX = click.x().toInt()
            val mouseY = click.y().toInt()
            return when (click.button()) {
                InputConstants.MOUSE_BUTTON_LEFT -> {
                    history.flushPending()
                    val element = editorScale.elementAt(elements, mouseX, mouseY)
                    val inventoryButton = if (element == null) {
                        inventoryButtonPlacements(oldScreen).placementAt(mouseX, mouseY)
                    } else {
                        null
                    }
                    when {
                        inventoryButton != null -> grabInventoryButton(inventoryButton, mouseX, mouseY, doubled)
                        element != null -> grabElement(element, mouseX, mouseY, doubled)
                        else -> {
                            releaseAndDeselect()
                            return super.mouseClicked(click, doubled)
                        }
                    }
                    true
                }

                InputConstants.MOUSE_BUTTON_RIGHT -> {
                    val element = editorScale.elementAt(elements, mouseX, mouseY) ?: return super.mouseClicked(click, doubled)
                    history.flushPending()
                    element.openConfig()
                    true
                }

                else -> super.mouseClicked(click, doubled)
            }
        }

        private fun grabInventoryButton(button: ButtonPlacement, mouseX: Int, mouseY: Int, doubled: Boolean) {
            if (doubled) {
                selectedInventoryButtonIndex = button.index
                selectedElement = null
            }
            snapper.clear()
            grabbedState = inventoryButtonEditorState()
            grabbedInventoryButtonIndex = button.index
            grabbedInventoryButtonOffsetX = mouseX - button.bounds.x
            grabbedInventoryButtonOffsetY = mouseY - button.bounds.y
            elementDrag = null
        }

        private fun grabElement(element: HudEditorElement, mouseX: Int, mouseY: Int, doubled: Boolean) {
            grabbedInventoryButtonIndex = null
            grabbedState = element.captureEditorState()
            if (doubled) {
                selectedElement = element
                selectedInventoryButtonIndex = null
            }
            snapper.clear()
            elementDrag = editorScale.withElementGuiScale(element) {
                HudEditorElementDrag(
                    element,
                    editorScale.elementMouseX(element, mouseX),
                    editorScale.elementMouseY(element, mouseY),
                )
            }
        }

        private fun releaseAndDeselect() {
            elementDrag = null
            grabbedInventoryButtonIndex = null
            grabbedState = null
            selectedElement = null
            selectedInventoryButtonIndex = null
        }

        override fun mouseDragged(click: MouseButtonEvent, dragX: Double, dragY: Double): Boolean {
            refreshVisibleElements()
            if (click.button() != InputConstants.MOUSE_BUTTON_LEFT) return false
            grabbedInventoryButtonIndex?.let { index ->
                oldScreen?.let { screen -> dragInventoryButton(screen, index, click) }
                return true
            }
            val drag = elementDrag ?: return super.mouseDragged(click, dragX, dragY)
            val element = drag.element
            val elementMouseX = editorScale.elementMouseX(element, click.x().toInt())
            val elementMouseY = editorScale.elementMouseY(element, click.y().toInt())
            editorScale.withElementGuiScale(element) {
                drag.moveTo(elementMouseX, elementMouseY, snapper)
            }
            return true
        }

        private fun dragInventoryButton(screen: AbstractContainerScreen<*>, index: Int, click: MouseButtonEvent) {
            val placements = InventoryButtonManager.placements(screen, includeInactive = true)
            val placement = placements.placementFor(index) ?: return
            val snapped = snapper.snapPosition(
                click.x().toInt() - grabbedInventoryButtonOffsetX,
                click.y().toInt() - grabbedInventoryButtonOffsetY,
                placement.bounds.width,
                placement.bounds.height,
                placements.filterNot { it.index == index }.map { it.bounds },
            )
            InventoryButtonManager.moveButton(screen, index, snapped.x, snapped.y)
            InventoryButtonManager.placements(screen, includeInactive = true)
                .placementFor(index)
                ?.let { snapper.confirmPosition(it.bounds) }
        }

        override fun mouseReleased(click: MouseButtonEvent): Boolean {
            if (click.button() == InputConstants.MOUSE_BUTTON_LEFT) finishDrag()
            return super.mouseReleased(click)
        }

        private fun finishDrag() {
            val before = grabbedState
            val after = if (grabbedInventoryButtonIndex != null) {
                inventoryButtonEditorState()
            } else {
                grabbedElement?.captureEditorState()
            }
            if (before != null && after != null) history.record(before, after)
            elementDrag = null
            grabbedInventoryButtonIndex = null
            grabbedState = null
            snapper.clear()
        }

        private fun refreshVisibleElements() {
            val visibleElements = registeredElements.filter(HudEditorElement::isVisible)
            if (visibleElements == elements) return
            elements = visibleElements
            if (grabbedElement?.let { it !in elements } == true) finishDrag()
            selectedElement = selectedElement?.takeIf(elements::contains)
            hoveredElement = hoveredElement?.takeIf(elements::contains)
            snapper.clear()
        }

        override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
            refreshVisibleElements()
            val element = grabbedElement ?: editorScale.elementAt(elements, mouseX.toInt(), mouseY.toInt())
            if (element != null) {
                val before = element.captureEditorState()
                if (element.applyEditorScroll(scrollY) == InputHandlingResult.IGNORED && element.canScale && scrollY != 0.0) {
                    editorScale.withElementGuiScale(element) { element.scaleInEditor(scrollY) }
                }
                if (grabbedElement == null) {
                    history.recordScroll("element:${element.id}", before, element.captureEditorState())
                }
                return true
            }
            val buttonIndex = grabbedInventoryButtonIndex
                ?: inventoryButtonPlacements(oldScreen).placementAt(mouseX.toInt(), mouseY.toInt())?.index
            if (buttonIndex != null) {
                val before = inventoryButtonEditorState()
                if (InventoryButtonEditorActions.changeButtonScale(buttonIndex, scrollY) == InputHandlingResult.CONSUMED) {
                    if (grabbedInventoryButtonIndex == null) {
                        history.recordScroll("inventory_button:$buttonIndex", before, inventoryButtonEditorState())
                    }
                    return true
                }
            }
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
        }

        override fun keyPressed(event: KeyEvent): Boolean {
            refreshVisibleElements()
            if (InputUtilities.isHistoryShortcut(event)) {
                if (grabbedElement == null && grabbedInventoryButtonIndex == null) {
                    if (InputUtilities.isRedoShortcut(event)) history.redo() else history.undo()
                    snapper.clear()
                }
                return true
            }
            if (event.key() == InputConstants.KEY_G) {
                snapper.gridEnabled = !snapper.gridEnabled
                snapper.clear()
                return true
            }
            val buttonIndex = grabbedInventoryButtonIndex
                ?: selectedInventoryButtonIndex
                ?: hoveredInventoryButtonIndex.takeIf { selectedElement == null }
            val nudge = hudEditorNudge(event.key())
            if (nudge != null && nudgeSelection(nudge, buttonIndex) == InputHandlingResult.CONSUMED) return true
            if (event.key() == InputConstants.KEY_R) {
                if (buttonIndex != null) resetInventoryButton(buttonIndex) else resetElement()
                return true
            }
            return super.keyPressed(event)
        }

        private fun nudgeSelection(nudge: Point, buttonIndex: Int?): InputHandlingResult {
            if (buttonIndex != null) {
                val before = inventoryButtonEditorState()
                return nudgeInventoryButton(nudge, buttonIndex, oldScreen, snapper).also { result ->
                    if (result == InputHandlingResult.CONSUMED) history.record(before, inventoryButtonEditorState())
                }
            }
            val selected = grabbedElement ?: selectedElement ?: return InputHandlingResult.IGNORED
            val before = selected.captureEditorState()
            return editorScale.withElementGuiScale(selected) { selected.nudgeInEditor(nudge) }.also { result ->
                if (result == InputHandlingResult.CONSUMED) {
                    history.record(before, selected.captureEditorState())
                    snapper.clear()
                }
            }
        }

        private fun resetInventoryButton(buttonIndex: Int) {
            val before = inventoryButtonEditorState()
            if (InventoryButtonEditorActions.resetOrRemoveButton(buttonIndex) == InventoryButtonResetShortcutResult.REMOVED) {
                grabbedInventoryButtonIndex = null
                hoveredInventoryButtonIndex = null
                selectedInventoryButtonIndex = null
            }
            history.record(before, inventoryButtonEditorState())
        }

        private fun resetElement() {
            val element = grabbedElement ?: selectedElement ?: hoveredElement ?: return
            val before = element.captureEditorState()
            element.resetEditorState()
            history.record(before, element.captureEditorState())
        }

        protected override fun beforeEditorClose() {
            history.flushPending()
            ScreenTitleRenderer.endPositionEditing()
            SkysoftConfigGui.config().saveNow()
        }
    }
}

private fun inventoryButtonPlacements(screen: AbstractContainerScreen<*>?): List<ButtonPlacement> {
    if (screen == null || !InventoryButtonManager.isAvailableInCurrentLocation()) return emptyList()
    return InventoryButtonManager.placements(screen, includeInactive = true)
}

private fun List<ButtonPlacement>.placementAt(mouseX: Int, mouseY: Int): ButtonPlacement? =
    lastOrNull { it.bounds.contains(mouseX, mouseY) }

private fun List<ButtonPlacement>.placementFor(index: Int?): ButtonPlacement? =
    index?.let { firstOrNull { placement -> placement.index == it } }

private fun nudgeInventoryButton(
    delta: Point,
    index: Int,
    screen: AbstractContainerScreen<*>?,
    snapper: HudEditorSnapper,
): InputHandlingResult {
    val inventoryScreen = screen ?: return InputHandlingResult.IGNORED
    val placement = InventoryButtonManager.placements(inventoryScreen, includeInactive = true).placementFor(index)
        ?: return InputHandlingResult.IGNORED
    InventoryButtonManager.moveButton(inventoryScreen, index, placement.bounds.x + delta.x, placement.bounds.y + delta.y)
    snapper.clear()
    return InputHandlingResult.CONSUMED
}

private fun HudEditorGuiScale.elementAt(elements: List<HudEditorElement>, mouseX: Int, mouseY: Int): HudEditorElement? {
    elements.lastOrNull { resizeHandleAt(it, mouseX, mouseY) != null }?.let { return it }
    val hovered = elements.filter { element ->
        withElementGuiScale(element) { element.isHovered(elementMouseX(element, mouseX), elementMouseY(element, mouseY)) }
    }
    val priority = hovered.maxOfOrNull(HudEditorElement::editorSelectionPriority) ?: return null
    return hovered.lastOrNull { it.editorSelectionPriority == priority }
}

private fun HudEditorGuiScale.resizeHandleAt(element: HudEditorElement, mouseX: Int, mouseY: Int): HudResizeHandle? =
    withElementGuiScale(element) {
        val bounds = element.editorBounds()
        val x = elementMouseX(element, mouseX) - bounds.x
        val y = elementMouseY(element, mouseY) - bounds.y
        findResizeHandle(element, x, y, bounds.width, bounds.height)
    }

private fun HudEditorGuiScale.activeEditorBounds(element: HudEditorElement?, activeButton: ButtonPlacement?): Rect? {
    if (activeButton != null) return activeButton.bounds
    val activeElement = element ?: return null
    return elementScreenBounds(activeElement, withElementGuiScale(activeElement) { activeElement.editorBounds() })
}

private fun HudEditorElement.scaleInEditor(scrollY: Double) {
    val oldScale = position.scale
    position.scaleByScroll(scrollY)
    val oldX = absoluteX(scaledWidth(oldScale)) - layoutOffsetX
    val oldY = absoluteY(scaledHeight(oldScale)) - layoutOffsetY
    position.moveToAbsoluteAllowingOverflow(oldX, oldY, scaledWidth(), scaledHeight())
}

private fun HudEditorElement.isHovered(mouseX: Int, mouseY: Int): Boolean {
    val bounds = editorBounds()
    return mouseX in (bounds.x - editorLeftPadding - HUD_EDITOR_BORDER)..(bounds.x + bounds.width + HUD_EDITOR_BORDER) &&
        mouseY in (bounds.y - HUD_EDITOR_BORDER)..(bounds.y + bounds.height + HUD_EDITOR_BORDER)
}
