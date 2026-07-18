package com.skysoft.features.inventory

import com.skysoft.config.InventoryButtonConfig
import com.skysoft.config.InventoryButtonDefaults
import com.skysoft.config.SkysoftConfigGui
import com.skysoft.features.inventory.InventoryButtonManager.BUTTON_SIZE
import com.skysoft.features.inventory.InventoryButtonManager.IconCandidate
import com.skysoft.gui.scale.InventoryScaledScreen
import com.skysoft.gui.tooltip.SkysoftNativeTooltip
import com.skysoft.gui.tooltip.TooltipScrollExcludedScreen
import com.skysoft.utils.MinecraftClient
import com.skysoft.utils.SoundUtilities
import com.skysoft.utils.gui.PixelButtonRenderer
import com.skysoft.utils.gui.PixelButtonTone
import com.skysoft.utils.gui.Rect
import com.skysoft.utils.gui.TextFieldState
import com.skysoft.utils.input.InputHandlingResult
import com.skysoft.utils.render.LegacyTextRenderer
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW

object InventoryButtonEditorScreen {
    fun open() {
        MinecraftClient.setScreen(EditorScreen(MinecraftClient.screen()))
    }

    class EditorScreen(private val parent: Screen?) :
        Screen(Component.literal("Skysoft Inventory Buttons")),
        InventoryScaledScreen,
        TooltipScrollExcludedScreen {
        override fun inventoryScaleLimit(): Int = 2
        internal val config get() = SkysoftConfigGui.config().inventory.inventoryButtons
        internal val commandField = TextFieldState(maxLength = 128)
        internal val iconField = TextFieldState(maxLength = 64)
        internal var selectedIndex: Int? = null
        internal var resultScrollRow = 0
        internal var lastIconSearch: String? = null
        internal var cachedIconCandidates: List<IconCandidate> = emptyList()
        internal var lastInventoryLeft = 0
        internal var lastInventoryTop = 0
        internal var lastPreviewScale = 1f
        internal var lastPanelBounds: Rect? = null
        internal var lastResultsBounds: Rect? = null
        internal var lastPresetBounds: Rect? = null
        internal var lastResetBounds: Rect? = null
        internal var lastClearBounds: Rect? = null
        internal var lastDoneBounds: Rect? = null
        internal var hoveredIndex: Int? = null
        private var grabbedIndex: Int? = null
        private var grabbedOffsetX = 0
        private var grabbedOffsetY = 0

        override fun extractRenderState(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
            context.fill(0, 0, width, height, EditorColors.SCREEN_OVERLAY)
            EditorRenderer.renderInventoryPreview(this, context, mouseX, mouseY)
            EditorRenderer.renderSidePanel(this, context, mouseX, mouseY)
        }

        override fun mouseClicked(click: MouseButtonEvent, doubled: Boolean): Boolean {
            val mouseX = click.x().toInt()
            val mouseY = click.y().toInt()
            if (click.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return super.mouseClicked(click, doubled)
            grabbedIndex = null

            val previewMouseX = EditorRenderer.previewMouseX(this, mouseX)
            val previewMouseY = EditorRenderer.previewMouseY(this, mouseY)
            val placement = InventoryButtonManager.placements(
                left = 0,
                top = 0,
                imageWidth = InventoryPreview.WIDTH,
                imageHeight = InventoryPreview.HEIGHT,
                playerInventory = true,
                includeInactive = true,
            ).lastOrNull { it.bounds.contains(previewMouseX, previewMouseY) }
            val addButtonClicked = InventoryButtonLayout.editorAddButtonBounds.contains(previewMouseX, previewMouseY)
            val panelButton = selectedButton()?.takeIf {
                lastPanelBounds?.contains(mouseX, mouseY) == true
            }
            return when {
                lastPresetBounds?.contains(mouseX, mouseY) == true -> {
                    SoundUtilities.playClickSound()
                    InventoryButtonManager.applySkyBlockPreset()
                    InventoryButtonManager.clearIconCache()
                    selectedIndex = null
                    syncFieldsFromSelection()
                    true
                }
                lastResetBounds?.contains(mouseX, mouseY) == true -> {
                    SoundUtilities.playClickSound()
                    config.buttons = InventoryButtonDefaults.create()
                    InventoryButtonManager.clearIconCache()
                    selectedIndex = null
                    syncFieldsFromSelection()
                    true
                }
                addButtonClicked -> {
                    SoundUtilities.playClickSound()
                    selectedIndex = InventoryButtonEditorActions.addButtonSlot()
                    syncFieldsFromSelection()
                    true
                }
                panelButton != null -> {
                    handlePanelClick(panelButton, mouseX, mouseY)
                    true
                }
                placement != null -> {
                    grabbedIndex = placement.index
                    grabbedOffsetX = previewMouseX - placement.bounds.x
                    grabbedOffsetY = previewMouseY - placement.bounds.y
                    selectedIndex = placement.index
                    syncFieldsFromSelection()
                    true
                }
                else -> {
                    commandField.focused = false
                    iconField.focused = false
                    selectedIndex = null
                    true
                }
            }
        }

        override fun mouseDragged(click: MouseButtonEvent, dragX: Double, dragY: Double): Boolean {
            if (click.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return super.mouseDragged(click, dragX, dragY)
            val button = grabbedIndex?.let(config.buttons::getOrNull)
                ?: return super.mouseDragged(click, dragX, dragY)
            InventoryButtonLayout.moveButton(
                InventoryButtonCanvas(
                    Rect(0, 0, InventoryPreview.WIDTH, InventoryPreview.HEIGHT),
                    playerInventory = true,
                ),
                button,
                EditorRenderer.previewMouseX(this, click.x().toInt()) - grabbedOffsetX,
                EditorRenderer.previewMouseY(this, click.y().toInt()) - grabbedOffsetY,
            )
            return true
        }

        override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
            val results = lastResultsBounds
            if (results != null && results.contains(mouseX.toInt(), mouseY.toInt())) {
                val maxScroll = maxResultScrollRow()
                resultScrollRow = (resultScrollRow - scrollY.toInt()).coerceIn(0, maxScroll)
                return true
            }
            val previewMouseX = EditorRenderer.previewMouseX(this, mouseX.toInt())
            val previewMouseY = EditorRenderer.previewMouseY(this, mouseY.toInt())
            val placement = InventoryButtonManager.placements(
                left = 0,
                top = 0,
                imageWidth = InventoryPreview.WIDTH,
                imageHeight = InventoryPreview.HEIGHT,
                playerInventory = true,
                includeInactive = true,
            ).lastOrNull { it.bounds.contains(previewMouseX, previewMouseY) }
            if (placement != null &&
                InventoryButtonEditorActions.changeButtonScale(placement.index, scrollY) == InputHandlingResult.CONSUMED
            ) {
                return true
            }
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
        }

        override fun keyPressed(event: KeyEvent): Boolean {
            return when {
                event.key() == GLFW.GLFW_KEY_ESCAPE && (commandField.focused || iconField.focused) -> {
                    commandField.focused = false
                    iconField.focused = false
                    true
                }
                commandField.focused && commandField.keyPressed(event) == InputHandlingResult.CONSUMED -> {
                    updateSelectedCommandFromField()
                    true
                }
                iconField.focused && handleIconFieldKey(event) == InputHandlingResult.CONSUMED -> true
                event.key() == GLFW.GLFW_KEY_R -> {
                    val index = hoveredIndex ?: selectedIndex
                    when (index?.let(InventoryButtonEditorActions::resetOrRemoveButton)) {
                        InventoryButtonResetShortcutResult.RESET -> true
                        InventoryButtonResetShortcutResult.REMOVED -> {
                            val removedIndex = requireNotNull(index)
                            val shiftedIndex: (Int?) -> Int? = { value ->
                                when {
                                    value == null || value == removedIndex -> null
                                    value > removedIndex -> value - 1
                                    else -> value
                                }
                            }
                            grabbedIndex = shiftedIndex(grabbedIndex)
                            hoveredIndex = shiftedIndex(hoveredIndex)
                            selectedIndex = shiftedIndex(selectedIndex)
                            syncFieldsFromSelection()
                            true
                        }
                        else -> super.keyPressed(event)
                    }
                }
                (event.key() == GLFW.GLFW_KEY_DELETE || event.key() == GLFW.GLFW_KEY_BACKSPACE) &&
                    clearSelectedButtonIfPresent() == InputHandlingResult.CONSUMED -> true
                else -> super.keyPressed(event)
            }
        }

        private fun handleIconFieldKey(event: KeyEvent): InputHandlingResult {
            if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
                val typed = iconField.text.trim()
                val selectedIcon = when {
                    typed.startsWith("text:", ignoreCase = true) -> typed
                    else -> iconCandidates().firstOrNull()?.id
                }
                if (!selectedIcon.isNullOrBlank()) {
                    selectedButton()?.icon = selectedIcon
                    InventoryButtonManager.clearIconCache()
                }
                iconField.focused = false
                return InputHandlingResult.CONSUMED
            }
            if (iconField.keyPressed(event) == InputHandlingResult.IGNORED) return InputHandlingResult.IGNORED
            resultScrollRow = 0
            return InputHandlingResult.CONSUMED
        }

        private fun clearSelectedButtonIfPresent(): InputHandlingResult {
            val button = selectedButton() ?: return InputHandlingResult.IGNORED
            button.command = ""
            button.icon = null
            button.backgroundIndex = 0
            syncFieldsFromSelection()
            return InputHandlingResult.CONSUMED
        }

        override fun charTyped(event: CharacterEvent): Boolean {
            if (!event.isAllowedChatCharacter) return false
            if (commandField.focused) {
                commandField.charTyped(event)
                updateSelectedCommandFromField()
                return true
            }
            if (iconField.focused) {
                iconField.charTyped(event)
                resultScrollRow = 0
                return true
            }
            return super.charTyped(event)
        }

        override fun onClose() {
            SkysoftConfigGui.config().saveNow()
            MinecraftClient.setScreen(parent)
        }

        override fun isPauseScreen(): Boolean = false

        private fun handlePanelClick(button: InventoryButtonConfig, mouseX: Int, mouseY: Int) {
            val panel = lastPanelBounds ?: return
            val fieldWidth = panel.width - EditorPanel.INSET * 2
            val commandBounds = Rect(
                panel.x + EditorPanel.INSET,
                panel.y + EditorPanel.COMMAND_FIELD_Y,
                fieldWidth,
                EditorPanel.FIELD_HEIGHT,
            )
            val iconBounds = Rect(
                panel.x + EditorPanel.INSET,
                panel.y + EditorPanel.ICON_FIELD_Y,
                fieldWidth,
                EditorPanel.FIELD_HEIGHT,
            )
            when {
                commandBounds.contains(mouseX, mouseY) -> {
                    commandField.focused = true
                    iconField.focused = false
                }
                iconBounds.contains(mouseX, mouseY) -> {
                    iconField.focused = true
                    commandField.focused = false
                }
                selectBackgroundIfClicked(button, panel, mouseX, mouseY) == InputHandlingResult.CONSUMED -> Unit
                selectIconResultIfClicked(button, mouseX, mouseY) == InputHandlingResult.CONSUMED -> Unit
                lastClearBounds?.contains(mouseX, mouseY) == true -> {
                    SoundUtilities.playClickSound()
                    button.command = ""
                    button.icon = null
                    button.backgroundIndex = 0
                    syncFieldsFromSelection()
                }
                lastDoneBounds?.contains(mouseX, mouseY) == true -> {
                    SoundUtilities.playClickSound()
                    commandField.focused = false
                    iconField.focused = false
                    selectedIndex = null
                }
                else -> {
                    commandField.focused = false
                    iconField.focused = false
                }
            }
        }

        private fun selectBackgroundIfClicked(
            button: InventoryButtonConfig,
            panel: Rect,
            mouseX: Int,
            mouseY: Int,
        ): InputHandlingResult {
            val backgroundsY = panel.y + EditorPanel.BACKGROUND_PICKER_Y
            val index = (0 until EditorPanel.BACKGROUND_PICKER_COUNT).firstOrNull {
                val bx = panel.x + EditorPanel.INSET + it * EditorGrid.STEP
                mouseX in bx until bx + BUTTON_SIZE && mouseY in backgroundsY until backgroundsY + BUTTON_SIZE
            } ?: return InputHandlingResult.IGNORED
            button.backgroundIndex = index
            return InputHandlingResult.CONSUMED
        }

        private fun selectIconResultIfClicked(
            button: InventoryButtonConfig,
            mouseX: Int,
            mouseY: Int,
        ): InputHandlingResult {
            val results = lastResultsBounds?.takeIf { it.contains(mouseX, mouseY) }
                ?: return InputHandlingResult.IGNORED
            val column = (mouseX - results.x - IconResults.PADDING) / EditorGrid.STEP
            val row = (mouseY - results.y - IconResults.PADDING) / EditorGrid.STEP
            if (column !in 0 until IconResults.COLUMNS || row !in 0 until IconResults.ROWS) {
                return InputHandlingResult.CONSUMED
            }
            val index = (resultScrollRow + row) * IconResults.COLUMNS + column
            val candidate = iconCandidates().getOrNull(index) ?: return InputHandlingResult.CONSUMED
            button.icon = candidate.id
            InventoryButtonManager.clearIconCache()
            return InputHandlingResult.CONSUMED
        }

        internal fun selectedButton(): InventoryButtonConfig? = selectedIndex?.let { config.buttons.getOrNull(it) }

        private fun syncFieldsFromSelection() {
            val button = selectedButton()
            commandField.text = button?.command?.removePrefix("/").orEmpty()
            iconField.text = ""
            commandField.focused = false
            iconField.focused = false
            resultScrollRow = 0
            lastIconSearch = null
            cachedIconCandidates = emptyList()
        }

        private fun updateSelectedCommandFromField() {
            val normalized = commandField.text.trimStart().removePrefix("/")
            commandField.text = normalized
            selectedButton()?.command = normalized
        }

        internal fun iconCandidates(): List<IconCandidate> {
            val search = iconField.text
            if (search == lastIconSearch) return cachedIconCandidates
            cachedIconCandidates = InventoryButtonManager.searchIconCandidates(search)
            lastIconSearch = search
            resultScrollRow = min(resultScrollRow, maxResultScrollRow())
            return cachedIconCandidates
        }

        internal fun maxResultScrollRow(): Int {
            val totalRows = ceil(iconCandidatesRawCount() / IconResults.COLUMNS.toDouble()).toInt()
            return (totalRows - IconResults.ROWS).coerceAtLeast(0)
        }

        private fun iconCandidatesRawCount(): Int {
            val search = iconField.text
            if (search != lastIconSearch) {
                cachedIconCandidates = InventoryButtonManager.searchIconCandidates(search)
                lastIconSearch = search
            }
            return cachedIconCandidates.size
        }
    }

