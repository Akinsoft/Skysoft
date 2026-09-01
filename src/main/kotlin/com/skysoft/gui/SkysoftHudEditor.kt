package com.skysoft.gui

import com.skysoft.config.SkysoftConfigGui
import com.skysoft.features.inventory.InventoryButtonEditorActions
import com.skysoft.features.inventory.InventoryButtonManager
import com.skysoft.features.inventory.InventoryButtonResetShortcutResult
import com.skysoft.features.inventory.inventoryButtonEditorState
import com.skysoft.gui.scale.InventoryScaledScreen
import com.skysoft.gui.scale.shouldUseConfiguredInventoryScale
import com.skysoft.gui.tooltip.SkysoftNativeTooltip
import com.skysoft.gui.tooltip.TooltipScrollExcludedScreen
import com.skysoft.utils.MinecraftClient
import com.skysoft.utils.gui.Point
import com.skysoft.utils.gui.Rect
import com.skysoft.utils.input.InputHandlingResult
import com.skysoft.utils.input.InputUtilities
import com.skysoft.utils.render.ScreenTitleRenderer
import java.util.Locale
import kotlin.math.roundToInt
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW

object SkysoftHudEditor {
    private const val PANEL_BACKGROUND = 0x90000000.toInt()
    private const val PANEL_HOVER = 0x90F0F0F0.toInt()
    private const val EDITOR_BACKGROUND = 0x60000000
    private const val OLD_SCREEN_DIM = 0x30000000
    private const val SCALE_STEP = 0.1f

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

        private var grabbedElement: HudEditorElement? = null
        private var grabbedOffsetX = 0
        private var grabbedOffsetY = 0
        private var grabbedWidth = 0
        private var grabbedHeight = 0
        private var grabbedResizeHandle: HudResizeHandle? = null
        private var grabbedElementX = 0
        private var grabbedElementY = 0
        private var hoveredElement: HudEditorElement? = null
        private var selectedElement: HudEditorElement? = null
        private var grabbedInventoryButtonIndex: Int? = null
        private var grabbedInventoryButtonOffsetX = 0
        private var grabbedInventoryButtonOffsetY = 0
        private var hoveredInventoryButtonIndex: Int? = null
        private var selectedInventoryButtonIndex: Int? = null
        private var grabbedState: HudEditorSnapshot? = null
        private var oldScreenWidth = -1
        private var oldScreenHeight = -1
        private val editorScale = EditorGuiScale(oldScreen != null)
        private val elements = HudEditorRegistry.visibleElements(oldScreen != null)
        private val snapper = HudEditorSnapper(elements, editorScale)
        private val history = HudEditorHistory()

        override fun extractRenderState(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
            super.extractRenderState(context, mouseX, mouseY, delta)
            renderEditor(context, mouseX, mouseY, delta)
        }

