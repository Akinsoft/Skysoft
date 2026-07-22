package com.skysoft.data.skyblock

import com.google.gson.Gson
import com.skysoft.config.SkysoftConfigGui
import java.io.StringReader
import java.util.Locale
import net.minecraft.world.item.ItemStack

internal object SkyBlockEnchantments {
    fun read(json: String): List<BundledEnchantment> = StringReader(json).use { reader ->
        Gson().fromJson(reader, Array<BundledEnchantment>::class.java).orEmpty().map { enchantment ->
            enchantment.copy(lore = normalizeEnchantmentLore(enchantment.lore))
        }
    }

    fun addTo(
        enchantments: List<BundledEnchantment>,
        entries: MutableList<ItemListEntry>,
        info: MutableMap<ItemListEntryKey, SkyBlockItemInfo>,
        providers: MutableMap<ItemListEntryKey, () -> ItemStack>,
        wiki: MutableMap<ItemListEntryKey, String>,
    ) {
        enchantments.forEach { enchantment ->
            val key = ItemListEntryKey(ItemListEntryKind.SKYBLOCK, enchantment.id)
            if (key in providers) return@forEach
            val tier = enchantmentTier(enchantment.id)
            val displayName = arabicDisplayName(enchantment.name, tier)
            val sources = buildList {
                if (enchantment.bazaarable) {
                    add(SkyBlockInfoSource(SkyBlockInfoSourceKind.BAZAAR, "Bazaar"))
                }
            }.distinct()
            entries += ItemListEntry(
                key = key,
                displayName = displayName,
                source = SKYBLOCK_SOURCE,
                searchableText = "${enchantment.name} $displayName ${enchantment.id}".lowercase(Locale.ROOT),
            )
            info[key] = SkyBlockItemInfo(
                key = key,
                displayName = displayName,
                source = SKYBLOCK_SOURCE,
                lore = enchantment.lore,
                enchantment = enchantment.applicableOn.takeIf(String::isNotBlank)?.let {
                    SkyBlockEnchantmentInfo(it, enchantment.applyCost, sources)
                },
            )
            providers[key] = {
                val name = if (SkysoftConfigGui.config().inventory.itemList.sources.useRomanNumerals) {
                    enchantment.name
                } else {
                    displayName
                }
                SkyBlockStackFactory.enchantmentBook(enchantment.id, name, enchantment.lore)
            }
            wiki[key] = enchantmentWikiLinks(enchantment.name)
        }
    }

    private fun arabicDisplayName(romanName: String, tier: Int): String = romanName.replace(TIER_SUFFIX, " $tier")

    private fun enchantmentTier(id: String): Int = id.substringAfterLast('_').toInt()

    private fun enchantmentWikiLinks(name: String): String {
        val romanTier = name.substringAfterLast(' ')
        val page = name.removeSuffix(" $romanTier").replace(' ', '_')
        return "$SKYBLOCK_WIKI_PAGE_URL$page#${page}_$romanTier"
    }

    private const val SKYBLOCK_SOURCE = "skyblock"
    private val TIER_SUFFIX = Regex(" [IVXLCDM]+$")
}

internal data class BundledEnchantment(
    val id: String = "",
    val name: String = "",
    val lore: List<String> = emptyList(),
    val bazaarable: Boolean = false,
    val applicableOn: String = "",
    val applyCost: Int? = null,
)

internal fun normalizeEnchantmentLore(source: List<String>): List<String> {
    val lines = source.filter(String::isNotBlank)
    return buildList {
        lines.forEach { line ->
            val plain = line.replace(COLOR_CODE, "").trim()
            val startsSection = plain.startsWith("Applicable on:") ||
                plain.startsWith("Use this ") ||
                plain.matches(RARITY_LINE)
            if (startsSection && isNotEmpty() && last().isNotBlank()) add("")
            add(line)
        }
    }
}

private val COLOR_CODE = Regex("§.")
private val RARITY_LINE = Regex("(COMMON|UNCOMMON|RARE|EPIC|LEGENDARY|MYTHIC|DIVINE|SPECIAL|VERY SPECIAL).*", RegexOption.IGNORE_CASE)
