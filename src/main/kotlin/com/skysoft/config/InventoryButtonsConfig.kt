package com.skysoft.config

import com.google.gson.annotations.Expose
import com.skysoft.config.core.ConfigRepairable
import com.skysoft.features.inventory.InventoryButtonEditorScreen
import io.github.notenoughupdates.moulconfig.annotations.Accordion
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorButton
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorDropdown
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorSlider
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption
import org.lwjgl.glfw.GLFW

class InventoryButtonsConfig : ConfigRepairable {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Enabled", desc = "Show custom command buttons on inventory screens.")
    @field:MainFeatureToggle
    @field:ConfigEditorBoolean
    var enabled = false

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Settings", desc = "Inventory button settings.")
    @field:Accordion
    val settings = InventoryButtonsSettingsConfig()

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Details", desc = "Inventory button visual settings.")
    @field:Accordion
    val details = InventoryButtonsDetailsConfig()

    @JvmField
    @field:Expose
    var buttons: MutableList<InventoryButtonConfig> = InventoryButtonDefaults.create()

    @JvmField
    @field:Expose
    var activePreset = 0

    @JvmField
    @field:Expose
    var presets: MutableList<InventoryButtonPresetConfig> = mutableListOf()

    override fun repairLoadedValues() {
        buttons = repairedButtons(buttons)
        presets = presets.take(PRESET_COUNT).toMutableList()
        if (presets.isEmpty()) {
            presets += InventoryButtonPresetConfig(defaultPresetName(0), buttons.mapTo(mutableListOf()) { it.copy() })
        }
        while (presets.size < PRESET_COUNT) {
            presets += InventoryButtonPresetConfig(defaultPresetName(presets.size))
        }
        presets.forEachIndexed { index, preset ->
            preset.name = preset.name.trim().take(PRESET_NAME_MAX_LENGTH).ifBlank { defaultPresetName(index) }
            preset.buttons = repairedButtons(preset.buttons)
        }
        activePreset = activePreset.coerceIn(presets.indices)
        storeActivePreset()
    }

    fun storeActivePreset() {
        presets.getOrNull(activePreset)?.buttons = buttons.mapTo(mutableListOf()) { it.copy() }
    }

    fun switchPreset(index: Int) {
        if (index !in presets.indices || index == activePreset) return
        storeActivePreset()
        activePreset = index
        buttons = presets[index].buttons.mapTo(mutableListOf()) { it.copy() }
    }

    fun replaceActiveButtons(replacement: List<InventoryButtonConfig>) {
        buttons = replacement.mapTo(mutableListOf()) { it.copy() }
        buttons = repairedButtons(buttons)
        storeActivePreset()
    }

    fun renamePreset(index: Int, name: String) {
        val preset = presets.getOrNull(index) ?: return
        preset.name = name.trim().take(PRESET_NAME_MAX_LENGTH).ifBlank { defaultPresetName(index) }
    }

    private fun repairedButtons(loadedButtons: MutableList<InventoryButtonConfig>): MutableList<InventoryButtonConfig> {
        val repaired = InventoryButtonDefaults.resettableButtons(loadedButtons)
        repaired.forEachIndexed { index, button ->
            button.repairLoadedValues(isLegacyExtraButton = index >= InventoryButtonDefaults.DEFAULT_BUTTON_COUNT)
        }
        return repaired
    }

    companion object {
        const val PRESET_COUNT = 3
        const val PRESET_NAME_MAX_LENGTH = 24

        fun defaultPresetName(index: Int): String = "Preset ${index + 1}"
    }
}

class InventoryButtonPresetConfig(
    @JvmField @field:Expose var name: String = "",
    @JvmField @field:Expose var buttons: MutableList<InventoryButtonConfig> = InventoryButtonDefaults.create(),
)

class InventoryButtonsSettingsConfig {
    @JvmField
    @field:ConfigOption(name = "Open Button Editor", desc = "Open the inventory button editor.")
    @field:ConfigEditorButton(buttonText = "Open")
    val openEditor = Runnable { InventoryButtonEditorScreen.open() }

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Button Click Type", desc = "Choose whether buttons trigger when the mouse is pressed or released.")
    @field:ConfigEditorDropdown
    var clickType = InventoryButtonClickType.MOUSE_DOWN
}

class InventoryButtonsDetailsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Tooltip Delay", desc = "Delay before showing a button's command tooltip, in milliseconds.")
    @field:ConfigEditorSlider(minValue = 0f, maxValue = 1500f, minStep = 50f)
    var tooltipDelay = 600
}

enum class InventoryButtonClickType(private val displayName: String) {
    MOUSE_DOWN("Mouse Down"),
    MOUSE_UP("Mouse Up"),
    ;

    override fun toString(): String = displayName
}

class InventoryButtonConfig(
    @JvmField @field:Expose var x: Int = 0,
    @JvmField @field:Expose var y: Int = 0,
    @JvmField @field:Expose var icon: String? = null,
    @JvmField @field:Expose var playerInvOnly: Boolean = false,
    @JvmField @field:Expose var anchorRight: Boolean = false,
    @JvmField @field:Expose var anchorBottom: Boolean = false,
    @JvmField @field:Expose var backgroundIndex: Int = 0,
    @JvmField @field:Expose var command: String = "",
    @JvmField @field:Expose var requiredKey: Int = GLFW.GLFW_KEY_UNKNOWN,
    @JvmField @field:Expose var scale: Float = DEFAULT_INVENTORY_BUTTON_SCALE,
    @JvmField @field:Expose var isUserCreated: Boolean? = null,
) {
    fun isActive(): Boolean = command.trim().isNotEmpty()

    fun copy(): InventoryButtonConfig = InventoryButtonConfig(
        x = x,
        y = y,
        icon = icon,
        playerInvOnly = playerInvOnly,
        anchorRight = anchorRight,
        anchorBottom = anchorBottom,
        backgroundIndex = backgroundIndex,
        command = command,
        requiredKey = requiredKey,
        scale = scale,
        isUserCreated = isUserCreated,
    )

    fun repairLoadedValues(isLegacyExtraButton: Boolean = false) {
        backgroundIndex = backgroundIndex.coerceIn(MIN_BUTTON_BACKGROUND_INDEX, MAX_BUTTON_BACKGROUND_INDEX)
        command = command.trimStart()
        icon = icon?.trim()?.takeIf { it.isNotEmpty() }
        requiredKey = requiredKey.takeIf { it in GLFW.GLFW_KEY_SPACE..GLFW.GLFW_KEY_LAST }
            ?: GLFW.GLFW_KEY_UNKNOWN
        scale = scale
            .takeIf(Float::isFinite)
            ?.takeIf { it > 0f }
            ?.coerceIn(MIN_INVENTORY_BUTTON_SCALE, MAX_INVENTORY_BUTTON_SCALE)
            ?: DEFAULT_INVENTORY_BUTTON_SCALE
        isUserCreated = isUserCreated ?: isLegacyExtraButton
    }
}

const val MIN_INVENTORY_BUTTON_SCALE = 0.5f

const val MAX_INVENTORY_BUTTON_SCALE = 3f

const val DEFAULT_INVENTORY_BUTTON_SCALE = 1f

const val INVENTORY_BUTTON_SCALE_STEP = 0.1f

private const val MIN_BUTTON_BACKGROUND_INDEX = 0

private const val MAX_BUTTON_BACKGROUND_INDEX = 6
