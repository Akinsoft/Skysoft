package com.skysoft.data.skyblock

import com.google.gson.JsonObject
import com.skysoft.utils.TextUtilities.removeColor
import java.util.Locale
import net.minecraft.world.item.ItemStack

internal object SkyBlockEntityCatalog {
    fun addTo(
        entities: Map<String, SkyBlockEntityInfo>,
        entries: MutableList<ItemListEntry>,
        providers: MutableMap<ItemListEntryKey, () -> ItemStack>,
    ) {
        entities.values.filter(SkyBlockEntityInfo::isMob).forEach { entity ->
            val key = entityItemKey(entity.id)
            entries += ItemListEntry(
                key = key,
                displayName = entity.name,
                source = CatalogSources.SKYBLOCK,
                searchableText = itemListSearchableText(
                    entity.name,
                    entity.id,
                    entity.details + entity.type + entity.location.orEmpty() + "mob",
                ),
                tags = setOf("mob", entity.type),
            )
            providers[key] = { requireNotNull(SkyBlockEntityStacks.stack(entity)) }
        }
    }

    fun parseLootTables(entityId: String, entityName: String, json: JsonObject): List<SkyBlockEntityLootTable> =
        json.array("lootTables")?.map { table -> parseLootTable(entityId, entityName, table.asJsonObject) }.orEmpty()

    private fun parseLootTable(
        entityId: String,
        entityName: String,
        json: JsonObject,
    ): SkyBlockEntityLootTable = SkyBlockEntityLootTable(
        name = json.string("name").removeColor().ifBlank { entityName },
        mobLevel = json.positiveInt("mobLevel"),
        xp = json.positiveInt("xp"),
        combatXp = json.positiveInt("combatXp"),
        drops = json.array("drops")?.map { element -> parseDrop(entityId, element.asJsonObject) }.orEmpty(),
    )

    private fun parseDrop(entityId: String, json: JsonObject): SkyBlockEntityDrop {
        val type = json.string("type").ifBlank { "item" }
        val chance = json.get("chance")?.takeUnless { it.isJsonNull }?.asDouble
        require(chance == null || chance.isFinite() && chance in 0.0..MAXIMUM_DROP_CHANCE) {
            "Item List entity $entityId has an invalid drop chance"
        }
        val minimum = json.long("minAmount")
        val maximum = json.long("maxAmount")
        require(minimum >= 0L && maximum >= minimum) { "Item List entity $entityId has an invalid drop amount" }
        val details = json.array("extraLore")?.map { detail ->
            detail.asString.removeColor().trim()
        }.orEmpty().filter(String::isNotBlank)
        require(details.all { it.length <= MAXIMUM_DROP_DETAIL_LENGTH }) {
            "Item List entity $entityId has an invalid drop detail"
        }
        return SkyBlockEntityDrop(
            itemId = json.dropItemId(type),
            displayName = json.dropDisplayName(type),
            currency = json.string("currency").takeIf(String::isNotBlank),
            chance = chance,
            minAmount = minimum,
            maxAmount = maximum,
            details = details,
        )
    }

    private fun JsonObject.dropItemId(type: String): String? = when (type) {
        "currency" -> null
        "enchantment" -> enchantmentItemId(
            string("id"),
            get("level")?.takeUnless { it.isJsonNull }?.asInt ?: return null,
        )
        "attribute" -> string("id").takeIf(String::isNotBlank)?.let { "ATTRIBUTE_SHARD_$it;1" }
        "pet" -> petItemKey("${string("pet")};${string("tier")}")?.id
        else -> string("id").takeIf(String::isNotBlank)
    }

    private fun JsonObject.dropDisplayName(type: String): String = when (type) {
        "currency" -> string("currency").readableName()
        "pet" -> "${string("tier").readableName()} ${string("pet").readableName()} Pet"
        "enchantment" -> "${string("id").readableName()} ${get("level")?.asInt ?: 1}"
        "attribute" -> "${string("id").readableName()} Attribute Shard"
        "rune" -> "${string("id").readableName()} Rune ${get("tier")?.asInt ?: 1}"
        else -> string("id").readableName()
    }

    private fun JsonObject.string(name: String): String = get(name)?.takeUnless { it.isJsonNull }?.asString.orEmpty()
    private fun JsonObject.long(name: String): Long = get(name)?.takeUnless { it.isJsonNull }?.asLong ?: 0L
    private fun JsonObject.positiveInt(name: String): Int? =
        get(name)?.takeUnless { it.isJsonNull }?.asInt?.takeIf { it > 0 }
    private fun JsonObject.array(name: String) = get(name)?.takeIf { it.isJsonArray }?.asJsonArray
    private fun String.readableName(): String =
        replace('_', ' ').lowercase(Locale.ROOT).replaceFirstChar(Char::uppercase)

    private const val MAXIMUM_DROP_DETAIL_LENGTH = 160
    private const val MAXIMUM_DROP_CHANCE = 100.0
}

internal fun entityItemKey(id: String): ItemListEntryKey = ItemListEntryKey(ItemListEntryKind.ENTITY, id)

internal fun SkyBlockEntityInfo.isMob(): Boolean = !type.contains("NPC") && !type.contains("Mayor")
