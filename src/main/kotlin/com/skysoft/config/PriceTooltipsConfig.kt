package com.skysoft.config

import com.google.gson.annotations.Expose
import com.skysoft.config.core.ConfigRepairable
import io.github.notenoughupdates.moulconfig.ChromaColour
import io.github.notenoughupdates.moulconfig.annotations.Accordion
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorColour
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorDropdown
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorDraggableList
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorKeybind
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption
import io.github.notenoughupdates.moulconfig.observer.Property
import org.lwjgl.glfw.GLFW

class PriceTooltipsConfig : ConfigRepairable {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Enabled", desc = "Show configured prices on item tooltips.")
    @field:ConfigEditorBoolean
    var enabled = false

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Settings", desc = "Price tooltip settings.")
    @field:Accordion
    val settings = PriceTooltipsSettingsConfig()

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Details", desc = "Price tooltip appearance.")
    @field:Accordion
    val details = PriceTooltipsDetailsConfig()

    override fun repairLoadedValues() {
        settings.repairLoadedValues()
    }
}

class PriceTooltipsSettingsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Require Key", desc = "Only show price lines while the hotkey is held.")
    @field:ConfigEditorBoolean
    var requireKey = false

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Hotkey", desc = "Hold this key to show price lines when Require Key is enabled.")
    @field:ConfigEditorKeybind(defaultKey = GLFW.GLFW_KEY_LEFT_SHIFT)
    var hotkey = GLFW.GLFW_KEY_LEFT_SHIFT

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Price Lines", desc = "Choose and order the prices shown in item tooltips.")
    @field:ConfigEditorDraggableList
    val priceLines: Property<MutableList<PriceTooltipLine>> = Property.of(defaultPriceTooltipLines())

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Bazaar Wording", desc = "Use Order or Offer names for bazaar order prices.")
    @field:ConfigEditorDropdown
    var bazaarWording = BazaarTooltipWording.ORDERS

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Stack Total Key", desc = "Hold this key to show prices for the full stack.")
    @field:ConfigEditorKeybind(defaultKey = GLFW.GLFW_KEY_LEFT_SHIFT)
    var stackTotalKey = GLFW.GLFW_KEY_LEFT_SHIFT

    @JvmField
    @field:Expose(serialize = false, deserialize = true)
    var legacyBazaarPriceType: BazaarPriceType? = null

    fun repairLoadedValues() {
        legacyBazaarPriceType?.let { legacyType ->
            priceLines.get().apply {
                clear()
                addAll(
                    when (legacyType) {
                        BazaarPriceType.ORDER_PRICES -> defaultPriceTooltipLines()
                        BazaarPriceType.INSTANT_PRICES -> mutableListOf(
                            PriceTooltipLine.BAZAAR_INSTANT_BUY,
                            PriceTooltipLine.BAZAAR_INSTANT_SELL,
                            PriceTooltipLine.LOWEST_BIN,
                        )
                    },
                )
            }
            legacyBazaarPriceType = null
        }
        val uniqueLines = priceLines.get().distinct()
        priceLines.get().apply {
            clear()
            addAll(uniqueLines)
        }
    }
}

class PriceTooltipsDetailsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Bold Text", desc = "Show price tooltip text in bold.")
    @field:ConfigEditorBoolean
    var boldText = true

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Label Color", desc = "Color used for price labels.")
    @field:ConfigEditorColour
    val labelColor: Property<ChromaColour> = Property.of(ChromaColour.fromRGB(255, 255, 85, 0, 255))

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Price Color", desc = "Color used for price values.")
    @field:ConfigEditorColour
    val priceColor: Property<ChromaColour> = Property.of(ChromaColour.fromRGB(255, 170, 0, 0, 255))
}

private fun defaultPriceTooltipLines(): MutableList<PriceTooltipLine> = mutableListOf(
    PriceTooltipLine.BAZAAR_BUY_ORDER,
    PriceTooltipLine.BAZAAR_SELL_ORDER,
    PriceTooltipLine.LOWEST_BIN,
)

enum class PriceTooltipLine(private val displayName: String) {
    BAZAAR_BUY_ORDER("Bazaar Buy Order"),
    BAZAAR_SELL_ORDER("Bazaar Sell Order"),
    BAZAAR_INSTANT_BUY("Bazaar Instant Buy"),
    BAZAAR_INSTANT_SELL("Bazaar Instant Sell"),
    LOWEST_BIN("Lowest BIN"),
    NPC_SELL_PRICE("NPC Sell Price"),
    RAW_CRAFT_COST("Raw Craft Cost"),
    ;

    val needsBazaarData: Boolean
        get() = this in BAZAAR_LINES || this == RAW_CRAFT_COST

    val needsLowestBinData: Boolean
        get() = this == LOWEST_BIN || this == RAW_CRAFT_COST

    override fun toString(): String = displayName

    companion object {
        private val BAZAAR_LINES = setOf(
            BAZAAR_BUY_ORDER,
            BAZAAR_SELL_ORDER,
            BAZAAR_INSTANT_BUY,
            BAZAAR_INSTANT_SELL,
        )
    }
}

enum class BazaarTooltipWording(private val displayName: String) {
    ORDERS("Orders"),
    OFFERS("Offers"),
    ;

    override fun toString(): String = displayName
}

enum class BazaarPriceType(private val displayName: String) {
    ORDER_PRICES("Order Prices"),
    INSTANT_PRICES("Instant Prices"),
    ;

    override fun toString(): String = displayName
}