        private fun renderEditor(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
            history.flushIdleScroll()
            val minecraft = Minecraft.getInstance()
            if (oldScreen == null) {
                context.fill(0, 0, minecraft.window.guiScaledWidth, minecraft.window.guiScaledHeight, EDITOR_BACKGROUND)
            } else {
                renderOldScreen(context, mouseX, mouseY, delta)
            }

            val placements = inventoryButtonPlacements()
            val hoveredButton = placements.lastOrNull { it.bounds.contains(mouseX, mouseY) }
            selectedElement = selectedElement?.takeIf(elements::contains)
            selectedInventoryButtonIndex = selectedInventoryButtonIndex?.takeIf { selected ->
                placements.any { it.index == selected }
            }
            val hovered = elementAt(mouseX, mouseY, elements)
            val activeHoveredButton = hoveredButton?.takeIf { hovered == null }
            val selectedButton = inventoryButtonPlacement(selectedInventoryButtonIndex)
            val activeButton = inventoryButtonPlacement(grabbedInventoryButtonIndex) ?: selectedButton ?: activeHoveredButton
            val active = grabbedElement ?: selectedElement ?: hovered.takeIf { activeButton == null }
            val activeUsesInventoryCoordinates = when {
                activeButton != null -> true
                active != null -> editorScale.usesInventoryCoordinates(active)
                else -> oldScreen != null
            }
            hoveredInventoryButtonIndex = activeHoveredButton?.index
            hoveredElement = hovered
            val gridElement = active.takeIf { activeButton == null }
            val editorPadding = if (snapper.gridEnabled) 0 else HUD_EDITOR_BORDER
            if (snapper.gridEnabled && activeUsesInventoryCoordinates) renderEditorGrid(context, gridElement)
            renderInventoryButtons(context, placements, hoveredButton, editorPadding)
            elements.filter(editorScale::usesInventoryCoordinates).forEach { element ->
                renderElement(
                    context,
                    element,
                    element == hovered || element == grabbedElement || element == selectedElement,
                    editorPadding,
                )
            }
            renderSnapGuides(
                context,
                snapper,
                inventorySnapGuidesActive(grabbedInventoryButtonIndex, grabbedElement, editorScale),
            )
            context.pose().pushMatrix()
            val tooltipLines = try {
                context.pose().scale(editorScale.normalRenderScale(), editorScale.normalRenderScale())
                editorScale.withNormalGuiScale {
                    if (snapper.gridEnabled && !activeUsesInventoryCoordinates) renderEditorGrid(context, gridElement)
                    for (element in elements.filterNot(editorScale::usesInventoryCoordinates)) {
                        renderElement(
                            context,
                            element,
                            element == hovered || element == grabbedElement || element == selectedElement,
                            editorPadding,
                        )
                    }
                    renderSnapGuides(
                        context,
                        snapper,
                        grabbedElement?.let(editorScale::usesInventoryCoordinates) == false,
                    )

                    hudEditorTooltipLines(active, activeButton, snapper.gridEnabled)
                }
            } finally {
                context.pose().popMatrix()
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
                    HudEditorHelpPositioner(activeEditorBounds(active, activeButton))
                },
            )
        }

        private fun renderOldScreen(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
            val screen = oldScreen ?: return
            val window = Minecraft.getInstance().window
            val screenWidth = window.guiScaledWidth
            val screenHeight = window.guiScaledHeight
            if (oldScreenWidth != screenWidth || oldScreenHeight != screenHeight) {
                screen.resize(screenWidth, screenHeight)
                oldScreenWidth = screenWidth
                oldScreenHeight = screenHeight
            }

            context.pose().pushMatrix()
            screen.extractBackground(context, mouseX, mouseY, delta)
            screen.extractRenderState(context, mouseX, mouseY, delta)
            context.pose().popMatrix()
            context.fill(0, 0, screenWidth, screenHeight, OLD_SCREEN_DIM)
        }

        private fun isElementHovered(element: HudEditorElement, mouseX: Int, mouseY: Int): Boolean =
            editorScale.withElementGuiScale(element) {
                element.isHovered(
                    editorScale.elementMouseX(element, mouseX),
                    editorScale.elementMouseY(element, mouseY),
                )
            }

        private fun elementAt(
            mouseX: Int,
            mouseY: Int,
            elements: List<HudEditorElement> = this.elements,
        ): HudEditorElement? {
            elements.lastOrNull { resizeHandleAt(it, mouseX, mouseY) != null }?.let { return it }
            val hovered = elements.filter { isElementHovered(it, mouseX, mouseY) }
            val priority = hovered.maxOfOrNull(HudEditorElement::editorSelectionPriority) ?: return null
            return hovered.lastOrNull { it.editorSelectionPriority == priority }
        }

        private fun resizeHandleAt(element: HudEditorElement, mouseX: Int, mouseY: Int): HudResizeHandle? =
            editorScale.withElementGuiScale(element) {
                val width = (element.width() * element.position.effectiveScale).roundToInt()
                val height = (element.height() * element.position.effectiveScale).roundToInt()
                val x = editorScale.elementMouseX(element, mouseX) - element.absoluteX(width)
                val y = editorScale.elementMouseY(element, mouseY) - element.absoluteY(height)
                findResizeHandle(element, x, y, width, height)
            }

        private fun inventoryButtonPlacements(): List<InventoryButtonManager.ButtonPlacement> {
            if (!InventoryButtonManager.isAvailableInCurrentLocation()) return emptyList()
            return oldScreen?.let { InventoryButtonManager.placements(it, includeInactive = true) }.orEmpty()
        }

        private fun renderInventoryButtons(
            context: GuiGraphicsExtractor,
            placements: List<InventoryButtonManager.ButtonPlacement>,
            hovered: InventoryButtonManager.ButtonPlacement?,
            padding: Int,
        ) {
            for (placement in placements) {
                val selected = placement.index == grabbedInventoryButtonIndex ||
                    placement.index == selectedInventoryButtonIndex ||
                    placement == hovered
                context.fill(
                    placement.bounds.x - padding,
                    placement.bounds.y - padding,
                    placement.bounds.x + placement.bounds.width + padding,
                    placement.bounds.y + placement.bounds.height + padding,
                    if (selected) PANEL_HOVER else PANEL_BACKGROUND,
                )
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

        private fun inventoryButtonPlacement(index: Int?): InventoryButtonManager.ButtonPlacement? {
            val buttonIndex = index ?: return null
            if (!InventoryButtonManager.isAvailableInCurrentLocation()) return null
            return oldScreen?.let { InventoryButtonManager.placements(it, includeInactive = true) }
                ?.firstOrNull { it.index == buttonIndex }
        }

        private fun activeEditorBounds(
            element: HudEditorElement?,
            activeButton: InventoryButtonManager.ButtonPlacement?,
        ): Rect? {
            val button = inventoryButtonPlacement(grabbedInventoryButtonIndex) ?: activeButton
            if (button != null) return button.bounds
            val activeElement = element ?: return null
            val bounds = editorScale.withElementGuiScale(activeElement) {
                val scale = activeElement.position.effectiveScale
                val width = (activeElement.width() * scale).roundToInt()
                val height = (activeElement.height() * scale).roundToInt()
                Rect(activeElement.absoluteX(width), activeElement.absoluteY(height), width, height)
            }
            return editorScale.elementScreenBounds(activeElement, bounds)
        }

        private fun renderElement(
            context: GuiGraphicsExtractor,
            element: HudEditorElement,
            selected: Boolean,
            padding: Int,
        ) {
            val position = element.position
            val scaledWidth = (element.width() * position.scale).roundToInt()
            val scaledHeight = (element.height() * position.scale).roundToInt()
            val x = element.absoluteX(scaledWidth)
            val y = element.absoluteY(scaledHeight)
            val color = if (selected) PANEL_HOVER else PANEL_BACKGROUND
            if (element.hasEditorBackground) {
                context.fill(
                    x - padding,
                    y - padding,
                    x + scaledWidth + padding,
                    y + scaledHeight + padding,
                    color,
                )
            } else if (selected) {
                drawEditorOutline(context, x, y, scaledWidth, scaledHeight, color, padding)
            }
            context.pose().pushMatrix()
            context.pose().translate(x.toFloat(), y.toFloat())
            context.pose().scale(position.scale, position.scale)
            element.renderEditor(context)
            context.pose().popMatrix()
            if (selected && (element.canResizeWidth || element.canResizeHeight)) {
                drawResizeHandles(context, x, y, scaledWidth, scaledHeight)
            }
        }

        private fun drawEditorOutline(
            context: GuiGraphicsExtractor,
            x: Int,
            y: Int,
            width: Int,
            height: Int,
            color: Int,
            padding: Int,
        ) {
            val left = x - padding
            val top = y - padding
            val right = x + width + padding
            val bottom = y + height + padding
            context.fill(left, top, right, top + HUD_EDITOR_BORDER, color)
            context.fill(left, bottom - HUD_EDITOR_BORDER, right, bottom, color)
            context.fill(left, top + HUD_EDITOR_BORDER, left + HUD_EDITOR_BORDER, bottom - HUD_EDITOR_BORDER, color)
            context.fill(right - HUD_EDITOR_BORDER, top + HUD_EDITOR_BORDER, right, bottom - HUD_EDITOR_BORDER, color)
        }

        override fun mouseClicked(click: MouseButtonEvent, doubled: Boolean): Boolean {
            val mouseX = click.x().toInt()
            val mouseY = click.y().toInt()
            return when (click.button()) {
                GLFW.GLFW_MOUSE_BUTTON_LEFT -> {
                    history.flushPending()
                    val element = elementAt(mouseX, mouseY)
                    val inventoryButton = if (element == null) {
                        inventoryButtonPlacements().lastOrNull { it.bounds.contains(mouseX, mouseY) }
                    } else {
                        null
                    }
                    if (inventoryButton != null) {
                        if (doubled) {
                            selectedInventoryButtonIndex = inventoryButton.index
                            selectedElement = null
                        }
                        snapper.clear()
                        grabbedState = inventoryButtonEditorState()
                        grabbedInventoryButtonIndex = inventoryButton.index
                        grabbedInventoryButtonOffsetX = mouseX - inventoryButton.bounds.x
                        grabbedInventoryButtonOffsetY = mouseY - inventoryButton.bounds.y
                        grabbedElement = null
                        true
                    } else {
                        grabbedElement = element
                        grabbedInventoryButtonIndex = null
                        grabbedState = element?.captureEditorState()
                        if (element != null) {
                            if (doubled) {
                                selectedElement = element
                                selectedInventoryButtonIndex = null
                            }
                            snapper.clear()
                            editorScale.withElementGuiScale(element) {
                                grabbedWidth = (element.width() * element.position.scale).roundToInt()
                                grabbedHeight = (element.height() * element.position.scale).roundToInt()
                                grabbedElementX = element.absoluteX(grabbedWidth)
                                grabbedElementY = element.absoluteY(grabbedHeight)
                                grabbedOffsetX = editorScale.elementMouseX(element, mouseX) -
                                    grabbedElementX
                                grabbedOffsetY = editorScale.elementMouseY(element, mouseY) -
                                    grabbedElementY
                                grabbedResizeHandle = findResizeHandle(
                                    element,
                                    grabbedOffsetX,
                                    grabbedOffsetY,
                                    grabbedWidth,
                                    grabbedHeight,
                                )
                                element.beginEditorDrag(
                                    grabbedOffsetX,
                                    grabbedOffsetY,
                                    grabbedWidth,
                                    grabbedHeight,
                                )
                            }
                            true
                        } else {
                            selectedElement = null
                            selectedInventoryButtonIndex = null
                            grabbedState = null
                            super.mouseClicked(click, doubled)
                        }
                    }
                }

                GLFW.GLFW_MOUSE_BUTTON_RIGHT -> {
                    val element = elementAt(mouseX, mouseY)
                    if (element == null) {
                        super.mouseClicked(click, doubled)
                    } else {
                        history.flushPending()
                        element.openConfig()
                        true
                    }
                }

                else -> super.mouseClicked(click, doubled)
            }
        }

        override fun mouseDragged(click: MouseButtonEvent, dragX: Double, dragY: Double): Boolean {
            if (click.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false
            grabbedInventoryButtonIndex?.let { index ->
                oldScreen?.let { screen ->
                    val placements = InventoryButtonManager.placements(screen, includeInactive = true)
                    val placement = placements.firstOrNull { it.index == index }
                    if (placement != null) {
                        val snapped = snapper.snapPosition(
                            click.x().toInt() - grabbedInventoryButtonOffsetX,
                            click.y().toInt() - grabbedInventoryButtonOffsetY,
                            placement.bounds.width,
                            placement.bounds.height,
                            placements.filterNot { it.index == index }.map { it.bounds },
                        )
                        InventoryButtonManager.moveButton(screen, index, snapped.x, snapped.y)
                        InventoryButtonManager.placements(screen, includeInactive = true)
                            .firstOrNull { it.index == index }
                            ?.let { snapper.confirmPosition(it.bounds) }
                    }
                }
                return true
            }
            val element = grabbedElement ?: return super.mouseDragged(click, dragX, dragY)
            val elementMouseX = editorScale.elementMouseX(element, click.x().toInt())
            val elementMouseY = editorScale.elementMouseY(element, click.y().toInt())
            editorScale.withElementGuiScale(element) {
                val width = grabbedWidth.takeIf { it > 0 } ?: (element.width() * element.position.scale).roundToInt()
                val height = grabbedHeight.takeIf { it > 0 } ?: (element.height() * element.position.scale).roundToInt()
                grabbedResizeHandle?.let { handle ->
                    resizeElement(element, handle, elementMouseX, elementMouseY)
                    return@withElementGuiScale
                }
                val targetX = elementMouseX - grabbedOffsetX
                val targetY = elementMouseY - grabbedOffsetY
                val snapped = snapper.snapPosition(element, targetX, targetY, width, height)
                val deltaX = snapped.x - element.absoluteX(width)
                val deltaY = snapped.y - element.absoluteY(height)
                if (element.applyEditorDrag(deltaX, deltaY) == InputHandlingResult.IGNORED && element.canMove) {
                    val positionX = snapped.x - element.layoutOffsetX
                    val positionY = snapped.y - element.layoutOffsetY
                    if (element.keepsInsideScreen) {
                        element.position.moveToAbsolute(positionX, positionY, width, height)
                    } else {
                        element.position.moveToAbsoluteAllowingOverflow(positionX, positionY, width, height)
                    }
                }
                snapper.confirmPosition(element, width, height)
            }
            return true
        }

        override fun mouseReleased(click: MouseButtonEvent): Boolean {
            if (click.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                val before = grabbedState
                val after = when {
                    grabbedInventoryButtonIndex != null -> inventoryButtonEditorState()
                    grabbedElement != null -> grabbedElement?.captureEditorState()
                    else -> null
                }
                if (before != null && after != null) history.record(before, after)
                grabbedElement = null
                grabbedInventoryButtonIndex = null
                grabbedState = null
                grabbedWidth = 0
                grabbedHeight = 0
                grabbedResizeHandle = null
                snapper.clear()
            }
            return super.mouseReleased(click)
        }

        override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
            val element = grabbedElement ?: elementAt(mouseX.toInt(), mouseY.toInt())
            if (element != null) {
                val before = element.captureEditorState()
                if (element.applyEditorScroll(scrollY) == InputHandlingResult.IGNORED && element.canScale && scrollY != 0.0) {
                    editorScale.withElementGuiScale(element) {
                        val oldScale = element.position.scale
                        element.position.scale += if (scrollY > 0.0) SCALE_STEP else -SCALE_STEP
                        val oldWidth = (element.width() * oldScale).roundToInt()
                        val oldHeight = (element.height() * oldScale).roundToInt()
                        val newWidth = (element.width() * element.position.scale).roundToInt()
                        val newHeight = (element.height() * element.position.scale).roundToInt()
                        val oldX = element.absoluteX(oldWidth) - element.layoutOffsetX
                        val oldY = element.absoluteY(oldHeight) - element.layoutOffsetY
                        element.position.moveToAbsoluteAllowingOverflow(oldX, oldY, newWidth, newHeight)
                    }
                }
                if (grabbedElement == null) {
                    history.recordScroll("element:${element.id}", before, element.captureEditorState())
                }
                return true
            }

            val buttonIndex = grabbedInventoryButtonIndex ?: inventoryButtonPlacements()
                .lastOrNull { it.bounds.contains(mouseX.toInt(), mouseY.toInt()) }
                ?.index
            if (buttonIndex != null) {
                val before = inventoryButtonEditorState()
                if (InventoryButtonEditorActions.changeButtonScale(buttonIndex, scrollY) == InputHandlingResult.CONSUMED) {
                    if (grabbedInventoryButtonIndex == null) {
                        history.recordScroll(
                            "inventory_button:$buttonIndex",
                            before,
                            inventoryButtonEditorState(),
                        )
                    }
                    return true
                }
            }
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
        }

        override fun keyPressed(event: KeyEvent): Boolean {
            if (event.isHudEditorHistoryKey()) {
                if (grabbedElement == null && grabbedInventoryButtonIndex == null) {
                    if (event.key() == GLFW.GLFW_KEY_Y || Minecraft.getInstance().hasShiftDown()) history.redo()
                    else history.undo()
                    snapper.clear()
                }
                return true
            }
            if (event.key() == GLFW.GLFW_KEY_G) {
                snapper.gridEnabled = !snapper.gridEnabled
                snapper.clear()
                return true
            }
            val nudge = hudEditorNudge(event.key())
            val buttonIndex = grabbedInventoryButtonIndex
                ?: selectedInventoryButtonIndex
                ?: hoveredInventoryButtonIndex.takeIf { selectedElement == null }
            val selected = grabbedElement ?: selectedElement
            val nudgeResult = when {
                nudge == null -> InputHandlingResult.IGNORED
                buttonIndex != null -> {
                    val before = inventoryButtonEditorState()
                    nudgeInventoryButton(nudge, buttonIndex, oldScreen, snapper).also { result ->
                        if (result == InputHandlingResult.CONSUMED) {
                            history.record(before, inventoryButtonEditorState())
                        }
                    }
                }
                selected != null -> {
                    val before = selected.captureEditorState()
                    editorScale.withElementGuiScale(selected) { selected.nudgeInEditor(nudge) }.also { result ->
                        if (result == InputHandlingResult.CONSUMED) {
                            history.record(before, selected.captureEditorState())
                            snapper.clear()
                        }
                    }
                }
                else -> InputHandlingResult.IGNORED
            }
            if (nudgeResult == InputHandlingResult.CONSUMED) return true
            if (event.key() == GLFW.GLFW_KEY_R) {
                if (buttonIndex != null) {
                    val before = inventoryButtonEditorState()
                    if (InventoryButtonEditorActions.resetOrRemoveButton(buttonIndex) ==
                        InventoryButtonResetShortcutResult.REMOVED
                    ) {
                        grabbedInventoryButtonIndex = null
                        hoveredInventoryButtonIndex = null
                        selectedInventoryButtonIndex = null
                    }
                    history.record(before, inventoryButtonEditorState())
                } else {
                    (grabbedElement ?: selectedElement ?: hoveredElement)?.let { element ->
                        val before = element.captureEditorState()
                        element.resetEditorState()
                        history.record(before, element.captureEditorState())
                    }
                }
                return true
            }
            return super.keyPressed(event)
        }

        private fun resizeElement(
            element: HudEditorElement,
            handle: HudResizeHandle,
            mouseX: Int,
            mouseY: Int,
        ) {
            val mouseDeltaX = mouseX - grabbedElementX - grabbedOffsetX
            val mouseDeltaY = mouseY - grabbedElementY - grabbedOffsetY
            var left = grabbedElementX
            var right = grabbedElementX + grabbedWidth
            var top = grabbedElementY
            var bottom = grabbedElementY + grabbedHeight
            if (element.canResizeWidth) {
                if (handle.isLeft) left += mouseDeltaX else right += mouseDeltaX
            }
            if (element.canResizeHeight) {
                if (handle.isTop) top += mouseDeltaY else bottom += mouseDeltaY
            }
            if (element.canResizeWidth) {
                if (handle.isLeft) {
                    left = snapper.snapResizeCoordinate(
                        element,
                        left,
                        HudSnapAxis.HORIZONTAL,
                        HudSnapAnchor.START,
                    )
                } else {
                    right = snapper.snapResizeCoordinate(
                        element,
                        right,
                        HudSnapAxis.HORIZONTAL,
                        HudSnapAnchor.END,
                    )
                }
            } else {
                snapper.clear(HudSnapAxis.HORIZONTAL)
            }
            if (element.canResizeHeight) {
                if (handle.isTop) {
                    top = snapper.snapResizeCoordinate(
                        element,
                        top,
                        HudSnapAxis.VERTICAL,
                        HudSnapAnchor.START,
                    )
                } else {
                    bottom = snapper.snapResizeCoordinate(
                        element,
                        bottom,
                        HudSnapAxis.VERTICAL,
                        HudSnapAnchor.END,
                    )
                }
            } else {
                snapper.clear(HudSnapAxis.VERTICAL)
            }
            val scale = element.position.effectiveScale
            val minimumWidth = (element.minEditorWidth() * scale).roundToInt()
            val minimumHeight = (element.minEditorHeight() * scale).roundToInt()
            if (right - left < minimumWidth) {
                if (handle.isLeft) left = right - minimumWidth else right = left + minimumWidth
            }
            if (bottom - top < minimumHeight) {
                if (handle.isTop) top = bottom - minimumHeight else bottom = top + minimumHeight
            }
            element.resizeEditor(
                ((right - left) / scale).roundToInt(),
                ((bottom - top) / scale).roundToInt(),
            )
            val actualWidth = (element.width() * scale).roundToInt()
            val actualHeight = (element.height() * scale).roundToInt()
            val actualLeft = if (handle.isLeft) right - actualWidth else left
            val actualTop = if (handle.isTop) bottom - actualHeight else top
            element.position.moveToAbsoluteAllowingOverflow(
                actualLeft - element.layoutOffsetX,
                actualTop - element.layoutOffsetY,
                actualWidth,
                actualHeight,
            )
            snapper.confirmPosition(element, actualWidth, actualHeight)
        }

        protected override fun beforeEditorClose() {
            history.flushPending()
            ScreenTitleRenderer.endPositionEditing()
            SkysoftConfigGui.config().saveNow()
        }
    }
}