    private object EditorRenderer {
        fun renderInventoryPreview(screen: EditorScreen, context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
            val previewScale = inventoryPreviewScale()
            val previewWidth = (InventoryPreview.WIDTH * previewScale).roundToInt()
            val previewHeight = (InventoryPreview.HEIGHT * previewScale).roundToInt()
            val leftCandidate = (screen.width - previewWidth) / 2
            val left = leftCandidate.coerceIn(
                InventoryPreview.HORIZONTAL_MARGIN,
                max(
                    InventoryPreview.HORIZONTAL_MARGIN,
                    screen.width - previewWidth - InventoryPreview.HORIZONTAL_MARGIN,
                ),
            )
            val top = ((screen.height - previewHeight) / 2).coerceIn(
                InventoryPreview.VERTICAL_MARGIN,
                max(InventoryPreview.VERTICAL_MARGIN, screen.height - previewHeight - InventoryPreview.VERTICAL_MARGIN),
            )
            screen.lastInventoryLeft = left
            screen.lastInventoryTop = top
            screen.lastPreviewScale = previewScale

            val previewMouseX = previewMouseX(screen, mouseX)
            val previewMouseY = previewMouseY(screen, mouseY)
            context.pose().pushMatrix()
            context.pose().translate(left.toFloat(), top.toFloat())
            context.pose().scale(previewScale, previewScale)
            try {
                val font = Minecraft.getInstance().font
                context.fill(0, 0, InventoryPreview.WIDTH, InventoryPreview.HEIGHT, EditorColors.INVENTORY_BACKGROUND)
                context.outline(0, 0, InventoryPreview.WIDTH, InventoryPreview.HEIGHT, EditorColors.INVENTORY_OUTLINE)
                context.text(
                    font,
                    "Crafting",
                    CraftingPreview.LABEL_X,
                    CraftingPreview.LABEL_Y,
                    EditorColors.INVENTORY_LABEL,
                    false,
                )

                drawInventorySlots(context, 0, 0)
                drawButtonSlots(screen, context, 0, 0, previewMouseX, previewMouseY, mouseX, mouseY)
            } finally {
                context.pose().popMatrix()
            }
        }

