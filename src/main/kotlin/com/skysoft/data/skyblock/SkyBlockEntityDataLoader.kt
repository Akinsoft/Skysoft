package com.skysoft.data.skyblock

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.skysoft.data.SkyBlockIsland
import com.skysoft.utils.TextUtilities.removeColor
import com.skysoft.utils.WorldVec
import com.skysoft.data.skyblock.CatalogJson.string
import com.skysoft.data.skyblock.CatalogJson.obj

internal object SkyBlockEntityDataLoader {
    fun read(
        mobsJson: String,
        npcsJson: String,
        entityContexts: Map<String, List<String>>,
        npcAvailability: Map<String, SkyBlockNpcAvailability>,
    ): LoadedEntityCatalog {
        val entities = mutableMapOf<String, SkyBlockEntityInfo>()
        val droppedByItem = mutableMapOf<String, MutableList<String>>()
        val dropSourcesByItem = mutableMapOf<String, MutableList<SkyBlockDropSource>>()
        val mobs = JsonParser.parseString(mobsJson).asJsonObject
        val npcs = JsonParser.parseString(npcsJson).asJsonObject
        require(mobs.keySet().intersect(npcs.keySet()).isEmpty()) {
            "Item List mob and NPC data contain duplicate entities"
        }
        require(
            mobs.entrySet().none { (_, element) ->
                element.isJsonObject && element.asJsonObject.string("type").isNpcEntityType()
            },
        ) {
            "Item List mob data contains NPCs"
        }
        require(
            npcs.entrySet().all { (_, element) ->
                element.isJsonObject && element.asJsonObject.string("type").isNpcEntityType()
            },
        ) {
            "Item List NPC data contains non-NPC entities"
        }
        require(mobs.size() >= MINIMUM_MOB_COUNT) {
            "Item List mob data contains only ${mobs.size()} mobs"
        }
        require(npcs.size() >= MINIMUM_NPC_COUNT) {
            "Item List NPC data contains only ${npcs.size()} NPCs"
        }
        (mobs.entrySet() + npcs.entrySet()).forEach { (id, element) ->
            if (!element.isJsonObject) return@forEach
            val value = element.asJsonObject
            val name = value.string("name").takeIf(String::isNotBlank) ?: return@forEach
            val contexts = entityContexts[id].orEmpty()
            val wikiLocation = value.string("location").takeIf(String::isNotBlank)
            val island = value.string("island").takeIf(String::isNotBlank)
                ?.let { SkyBlockIsland.getByLocation(it, null) }
                ?: wikiLocation?.let { SkyBlockIsland.getByLocation(it, it) }
                ?: contexts.firstNotNullOfOrNull(::entityContextIsland)
            val position = value.obj("position")?.let { position ->
                WorldVec(position.coordinateValue("x"), position.coordinateValue("y"), position.coordinateValue("z"))
            }
            val plainName = name.removeColor()
            val type = value.string("type").ifBlank { "Entity" }
            val lootTables = SkyBlockEntityCatalog.parseLootTables(id, plainName, value)
            entities[id] = SkyBlockEntityInfo(
                id = id,
                name = plainName,
                type = type,
                location = entityLocation(value)
                    ?: "Hub".takeIf { type.equals("Mythological Creature", ignoreCase = true) }
                    ?: contexts.firstOrNull(),
                texture = value.string("texture").takeIf(String::isNotBlank),
                itemId = value.string("itemId").takeIf(String::isNotBlank),
                island = island,
                position = position,
                details = contexts,
                lootTables = lootTables,
                availability = npcAvailability[id],
            )
            lootTables.forEach { table ->
                table.drops.forEach { drop ->
                    val itemId = drop.itemId ?: return@forEach
                    droppedByItem.getOrPut(itemId) { mutableListOf() }.add(id)
                    dropSourcesByItem.getOrPut(itemId) { mutableListOf() }.add(
                        SkyBlockDropSource(id, drop.chance, table.name, drop.details),
                    )
                }
            }
        }
        require(entities.size >= MINIMUM_ENTITY_COUNT) {
            "Item List entity data contains only ${entities.size} entities"
        }
        require(droppedByItem.size >= MINIMUM_DROPPED_ITEM_COUNT) {
            "Item List entity data contains only ${droppedByItem.size} dropped items"
        }
        require(npcAvailability.keys.all(entities::containsKey)) {
            "Item List NPC availability references unknown entities: " +
                npcAvailability.keys.filterNot(entities::containsKey).joinToString()
        }
        require(entityContexts.keys.all(entities::containsKey)) {
            "Item List entity contexts reference unknown entities: " +
                entityContexts.keys.filterNot(entities::containsKey).joinToString()
        }
        return LoadedEntityCatalog(
            entities = entities,
            droppedByItem = droppedByItem.mapValues { (_, ids) -> ids.distinct() },
            dropSourcesByItem = dropSourcesByItem.mapValues { (_, sources) -> sources.distinct() },
        )
    }

    private fun entityLocation(json: JsonObject): String? {
        val islandId = json.string("island").takeIf(String::isNotBlank)
        val location = json.string("location").takeIf(String::isNotBlank)
            ?: islandId?.let { id ->
                SkyBlockIsland.getByLocation(id, null)?.displayName
                    ?: id.replace('_', ' ').lowercase().replaceFirstChar(Char::uppercase)
            }
            ?: return null
        val position = json.obj("position") ?: return location
        val coordinates = listOf("x", "y", "z").map { position.coordinate(it) }
        return "$location (${coordinates.joinToString()})"
    }

    private const val MINIMUM_MOB_COUNT = 300
    private const val MINIMUM_NPC_COUNT = 400
    private const val MINIMUM_ENTITY_COUNT = 500
    private const val MINIMUM_DROPPED_ITEM_COUNT = 500
}

internal data class LoadedEntityCatalog(
    val entities: Map<String, SkyBlockEntityInfo>,
    val droppedByItem: Map<String, List<String>>,
    val dropSourcesByItem: Map<String, List<SkyBlockDropSource>>,
)

private fun entityContextIsland(context: String): SkyBlockIsland? {
    val location = context.substringBefore(" >").trim()
    return SkyBlockIsland.getByLocation(location, location)
}

private fun JsonObject.coordinate(name: String): String {
    val value = coordinateValue(name)
    return if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()
}
private fun JsonObject.coordinateValue(name: String): Double =
    get(name)?.takeUnless { it.isJsonNull }?.asDouble ?: 0.0