private fun drawResizeHandles(
    context: GuiGraphicsExtractor,
    x: Int,
    y: Int,
    width: Int,
    height: Int,
) {
    drawResizeHandle(context, x, y, horizontalDirection = -1, verticalDirection = -1)
    drawResizeHandle(context, x + width, y, horizontalDirection = 1, verticalDirection = -1)
    drawResizeHandle(context, x, y + height, horizontalDirection = -1, verticalDirection = 1)
    drawResizeHandle(context, x + width, y + height, horizontalDirection = 1, verticalDirection = 1)
}

private fun drawResizeHandle(
    context: GuiGraphicsExtractor,
    x: Int,
    y: Int,
    horizontalDirection: Int,
    verticalDirection: Int,
) {
    val left = if (horizontalDirection < 0) x - RESIZE_HANDLE_SIZE else x
    val top = if (verticalDirection < 0) y - RESIZE_HANDLE_SIZE else y
    context.fill(left, top, left + RESIZE_HANDLE_SIZE, top + RESIZE_HANDLE_SIZE, RESIZE_HANDLE_COLOR)
}

private fun hudEditorTooltipLines(
    active: HudEditorElement?,
    activeButton: InventoryButtonManager.ButtonPlacement?,
    gridEnabled: Boolean,
): List<String> = buildList {
    when {
        activeButton != null -> {
            val button = activeButton.button
            add("§cSkysoft Position Editor")
            add("§bInventory Button")
            add("§7Command: §e${button.command.takeIf { it.isNotBlank() } ?: "empty"}")
            add("§7Scale: §e${"%.2f".format(Locale.US, button.scale)}")
            add(inventoryButtonHoldKeyLine(button.requiredKey))
            add("§eLeft-click drag §7to move")
            add("§eDouble-click §7to select")
            add("§eArrow Keys §7to move one pixel")
            add("§eHold Shift §7to snap to other buttons")
            add("§eScroll-Wheel §7to resize")
            add(if (button.isUserCreated == true) "§eR §7to remove" else "§eR §7to reset")
        }

        active == null -> {
            add("§cSkysoft Position Editor")
            add("§7Hover a HUD element or inventory button to move it.")
            add("§eDouble-click §7to select")
            add("§eLeft-click drag §7to move")
            add("§eScroll §7to resize")
        }

        else -> {
            add("§cSkysoft Position Editor")
            add("§b${active.label}")
            val details = active.editorDetailsLines()
            if (details != null) {
                addAll(details)
            } else {
                add(
                    if (active.canScale) {
                        "§7x: §e${active.position.x}§7, y: §e${active.position.y}§7, scale: §e${
                            "%.2f".format(Locale.US, active.position.scale)
                        }"
                    } else {
                        "§7x: §e${active.position.x}§7, y: §e${active.position.y}"
                    },
                )
            }
            addAll(active.editorActionLines() ?: defaultHudEditorActionLines(active))
        }
    }
    addAll(editorGlobalTooltipLines(gridEnabled))
}

