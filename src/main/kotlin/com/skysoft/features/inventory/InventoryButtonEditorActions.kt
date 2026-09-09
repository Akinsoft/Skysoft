package com.skysoft.features.inventory

import com.skysoft.config.DEFAULT_INVENTORY_BUTTON_SCALE
import com.skysoft.config.InventoryButtonConfig
import com.skysoft.config.InventoryButtonDefaults
import com.skysoft.config.SkysoftConfigGui
import com.skysoft.gui.HudEditorSnapshot
import com.skysoft.gui.hudEditorSnapshot
import com.skysoft.utils.input.InputHandlingResult

internal fun inventoryButtonEditorState(): HudEditorSnapshot {
    val config = SkysoftConfigGui.config().inventory.inventoryButtons
    val buttons = config.buttons.map(InventoryButtonConfig::copy)
    val values = buttons.map(InventoryButtonConfig::editorValue)
    return hudEditorSnapshot(values) { config.replaceActiveButtons(buttons) }
}

private data class InventoryButtonEditorValue(
    val x: Int,
    val y: Int,
    val icon: String?,
    val playerInvOnly: Boolean,
    val anchorRight: Boolean,
    val anchorBottom: Boolean,
    val backgroundIndex: Int,
    val command: String,
    val requiredKey: Int,
    val scale: Float,
    val isUserCreated: Boolean?,
)

private fun InventoryButtonConfig.editorValue(): InventoryButtonEditorValue = InventoryButtonEditorValue(
    x,
    y,
    icon,
    playerInvOnly,
    anchorRight,
    anchorBottom,
    backgroundIndex,
    command,
    requiredKey,
    scale,
    isUserCreated,
)

internal enum class InventoryButtonResetShortcutResult {
    IGNORED,
    RESET,
    REMOVED,
}

internal object InventoryButtonEditorActions {
    private val config get() = SkysoftConfigGui.config().inventory.inventoryButtons

    fun resetButtonPosition(index: Int) {
        val button = config.buttons.getOrNull(index) ?: return
        InventoryButtonDefaults.create().getOrNull(index)?.let { default ->
            button.x = default.x
            button.y = default.y
            button.playerInvOnly = default.playerInvOnly
            button.anchorRight = default.anchorRight
            button.anchorBottom = default.anchorBottom
        }
        button.scale = DEFAULT_INVENTORY_BUTTON_SCALE
    }

    fun changeButtonScale(index: Int, scrollY: Double): InputHandlingResult {
        val button = config.buttons.getOrNull(index) ?: return InputHandlingResult.IGNORED
        if (scrollY == 0.0) return InputHandlingResult.IGNORED
        button.scale = inventoryButtonScaleAfterScroll(button.scale, scrollY)
        return InputHandlingResult.CONSUMED
    }

    fun addButtonSlot(): Int {
        config.buttons.add(InventoryButtonLayout.nextEditorButtonSlot(config.buttons))
        return config.buttons.lastIndex
    }

    fun resetOrRemoveButton(index: Int): InventoryButtonResetShortcutResult {
        val button = config.buttons.getOrNull(index) ?: return InventoryButtonResetShortcutResult.IGNORED
        if (button.isUserCreated == true) {
            config.buttons.removeAt(index)
            return InventoryButtonResetShortcutResult.REMOVED
        }
        resetButtonPosition(index)
        return InventoryButtonResetShortcutResult.RESET
    }
}