        fun renderSidePanel(screen: EditorScreen, context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
            val selectedButton = screen.selectedButton()
            val bounds = choosePanelBounds(screen)
            screen.lastPanelBounds = bounds
            val font = Minecraft.getInstance().font

            context.fill(
                bounds.x,
                bounds.y,
                bounds.x + bounds.width,
                bounds.y + bounds.height,
                EditorColors.PANEL_BACKGROUND,
            )
            context.outline(bounds.x, bounds.y, bounds.width, bounds.height, EditorColors.PANEL_OUTLINE)
            context.text(
                font,
                "Inventory Buttons",
                bounds.x + EditorPanel.INSET,
                bounds.y + EditorPanel.TITLE_Y,
                EditorColors.WHITE_TEXT,
                false,
            )

            val fieldWidth = bounds.width - EditorPanel.INSET * 2
            val preset = Rect(
                bounds.x + EditorPanel.INSET,
                bounds.y + EditorPanel.PRESET_BUTTON_Y,
                fieldWidth,
                EditorPanel.FIELD_HEIGHT,
            )
            val reset = Rect(
                bounds.x + EditorPanel.INSET,
                bounds.y + EditorPanel.RESET_BUTTON_Y,
                fieldWidth,
                EditorPanel.FIELD_HEIGHT,
            )
            screen.lastPresetBounds = preset
            screen.lastResetBounds = reset
            drawEditorButton(context, preset, "Load Defaults", mouseX, mouseY)
            drawEditorButton(
                context,
                reset,
                "Reset Slots",
                mouseX,
                mouseY,
                tone = PixelButtonTone.DANGER,
            )

            if (selectedButton == null) {
                LegacyTextRenderer.draw(
                    context,
                    "§7Click any §fslot§7 to edit it.",
                    bounds.x + EditorPanel.EMPTY_HELP_X_OFFSET,
                    bounds.y + EditorPanel.EMPTY_HELP_Y_OFFSET,
                    shadow = false,
                )
                screen.lastResultsBounds = null
                screen.lastClearBounds = null
                screen.lastDoneBounds = null
                return
            }

            renderSelectedEditor(screen, context, selectedButton, bounds, mouseX, mouseY)
        }