private fun defaultHudEditorActionLines(element: HudEditorElement): List<String> = buildList {
    if (element.canMove) {
        add("§eLeft-click drag §7to move")
        add("§eDouble-click §7to select")
        add("§eArrow Keys §7to move one pixel")
    }
    if (element.canResizeWidth || element.canResizeHeight) add("§eDrag outside corner handles §7to resize")
    if (element.canMove || element.canResizeWidth || element.canResizeHeight) add("§eHold Shift §7to snap")
    add("§eRight-click §7to open settings")
    if (element.canScale) add("§eScroll-Wheel §7to resize")
    add("§eR §7to reset")
}

private fun editorGlobalTooltipLines(gridEnabled: Boolean): List<String> = listOf(
    if (gridEnabled) "§eG §7to hide the snapping grid" else "§eG §7to show the snapping grid",
    "§eCtrl+Z / Ctrl+Y §7to undo or redo",
)

private fun renderEditorGrid(context: GuiGraphicsExtractor, element: HudEditorElement?) {
    val window = Minecraft.getInstance().window
    val spacing = element?.editorGridSpacing ?: HUD_GRID_SPACING
    val scale = element?.position?.effectiveScale ?: 1f
    val width = element?.let { (it.width() * scale).roundToInt() } ?: 0
    val height = element?.let { (it.height() * scale).roundToInt() } ?: 0
    val xOrigin = element?.let { it.absoluteX(width) - it.position.x } ?: 0
    val yOrigin = element?.let { it.absoluteY(height) - it.position.y } ?: 0
    val color = if (spacing < HUD_GRID_SPACING) HUD_FINE_GRID_COLOR else HUD_GRID_COLOR
    for (x in xOrigin.mod(spacing)..window.guiScaledWidth step spacing) {
        context.fill(x, 0, x + 1, window.guiScaledHeight, color)
    }
    for (y in yOrigin.mod(spacing)..window.guiScaledHeight step spacing) {
        context.fill(0, y, window.guiScaledWidth, y + 1, color)
    }
}

