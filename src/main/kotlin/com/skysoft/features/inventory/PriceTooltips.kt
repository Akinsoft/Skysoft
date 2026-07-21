package com.skysoft.features.inventory

import com.skysoft.config.PriceTooltipLine
import com.skysoft.config.SkysoftConfigGui
import com.skysoft.data.skyblock.ItemListEntryKind
import com.skysoft.data.skyblock.SkyBlockDataRepository
import com.skysoft.data.skyblock.SkyBlockItemId.skyBlockId
import com.skysoft.data.skyblock.SkyBlockItemUtilities.loreLines
import com.skysoft.data.skyblock.price.SkyBlockPriceData
import com.skysoft.utils.MinecraftClient
import com.skysoft.utils.NumberUtilities.romanToDecimal
import com.skysoft.utils.SkysoftErrorBoundary
import com.skysoft.utils.TextUtilities.cleanSkyBlockText
import com.skysoft.utils.input.InputUtilities
import java.util.Locale
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

object PriceTooltips {
    private val config get() = SkysoftConfigGui.config().inventory.priceTooltips
    private var catalogVersion = Long.MIN_VALUE
    private var catalogIdsByDisplayName: Map<String, Set<String>> = emptyMap()

    fun register() {
        PriceTooltipRawCraftCosts.register()
        SkyBlockDataRepository.Demand.register("Price Tooltips") {
            config.enabled && config.settings.priceLines.get().isNotEmpty()
        }
        ItemTooltipCallback.EVENT.register { stack, _, _, tooltip ->
            SkysoftErrorBoundary.run("Price Tooltip rendering") tooltip@{
                if (!shouldShow()) return@tooltip
                if (config.settings.priceLines.get().isEmpty()) return@tooltip

                val itemId = priceItemId(stack) ?: return@tooltip
                val priceLines = createPriceLines(itemId, stack.count)
                if (priceLines.isEmpty()) return@tooltip

                tooltip.add(Component.literal(""))
                tooltip.addAll(priceLines)
            }
        }
    }

    private fun shouldShow(): Boolean {
        if (!config.enabled) return false
        if (!config.settings.requireKey) return true
        return InputUtilities.isBindingDown(config.settings.hotkey)
    }

    internal fun priceItemId(stack: ItemStack): String? {
        val directId = stack.skyBlockId()
        if (directId != null) return directId
        return menuCatalogItemId(stack)
    }

    private fun menuCatalogItemId(stack: ItemStack): String? {
        val menuTitle = MinecraftClient.screen()?.title?.cleanSkyBlockText() ?: return null
        val itemName = stack.hoverName.cleanSkyBlockText()
        val lore = stack.loreLines().map { it.cleanSkyBlockText() }
        val experimentationId = resolveExperimentationTableItemId(
            menuTitle = menuTitle,
            itemName = itemName,
            lore = lore,
            resolveDisplayName = ::bazaarCatalogId,
        )
        if (experimentationId != null) return experimentationId
        val bazaarEnchantmentId = if (stack.item == Items.ENCHANTED_BOOK) {
            resolveBazaarEnchantmentItemId(
                menuTitle = menuTitle,
                itemName = itemName,
                lore = lore,
                resolveDisplayName = ::bazaarCatalogId,
            )
        } else {
            null
        }
        return bazaarEnchantmentId
    }

    private fun bazaarCatalogId(displayName: String): String? {
        refreshCatalogIndex()
        return catalogIdsByDisplayName[displayName.lowercase(Locale.ROOT)]
            ?.filter { SkyBlockPriceData.getBazaarPrice(it) != null }
            ?.singleOrNull()
    }

    private fun refreshCatalogIndex() {
        val currentVersion = SkyBlockDataRepository.snapshotVersion
        if (catalogVersion == currentVersion) return
        catalogVersion = currentVersion
        val idsByDisplayName = linkedMapOf<String, MutableSet<String>>()
        SkyBlockDataRepository.entries
            .asSequence()
            .filter { it.key.kind == ItemListEntryKind.SKYBLOCK }
            .forEach { entry ->
                listOfNotNull(entry.displayName, enchantmentCatalogDisplayName(entry.key.id))
                    .distinct()
                    .forEach { displayName ->
                        idsByDisplayName
                            .getOrPut(displayName.lowercase(Locale.ROOT), ::linkedSetOf)
                            .add(entry.key.id)
                    }
            }
        catalogIdsByDisplayName = idsByDisplayName
    }