        fun previewMouseX(screen: EditorScreen, mouseX: Int): Int =
            floor((mouseX - screen.lastInventoryLeft) / screen.lastPreviewScale.toDouble()).toInt()

        fun previewMouseY(screen: EditorScreen, mouseY: Int): Int =
            floor((mouseY - screen.lastInventoryTop) / screen.lastPreviewScale.toDouble()).toInt()

        private fun drawInventorySlots(context: GuiGraphicsExtractor, left: Int, top: Int) {
            val font = Minecraft.getInstance().font
            repeat(InventorySlots.MAIN_ROWS) { row ->
                repeat(InventorySlots.COLUMNS) { column ->
                    drawSlot(
                        context,
                        left + InventorySlots.LEFT + column * InventorySlots.SPACING,
                        top + InventorySlots.MAIN_TOP + row * InventorySlots.SPACING,
                    )
                }
            }
            repeat(InventorySlots.COLUMNS) { column ->
                drawSlot(
                    context,
                    left + InventorySlots.LEFT + column * InventorySlots.SPACING,
                    top + InventorySlots.HOTBAR_TOP,
                )
            }
            repeat(InventorySlots.ARMOR_COUNT) { row ->
                drawSlot(
                    context,
                    left + InventorySlots.LEFT,
                    top + InventorySlots.ARMOR_TOP + row * InventorySlots.SPACING,
                )
            }
            drawSlot(context, left + InventorySlots.OFFHAND_X, top + InventorySlots.OFFHAND_Y)
            context.outline(
                left + PlayerPreview.X,
                top + PlayerPreview.Y,
                PlayerPreview.WIDTH,
                PlayerPreview.HEIGHT,
                EditorColors.PLAYER_PREVIEW,
            )
            drawSlot(context, left + CraftingPreview.INPUT_LEFT_X, top + CraftingPreview.INPUT_TOP_Y)
            drawSlot(context, left + CraftingPreview.INPUT_RIGHT_X, top + CraftingPreview.INPUT_TOP_Y)
            drawSlot(context, left + CraftingPreview.INPUT_LEFT_X, top + CraftingPreview.INPUT_BOTTOM_Y)
            drawSlot(context, left + CraftingPreview.INPUT_RIGHT_X, top + CraftingPreview.INPUT_BOTTOM_Y)
            context.text(
                font,
                "→",
                left + CraftingPreview.ARROW_X,
                top + CraftingPreview.ARROW_Y,
                EditorColors.CRAFTING_ARROW,
                false,
            )
            drawSlot(context, left + CraftingPreview.RESULT_X, top + CraftingPreview.RESULT_Y)
            context.outline(
                left + CraftingPreview.BOOK_X,
                top + CraftingPreview.BOOK_Y,
                CraftingPreview.BOOK_WIDTH,
                CraftingPreview.BOOK_HEIGHT,
                EditorColors.CRAFTING_BOOK,
            )
        }