private fun inventorySnapGuidesActive(
    buttonIndex: Int?,
    element: HudEditorElement?,
    editorScale: EditorGuiScale,
): Boolean = buttonIndex != null || element?.let(editorScale::usesInventoryCoordinates) == true

private fun inventoryButtonHoldKeyLine(requiredKey: Int?): String =
    if (requiredKey != null && requiredKey != GLFW.GLFW_KEY_UNKNOWN) {
        "§7Hold Key: §e${InputUtilities.bindingName(requiredKey)}"
    } else {
        "§7Hold Key: §eNone"
    }

private fun nudgeInventoryButton(
    delta: Point,
    index: Int,
    screen: AbstractContainerScreen<*>?,
    snapper: HudEditorSnapper,
): InputHandlingResult {
    val inventoryScreen = screen ?: return InputHandlingResult.IGNORED
    val placement = InventoryButtonManager.placements(inventoryScreen, includeInactive = true)
        .firstOrNull { it.index == index }
        ?: return InputHandlingResult.IGNORED
    InventoryButtonManager.moveButton(
        inventoryScreen,
        index,
        placement.bounds.x + delta.x,
        placement.bounds.y + delta.y,
    )
    snapper.clear()
    return InputHandlingResult.CONSUMED
}

private fun renderSnapGuides(
    context: GuiGraphicsExtractor,
    snapper: HudEditorSnapper,
    active: Boolean,
) {
    if (!active) return
    if (!Minecraft.getInstance().hasShiftDown()) return
    val guides = snapper.guides()
    for (guide in guides) {
        if (guide.axis == HudSnapAxis.HORIZONTAL) {
            context.fill(
                guide.coordinate - SNAP_GUIDE_OUTLINE_WIDTH,
                guide.start,
                guide.coordinate + SNAP_GUIDE_OUTLINE_WIDTH + 1,
                guide.end + 1,
                SNAP_GUIDE_OUTLINE_COLOR,
            )
            context.fill(
                guide.coordinate,
                guide.start,
                guide.coordinate + 1,
                guide.end + 1,
                SNAP_GUIDE_COLOR,
            )
        } else {
            context.fill(
                guide.start,
                guide.coordinate - SNAP_GUIDE_OUTLINE_WIDTH,
                guide.end + 1,
                guide.coordinate + SNAP_GUIDE_OUTLINE_WIDTH + 1,
                SNAP_GUIDE_OUTLINE_COLOR,
            )
            context.fill(
                guide.start,
                guide.coordinate,
                guide.end + 1,
                guide.coordinate + 1,
                SNAP_GUIDE_COLOR,
            )
        }
    }
    guides.map(HudSnapGuide::targetBounds).distinct().forEach { bounds ->
        context.fill(bounds.left - 1, bounds.top - 1, bounds.right + 1, bounds.top, SNAP_TARGET_OUTLINE_COLOR)
        context.fill(bounds.left - 1, bounds.bottom, bounds.right + 1, bounds.bottom + 1, SNAP_TARGET_OUTLINE_COLOR)
        context.fill(bounds.left - 1, bounds.top, bounds.left, bounds.bottom, SNAP_TARGET_OUTLINE_COLOR)
        context.fill(bounds.right, bounds.top, bounds.right + 1, bounds.bottom, SNAP_TARGET_OUTLINE_COLOR)
    }
}

