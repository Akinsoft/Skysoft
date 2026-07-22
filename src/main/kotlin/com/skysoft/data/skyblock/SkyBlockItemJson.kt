package com.skysoft.data.skyblock

import com.google.gson.annotations.SerializedName
import com.skysoft.features.pets.PetItemStacks
import com.skysoft.utils.TextUtilities.removeColor
import net.minecraft.world.item.ItemStack

internal object SkyBlockItemCatalog {
    fun addTo(
        items: List<SkyBlockItemJson>,
        entries: MutableList<ItemListEntry>,
        info: MutableMap<ItemListEntryKey, SkyBlockItemInfo>,
        providers: MutableMap<ItemListEntryKey, () -> ItemStack>,
    ) {
        items.forEach { item ->
            val internalName = item.internalName ?: return@forEach
            val key = ItemListEntryKey(ItemListEntryKind.SKYBLOCK, internalName)
            val formattedDisplayName = item.displayName ?: internalName
            val displayName = formattedDisplayName.removeColor()
            val lore = item.components.lore.map { it.text }
            val classification = itemClassification(lore)
            entries += ItemListEntry(
                key = key,
                displayName = displayName,
                source = CatalogSources.SKYBLOCK,
                searchableText = itemListSearchableText(displayName, internalName, lore),
                formattedDisplayName = formattedDisplayName,
            )
            info[key] = SkyBlockItemInfo(
                key = key,
                displayName = displayName,
                source = CatalogSources.SKYBLOCK,
                category = classification?.category,
                rarity = classification?.rarity,
                lore = lore,
            )
            providers[key] = { PetItemStacks.fromLocalItem(item) }
        }
    }

    private fun itemClassification(lore: List<String>): ItemClassification? {
        val line = lore.asReversed().firstNotNullOfOrNull { value ->
            value.removeColor().trim().takeIf(String::isNotBlank)
        } ?: return null
        val match = rarityLine.matchEntire(line) ?: return null
        return ItemClassification(match.groupValues[1], match.groupValues[2].takeIf(String::isNotBlank))
    }

    private data class ItemClassification(val rarity: String, val category: String?)

    private val rarityLine = Regex(
        "(VERY SPECIAL|ULTIMATE|LEGENJERRY|COMMON|UNCOMMON|RARE|EPIC|LEGENDARY|MYTHIC|DIVINE|SPECIAL|ADMIN|ERAR)(?: (.+))?",
    )
}

internal data class SkyBlockItemJson(
    val id: String = "minecraft:stone",
    val components: SkyBlockItemComponentsJson = SkyBlockItemComponentsJson(),
) {
    val internalName: String? get() = components.customData?.id
    val displayName: String? get() = components.customName?.text
}

internal data class SkyBlockItemComponentsJson(
    @SerializedName("minecraft:custom_data") val customData: SkyBlockItemCustomDataJson? = null,
    @SerializedName("minecraft:custom_name") val customName: SkyBlockItemTextJson? = null,
    @SerializedName("minecraft:dyed_color") val dyedColor: Int? = null,
    @SerializedName("minecraft:enchantment_glint_override") val hasEnchantmentGlint: Boolean? = null,
    @SerializedName("minecraft:item_model") val itemModel: String? = null,
    @SerializedName("minecraft:lore") val lore: List<SkyBlockItemTextJson> = emptyList(),
    @SerializedName("minecraft:profile") val profile: SkyBlockItemProfileJson? = null,
)

internal data class SkyBlockItemCustomDataJson(
    val id: String? = null,
)

internal data class SkyBlockItemTextJson(
    val text: String = "",
)

internal data class SkyBlockItemProfileJson(
    val properties: List<SkyBlockItemProfilePropertyJson> = emptyList(),
)

internal data class SkyBlockItemProfilePropertyJson(
    val name: String = "",
    val value: String = "",
    val signature: String? = null,
)