        private fun drawButtonSlots(
            screen: EditorScreen,
            context: GuiGraphicsExtractor,
            left: Int,
            top: Int,
            mouseX: Int,
            mouseY: Int,
            tooltipMouseX: Int,
            tooltipMouseY: Int,
        ) {
            val placements = InventoryButtonManager.placements(
                left = left,
                top = top,
                imageWidth = InventoryPreview.WIDTH,
                imageHeight = InventoryPreview.HEIGHT,
                playerInventory = true,
                includeInactive = true,
            )
            val hoveredPlacement = placements.lastOrNull { it.bounds.contains(mouseX, mouseY) }
            screen.hoveredIndex = hoveredPlacement?.index
            for (placement in placements) {
                val hovered = placement == hoveredPlacement
                InventoryButtonManager.drawButton(
                    context = context,
                    x = placement.bounds.x,
                    y = placement.bounds.y,
                    button = placement.button,
                    active = placement.button.isActive(),
                    hovered = hovered,
                    selected = placement.index == screen.selectedIndex,
                )
                if (hovered) {
                    val action = if (placement.button.isActive()) "edit" else "create a button"
                    SkysoftNativeTooltip.setForNextFrame(
                        context,
                        listOf(
                            if (placement.button.isActive()) {
                                "§7Command: §e${InventoryButtonManager.displayCommand(placement.button.command)}"
                            } else {
                                "§7Empty button slot"
                            },
                            "§7Scale: §e${"%.2f".format(Locale.US, placement.button.scale)}",
                            "§eLeft-click §7to $action",
                            "§eLeft-click drag §7to move",
                            "§eScroll-Wheel §7to resize",
                            if (placement.button.isUserCreated == true) {
                                "§eR §7to remove"
                            } else {
                                "§eR §7to reset"
                            },
                        ),
                        tooltipMouseX,
                        tooltipMouseY,
                    )
                }
            }
            val addBounds = InventoryButtonLayout.editorAddButtonBounds.let { bounds ->
                Rect(left + bounds.x, top + bounds.y, bounds.width, bounds.height)
            }
            val hoveredAddButton = addBounds.contains(mouseX, mouseY)
            PixelButtonRenderer.draw(
                context,
                Minecraft.getInstance().font,
                addBounds,
                "+",
                selected = false,
                hovered = hoveredAddButton,
                enabled = true,
                tone = PixelButtonTone.CONFIRM,
            )
            if (hoveredAddButton) {
                SkysoftNativeTooltip.setForNextFrame(
                    context,
                    listOf(
                        "§aAdd inventory button",
                        "§eLeft-click §7to create a button to the right",
                    ),
                    tooltipMouseX,
                    tooltipMouseY,
                )
            }
        }

        private fun renderSelectedEditor(
            screen: EditorScreen,
            context: GuiGraphicsExtractor,
            button: InventoryButtonConfig,
            panel: Rect,
            mouseX: Int,
            mouseY: Int,
        ) {
            val font = Minecraft.getInstance().font
            val x = panel.x + EditorPanel.INSET
            var y = panel.y + SelectedEditor.TOP
            context.text(font, "Command", x, y, EditorColors.MUTED_TEXT, false)
            y += SelectedEditor.LABEL_TO_FIELD_GAP
            val fieldWidth = panel.width - EditorPanel.INSET * 2
            screen.commandField.render(context, x, y, fieldWidth, EditorPanel.FIELD_HEIGHT, "storage", prefix = "/")
            y += SelectedEditor.FIELD_SECTION_GAP

            context.text(font, "Background", x, y, EditorColors.MUTED_TEXT, false)
            y += SelectedEditor.LABEL_TO_FIELD_GAP
            repeat(EditorPanel.BACKGROUND_PICKER_COUNT) { index ->
                val bx = x + index * EditorGrid.STEP
                InventoryButtonManager.drawButtonBackground(
                    context,
                    bx,
                    y,
                    index,
                    active = true,
                    hovered = mouseX in bx until bx + BUTTON_SIZE && mouseY in y until y + BUTTON_SIZE,
                    selected = button.backgroundIndex == index,
                )
            }
            y += SelectedEditor.BACKGROUND_SECTION_GAP

            context.text(font, "Selected Icon", x, y, EditorColors.MUTED_TEXT, false)
            val previewX = panel.x + panel.width - SelectedEditor.ICON_PREVIEW_RIGHT_INSET
            InventoryButtonManager.drawButton(
                context,
                previewX,
                y - SelectedEditor.ICON_PREVIEW_Y_OFFSET,
                button,
                active = button.isActive(),
                hovered = false,
                renderScale = 1f,
            )
            y += SelectedEditor.LABEL_TO_FIELD_GAP
            val selectedIconText = (button.icon ?: "No icon selected").take(SelectedEditor.ICON_LABEL_MAX_LENGTH)
            context.text(font, selectedIconText, x, y, EditorColors.SELECTED_ICON_TEXT, false)
            y += SelectedEditor.SELECTED_ICON_SECTION_GAP

            context.text(font, "Icon Search", x, y, EditorColors.MUTED_TEXT, false)
            y += SelectedEditor.LABEL_TO_FIELD_GAP
            screen.iconField.render(context, x, y, fieldWidth, EditorPanel.FIELD_HEIGHT, "chess, sprayonator, akinsoft")
            y += SelectedEditor.ICON_SEARCH_SECTION_GAP

            val results = Rect(x, y, fieldWidth, IconResults.HEIGHT)
            screen.lastResultsBounds = results
            renderIconResults(screen, context, results, mouseX, mouseY)
            y += results.height + EditorButtons.ROW_GAP

            val clear = Rect(x, y, EditorButtons.ACTION_WIDTH, EditorPanel.FIELD_HEIGHT)
            val done = Rect(
                panel.x + panel.width - EditorButtons.ACTION_RIGHT_INSET,
                y,
                EditorButtons.ACTION_WIDTH,
                EditorPanel.FIELD_HEIGHT,
            )
            screen.lastClearBounds = clear
            screen.lastDoneBounds = done
            drawEditorButton(context, clear, "Clear", mouseX, mouseY, tone = PixelButtonTone.DANGER)
            drawEditorButton(context, done, "Done", mouseX, mouseY, tone = PixelButtonTone.CONFIRM)
        }