private class HudEditorSnapper(
    private val elements: List<HudEditorElement>,
    private val editorScale: EditorGuiScale,
) {
    private var horizontalLock: HudSnapLock? = null
    private var verticalLock: HudSnapLock? = null
    private var movingBounds: HudSnapBounds? = null

    var gridEnabled = false

    fun snapPosition(
        element: HudEditorElement,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
    ): HudSnappedPosition {
        val gridPosition = HudSnappedPosition(
            hudGridTarget(
                x,
                element.absoluteX(width),
                element.position.x,
                element.editorGridSpacing,
                gridEnabled,
            ),
            hudGridTarget(
                y,
                element.absoluteY(height),
                element.position.y,
                element.editorGridSpacing,
                gridEnabled,
            ),
        )
        return snapPosition(x, y, width, height, gridPosition) { axis -> targets(element, axis) }
    }

    fun snapPosition(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        targets: List<Rect>,
    ): HudSnappedPosition = snapPosition(
        x,
        y,
        width,
        height,
        HudSnappedPosition(
            hudGridCoordinate(x, HUD_GRID_SPACING, gridEnabled),
            hudGridCoordinate(y, HUD_GRID_SPACING, gridEnabled),
        ),
    ) { axis ->
        targets.flatMap { target ->
            targetPoints(
                HudSnapBounds(target.x, target.y, target.x + target.width, target.y + target.height),
                axis,
            )
        }
    }

    private fun snapPosition(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        gridPosition: HudSnappedPosition,
        targetProvider: (HudSnapAxis) -> List<HudSnapTargetPoint>,
    ): HudSnappedPosition {
        if (!Minecraft.getInstance().hasShiftDown()) {
            clear()
            return gridPosition
        }
        val rawBounds = HudSnapBounds(x, y, x + width, y + height)
        val horizontalOffset = snapAxis(
            HudSnapAxis.HORIZONTAL,
            axisPoints(x, width),
            rawBounds,
            matchingAnchorsOnly = true,
            targets = targetProvider(HudSnapAxis.HORIZONTAL),
        )
        val verticalOffset = snapAxis(
            HudSnapAxis.VERTICAL,
            axisPoints(y, height),
            rawBounds,
            matchingAnchorsOnly = true,
            targets = targetProvider(HudSnapAxis.VERTICAL),
        )
        return HudSnappedPosition(
            if (horizontalLock != null) x + horizontalOffset else gridPosition.x,
            if (verticalLock != null) y + verticalOffset else gridPosition.y,
        )
    }

    fun snapResizeCoordinate(
        element: HudEditorElement,
        value: Int,
        axis: HudSnapAxis,
        anchor: HudSnapAnchor,
    ): Int {
        val elementBounds = bounds(element)
        val gridOrigin = when (axis) {
            HudSnapAxis.HORIZONTAL -> elementBounds.left - element.position.x
            HudSnapAxis.VERTICAL -> elementBounds.top - element.position.y
        }
        val gridValue = gridOrigin + hudGridCoordinate(
            value - gridOrigin,
            element.editorGridSpacing,
            gridEnabled,
        )
        if (!Minecraft.getInstance().hasShiftDown()) {
            clear(axis)
            return gridValue
        }
        val offset = snapAxis(
            axis,
            listOf(HudSnapPoint(anchor, value)),
            elementBounds,
            matchingAnchorsOnly = false,
            targets = targets(element, axis),
        )
        return if (lock(axis) != null) value + offset else gridValue
    }

    fun guides(): List<HudSnapGuide> {
        val currentBounds = movingBounds ?: return emptyList()
        return buildList {
            horizontalLock?.let { add(guide(HudSnapAxis.HORIZONTAL, it, currentBounds)) }
            verticalLock?.let { add(guide(HudSnapAxis.VERTICAL, it, currentBounds)) }
        }
    }

    fun confirmPosition(element: HudEditorElement, width: Int, height: Int) {
        val left = element.absoluteX(width)
        val top = element.absoluteY(height)
        confirmPosition(
            HudSnapBounds(
                left = left,
                top = top,
                right = left + width,
                bottom = top + height,
            ),
        )
    }

    fun confirmPosition(bounds: Rect) {
        confirmPosition(
            HudSnapBounds(
                left = bounds.x,
                top = bounds.y,
                right = bounds.x + bounds.width,
                bottom = bounds.y + bounds.height,
            ),
        )
    }

    private fun confirmPosition(confirmedBounds: HudSnapBounds) {
        movingBounds = confirmedBounds
        confirmAxis(
            HudSnapAxis.HORIZONTAL,
            axisPoints(confirmedBounds.left, confirmedBounds.right - confirmedBounds.left),
        )
        confirmAxis(
            HudSnapAxis.VERTICAL,
            axisPoints(confirmedBounds.top, confirmedBounds.bottom - confirmedBounds.top),
        )
    }

    fun clear() {
        horizontalLock = null
        verticalLock = null
        movingBounds = null
    }

    fun clear(axis: HudSnapAxis) {
        setLock(axis, null)
    }

    private fun snapAxis(
        axis: HudSnapAxis,
        movingPoints: List<HudSnapPoint>,
        movingBounds: HudSnapBounds,
        matchingAnchorsOnly: Boolean,
        targets: List<HudSnapTargetPoint>,
    ): Int {
        val currentLock = lock(axis)
        if (currentLock != null) {
            val movingPoint = movingPoints.firstOrNull { it.anchor == currentLock.movingAnchor }
            if (movingPoint != null) {
                val offset = currentLock.targetCoordinate - movingPoint.coordinate
                if (kotlin.math.abs(offset) <= SNAP_RELEASE_DISTANCE) return offset
            }
            setLock(axis, null)
        }

        val candidate = targets
            .asSequence()
            .flatMap { target ->
                movingPoints.asSequence().mapNotNull { moving ->
                    candidate(moving, target, axis, movingBounds, matchingAnchorsOnly)
                }
            }
            .filter { it.distance <= SNAP_ACQUIRE_DISTANCE }
            .minWithOrNull(
                compareBy<HudSnapCandidate> { it.distance }
                    .thenBy { it.perpendicularDistance }
                    .thenBy { it.priority },
            )
            ?: return 0
        setLock(
            axis,
            HudSnapLock(
                movingAnchor = candidate.movingAnchor,
                targetCoordinate = candidate.targetCoordinate,
                targetBounds = candidate.targetBounds,
                relation = candidate.relation,
            ),
        )
        return candidate.targetCoordinate - candidate.movingCoordinate
    }

    private fun targets(element: HudEditorElement, axis: HudSnapAxis): List<HudSnapTargetPoint> {
        val values = mutableListOf<HudSnapTargetPoint>()
        elements
            .filter {
                it !== element &&
                    !it.isInSnapGroupWith(element) &&
                    editorScale.usesInventoryCoordinates(it) == editorScale.usesInventoryCoordinates(element)
            }
            .forEach { target ->
                values += targetPoints(bounds(target), axis)
            }
        return values
    }

    private fun candidate(
        moving: HudSnapPoint,
        target: HudSnapTargetPoint,
        axis: HudSnapAxis,
        movingBounds: HudSnapBounds,
        matchingAnchorsOnly: Boolean,
    ): HudSnapCandidate? {
        val isAdjacent = moving.anchor.isAdjacentTo(target.anchor)
        val priority = when {
            moving.anchor == HudSnapAnchor.CENTER && target.anchor == HudSnapAnchor.CENTER ->
                CENTER_SNAP_PRIORITY
            moving.anchor == target.anchor -> MATCHING_EDGE_SNAP_PRIORITY
            isAdjacent -> ADJACENT_EDGE_SNAP_PRIORITY
            matchingAnchorsOnly -> return null
            else -> CROSS_ANCHOR_SNAP_PRIORITY
        }
        return HudSnapCandidate(
            movingAnchor = moving.anchor,
            movingCoordinate = moving.coordinate,
            targetCoordinate = target.coordinate,
            distance = kotlin.math.abs(target.coordinate - moving.coordinate),
            priority = priority,
            perpendicularDistance = movingBounds.perpendicularDistance(target.bounds, axis),
            targetBounds = target.bounds,
            relation = if (isAdjacent) HudSnapRelation.ADJACENCY else HudSnapRelation.ALIGNMENT,
        )
    }

    private fun axisPoints(
        start: Int,
        length: Int,
    ): List<HudSnapPoint> = listOf(
        HudSnapPoint(HudSnapAnchor.START, start),
        HudSnapPoint(HudSnapAnchor.CENTER, start + length / 2),
        HudSnapPoint(HudSnapAnchor.END, start + length),
    )

    private fun targetPoints(bounds: HudSnapBounds, axis: HudSnapAxis): List<HudSnapTargetPoint> {
        val start = if (axis == HudSnapAxis.HORIZONTAL) bounds.left else bounds.top
        val end = if (axis == HudSnapAxis.HORIZONTAL) bounds.right else bounds.bottom
        return listOf(
            HudSnapTargetPoint(HudSnapAnchor.START, start, bounds),
            HudSnapTargetPoint(HudSnapAnchor.CENTER, (start + end) / 2, bounds),
            HudSnapTargetPoint(HudSnapAnchor.END, end, bounds),
        )
    }

    private fun bounds(element: HudEditorElement): HudSnapBounds {
        val scale = element.position.effectiveScale
        val width = (element.width() * scale).roundToInt()
        val height = (element.height() * scale).roundToInt()
        val left = element.absoluteX(width)
        val top = element.absoluteY(height)
        return HudSnapBounds(left, top, left + width, top + height)
    }

    private fun guide(axis: HudSnapAxis, lock: HudSnapLock, moving: HudSnapBounds): HudSnapGuide {
        val movingCenter = moving.perpendicularCenter(axis)
        val targetCenter = lock.targetBounds.perpendicularCenter(axis)
        val useFullBounds = lock.relation == HudSnapRelation.ADJACENCY || movingCenter == targetCenter
        val start = if (useFullBounds) {
            minOf(moving.perpendicularStart(axis), lock.targetBounds.perpendicularStart(axis))
        } else {
            minOf(movingCenter, targetCenter)
        }
        val end = if (useFullBounds) {
            maxOf(moving.perpendicularEnd(axis), lock.targetBounds.perpendicularEnd(axis))
        } else {
            maxOf(movingCenter, targetCenter)
        }
        return HudSnapGuide(axis, lock.targetCoordinate, start, end, lock.targetBounds)
    }

    private fun confirmAxis(axis: HudSnapAxis, points: List<HudSnapPoint>) {
        val currentLock = lock(axis) ?: return
        val actualCoordinate = points.firstOrNull { it.anchor == currentLock.movingAnchor }?.coordinate
        if (actualCoordinate != currentLock.targetCoordinate) clear(axis)
    }

    private fun lock(axis: HudSnapAxis): HudSnapLock? = when (axis) {
        HudSnapAxis.HORIZONTAL -> horizontalLock
        HudSnapAxis.VERTICAL -> verticalLock
    }

    private fun setLock(axis: HudSnapAxis, lock: HudSnapLock?) {
        when (axis) {
            HudSnapAxis.HORIZONTAL -> horizontalLock = lock
            HudSnapAxis.VERTICAL -> verticalLock = lock
        }
    }
}

