package com.skysoft.data.skyblock

import com.google.gson.Gson
import com.skysoft.utils.TextUtilities.cleanSkyBlockText
import java.util.Locale
import net.minecraft.world.item.ItemStack

internal object SkyBlockBestiaryFamilies {
    private val families: Map<String, BestiaryFamilyData> by lazy {
        val stream = requireNotNull(javaClass.getResourceAsStream(RESOURCE)) { "Missing bundled Bestiary data" }
        val catalog = stream.bufferedReader().use { reader -> Gson().fromJson(reader, BestiaryCatalog::class.java) }
        require(catalog.schemaVersion == 1 && catalog.families.isNotEmpty()) { "Invalid bundled Bestiary data" }
        catalog.families.forEach { (name, family) ->
            require(name.isNotBlank() && name == familyKey(name)) { "Invalid Bestiary family name: $name" }
            require((family.icon != null) != (family.texture != null)) { "Invalid Bestiary icon for $name" }
            require(family.mobNames.orEmpty().all(String::isNotBlank)) { "Invalid Bestiary mob names for $name" }
        }
        catalog.families
    }
    private val heads = mutableMapOf<String, ItemStack>()

    fun icon(name: String): ItemStack? {
        val family = families[familyKey(name)] ?: return null
        family.icon?.let { return SkyBlockDataRepository.displayStack(it) }
        val texture = requireNotNull(family.texture)
        return heads.getOrPut(texture) { SkyBlockStackFactory.texturedHead(texture, null) }
    }

    fun mobNames(selectedFamilies: Collection<String>): Set<String> =
        selectedFamilies.flatMapTo(selectedFamilies.toMutableSet()) { name ->
            families[familyKey(name)]?.mobNames.orEmpty()
        }

    private fun familyKey(name: String): String = name.cleanSkyBlockText().lowercase(Locale.ROOT)

    private const val RESOURCE = "/assets/skysoft/data/bestiary.json"
}

private data class BestiaryCatalog(
    val schemaVersion: Int,
    val families: Map<String, BestiaryFamilyData>,
)

private data class BestiaryFamilyData(
    val icon: ItemListEntryKey?,
    val texture: String?,
    val mobNames: List<String>?,
)