        private fun renderIconResults(
            screen: EditorScreen,
            context: GuiGraphicsExtractor,
            bounds: Rect,
            mouseX: Int,
            mouseY: Int,
        ) {
            val font = Minecraft.getInstance().font
            context.fill(
                bounds.x,
                bounds.y,
                bounds.x + bounds.width,
                bounds.y + bounds.height,
                EditorColors.ICON_RESULTS_BACKGROUND,
            )
            context.outline(bounds.x, bounds.y, bounds.width, bounds.height, EditorColors.ICON_RESULTS_OUTLINE)
            val candidates = screen.iconCandidates()
            if (candidates.isEmpty()) {
                context.text(
                    font,
                    "No items found",
                    bounds.x + IconResults.PADDING,
                    bounds.y + IconResults.EMPTY_TEXT_Y,
                    EditorColors.EMPTY_RESULTS_TEXT,
                    false,
                )
                return
            }

            screen.resultScrollRow = screen.resultScrollRow.coerceIn(0, screen.maxResultScrollRow())
            val visible = candidates
                .drop(screen.resultScrollRow * IconResults.COLUMNS)
                .take(IconResults.COLUMNS * IconResults.ROWS)
            context.enableScissor(bounds.x + 1, bounds.y + 1, bounds.x + bounds.width - 1, bounds.y + bounds.height - 1)
            try {
                visible.forEachIndexed { index, candidate ->
                    val column = index % IconResults.COLUMNS
                    val row = index / IconResults.COLUMNS
                    val x = bounds.x + IconResults.PADDING + column * EditorGrid.STEP
                    val y = bounds.y + IconResults.PADDING + row * EditorGrid.STEP
                    val hovered = mouseX in x until x + BUTTON_SIZE && mouseY in y until y + BUTTON_SIZE
                    InventoryButtonManager.drawButtonBackground(context, x, y, 0, active = true, hovered = hovered)
                    context.item(candidate.stack, x + 1, y + 1)
                    if (hovered && context.containsPointInScissor(mouseX, mouseY)) {
                        context.setComponentTooltipForNextFrame(
                            font,
                            listOf(
                                Component.literal(candidate.displayName).withStyle(ChatFormatting.WHITE),
                                Component.literal(candidate.id).withStyle(ChatFormatting.GRAY),
                            ),
                            mouseX,
                            mouseY,
                        )
                    }
                }
            } finally {
                context.disableScissor()
            }

            val maxScroll = screen.maxResultScrollRow()
            if (maxScroll > 0) {
                val barX = bounds.x + bounds.width - IconResults.SCROLLBAR_RIGHT_INSET
                val barHeight = bounds.height - IconResults.SCROLLBAR_VERTICAL_INSET * 2
                val knobHeight = max(IconResults.SCROLLBAR_MIN_KNOB_HEIGHT, barHeight / (maxScroll + 1))
                val travel = barHeight - knobHeight
                val knobY = bounds.y + IconResults.SCROLLBAR_VERTICAL_INSET +
                    travel * screen.resultScrollRow / maxScroll
                context.fill(
                    barX,
                    bounds.y + IconResults.SCROLLBAR_VERTICAL_INSET,
                    barX + IconResults.SCROLLBAR_WIDTH,
                    bounds.y + bounds.height - IconResults.SCROLLBAR_VERTICAL_INSET,
                    EditorColors.ICON_SCROLLBAR_TRACK,
                )
                context.fill(
                    barX,
                    knobY,
                    barX + IconResults.SCROLLBAR_WIDTH,
                    knobY + knobHeight,
                    EditorColors.ICON_SCROLLBAR_KNOB,
                )
            }
        }

