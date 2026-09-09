package com.skysoft.gui

import com.skysoft.utils.renderables.withIsolatedPose
import com.skysoft.config.SkysoftConfigGui
import com.skysoft.features.inventory.InventoryButtonEditorActions
import com.skysoft.features.inventory.InventoryButtonManager
import com.skysoft.features.inventory.InventoryButtonResetShortcutResult
import com.skysoft.features.inventory.inventoryButtonEditorState
import com.skysoft.gui.scale.InventoryScaledScreen
import com.skysoft.gui.scale.HudEditorGuiScale
import com.skysoft.gui.tooltip.SkysoftNativeTooltip
import com.skysoft.gui.tooltip.TooltipScrollExcludedScreen
import com.skysoft.utils.MinecraftClient
import com.skysoft.utils.gui.Point
import com.skysoft.utils.gui.Rect
import com.skysoft.utils.input.InputHandlingResult
import com.skysoft.utils.render.ScreenTitleRenderer
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

        private var elementDrag: HudEditorElementDrag? = null
        private val grabbedElement get() = elementDrag?.element
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
            renderEditor(context, mouseX, mouseY, delta)
        }

        private fun renderEditor(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
            refreshVisibleElements()
            history.flushIdleScroll()
            val minecraft = Minecraft.getInstance()
            if (oldScreen == null) {
                context.fill(0, 0, minecraft.window.guiScaledWidth, minecraft.window.guiScaledHeight, EDITOR_BACKGROUND)
            } else {
                renderOldScreen(context, mouseX, mouseY, delta)
            }

            val placements = inventoryButtonPlacements()
            val hoveredButton = placements.lastOrNull { it.bounds.contains(mouseX, mouseY) }
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
            val tooltipLines = context.withIsolatedPose {
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

            context.withIsolatedPose {
                screen.extractBackground(context, mouseX, mouseY, delta)
                screen.extractRenderState(context, mouseX, mouseY, delta)
            }
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
            context.withIsolatedPose {
                context.pose().translate(x.toFloat(), y.toFloat())
                context.pose().scale(position.scale, position.scale)
                element.renderEditor(context)
            }
            if (selected && (element.canResizeWidth || element.canResizeHeight)) {
                drawResizeHandles(context, x, y, scaledWidth, scaledHeight)
            }
        }

        override fun mouseClicked(click: MouseButtonEvent, doubled: Boolean): Boolean {
            refreshVisibleElements()
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
                        elementDrag = null
                        true
                    } else {
                        elementDrag = null
                        grabbedInventoryButtonIndex = null
                        grabbedState = element?.captureEditorState()
                        if (element != null) {
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
            refreshVisibleElements()
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
            val drag = elementDrag ?: return super.mouseDragged(click, dragX, dragY)
            val element = drag.element
            val elementMouseX = editorScale.elementMouseX(element, click.x().toInt())
            val elementMouseY = editorScale.elementMouseY(element, click.y().toInt())
            editorScale.withElementGuiScale(element) {
                drag.moveTo(elementMouseX, elementMouseY, snapper)
            }
            return true
        }

        override fun mouseReleased(click: MouseButtonEvent): Boolean {
            if (click.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) finishDrag()
            return super.mouseReleased(click)
        }

        private fun finishDrag() {
            val before = grabbedState
            val after = when {
                grabbedInventoryButtonIndex != null -> inventoryButtonEditorState()
                grabbedElement != null -> grabbedElement?.captureEditorState()
                else -> null
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
            refreshVisibleElements()
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

        protected override fun beforeEditorClose() {
            history.flushPending()
            ScreenTitleRenderer.endPositionEditing()
            SkysoftConfigGui.config().saveNow()
        }
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
    editorScale: HudEditorGuiScale,
): Boolean = buttonIndex != null || element?.let(editorScale::usesInventoryCoordinates) == true

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

private fun HudEditorElement.isHovered(mouseX: Int, mouseY: Int): Boolean {
    val scaledWidth = (width() * position.scale).roundToInt()
    val scaledHeight = (height() * position.scale).roundToInt()
    val x = absoluteX(scaledWidth)
    val y = absoluteY(scaledHeight)
    return mouseX in (x - editorLeftPadding - HUD_EDITOR_BORDER)..(x + scaledWidth + HUD_EDITOR_BORDER) &&
        mouseY in (y - HUD_EDITOR_BORDER)..(y + scaledHeight + HUD_EDITOR_BORDER)
}

private const val HUD_EDITOR_BORDER = 2
private const val RESIZE_HANDLE_SIZE = 4
private const val RESIZE_HANDLE_COLOR = 0xFFF0F0F0.toInt()
private const val HUD_GRID_SPACING = HUD_EDITOR_GRID_SPACING
private const val HUD_GRID_COLOR = 0x2855FFFF
private const val HUD_FINE_GRID_COLOR = 0x1055FFFF
private const val SNAP_GUIDE_OUTLINE_WIDTH = 1
private const val SNAP_GUIDE_COLOR = 0xFF55FFFF.toInt()
private const val SNAP_GUIDE_OUTLINE_COLOR = 0xB0000000.toInt()
private const val SNAP_TARGET_OUTLINE_COLOR = 0xD055FFFF.toInt()
