package com.skysoft.features.inventory

import com.skysoft.config.SkysoftConfigGui
import com.skysoft.gui.tooltip.SkysoftNativeTooltip
import com.skysoft.utils.SmoothFloatTransition
import com.skysoft.utils.SoundUtilities
import com.skysoft.utils.gui.PixelButtonRenderer
import com.skysoft.utils.gui.PixelButtonTone
import com.skysoft.utils.gui.PixelControlColors
import com.skysoft.utils.gui.PixelControlPanelRenderer
import com.skysoft.utils.gui.PixelSliderRenderer
import com.skysoft.utils.input.InputHandlingResult
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import org.lwjgl.glfw.GLFW

internal var isStorageSettingsPanelOpen = false
    private set
internal var storageSettingsPanelProgress = 0f
    private set
internal var draggedStorageVisualSetting: StorageVisualSetting? = null
    private set
internal var lastStorageSettingsOutcome = "none"
    private set

private val storageSettingsTransition = SmoothFloatTransition(0f, STORAGE_SETTINGS_ANIMATION_NANOS)

internal fun drawStorageSettingsPanel(
    context: GuiGraphicsExtractor,
    screenWidth: Int,
    screenHeight: Int,
    measurements: Measurements,
    mouseX: Int,
    mouseY: Int,
) {
    val layout = storageSettingsPanelLayout(screenWidth, screenHeight, measurements)
    val progress = storageSettingsTransition.value(if (isStorageSettingsPanelOpen) 1f else 0f)
    storageSettingsPanelProgress = progress
    if (progress <= MIN_VISIBLE_PROGRESS) {
        drawStorageSettingsButton(context, layout, mouseX, mouseY)
        return
    }
    val visiblePanel = layout.animatedPanel(progress)
    PixelControlPanelRenderer.draw(context, visiblePanel, StorageSettingsPanel.HEADER_HEIGHT)
    context.enableScissor(visiblePanel.x, visiblePanel.y, visiblePanel.x + visiblePanel.width, visiblePanel.y + visiblePanel.height)
    try {
        val font = Minecraft.getInstance().font
        val title = "Storage Settings"
        context.text(
            font,
            title,
            layout.panel.x + (layout.panel.width - font.width(title)) / 2,
            layout.panel.y + (StorageSettingsPanel.HEADER_HEIGHT - font.lineHeight + 2) / 2,
            PixelControlColors.TEXT,
            false,
        )
        PixelButtonRenderer.draw(
            context,
            font,
            layout.close,
            "X",
            false,
            layout.close.contains(mouseX, mouseY),
            true,
            PixelButtonTone.DANGER,
        )
        layout.settings.forEach { setting ->
            drawStorageVisualSetting(context, layout, setting, screenWidth, screenHeight, measurements, mouseX, mouseY)
        }
    } finally {
        context.disableScissor()
    }
}