        private fun choosePanelBounds(screen: EditorScreen): Rect {
            val content = previewContentBounds(screen)
            val maxPanelLeft = max(EditorPanel.MARGIN, screen.width - EditorPanel.WIDTH - EditorPanel.MARGIN)
            val right = content.x + content.width + previewSideGap(screen, rightSide = true)
            if (right + EditorPanel.WIDTH <= screen.width - EditorPanel.MARGIN) {
                return Rect(right, sidePanelTop(screen, rightSide = true), EditorPanel.WIDTH, EditorPanel.HEIGHT)
            }

            val left = content.x - EditorPanel.WIDTH - previewSideGap(screen, rightSide = false)
            if (left >= EditorPanel.MARGIN) {
                return Rect(left, sidePanelTop(screen, rightSide = false), EditorPanel.WIDTH, EditorPanel.HEIGHT)
            }

            val horizontal = content.x + (content.width - EditorPanel.WIDTH) / 2
                .coerceIn(EditorPanel.MARGIN, maxPanelLeft)
            val below = content.y + content.height + previewVerticalGap(screen, bottomSide = true)
            if (below + EditorPanel.HEIGHT <= screen.height - EditorPanel.MARGIN) {
                return Rect(horizontal, below, EditorPanel.WIDTH, EditorPanel.HEIGHT)
            }

            val above = content.y - EditorPanel.HEIGHT - previewVerticalGap(screen, bottomSide = false)
            if (above >= EditorPanel.MARGIN) return Rect(horizontal, above, EditorPanel.WIDTH, EditorPanel.HEIGHT)

            val rightSpace = screen.width - (content.x + content.width)
            val fallbackLeft = if (rightSpace >= content.x) {
                screen.width - EditorPanel.WIDTH - EditorPanel.MARGIN
            } else {
                EditorPanel.MARGIN
            }.coerceIn(EditorPanel.MARGIN, maxPanelLeft)
            val top = sidePanelTop(screen, rightSide = rightSpace >= content.x)
            return Rect(fallbackLeft, top, EditorPanel.WIDTH, EditorPanel.HEIGHT)
        }

        private fun sidePanelTop(screen: EditorScreen, rightSide: Boolean): Int {
            val buttonTop = previewPlacements()
                .asSequence()
                .filter { placement ->
                    if (rightSide) {
                        placement.bounds.x >= InventoryPreview.WIDTH
                    } else {
                        placement.bounds.x + placement.bounds.width <= 0
                    }
                }
                .minOfOrNull { it.bounds.y }
                ?: 0
            return (screen.lastInventoryTop + buttonTop * screen.lastPreviewScale).roundToInt().coerceIn(
                EditorPanel.MARGIN,
                max(EditorPanel.MARGIN, screen.height - EditorPanel.HEIGHT - EditorPanel.MARGIN),
            )
        }

        private fun previewContentBounds(screen: EditorScreen): Rect {
            var minX = 0
            var minY = 0
            var maxX = InventoryPreview.WIDTH
            var maxY = InventoryPreview.HEIGHT
            for (placement in previewPlacements()) {
                minX = min(minX, placement.bounds.x)
                minY = min(minY, placement.bounds.y)
                maxX = max(maxX, placement.bounds.x + placement.bounds.width)
                maxY = max(maxY, placement.bounds.y + placement.bounds.height)
            }
            val x0 = screen.lastInventoryLeft + floor(minX * screen.lastPreviewScale.toDouble()).toInt()
            val y0 = screen.lastInventoryTop + floor(minY * screen.lastPreviewScale.toDouble()).toInt()
            val x1 = screen.lastInventoryLeft + ceil(maxX * screen.lastPreviewScale.toDouble()).toInt()
            val y1 = screen.lastInventoryTop + ceil(maxY * screen.lastPreviewScale.toDouble()).toInt()
            return Rect(x0, y0, x1 - x0, y1 - y0)
        }

        private fun previewSideGap(screen: EditorScreen, rightSide: Boolean): Int {
            val rawGap = previewPlacements().mapNotNull { placement ->
                if (rightSide) {
                    (placement.bounds.x - InventoryPreview.WIDTH).takeIf { it >= 0 }
                } else {
                    (0 - (placement.bounds.x + placement.bounds.width)).takeIf { it >= 0 }
                }
            }.minOrNull() ?: PREVIEW_PLACEMENT_GAP_FALLBACK
            return (rawGap * screen.lastPreviewScale).roundToInt().coerceAtLeast(1)
        }

        private fun previewVerticalGap(screen: EditorScreen, bottomSide: Boolean): Int {
            val rawGap = previewPlacements().mapNotNull { placement ->
                if (bottomSide) {
                    (placement.bounds.y - InventoryPreview.HEIGHT).takeIf { it >= 0 }
                } else {
                    (0 - (placement.bounds.y + placement.bounds.height)).takeIf { it >= 0 }
                }
            }.minOrNull() ?: PREVIEW_PLACEMENT_GAP_FALLBACK
            return (rawGap * screen.lastPreviewScale).roundToInt().coerceAtLeast(1)
        }

        private fun previewPlacements(): List<InventoryButtonManager.ButtonPlacement> =
            InventoryButtonManager.placements(
                left = 0,
                top = 0,
                imageWidth = InventoryPreview.WIDTH,
                imageHeight = InventoryPreview.HEIGHT,
                playerInventory = true,
                includeInactive = true,
            )

        private fun inventoryPreviewScale(): Float {
            val minecraft = Minecraft.getInstance()
            val inventoryConfig = SkysoftConfigGui.config().gui.inventoryScreen
            if (!inventoryConfig.separateInventoryGuiScale) return 1f
            val inventoryScale = minecraft.window.calculateScale(
                inventoryConfig.settings.inventoryGuiScale.coerceAtLeast(0),
                minecraft.isEnforceUnicode,
            ).coerceAtLeast(1)
            val editorScale = minecraft.window.guiScale.coerceAtLeast(1)
            return (inventoryScale / editorScale.toFloat()).coerceAtLeast(1f)
        }

        private fun drawSlot(context: GuiGraphicsExtractor, x: Int, y: Int) {
            context.fill(x, y, x + InventorySlots.SIZE, y + InventorySlots.SIZE, EditorColors.SLOT_OUTER)
            context.fill(
                x + 1,
                y + 1,
                x + InventorySlots.SIZE - 1,
                y + InventorySlots.SIZE - 1,
                EditorColors.SLOT_INNER,
            )
        }