private fun HudEditorElement.isInSnapGroupWith(other: HudEditorElement): Boolean =
    snapGroup != null && snapGroup == other.snapGroup

private fun HudSnapAnchor.isAdjacentTo(other: HudSnapAnchor): Boolean =
    this == HudSnapAnchor.START && other == HudSnapAnchor.END ||
        this == HudSnapAnchor.END && other == HudSnapAnchor.START

private enum class HudSnapAxis {
    HORIZONTAL,
    VERTICAL,
}

private enum class HudSnapAnchor {
    START,
    CENTER,
    END,
}

private enum class HudSnapRelation {
    ALIGNMENT,
    ADJACENCY,
}

private data class HudSnapPoint(val anchor: HudSnapAnchor, val coordinate: Int)

private data class HudSnapTargetPoint(
    val anchor: HudSnapAnchor,
    val coordinate: Int,
    val bounds: HudSnapBounds,
)

private data class HudSnapCandidate(
    val movingAnchor: HudSnapAnchor,
    val movingCoordinate: Int,
    val targetCoordinate: Int,
    val distance: Int,
    val priority: Int,
    val perpendicularDistance: Int,
    val targetBounds: HudSnapBounds,
    val relation: HudSnapRelation,
)

private data class HudSnapLock(
    val movingAnchor: HudSnapAnchor,
    val targetCoordinate: Int,
    val targetBounds: HudSnapBounds,
    val relation: HudSnapRelation,
)

private data class HudSnapGuide(
    val axis: HudSnapAxis,
    val coordinate: Int,
    val start: Int,
    val end: Int,
    val targetBounds: HudSnapBounds,
)

private data class HudSnappedPosition(val x: Int, val y: Int)