internal fun processStorageSettingsClick(
    screen: AbstractContainerScreen<*>,
    click: MouseButtonEvent,
    measurements: Measurements,
): InputHandlingResult {
    val mouseX = click.x().toInt()
    val mouseY = click.y().toInt()
    val layout = storageSettingsPanelLayout(screen.width, screen.height, measurements)
    if (!isStorageSettingsPanelOpen) {
        return processClosedStorageSettingsClick(click, layout, mouseX, mouseY)
    }
    if (!layout.panel.contains(mouseX, mouseY)) return InputHandlingResult.IGNORED
    if (click.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return InputHandlingResult.CONSUMED
    return processOpenStorageSettingsClick(screen, measurements, layout, mouseX, mouseY)
}

internal fun processStorageSettingsDrag(
    screen: AbstractContainerScreen<*>,
    click: MouseButtonEvent,
): InputHandlingResult {
    val setting = draggedStorageVisualSetting ?: return InputHandlingResult.IGNORED
    if (click.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return InputHandlingResult.IGNORED
    val layoutState = storageOverlayLayoutScreen(screen, shouldReadScreen = false) ?: run {
        draggedStorageVisualSetting = null
        return InputHandlingResult.IGNORED
    }
    val layout = storageSettingsPanelLayout(screen.width, screen.height, layoutState.measurements)
    updateStorageSettingFromPointer(screen, layoutState.measurements, layout, setting, click.x().toInt())
    return InputHandlingResult.CONSUMED
}

internal fun processStorageSettingsRelease(click: MouseButtonEvent): InputHandlingResult {
    val setting = draggedStorageVisualSetting ?: return InputHandlingResult.IGNORED
    if (click.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return InputHandlingResult.IGNORED
    draggedStorageVisualSetting = null
    saveStorageSettings("${setting.name.lowercase()}=${setting.displayValue()}")
    return InputHandlingResult.CONSUMED
}

internal fun processStorageSettingsKey(key: Int): InputHandlingResult {
    if (!isStorageSettingsPanelOpen || key != GLFW.GLFW_KEY_ESCAPE) return InputHandlingResult.IGNORED
    isStorageSettingsPanelOpen = false
    draggedStorageVisualSetting = null
    lastStorageSettingsOutcome = "closed:escape"
    return InputHandlingResult.CONSUMED
}

internal fun storageSettingsContains(
    screenWidth: Int,
    screenHeight: Int,
    measurements: Measurements,
    mouseX: Int,
    mouseY: Int,
): Boolean {
    val layout = storageSettingsPanelLayout(screenWidth, screenHeight, measurements)
    val progress = storageSettingsTransition.value(if (isStorageSettingsPanelOpen) 1f else 0f)
    return if (progress <= MIN_VISIBLE_PROGRESS) {
        layout.button.contains(mouseX, mouseY)
    } else {
        layout.animatedPanel(progress).contains(mouseX, mouseY)
    }
}

internal fun storageSettingsPanelLayout(
    screenWidth: Int,
    screenHeight: Int,
    measurements: Measurements,
): StorageSettingsPanelLayout = StorageSettingsPanelLayout.create(
    screenWidth = screenWidth,
    screenHeight = screenHeight,
    playerBounds = measurements.playerBounds,
    settings = visibleStorageSettings(),
    buttonAnchor = if (measurements.isModern) measurements.search else measurements.playerBounds,
)

private fun processClosedStorageSettingsClick(
    click: MouseButtonEvent,
    layout: StorageSettingsPanelLayout,
    mouseX: Int,
    mouseY: Int,
): InputHandlingResult {
    val closingProgress = storageSettingsTransition.value(0f)
    if (
        closingProgress > MIN_VISIBLE_PROGRESS &&
        layout.animatedPanel(closingProgress).contains(mouseX, mouseY)
    ) {
        return InputHandlingResult.CONSUMED
    }
    if (click.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT || !layout.button.contains(mouseX, mouseY)) {
        return InputHandlingResult.IGNORED
    }
    isStorageSettingsPanelOpen = true
    SoundUtilities.playClickSound()
    storageSearchField.focused = false
    finishTitleEdit()
    lastStorageSettingsOutcome = "opened"
    return InputHandlingResult.CONSUMED
}

private fun processOpenStorageSettingsClick(
    screen: AbstractContainerScreen<*>,
    measurements: Measurements,
    layout: StorageSettingsPanelLayout,
    mouseX: Int,
    mouseY: Int,
): InputHandlingResult {
    if (layout.close.contains(mouseX, mouseY)) {
        isStorageSettingsPanelOpen = false
        draggedStorageVisualSetting = null
        lastStorageSettingsOutcome = "closed"
        SoundUtilities.playClickSound()
        return InputHandlingResult.CONSUMED
    }
    val setting = layout.settingAt(mouseX, mouseY) ?: return InputHandlingResult.CONSUMED
    SoundUtilities.playClickSound()
    if (setting.isToggle) {
        setting.set(if (setting.value() == 0) 1 else 0)
        saveStorageSettings("${setting.name.lowercase()}=${setting.displayValue()}")
        storageOverlayLayoutScreen(screen, shouldReadScreen = false)
    } else {
        draggedStorageVisualSetting = setting
        updateStorageSettingFromPointer(screen, measurements, layout, setting, mouseX)
    }
    return InputHandlingResult.CONSUMED
}

internal fun resetStorageSettingsPanel() {
    isStorageSettingsPanelOpen = false
    storageSettingsPanelProgress = 0f
    draggedStorageVisualSetting = null
    storageSettingsTransition.snap(0f)
}

private fun updateStorageSettingFromPointer(
    screen: AbstractContainerScreen<*>,
    measurements: Measurements,
    layout: StorageSettingsPanelLayout,
    setting: StorageVisualSetting,
    pointerX: Int,
) {
    setting.set(
        storageSettingValueAt(
            pointerX,
            layout.track(setting),
            setting.range(screen.width, screen.height, measurements),
            setting.step(),
        ),
    )
    lastStorageSettingsOutcome = "dragging:${setting.name.lowercase()}=${setting.displayValue()}"
    storageOverlayLayoutScreen(screen, shouldReadScreen = false)
}

private fun drawStorageVisualSetting(
    context: GuiGraphicsExtractor,
    layout: StorageSettingsPanelLayout,
    setting: StorageVisualSetting,
    screenWidth: Int,
    screenHeight: Int,
    measurements: Measurements,
    mouseX: Int,
    mouseY: Int,
) {
    val font = Minecraft.getInstance().font
    val row = layout.row(setting)
    context.text(
        font,
        setting.label,
        row.x,
        row.y + StorageSettingsPanel.TEXT_Y,
        PixelControlColors.MUTED_TEXT,
        false,
    )
    if (setting.isToggle) {
        val toggle = layout.toggle(setting)
        PixelButtonRenderer.draw(
            context,
            font,
            toggle,
            setting.displayValue(),
            setting.value() != 0,
            toggle.contains(mouseX, mouseY),
            true,
        )
        return
    }
    val range = setting.range(screenWidth, screenHeight, measurements)
    val currentValue = setting.value().coerceIn(range)
    val value = currentValue.toString()
    context.text(
        font,
        value,
        row.x + row.width - font.width(value),
        row.y + StorageSettingsPanel.TEXT_Y,
        PixelControlColors.TEXT,
        false,
    )
    val track = layout.track(setting)
    val progress = if (range.first >= range.last) {
        0f
    } else {
        ((currentValue - range.first).toFloat() / (range.last - range.first)).coerceIn(0f, 1f)
    }
    PixelSliderRenderer.draw(context, track, progress, row.contains(mouseX, mouseY))
}

private fun drawStorageSettingsButton(
    context: GuiGraphicsExtractor,
    layout: StorageSettingsPanelLayout,
    mouseX: Int,
    mouseY: Int,
) {
    val isHovered = layout.button.contains(mouseX, mouseY)
    PixelButtonRenderer.draw(
        context,
        Minecraft.getInstance().font,
        layout.button,
        "",
        false,
        isHovered,
        true,
    )
    context.item(ItemStack(Items.REDSTONE_TORCH), layout.button.x + 1, layout.button.y + 1)
    if (isHovered) SkysoftNativeTooltip.setForNextFrame(context, listOf("§eStorage Settings"), mouseX, mouseY)
}

private fun saveStorageSettings(outcome: String) {
    lastStorageSettingsOutcome = outcome
    SkysoftConfigGui.config().saveNow()
}

private const val STORAGE_SETTINGS_ANIMATION_NANOS = 180_000_000L
private const val MIN_VISIBLE_PROGRESS = 0.001f
