package com.skysoft.gui

import com.skysoft.config.SkysoftConfigGui
import com.skysoft.features.inventory.InventoryButtonEditorActions
import com.skysoft.features.inventory.InventoryButtonManager
import com.skysoft.features.inventory.InventoryButtonResetShortcutResult
import com.skysoft.gui.scale.InventoryScaledScreen
import com.skysoft.gui.tooltip.SkysoftNativeTooltip
import com.skysoft.gui.tooltip.TooltipScrollExcludedScreen
import com.skysoft.utils.MinecraftClient
import com.skysoft.utils.input.InputHandlingResult
import com.skysoft.utils.render.ScreenTitleRenderer
import java.util.Locale
import kotlin.math.roundToInt
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW

object SkysoftHudEditor {
    private const val BORDER = 2
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
        Screen(Component.literal("Skysoft Position Editor")),
        InventoryScaledScreen,
        TooltipScrollExcludedScreen {
        override fun usesInventoryScale(): Boolean = oldScreen != null

        private var grabbedElement: HudEditorElement? = null
        private var grabbedOffsetX = 0
        private var grabbedOffsetY = 0
        private var grabbedWidth = 0
        private var grabbedHeight = 0
        private var hoveredElement: HudEditorElement? = null
        private var grabbedInventoryButtonIndex: Int? = null
        private var grabbedInventoryButtonOffsetX = 0
        private var grabbedInventoryButtonOffsetY = 0
        private var hoveredInventoryButtonIndex: Int? = null
        private var oldScreenWidth = -1
        private var oldScreenHeight = -1
        private val editorScale = EditorGuiScale(oldScreen != null)

        override fun extractRenderState(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
            super.extractRenderState(context, mouseX, mouseY, delta)
            renderEditor(context, mouseX, mouseY, delta)
        }

        private fun renderEditor(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
            val minecraft = Minecraft.getInstance()
            if (oldScreen == null) {
                context.fill(0, 0, minecraft.window.guiScaledWidth, minecraft.window.guiScaledHeight, EDITOR_BACKGROUND)
            } else {
                renderOldScreen(context, mouseX, mouseY, delta)
            }

            val hoveredButton = renderInventoryButtons(context, mouseX, mouseY)
            val elements = HudEditorRegistry.visibleElements(oldScreen != null)
            val hovered = elements.lastOrNull { isElementHovered(it, mouseX, mouseY) }
            val activeHoveredButton = hoveredButton?.takeIf { hovered == null }
            hoveredInventoryButtonIndex = activeHoveredButton?.index
            hoveredElement = hovered
            elements.filter(editorScale::usesInventoryCoordinates).forEach { element ->
                renderElement(context, element, element == hovered || element == grabbedElement)
            }
            context.pose().pushMatrix()
            val tooltipLines = try {
                context.pose().scale(editorScale.normalRenderScale(), editorScale.normalRenderScale())
                editorScale.withNormalGuiScale {
                    for (element in elements.filterNot(editorScale::usesInventoryCoordinates)) {
                        renderElement(context, element, element == hovered || element == grabbedElement)
                    }

                    val active = grabbedElement ?: hovered
                    when {
                        grabbedInventoryButtonIndex != null || activeHoveredButton != null -> {
                            val placement = activeHoveredButton ?: inventoryButtonPlacement(grabbedInventoryButtonIndex)
                            val button = placement?.button
                            listOf(
                                "§cSkysoft Position Editor",
                                "§bInventory Button",
                                "§7Command: §e${button?.command?.takeIf { it.isNotBlank() } ?: "empty"}",
                                "§7Scale: §e${"%.2f".format(Locale.US, button?.scale ?: 1f)}",
                                "§eLeft-click drag §7to move",
                                "§eScroll-Wheel §7to resize",
                                if (button?.isUserCreated == true) "§eR §7to remove" else "§eR §7to reset",
                            )
                        }

                        active == null -> listOf(
                            "§cSkysoft Position Editor",
                            "§7Hover a HUD element or inventory button to move it.",
                            "§eLeft-click drag §7to move",
                            "§eScroll §7to resize",
                        )

                        else -> buildList {
                            add("§cSkysoft Position Editor")
                            add("§b${active.label}")
                            val customLines = active.editorTooltipLines()
                            if (customLines != null) {
                                addAll(customLines)
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
                                if (active.canMove) add("§eLeft-click drag §7to move")
                                add("§eRight-click §7to open settings")
                                if (active.canScale) add("§eScroll-Wheel §7to resize")
                                add("§eR §7to reset")
                            }
                        }
                    }
                }
            } finally {
                context.pose().popMatrix()
            }
            SkysoftNativeTooltip.setForNextFrame(context, tooltipLines, mouseX, mouseY)
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

        private fun elementAt(mouseX: Int, mouseY: Int): HudEditorElement? =
            HudEditorRegistry.visibleElements(oldScreen != null).lastOrNull { isElementHovered(it, mouseX, mouseY) }

        private fun renderInventoryButtons(
            context: GuiGraphicsExtractor,
            mouseX: Int,
            mouseY: Int,
        ): InventoryButtonManager.ButtonPlacement? {
            if (!InventoryButtonManager.isAvailableInCurrentLocation()) return null
            val placements = oldScreen?.let { InventoryButtonManager.placements(it, includeInactive = true) }.orEmpty()
            val hovered = placements.lastOrNull { it.bounds.contains(mouseX, mouseY) }
            for (placement in placements) {
                val selected = placement.index == grabbedInventoryButtonIndex || placement == hovered
                context.fill(
                    placement.bounds.x - BORDER,
                    placement.bounds.y - BORDER,
                    placement.bounds.x + placement.bounds.width + BORDER,
                    placement.bounds.y + placement.bounds.height + BORDER,
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
            return hovered
        }

        private fun inventoryButtonPlacement(index: Int?): InventoryButtonManager.ButtonPlacement? {
            val buttonIndex = index ?: return null
            if (!InventoryButtonManager.isAvailableInCurrentLocation()) return null
            return oldScreen?.let { InventoryButtonManager.placements(it, includeInactive = true) }
                ?.firstOrNull { it.index == buttonIndex }
        }

        private fun renderElement(context: GuiGraphicsExtractor, element: HudEditorElement, selected: Boolean) {
            val position = element.position
            val scaledWidth = (element.width() * position.scale).roundToInt()
            val scaledHeight = (element.height() * position.scale).roundToInt()
            val x = element.absoluteX(scaledWidth)
            val y = element.absoluteY(scaledHeight)
            val color = if (selected) PANEL_HOVER else PANEL_BACKGROUND
            if (element.hasEditorBackground) {
                context.fill(x - BORDER, y - BORDER, x + scaledWidth + BORDER, y + scaledHeight + BORDER, color)
            } else {
                drawEditorOutline(context, x, y, scaledWidth, scaledHeight, color)
            }
            context.pose().pushMatrix()
            context.pose().translate(x.toFloat(), y.toFloat())
            context.pose().scale(position.scale, position.scale)
            element.renderEditorDummy(context)
            context.pose().popMatrix()
        }

        private fun drawEditorOutline(
            context: GuiGraphicsExtractor,
            x: Int,
            y: Int,
            width: Int,
            height: Int,
            color: Int,
        ) {
            context.fill(x - BORDER, y - BORDER, x + width + BORDER, y, color)
            context.fill(x - BORDER, y + height, x + width + BORDER, y + height + BORDER, color)
            context.fill(x - BORDER, y, x, y + height, color)
            context.fill(x + width, y, x + width + BORDER, y + height, color)
        }

        override fun mouseClicked(click: MouseButtonEvent, doubled: Boolean): Boolean {
            val mouseX = click.x().toInt()
            val mouseY = click.y().toInt()
            return when (click.button()) {
                GLFW.GLFW_MOUSE_BUTTON_LEFT -> {
                    val element = elementAt(mouseX, mouseY)
                    val inventoryButton = if (element == null && InventoryButtonManager.isAvailableInCurrentLocation()) {
                        oldScreen?.let { InventoryButtonManager.placements(it, includeInactive = true) }
                            ?.lastOrNull { it.bounds.contains(mouseX, mouseY) }
                    } else {
                        null
                    }
                    if (inventoryButton != null) {
                        grabbedInventoryButtonIndex = inventoryButton.index
                        grabbedInventoryButtonOffsetX = mouseX - inventoryButton.bounds.x
                        grabbedInventoryButtonOffsetY = mouseY - inventoryButton.bounds.y
                        grabbedElement = null
                        true
                    } else {
                        grabbedElement = element
                        if (element != null) {
                            editorScale.withElementGuiScale(element) {
                                grabbedWidth = (element.width() * element.position.scale).roundToInt()
                                grabbedHeight = (element.height() * element.position.scale).roundToInt()
                                grabbedOffsetX = editorScale.elementMouseX(element, mouseX) -
                                    element.absoluteX(grabbedWidth)
                                grabbedOffsetY = editorScale.elementMouseY(element, mouseY) -
                                    element.absoluteY(grabbedHeight)
                                element.beginEditorDrag(
                                    grabbedOffsetX,
                                    grabbedOffsetY,
                                    grabbedWidth,
                                    grabbedHeight,
                                )
                            }
                            true
                        } else {
                            super.mouseClicked(click, doubled)
                        }
                    }
                }

                GLFW.GLFW_MOUSE_BUTTON_RIGHT -> {
                    val element = elementAt(mouseX, mouseY)
                    if (element == null) {
                        super.mouseClicked(click, doubled)
                    } else {
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
                    InventoryButtonManager.moveButton(
                        screen,
                        index,
                        click.x().toInt() - grabbedInventoryButtonOffsetX,
                        click.y().toInt() - grabbedInventoryButtonOffsetY,
                    )
                }
                return true
            }
            val element = grabbedElement ?: return super.mouseDragged(click, dragX, dragY)
            val elementMouseX = editorScale.elementMouseX(element, click.x().toInt())
            val elementMouseY = editorScale.elementMouseY(element, click.y().toInt())
            editorScale.withElementGuiScale(element) {
                val width = grabbedWidth.takeIf { it > 0 } ?: (element.width() * element.position.scale).roundToInt()
                val height = grabbedHeight.takeIf { it > 0 } ?: (element.height() * element.position.scale).roundToInt()
                val targetX = elementMouseX - grabbedOffsetX
                val targetY = elementMouseY - grabbedOffsetY
                val deltaX = targetX - element.absoluteX(width)
                val deltaY = targetY - element.absoluteY(height)
                if (element.applyEditorDrag(deltaX, deltaY) == InputHandlingResult.IGNORED && element.canMove) {
                    if (element.keepsInsideScreen) {
                        element.position.moveToAbsolute(targetX, targetY, width, height)
                    } else {
                        element.position.moveToAbsoluteAllowingOverflow(targetX, targetY, width, height)
                    }
                }
            }
            return true
        }

        override fun mouseReleased(click: MouseButtonEvent): Boolean {
            if (click.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                grabbedElement = null
                grabbedInventoryButtonIndex = null
                grabbedWidth = 0
                grabbedHeight = 0
            }
            return super.mouseReleased(click)
        }

        override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
            val element = grabbedElement ?: elementAt(mouseX.toInt(), mouseY.toInt())
            if (element != null) {
                if (element.applyEditorScroll(scrollY) == InputHandlingResult.CONSUMED) return true
                if (!element.canScale) return true
                editorScale.withElementGuiScale(element) {
                    val oldScale = element.position.scale
                    element.position.scale += if (scrollY > 0.0) SCALE_STEP else -SCALE_STEP
                    val oldWidth = (element.width() * oldScale).roundToInt()
                    val oldHeight = (element.height() * oldScale).roundToInt()
                    val newWidth = (element.width() * element.position.scale).roundToInt()
                    val newHeight = (element.height() * element.position.scale).roundToInt()
                    val oldX = element.position.getAbsX0AllowingOverflow(oldWidth)
                    val oldY = element.position.getAbsY0AllowingOverflow(oldHeight)
                    element.position.moveToAbsoluteAllowingOverflow(oldX, oldY, newWidth, newHeight)
                }
                return true
            }

            val buttonIndex = grabbedInventoryButtonIndex ?: if (InventoryButtonManager.isAvailableInCurrentLocation()) {
                oldScreen?.let { InventoryButtonManager.placements(it, includeInactive = true) }
                    ?.lastOrNull { it.bounds.contains(mouseX.toInt(), mouseY.toInt()) }
                    ?.index
            } else {
                null
            }
            return if (buttonIndex != null &&
                InventoryButtonEditorActions.changeButtonScale(buttonIndex, scrollY) == InputHandlingResult.CONSUMED
            ) {
                true
            } else {
                super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
            }
        }

        override fun keyPressed(event: KeyEvent): Boolean {
            if (event.key() == GLFW.GLFW_KEY_R) {
                val buttonIndex = grabbedInventoryButtonIndex ?: hoveredInventoryButtonIndex
                if (buttonIndex != null) {
                    if (InventoryButtonEditorActions.resetOrRemoveButton(buttonIndex) ==
                        InventoryButtonResetShortcutResult.REMOVED
                    ) {
                        grabbedInventoryButtonIndex = null
                        hoveredInventoryButtonIndex = null
                    }
                    return true
                }
                val element = grabbedElement ?: hoveredElement ?: return true
                element.resetEditorState()
                return true
            }
            return super.keyPressed(event)
        }

        override fun onClose() {
            ScreenTitleRenderer.endPositionEditing()
            SkysoftConfigGui.config().saveNow()
            if (oldScreen != null) {
                MinecraftClient.setScreen(oldScreen)
            } else {
                super.onClose()
            }
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
        if (!hasInventoryScreen || !inventoryConfig.separateInventoryGuiScale) return normalGuiScale()
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

private const val HUD_EDITOR_BORDER = 2
