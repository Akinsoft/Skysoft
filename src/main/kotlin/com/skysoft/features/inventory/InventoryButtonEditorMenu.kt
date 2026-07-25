package com.skysoft.features.inventory

import com.skysoft.config.InventoryButtonConfig
import com.skysoft.config.InventoryButtonDefaults
import com.skysoft.config.InventoryButtonsConfig
import com.skysoft.gui.tooltip.SkysoftNativeTooltip
import com.skysoft.utils.ColorUtilities.withScaledAlpha
import com.skysoft.utils.SoundUtilities
import com.skysoft.utils.animation.PanelFadeTransition
import com.skysoft.utils.gui.OverlayPanelStyle
import com.skysoft.utils.gui.PixelButtonRenderer
import com.skysoft.utils.gui.PixelButtonTone
import com.skysoft.utils.gui.Rect
import com.skysoft.utils.gui.TextFieldState
import com.skysoft.utils.input.InputHandlingResult
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import org.lwjgl.glfw.GLFW

internal class InventoryButtonEditorMenu(
    private val config: () -> InventoryButtonsConfig,
    private val onLayoutChanged: () -> Unit,
    nanoTime: () -> Long = System::nanoTime,
) {
    private val transition = PanelFadeTransition(nanoTime)
    private val renameField = TextFieldState(maxLength = InventoryButtonsConfig.PRESET_NAME_MAX_LENGTH)
    private var pendingAction: PendingAction? = null
    private var renamingPreset: Int? = null
    private var lastMenuLayout: MenuLayout? = null
    private var status: MenuStatus? = null

    fun render(
        context: GuiGraphicsExtractor,
        editorPanel: Rect,
        screenWidth: Int,
        screenHeight: Int,
        mouseX: Int,
        mouseY: Int,
    ) {
        val toggle = toggleBounds(editorPanel)
        PixelButtonRenderer.draw(
            context,
            Minecraft.getInstance().font,
            toggle,
            "...",
            selected = transition.isVisible && !transition.isClosing,
            hovered = toggle.contains(mouseX, mouseY),
            enabled = true,
        )
        if (toggle.contains(mouseX, mouseY)) {
            SkysoftNativeTooltip.setForNextFrame(
                context,
                listOf(if (transition.isVisible && !transition.isClosing) "§7Close layout menu" else "§7Open layout menu"),
                mouseX,
                mouseY,
            )
        }

        val opacity = transition.opacity()
        if (!transition.isVisible) {
            lastMenuLayout = null
            return
        }
        val layout = MenuLayout.create(editorPanel, screenWidth, screenHeight)
        lastMenuLayout = layout
        context.fill(
            layout.panel.x,
            layout.panel.y,
            layout.panel.x + layout.panel.width,
            layout.panel.y + layout.panel.height,
            OverlayPanelStyle.BACKGROUND.withScaledAlpha(opacity),
        )
        context.outline(
            layout.panel.x,
            layout.panel.y,
            layout.panel.width,
            layout.panel.height,
            OverlayPanelStyle.OUTLINE.withScaledAlpha(opacity),
        )
        renderPrimaryActions(context, layout, mouseX, mouseY, opacity)
        renderPresets(context, layout, mouseX, mouseY, opacity)
        renderSharingActions(context, layout, mouseX, mouseY, opacity)
    }

    fun wasMouseClickHandled(click: MouseButtonEvent, editorPanel: Rect): Boolean {
        val mouseX = click.x().toInt()
        val mouseY = click.y().toInt()
        return if (click.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT &&
            toggleBounds(editorPanel).contains(mouseX, mouseY)
        ) {
            SoundUtilities.playClickSound()
            if (transition.isVisible && !transition.isClosing) closeMenu() else transition.show()
            true
        } else {
            wasPanelClickHandled(click, mouseX, mouseY)
        }
    }

    private fun wasPanelClickHandled(click: MouseButtonEvent, mouseX: Int, mouseY: Int): Boolean {
        if (!transition.isVisible) return false
        val layout = lastMenuLayout
        if (layout == null || !layout.panel.contains(mouseX, mouseY)) {
            closeMenu()
            return false
        }
        if (!transition.isInteractive) return true
        renamingPreset?.let { presetIndex ->
            val bounds = layout.presets[presetIndex]
            if (click.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT && bounds.contains(mouseX, mouseY)) {
                renameField.placeCursorAt(mouseX, bounds.x, bounds.width)
                return true
            }
            finishRename()
        }
        handleMenuClick(layout, mouseX, mouseY, click.button())
        return true
    }

    fun handleKeyPress(event: KeyEvent): InputHandlingResult {
        if (renamingPreset != null) {
            if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
                cancelRename()
                return InputHandlingResult.CONSUMED
            }
            renameField.keyPressed(event)
            if (!renameField.focused) finishRename()
            return InputHandlingResult.CONSUMED
        }
        if (event.key() != GLFW.GLFW_KEY_ESCAPE || !transition.isVisible) return InputHandlingResult.IGNORED
        closeMenu()
        return InputHandlingResult.CONSUMED
    }

    fun handleCharTyped(event: CharacterEvent): InputHandlingResult {
        if (renamingPreset == null || !event.isAllowedChatCharacter) return InputHandlingResult.IGNORED
        renameField.charTyped(event)
        return InputHandlingResult.CONSUMED
    }

    fun saveActivePreset() {
        finishRename()
        config().storeActivePreset()
    }

    private fun handleMenuClick(layout: MenuLayout, mouseX: Int, mouseY: Int, button: Int) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT && button != GLFW.GLFW_MOUSE_BUTTON_RIGHT) return
        val pending = pendingAction
        if (pending != null) {
            when {
                layout.primary.contains(mouseX, mouseY) && button == GLFW.GLFW_MOUSE_BUTTON_LEFT -> {
                    SoundUtilities.playClickSound()
                    pendingAction = null
                }
                layout.secondary.contains(mouseX, mouseY) && button == GLFW.GLFW_MOUSE_BUTTON_LEFT -> {
                    SoundUtilities.playClickSound()
                    confirm(pending)
                }
            }
            return
        }
        when {
            layout.primary.contains(mouseX, mouseY) && button == GLFW.GLFW_MOUSE_BUTTON_LEFT -> {
                SoundUtilities.playClickSound()
                pendingAction = PendingAction.LoadDefaults
            }
            layout.secondary.contains(mouseX, mouseY) && button == GLFW.GLFW_MOUSE_BUTTON_LEFT -> {
                SoundUtilities.playClickSound()
                pendingAction = PendingAction.ResetSlots
            }
            else -> layout.presets.indexOfFirst { it.contains(mouseX, mouseY) }
                .takeIf { it >= 0 }
                ?.let { presetIndex ->
                    SoundUtilities.playClickSound()
                    if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                        beginRename(presetIndex)
                    } else if (config().activePreset != presetIndex) {
                        config().switchPreset(presetIndex)
                        InventoryButtonManager.clearIconCache()
                        onLayoutChanged()
                    }
                }
                ?: handleSharingClick(layout, mouseX, mouseY, button)
        }
    }

    private fun handleSharingClick(layout: MenuLayout, mouseX: Int, mouseY: Int, button: Int) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return
        when {
            layout.share.contains(mouseX, mouseY) -> {
                SoundUtilities.playClickSound()
                config().storeActivePreset()
                Minecraft.getInstance().keyboardHandler.setClipboard(
                    InventoryButtonLayoutSharing.export(config().buttons),
                )
                status = MenuStatus(StatusTarget.SHARE, "Copied!", System.currentTimeMillis() + STATUS_DURATION_MILLIS)
            }
            layout.import.contains(mouseX, mouseY) -> {
                SoundUtilities.playClickSound()
                val imported = runCatching {
                    InventoryButtonLayoutSharing.import(Minecraft.getInstance().keyboardHandler.clipboard)
                }
                imported.onSuccess { buttons ->
                    pendingAction = PendingAction.Import(buttons)
                    status = null
                }.onFailure {
                    status = MenuStatus(StatusTarget.IMPORT, "Invalid", System.currentTimeMillis() + STATUS_DURATION_MILLIS)
                }
            }
        }
    }

    private fun confirm(action: PendingAction) {
        when (action) {
            PendingAction.LoadDefaults -> InventoryButtonManager.applySkyBlockPreset()
            PendingAction.ResetSlots -> config().replaceActiveButtons(InventoryButtonDefaults.create())
            is PendingAction.Import -> config().replaceActiveButtons(action.buttons)
        }
        InventoryButtonManager.clearIconCache()
        onLayoutChanged()
        pendingAction = null
        transition.hide()
    }

    private fun beginRename(index: Int) {
        pendingAction = null
        renamingPreset = index
        renameField.text = config().presets[index].name
        renameField.focused = true
        renameField.moveCursorToEnd()
    }

    private fun finishRename() {
        val index = renamingPreset ?: return
        config().renamePreset(index, renameField.text)
        renamingPreset = null
        renameField.focused = false
    }

    private fun cancelRename() {
        renamingPreset = null
        renameField.focused = false
    }

    private fun closeMenu() {
        finishRename()
        pendingAction = null
        transition.hide()
    }

    private fun renderPrimaryActions(
        context: GuiGraphicsExtractor,
        layout: MenuLayout,
        mouseX: Int,
        mouseY: Int,
        opacity: Double,
    ) {
        val pending = pendingAction
        drawButton(
            context,
            layout.primary,
            if (pending == null) "Load Defaults" else "Cancel",
            mouseX,
            mouseY,
            opacity,
        )
        drawButton(
            context,
            layout.secondary,
            pending?.confirmLabel ?: "Reset Slots",
            mouseX,
            mouseY,
            opacity,
            if (pending == null) PixelButtonTone.DANGER else PixelButtonTone.CONFIRM,
        )
    }

    private fun renderPresets(
        context: GuiGraphicsExtractor,
        layout: MenuLayout,
        mouseX: Int,
        mouseY: Int,
        opacity: Double,
    ) {
        layout.presets.forEachIndexed { index, bounds ->
            val font = Minecraft.getInstance().font
            val name = config().presets[index].name
            val maximumTextWidth = bounds.width - PRESET_TEXT_INSET
            if (renamingPreset == index) {
                renameField.render(
                    context,
                    bounds.x,
                    bounds.y,
                    bounds.width,
                    bounds.height,
                    InventoryButtonsConfig.defaultPresetName(index),
                    alpha = opacity,
                )
            } else {
                PixelButtonRenderer.draw(
                    context,
                    font,
                    bounds,
                    font.plainSubstrByWidth(name, maximumTextWidth),
                    selected = config().activePreset == index,
                    hovered = bounds.contains(mouseX, mouseY),
                    enabled = transition.isInteractive && pendingAction == null,
                    alpha = opacity,
                )
            }
            if (transition.isInteractive && pendingAction == null && bounds.contains(mouseX, mouseY)) {
                val isNameTruncated = font.width(name) > maximumTextWidth
                SkysoftNativeTooltip.setForNextFrame(
                    context,
                    buildList {
                        if (isNameTruncated) add("§f$name")
                        add("§eLeft-click §7to switch")
                        add("§eRight-click §7to rename")
                    },
                    mouseX,
                    mouseY,
                )
            }
        }
    }

    private fun renderSharingActions(
        context: GuiGraphicsExtractor,
        layout: MenuLayout,
        mouseX: Int,
        mouseY: Int,
        opacity: Double,
    ) {
        val currentStatus = status?.takeIf { it.expiresAt > System.currentTimeMillis() }.also {
            if (it == null) status = null
        }
        drawButton(
            context,
            layout.share,
            currentStatus?.takeIf { it.target == StatusTarget.SHARE }?.text ?: "Share",
            mouseX,
            mouseY,
            opacity,
            enabled = pendingAction == null,
        )
        drawButton(
            context,
            layout.import,
            currentStatus?.takeIf { it.target == StatusTarget.IMPORT }?.text ?: "Import",
            mouseX,
            mouseY,
            opacity,
            enabled = pendingAction == null,
        )
    }

    private fun drawButton(
        context: GuiGraphicsExtractor,
        bounds: Rect,
        label: String,
        mouseX: Int,
        mouseY: Int,
        opacity: Double,
        tone: PixelButtonTone = PixelButtonTone.NORMAL,
        enabled: Boolean = true,
    ) {
        val isEnabled = transition.isInteractive && enabled
        PixelButtonRenderer.draw(
            context,
            Minecraft.getInstance().font,
            bounds,
            label,
            selected = false,
            hovered = isEnabled && bounds.contains(mouseX, mouseY),
            enabled = isEnabled,
            tone = tone,
            alpha = opacity,
        )
    }

    private fun toggleBounds(panel: Rect): Rect = Rect(
        panel.x + panel.width - TOGGLE_RIGHT_INSET,
        panel.y + TOGGLE_TOP,
        TOGGLE_WIDTH,
        TOGGLE_HEIGHT,
    )

    private sealed interface PendingAction {
        val confirmLabel: String

        data object LoadDefaults : PendingAction {
            override val confirmLabel = "Confirm Load"
        }

        data object ResetSlots : PendingAction {
            override val confirmLabel = "Confirm Reset"
        }

        data class Import(val buttons: List<InventoryButtonConfig>) : PendingAction {
            override val confirmLabel = "Confirm Import"
        }
    }

    private data class MenuStatus(
        val target: StatusTarget,
        val text: String,
        val expiresAt: Long,
    )

    private enum class StatusTarget {
        SHARE,
        IMPORT,
    }

    private data class MenuLayout(
        val panel: Rect,
        val primary: Rect,
        val secondary: Rect,
        val presets: List<Rect>,
        val share: Rect,
        val import: Rect,
    ) {
        companion object {
            fun create(editorPanel: Rect, screenWidth: Int, screenHeight: Int): MenuLayout {
                val fitsRight =
                    editorPanel.x + editorPanel.width + PANEL_GAP + WIDTH <= screenWidth - SCREEN_MARGIN
                val fitsLeft = editorPanel.x - PANEL_GAP - WIDTH >= SCREEN_MARGIN
                val prefersRight = editorPanel.x + editorPanel.width / 2 >= screenWidth / 2
                val panelX = when {
                    prefersRight && fitsRight ->
                        editorPanel.x + editorPanel.width + PANEL_GAP
                    !prefersRight && fitsLeft -> editorPanel.x - PANEL_GAP - WIDTH
                    fitsRight -> editorPanel.x + editorPanel.width + PANEL_GAP
                    fitsLeft -> editorPanel.x - PANEL_GAP - WIDTH
                    else -> editorPanel.x + editorPanel.width - WIDTH
                }.coerceIn(SCREEN_MARGIN, (screenWidth - WIDTH - SCREEN_MARGIN).coerceAtLeast(SCREEN_MARGIN))
                val panelY = editorPanel.y.coerceIn(
                    SCREEN_MARGIN,
                    (screenHeight - HEIGHT - SCREEN_MARGIN).coerceAtLeast(SCREEN_MARGIN),
                )
                val panel = Rect(panelX, panelY, WIDTH, HEIGHT)
                val contentX = panel.x + OverlayPanelStyle.PADDING
                val contentWidth = panel.width - OverlayPanelStyle.PADDING * 2
                var y = panel.y + OverlayPanelStyle.PADDING
                val primary = Rect(contentX, y, contentWidth, ROW_HEIGHT)
                y += ROW_HEIGHT + ROW_GAP
                val secondary = Rect(contentX, y, contentWidth, ROW_HEIGHT)
                y += ROW_HEIGHT + SECTION_GAP
                val presets = List(InventoryButtonsConfig.PRESET_COUNT) {
                    Rect(contentX, y + it * (ROW_HEIGHT + ROW_GAP), contentWidth, ROW_HEIGHT)
                }
                y += InventoryButtonsConfig.PRESET_COUNT * ROW_HEIGHT +
                    (InventoryButtonsConfig.PRESET_COUNT - 1) * ROW_GAP + SECTION_GAP
                val sharingWidth = (contentWidth - ROW_GAP) / 2
                val share = Rect(contentX, y, sharingWidth, ROW_HEIGHT)
                val import = Rect(contentX + sharingWidth + ROW_GAP, y, contentWidth - sharingWidth - ROW_GAP, ROW_HEIGHT)
                return MenuLayout(panel, primary, secondary, presets, share, import)
            }
        }
    }

    private companion object {
        const val TOGGLE_WIDTH = 24
        const val TOGGLE_HEIGHT = 18
        const val TOGGLE_RIGHT_INSET = 30
        const val TOGGLE_TOP = 5
        const val PANEL_GAP = 4
        const val SCREEN_MARGIN = 4
        const val WIDTH = 164
        const val HEIGHT = 144
        const val ROW_HEIGHT = 18
        const val ROW_GAP = 4
        const val SECTION_GAP = 7
        const val PRESET_TEXT_INSET = 10
        const val STATUS_DURATION_MILLIS = 1_500L
    }
}
