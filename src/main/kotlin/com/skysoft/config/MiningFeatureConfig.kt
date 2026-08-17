package com.skysoft.config

import com.google.gson.annotations.Expose
import com.skysoft.config.core.HudPosition
import com.skysoft.data.SkyBlockIsland
import com.skysoft.features.mining.MiningAbilityCooldownDisplay
import com.skysoft.features.misc.conditions.FeatureConditions
import io.github.notenoughupdates.moulconfig.ChromaColour
import io.github.notenoughupdates.moulconfig.annotations.Accordion
import io.github.notenoughupdates.moulconfig.annotations.Category
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorColour
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorCombinations
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorDropdown
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption
import io.github.notenoughupdates.moulconfig.observer.Property

class MiningFeatureConfig {
    @JvmField
    @field:Expose
    @field:Category(name = "Mining Ability Cooldown", desc = "Show when your Pickaxe Ability is ready.")
    val abilityCooldown = MiningAbilityCooldownConfig()
}

class MiningAbilityCooldownConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Enabled", desc = "Show your Pickaxe Ability cooldown.")
    @field:MainFeatureToggle
    @field:ConfigEditorBoolean
    var enabled = false

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Settings", desc = "Mining Ability Cooldown settings.")
    @field:Accordion
    val settings = MiningAbilityCooldownSettingsConfig()

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Details", desc = "Mining Ability Cooldown appearance.")
    @field:Accordion
    val details = MiningAbilityCooldownDetailsConfig()

    @JvmField
    @field:Expose
    val position = HudPosition(40, 0, centerX = true, centerY = true).rememberDefault()
}

class MiningAbilityCooldownSettingsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Orientation", desc = "Choose a vertical or horizontal cooldown bar.")
    @field:ConfigEditorDropdown
    var orientation = MiningAbilityBarOrientation.VERTICAL

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Ready Text", desc = "Choose where Ready appears when the ability is available.")
    @field:ConfigEditorDropdown
    var readyText = MiningAbilityReadyTextPosition.RIGHT

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Locations", desc = "Any matching island or event shows the display.")
    @field:ConfigEditorCombinations(provider = MiningAbilityCooldownCombinationsProvider::class)
    val locations: MutableList<FeatureConditionCombination> = defaultMiningAbilityLocations()
}

class MiningAbilityCooldownDetailsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Ready Color", desc = "Color used when the ability is ready.")
    @field:ConfigEditorColour
    val readyColor: Property<ChromaColour> = Property.of(configColor(MINING_ABILITY_READY_COLOR))

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Empty Color", desc = "Color used when the ability has just been used.")
    @field:ConfigEditorColour
    val emptyColor: Property<ChromaColour> = Property.of(configColor(MINING_ABILITY_EMPTY_COLOR))
}

object MiningAbilityCooldownCombinationsProvider : FeatureCombinationsProvider() {
    override fun getChoices(): List<FeatureCondition> = FeatureConditions.builtInConditions()

    override fun onChanged() = MiningAbilityCooldownDisplay.markConditionsChanged()
}

enum class MiningAbilityBarOrientation(private val displayName: String) {
    VERTICAL("Vertical"),
    HORIZONTAL("Horizontal"),
    ;

    override fun toString(): String = displayName
}

enum class MiningAbilityReadyTextPosition(private val displayName: String) {
    OFF("Off"),
    RIGHT("Right"),
    BOTTOM("Bottom"),
    TOP("Top"),
    LEFT("Left"),
    ;

    override fun toString(): String = displayName
}

private fun defaultMiningAbilityLocations(): MutableList<FeatureConditionCombination> = listOf(
    SkyBlockIsland.GOLD_MINE,
    SkyBlockIsland.DEEP_CAVERNS,
    SkyBlockIsland.DWARVEN_MINES,
    SkyBlockIsland.CRYSTAL_HOLLOWS,
    SkyBlockIsland.GLACITE_TUNNELS,
    SkyBlockIsland.GLACITE_MINESHAFTS,
).mapTo(mutableListOf()) { island ->
    FeatureConditionCombination(
        mutableListOf(FeatureCondition(FeatureConditionKind.ISLAND, island.name, "On Island: ${island.displayName}")),
    )
}

private const val MINING_ABILITY_READY_COLOR = 0xFF55FF55.toInt()
private const val MINING_ABILITY_EMPTY_COLOR = 0xFFFF5555.toInt()