private data class HudSnapBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    fun perpendicularStart(axis: HudSnapAxis): Int =
        if (axis == HudSnapAxis.HORIZONTAL) top else left

    fun perpendicularEnd(axis: HudSnapAxis): Int =
        if (axis == HudSnapAxis.HORIZONTAL) bottom else right

    fun perpendicularCenter(axis: HudSnapAxis): Int =
        (perpendicularStart(axis) + perpendicularEnd(axis)) / 2

    fun perpendicularDistance(other: HudSnapBounds, axis: HudSnapAxis): Int {
        val start = perpendicularStart(axis)
        val end = perpendicularEnd(axis)
        val otherStart = other.perpendicularStart(axis)
        val otherEnd = other.perpendicularEnd(axis)
        return when {
            end < otherStart -> otherStart - end
            otherEnd < start -> start - otherEnd
            else -> 0
        }
    }
}

private class EditorGuiScale(private val hasInventoryScreen: Boolean) {
    fun normalRenderScale(): Float = normalGuiScale() / activeInventoryGuiScale().toFloat()

    fun usesInventoryCoordinates(element: HudEditorElement): Boolean =
        hasInventoryScreen && element.usesInventoryScale

    fun toNormalGuiX(mouseX: Int): Int =
        (mouseX * activeInventoryGuiScale() / normalGuiScale().toFloat()).roundToInt()

    fun toNormalGuiY(mouseY: Int): Int =
        (mouseY * activeInventoryGuiScale() / normalGuiScale().toFloat()).roundToInt()

    fun elementMouseX(element: HudEditorElement, mouseX: Int): Int =
        if (usesInventoryCoordinates(element)) mouseX else toNormalGuiX(mouseX)

    fun elementMouseY(element: HudEditorElement, mouseY: Int): Int =
        if (usesInventoryCoordinates(element)) mouseY else toNormalGuiY(mouseY)

    fun elementScreenBounds(element: HudEditorElement, bounds: Rect): Rect {
        if (usesInventoryCoordinates(element)) return bounds
        val scale = normalRenderScale()
        val left = (bounds.x * scale).roundToInt()
        val top = (bounds.y * scale).roundToInt()
        val right = ((bounds.x + bounds.width) * scale).roundToInt()
        val bottom = ((bounds.y + bounds.height) * scale).roundToInt()
        return Rect(left, top, right - left, bottom - top)
    }

    fun <T> withElementGuiScale(element: HudEditorElement, block: () -> T): T =
        if (usesInventoryCoordinates(element)) withInventoryGuiScale(block) else withNormalGuiScale(block)

    fun <T> withNormalGuiScale(block: () -> T): T {
        return withGuiScale(normalGuiScale(), block)
    }

    private fun <T> withInventoryGuiScale(block: () -> T): T =
        withGuiScale(activeInventoryGuiScale(), block)

    private fun <T> withGuiScale(scale: Int, block: () -> T): T {
        val window = Minecraft.getInstance().window
        val previousScale = window.guiScale
        if (previousScale == scale) return block()
        window.setGuiScale(scale)
        try {
            return block()
        } finally {
            window.setGuiScale(previousScale)
        }
    }

    private fun normalGuiScale(): Int {
        val minecraft = Minecraft.getInstance()
        val configuredScale = minecraft.options.guiScale().get()
        return minecraft.window.calculateScale(configuredScale, minecraft.isEnforceUnicode).coerceAtLeast(1)
    }

    private fun activeInventoryGuiScale(): Int {
        val minecraft = Minecraft.getInstance()
        val inventoryConfig = SkysoftConfigGui.config().gui.inventoryScreen
        if (!hasInventoryScreen || !shouldUseConfiguredInventoryScale(
                inventoryConfig.separateInventoryGuiScale,
                inventoryConfig.settings.isInventoryGuiScaleStorageOnly,
                isStorageOverlayActive = false,
            )
        ) return normalGuiScale()
        return minecraft.window.calculateScale(
            inventoryConfig.settings.inventoryGuiScale.coerceAtLeast(0),
            minecraft.isEnforceUnicode,
        ).coerceAtLeast(1)
    }
}

private fun HudEditorElement.isHovered(mouseX: Int, mouseY: Int): Boolean {
    val scaledWidth = (width() * position.scale).roundToInt()
    val scaledHeight = (height() * position.scale).roundToInt()
    val x = absoluteX(scaledWidth)
    val y = absoluteY(scaledHeight)
    return mouseX in (x - editorLeftPadding - HUD_EDITOR_BORDER)..(x + scaledWidth + HUD_EDITOR_BORDER) &&
        mouseY in (y - HUD_EDITOR_BORDER)..(y + scaledHeight + HUD_EDITOR_BORDER)
}

private fun findResizeHandle(
    element: HudEditorElement,
    localX: Int,
    localY: Int,
    width: Int,
    height: Int,
): HudResizeHandle? {
    if (!element.canResizeWidth && !element.canResizeHeight) return null
    val horizontal = when {
        localX in -RESIZE_HANDLE_HITBOX until 0 -> true
        localX in width until width + RESIZE_HANDLE_HITBOX -> false
        else -> return null
    }
    val vertical = when {
        localY in -RESIZE_HANDLE_HITBOX until 0 -> true
        localY in height until height + RESIZE_HANDLE_HITBOX -> false
        else -> return null
    }
    return HudResizeHandle(isLeft = horizontal, isTop = vertical)
}

private const val HUD_EDITOR_BORDER = 2
private const val RESIZE_HANDLE_SIZE = 4
private const val RESIZE_HANDLE_HITBOX = 6
private const val RESIZE_HANDLE_COLOR = 0xFFF0F0F0.toInt()
private const val HUD_GRID_SPACING = HUD_EDITOR_GRID_SPACING
private const val HUD_GRID_COLOR = 0x2855FFFF
private const val HUD_FINE_GRID_COLOR = 0x1055FFFF
private const val SNAP_ACQUIRE_DISTANCE = 8
private const val SNAP_RELEASE_DISTANCE = 12
private const val SNAP_GUIDE_OUTLINE_WIDTH = 1
private const val SNAP_GUIDE_COLOR = 0xFF55FFFF.toInt()
private const val SNAP_GUIDE_OUTLINE_COLOR = 0xB0000000.toInt()
private const val SNAP_TARGET_OUTLINE_COLOR = 0xD055FFFF.toInt()
private const val CENTER_SNAP_PRIORITY = 0
private const val MATCHING_EDGE_SNAP_PRIORITY = 1
private const val ADJACENT_EDGE_SNAP_PRIORITY = 2
private const val CROSS_ANCHOR_SNAP_PRIORITY = 3

private data class HudResizeHandle(val isLeft: Boolean, val isTop: Boolean)
