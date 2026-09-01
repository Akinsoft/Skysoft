package com.skysoft.features.inventory

import com.google.gson.Gson
import com.skysoft.config.SkysoftConfigGui
import com.skysoft.data.hypixel.HypixelLocationState
import com.skysoft.data.skyblock.SkyBlockItemUtilities.extraAttributes
import com.skysoft.data.skyblock.SkyBlockItemUtilities.getStringOrNull
import com.skysoft.data.skyblock.SkyBlockItemUtilities.skyBlockEnchantments
import com.skysoft.data.skyblock.itemCategoryFromLore
import com.skysoft.utils.ItemTooltipEvents
import com.skysoft.utils.NumberUtilities.romanNumeral
import com.skysoft.utils.render.ChromaTextRendering
import io.github.notenoughupdates.moulconfig.ChromaColour
import java.util.Locale
import java.util.Optional
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style

object MaxEnchantChroma {
    private val config get() = SkysoftConfigGui.config().inventory.maxEnchantChroma
    private val enchantments by lazy(::loadGeneratedMaxEnchantments)

    fun register() {
        ItemTooltipEvents.register(
            "Max Enchant Chroma tooltip",
            isActive = { config.enabled && HypixelLocationState.inSkyBlock },
        ) tooltip@{ stack, _, _, tooltip ->
            val extraAttributes = stack.extraAttributes()
            val labels = maxedEnchantmentLabels(
                extraAttributes?.skyBlockEnchantments().orEmpty(),
                enchantments,
                config.settings.includeUltimateEnchantments,
                maximumEfficiencyLevel(extraAttributes?.getStringOrNull("id"), itemCategoryFromLore(tooltip)),
            )
            if (labels.isEmpty()) return@tooltip
            tooltip.indices.forEach { index ->
                tooltip[index] = applyMaxEnchantChroma(tooltip[index], labels, MAX_ENCHANT_CHROMA)
            }
        }
    }
}

internal data class MaxEnchantment(
    val displayName: String,
    val level: Int,
)

internal fun readMaxEnchantments(json: String): Map<String, MaxEnchantment> {
    val catalog = Gson().fromJson(json, GeneratedMaxEnchantments::class.java)
    require(catalog.schemaVersion == MAX_ENCHANTMENT_SCHEMA_VERSION)
    val result = catalog.enchantments.associate { enchantment ->
        require(enchantment.id.isNotBlank() && enchantment.name.isNotBlank() && enchantment.maxLevel > 0)
        enchantment.id to MaxEnchantment(enchantment.name, enchantment.maxLevel)
    }
    require(result.size == catalog.enchantments.size && result.size >= MINIMUM_ENCHANTMENT_COUNT)
    return result
}

private fun loadGeneratedMaxEnchantments(): Map<String, MaxEnchantment> {
    val json = MaxEnchantChroma::class.java.getResourceAsStream(MAX_ENCHANTMENT_RESOURCE)
        ?.bufferedReader()
        ?.use { reader -> reader.readText() }
        ?: error("Max enchantment data is missing")
    return readMaxEnchantments(json)
}

private data class GeneratedMaxEnchantments(
    val schemaVersion: Int = 0,
    val enchantments: List<GeneratedMaxEnchantment> = emptyList(),
)

private data class GeneratedMaxEnchantment(
    val id: String = "",
    val name: String = "",
    val maxLevel: Int = 0,
)

internal fun maxedEnchantmentLabels(
    appliedEnchantments: Map<String, Int>,
    maximums: Map<String, MaxEnchantment>,
    includeUltimateEnchantments: Boolean,
    efficiencyMaximumLevel: Int,
): Set<String> = buildSet {
    appliedEnchantments.forEach { (key, level) ->
        val id = key.lowercase(Locale.ROOT)
        if (!includeUltimateEnchantments && id.startsWith(ULTIMATE_ENCHANTMENT_PREFIX)) return@forEach
        val enchantment = maximums[id] ?: return@forEach
        val maximumLevel = if (id == EFFICIENCY_ENCHANTMENT_ID) efficiencyMaximumLevel else enchantment.level
        if (level < maximumLevel) return@forEach
        add("${enchantment.displayName} ${level.romanNumeral()}")
        add("${enchantment.displayName} $level")
    }
}

internal fun maximumEfficiencyLevel(itemId: String?, itemCategory: String?): Int = when {
    itemId == STONK_ITEM_ID -> STONK_EFFICIENCY_MAX_LEVEL
    itemId == PROMISING_SHOVEL_ITEM_ID || itemCategory in EXTENDED_EFFICIENCY_ITEM_CATEGORIES ->
        EXTENDED_EFFICIENCY_MAX_LEVEL
    else -> STANDARD_EFFICIENCY_MAX_LEVEL
}

internal fun applyMaxEnchantChroma(
    line: Component,
    labels: Set<String>,
    colour: ChromaColour,
): Component {
    val text = line.string
    if (!ENCHANTMENT_LINE_PATTERN.matches(text)) return line
    val highlighted = BooleanArray(text.length)
    labels.forEach { label ->
        var searchFrom = 0
        while (searchFrom < text.length) {
            val start = text.indexOf(label, searchFrom)
            if (start < 0) break
            val end = start + label.length
            if (text.getOrNull(start - 1)?.isLetterOrDigit() != true && text.getOrNull(end)?.isLetterOrDigit() != true) {
                for (index in start until end) highlighted[index] = true
            }
            searchFrom = end
        }
    }
    if (highlighted.none { it }) return line

    val result = Component.empty()
    var offset = 0
    line.visit({ style: Style, segment: String ->
        var start = 0
        while (start < segment.length) {
            val usesChroma = highlighted.getOrElse(offset + start) { false }
            var end = start + 1
            while (end < segment.length && highlighted.getOrElse(offset + end) { false } == usesChroma) end++
            val segmentStyle = if (usesChroma) ChromaTextRendering.apply(style, colour) else style
            result.append(Component.literal(segment.substring(start, end)).withStyle(segmentStyle))
            start = end
        }
        offset += segment.length
        Optional.empty<Unit>()
    }, Style.EMPTY)
    return result
}

private val MAX_ENCHANT_CHROMA = ChromaColour(0f, 0.75f, 1f, 3_000, 255)
private val ENCHANTMENT_LINE_PATTERN = Regex(
    "^[A-Za-z][A-Za-z '\\-]+ (?:[IVXLCDM]+|[0-9]+)(?: [0-9][0-9,.]*[kKmMbB]?)?" +
        "(?:, [A-Za-z][A-Za-z '\\-]+ (?:[IVXLCDM]+|[0-9]+)(?: [0-9][0-9,.]*[kKmMbB]?)?)*$",
)
private val EXTENDED_EFFICIENCY_ITEM_CATEGORIES = setOf("PICKAXE", "DRILL", "GAUNTLET")
private const val MAX_ENCHANTMENT_RESOURCE = "/assets/skysoft/data/max_enchantments.json"
private const val MAX_ENCHANTMENT_SCHEMA_VERSION = 1
private const val MINIMUM_ENCHANTMENT_COUNT = 150
private const val EFFICIENCY_ENCHANTMENT_ID = "efficiency"
private const val PROMISING_SHOVEL_ITEM_ID = "PROMISING_SPADE"
private const val STONK_ITEM_ID = "STONK_PICKAXE"
private const val STANDARD_EFFICIENCY_MAX_LEVEL = 5
private const val STONK_EFFICIENCY_MAX_LEVEL = 6
private const val EXTENDED_EFFICIENCY_MAX_LEVEL = 10
private const val ULTIMATE_ENCHANTMENT_PREFIX = "ultimate_"