        private fun drawEditorButton(
            context: GuiGraphicsExtractor,
            bounds: Rect,
            label: String,
            mouseX: Int,
            mouseY: Int,
            tone: PixelButtonTone = PixelButtonTone.NORMAL,
        ) {
            val font = Minecraft.getInstance().font
            PixelButtonRenderer.draw(
                context,
                font,
                bounds,
                label,
                selected = false,
                hovered = bounds.contains(mouseX, mouseY),
                enabled = true,
                tone = tone,
            )
        }

        private const val PREVIEW_PLACEMENT_GAP_FALLBACK = 2
    }

    private object InventoryPreview {
        const val WIDTH = 176
        const val HEIGHT = InventoryButtonDefaults.PLAYER_INVENTORY_HEIGHT
        const val HORIZONTAL_MARGIN = 48
        const val VERTICAL_MARGIN = 32
    }

    private object InventorySlots {
        const val MAIN_ROWS = 3
        const val COLUMNS = 9
        const val LEFT = 8
        const val SIZE = 18
        const val SPACING = 18
        const val MAIN_TOP = 84
        const val HOTBAR_TOP = 142
        const val ARMOR_COUNT = 4
        const val ARMOR_TOP = 8
        const val OFFHAND_X = 77
        const val OFFHAND_Y = 62
    }

    private object PlayerPreview {
        const val X = 26
        const val Y = 8
        const val WIDTH = 49
        const val HEIGHT = 70
    }

    private object CraftingPreview {
        const val LABEL_X = 97
        const val LABEL_Y = 6
        const val INPUT_LEFT_X = 98
        const val INPUT_RIGHT_X = 116
        const val INPUT_TOP_Y = 18
        const val INPUT_BOTTOM_Y = 36
        const val ARROW_X = 139
        const val ARROW_Y = 31
        const val RESULT_X = 154
        const val RESULT_Y = 28
        const val BOOK_X = 104
        const val BOOK_Y = 61
        const val BOOK_WIDTH = 20
        const val BOOK_HEIGHT = 18
    }

    private object EditorPanel {
        const val WIDTH = 196
        const val HEIGHT = 316
        const val MARGIN = 8
        const val INSET = 10
        const val FIELD_HEIGHT = 18
        const val TITLE_Y = 9
        const val PRESET_BUTTON_Y = 25
        const val RESET_BUTTON_Y = 47
        const val COMMAND_FIELD_Y = 87
        const val BACKGROUND_PICKER_Y = 123
        const val ICON_FIELD_Y = 186
        const val BACKGROUND_PICKER_COUNT = 7
        const val EMPTY_HELP_X_OFFSET = 12
        const val EMPTY_HELP_Y_OFFSET = 80
    }

    private object SelectedEditor {
        const val TOP = 76
        const val LABEL_TO_FIELD_GAP = 11
        const val FIELD_SECTION_GAP = 25
        const val BACKGROUND_SECTION_GAP = 26
        const val ICON_PREVIEW_RIGHT_INSET = 32
        const val ICON_PREVIEW_Y_OFFSET = 5
        const val ICON_LABEL_MAX_LENGTH = 26
        const val SELECTED_ICON_SECTION_GAP = 15
        const val ICON_SEARCH_SECTION_GAP = 24
    }

    private object IconResults {
        const val COLUMNS = 7
        const val ROWS = 3
        const val PADDING = 6
        const val HEIGHT = 76
        const val EMPTY_TEXT_Y = 8
        const val SCROLLBAR_RIGHT_INSET = 5
        const val SCROLLBAR_VERTICAL_INSET = 2
        const val SCROLLBAR_WIDTH = 2
        const val SCROLLBAR_MIN_KNOB_HEIGHT = 10
    }

    private object EditorButtons {
        const val ROW_GAP = 8
        const val ACTION_WIDTH = 78
        const val ACTION_RIGHT_INSET = 88
    }

    private object EditorGrid {
        const val STEP = 22
    }

    private object EditorColors {
        val SCREEN_OVERLAY = 0xB0000000.toInt()
        val INVENTORY_BACKGROUND = 0xFFE8E8E8.toInt()
        val INVENTORY_OUTLINE = 0xFF303030.toInt()
        val INVENTORY_LABEL = 0xFF404040.toInt()
        val PANEL_BACKGROUND = 0xF0181818.toInt()
        val PANEL_OUTLINE = 0xFF505050.toInt()
        val WHITE_TEXT = 0xFFFFFFFF.toInt()
        val MUTED_TEXT = 0xFFB0B0B0.toInt()
        val SELECTED_ICON_TEXT = 0xFFDDDDDD.toInt()
        val ICON_RESULTS_BACKGROUND = 0xFF101010.toInt()
        val ICON_RESULTS_OUTLINE = 0xFF383838.toInt()
        val EMPTY_RESULTS_TEXT = 0xFF808080.toInt()
        val ICON_SCROLLBAR_TRACK = 0xFF303030.toInt()
        val ICON_SCROLLBAR_KNOB = 0xFF909090.toInt()
        val PLAYER_PREVIEW = 0xFF909090.toInt()
        val CRAFTING_ARROW = 0xFF606060.toInt()
        val CRAFTING_BOOK = 0xFFB0B0B0.toInt()
        val SLOT_OUTER = 0xFF8B8B8B.toInt()
        val SLOT_INNER = 0xFFC6C6C6.toInt()
    }
}