    private fun createPriceLines(itemId: String, stackCount: Int): List<Component> {
        val selectedLines = config.settings.priceLines.get().distinct()
        val hasBazaarLine = selectedLines.any(PriceTooltipLine::needsBazaarData)
        val hasLowestBinLine = selectedLines.any(PriceTooltipLine::needsLowestBinData)
        val stackMultiplier = if (
            stackCount > 1 && InputUtilities.isBindingDown(config.settings.stackTotalKey)
        ) {
            stackCount
        } else {
            1
        }
        val values = collectPriceTooltipValues(
            selectedLines = selectedLines,
            wording = config.settings.bazaarWording,
            bazaar = if (hasBazaarLine) SkyBlockPriceData.getBazaarPrice(itemId) else null,
            lowestBin = if (hasLowestBinLine) SkyBlockPriceData.getLowestBin(itemId)?.toDouble() else null,
            npcSellPrice = if (PriceTooltipLine.NPC_SELL_PRICE in selectedLines) {
                SkyBlockPriceData.getNpcSellPrice(itemId)
            } else {
                null
            },
            rawCraftCost = { PriceTooltipRawCraftCosts.cost(itemId) },
        )
        val details = config.details
        val labelRgb = details.labelColor.get().getEffectiveColourRGB()
        val priceRgb = details.priceColor.get().getEffectiveColourRGB()
        return values.map { value ->
            formatPriceTooltipValue(
                value = value,
                stackMultiplier = stackMultiplier,
                labelRgb = labelRgb,
                priceRgb = priceRgb,
                isBold = details.boldText,
            )
        }
    }
}

internal fun resolveExperimentationTableItemId(
    menuTitle: String,
    itemName: String,
    lore: List<String>,
    resolveDisplayName: (String) -> String?,
): String? {
    val candidates = when {
        menuTitle.startsWith(SUPERPAIRS_MENU_PREFIX) ->
            if (itemName == ENCHANTED_BOOK_NAME) lore else listOf(itemName)
        menuTitle.endsWith(EXPERIMENTATION_RNG_MENU_SUFFIX) -> listOf(itemName)
        else -> return null
    }
    return candidates.asSequence()
        .filter(String::isNotBlank)
        .map(::catalogDisplayName)
        .mapNotNull(resolveDisplayName)
        .firstOrNull()
}

internal fun resolveBazaarEnchantmentItemId(
    menuTitle: String,
    itemName: String,
    lore: List<String>,
    resolveDisplayName: (String) -> String?,
): String? {
    val romanTier = itemName.substringAfterLast(' ')
    if (romanTier.romanToDecimal() == 0) return null
    val enchantmentName = itemName.removeSuffix(" $romanTier")
    val menuPath = menuTitle.split(BAZAAR_MENU_SEPARATOR).map(String::trim)
    if (menuPath.size != 2) return null
    val isTierListEntry = menuPath.first().endsWith(ENCHANTMENTS_MENU_SUFFIX) &&
        menuPath.last() == enchantmentName &&
        lore.any { it.endsWith(COMMODITY_LORE_SUFFIX) } &&
        BAZAAR_DETAILS_LORE in lore
    val isProductDetailsEntry = menuPath.first() == enchantmentName && menuPath.last() == itemName
    if (!isTierListEntry && !isProductDetailsEntry) return null
    return resolveDisplayName(catalogDisplayName(itemName))?.takeIf { it.startsWith(ENCHANTMENT_ID_PREFIX) }
}

internal fun enchantmentCatalogDisplayName(itemId: String): String? {
    if (!itemId.startsWith(ENCHANTMENT_ID_PREFIX)) return null
    val tier = itemId.substringAfterLast('_').toIntOrNull() ?: return null
    val encodedName = itemId
        .removePrefix(ENCHANTMENT_ID_PREFIX)
        .substringBeforeLast('_')
        .removePrefix(ULTIMATE_ENCHANTMENT_ID_PREFIX)
    if (encodedName.isBlank()) return null
    val displayName = encodedName.split('_').joinToString(" ") { word ->
        word.lowercase(Locale.ROOT).replaceFirstChar(Char::uppercase)
    }
    return "$displayName $tier"
}

private fun catalogDisplayName(displayName: String): String {
    val romanTier = displayName.substringAfterLast(' ')
    val tier = romanTier.romanToDecimal()
    return if (tier == 0) displayName else displayName.removeSuffix(romanTier) + tier
}

private const val SUPERPAIRS_MENU_PREFIX = "Superpairs"
private const val EXPERIMENTATION_RNG_MENU_SUFFIX = "Experimentation Table RNG"
private const val ENCHANTED_BOOK_NAME = "Enchanted Book"
private const val BAZAAR_MENU_SEPARATOR = "➜"
private const val ENCHANTMENTS_MENU_SUFFIX = "Enchantments"
private const val COMMODITY_LORE_SUFFIX = "commodity"
private const val BAZAAR_DETAILS_LORE = "Click to view details!"
private const val ENCHANTMENT_ID_PREFIX = "ENCHANTMENT_"
private const val ULTIMATE_ENCHANTMENT_ID_PREFIX = "ULTIMATE_"
