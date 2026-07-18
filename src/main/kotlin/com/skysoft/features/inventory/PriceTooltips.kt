package com.skysoft.features.inventory

import com.skysoft.config.BazaarPriceType
import com.skysoft.config.SkysoftConfigGui
import com.skysoft.data.skyblock.ItemListEntryKind
import com.skysoft.data.skyblock.SkyBlockDataRepository
import com.skysoft.data.skyblock.SkyBlockItemId.skyBlockId
import com.skysoft.data.skyblock.SkyBlockItemUtilities.loreLines
import com.skysoft.data.skyblock.price.SkyBlockPriceData
import com.skysoft.utils.MinecraftClient
import com.skysoft.utils.NumberUtilities.coinAmountFormat
import com.skysoft.utils.NumberUtilities.romanToDecimal
import com.skysoft.utils.SkysoftErrorBoundary
import com.skysoft.utils.TextUtilities.cleanSkyBlockText
import com.skysoft.utils.input.InputUtilities
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack

object PriceTooltips {
    private val config get() = SkysoftConfigGui.config().inventory.priceTooltips
    private var catalogVersion = Long.MIN_VALUE
    private var catalogIdsByDisplayName: Map<String, List<String>> = emptyMap()

    fun register() {
        SkyBlockDataRepository.Demand.register("Price Tooltips") { config.enabled }
        ItemTooltipCallback.EVENT.register { stack, _, _, tooltip ->
            SkysoftErrorBoundary.run("Price Tooltip rendering") tooltip@{
                if (!shouldShow()) return@tooltip

                val itemId = priceItemId(stack) ?: return@tooltip
                val priceLines = createPriceLines(itemId)
                if (priceLines.isEmpty()) return@tooltip

                tooltip.add(Component.literal(""))
                tooltip.addAll(priceLines)
            }
        }
    }

    private fun shouldShow(): Boolean {
        if (!config.enabled) return false
        if (!config.settings.requireKey) return true
        return InputUtilities.isKeyDown(config.settings.hotkey)
    }

    internal fun priceItemId(stack: ItemStack): String? =
        stack.skyBlockId() ?: experimentationTableItemId(stack)

    private fun experimentationTableItemId(stack: ItemStack): String? {
        val menuTitle = MinecraftClient.screen()?.title?.cleanSkyBlockText() ?: return null
        return resolveExperimentationTableItemId(
            menuTitle = menuTitle,
            itemName = stack.hoverName.cleanSkyBlockText(),
            lore = stack.loreLines().map { it.cleanSkyBlockText() },
            resolveDisplayName = ::bazaarCatalogId,
        )
    }

    private fun bazaarCatalogId(displayName: String): String? {
        refreshCatalogIndex()
        return catalogIdsByDisplayName[displayName]
            ?.filter { SkyBlockPriceData.getBazaarPrice(it) != null }
            ?.singleOrNull()
    }

    private fun refreshCatalogIndex() {
        val currentVersion = SkyBlockDataRepository.snapshotVersion
        if (catalogVersion == currentVersion) return
        catalogVersion = currentVersion
        catalogIdsByDisplayName = SkyBlockDataRepository.entries
            .asSequence()
            .filter { it.key.kind == ItemListEntryKind.SKYBLOCK }
            .groupBy({ it.displayName }, { it.key.id })
    }

    private fun createPriceLines(itemId: String): List<Component> = buildList {
        SkyBlockPriceData.getBazaarPrice(itemId)?.let { bazaar ->
            when (config.settings.bazaarPriceType) {
                BazaarPriceType.ORDER_PRICES -> {
                    addPrice("Bazaar Buy Order", bazaar.buyOrderPrice)
                    addPrice("Bazaar Sell Order", bazaar.sellOrderPrice)
                }

                BazaarPriceType.INSTANT_PRICES -> {
                    addPrice("Bazaar Instant Buy", bazaar.instantBuyPrice)
                    addPrice("Bazaar Instant Sell", bazaar.instantSellPrice)
                }
            }
        }

        SkyBlockPriceData.getLowestBin(itemId)?.let { lowestBin ->
            add(formatPrice("Lowest BIN", lowestBin.toDouble()))
        }
    }

    private fun MutableList<Component>.addPrice(label: String, price: Double) {
        if (price <= 0.0) return
        add(formatPrice(label, price))
    }

    private fun formatPrice(label: String, price: Double): Component =
        Component.literal("")
            .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD)
            .append(Component.literal(label))
            .append(": ")
            .append(
                Component.literal("${price.coinAmountFormat()} ${if (price == 1.0) "coin" else "coins"}")
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
            )
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

private fun catalogDisplayName(displayName: String): String {
    val romanTier = displayName.substringAfterLast(' ')
    val tier = romanTier.romanToDecimal()
    return if (tier == 0) displayName else displayName.removeSuffix(romanTier) + tier
}

private const val SUPERPAIRS_MENU_PREFIX = "Superpairs"
private const val EXPERIMENTATION_RNG_MENU_SUFFIX = "Experimentation Table RNG"
private const val ENCHANTED_BOOK_NAME = "Enchanted Book"
