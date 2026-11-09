package com.skysoft.data.skyblock

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.skysoft.data.skyblock.CatalogJson.canonicalItemId
import com.skysoft.data.skyblock.CatalogJson.intOrNull
import com.skysoft.data.skyblock.CatalogJson.readableName
import java.io.StringReader
import com.skysoft.data.skyblock.CatalogJson.string
import com.skysoft.data.skyblock.CatalogJson.long
import com.skysoft.data.skyblock.CatalogJson.obj
import com.skysoft.data.skyblock.CatalogJson.array

internal object SkyBlockRecipeDataLoader {
    fun read(
        json: String,
        progressionRequirements: Map<String, SkyBlockProgressionRequirement>,
    ): List<SkyBlockRecipe> = StringReader(json).use { reader ->
        JsonParser.parseReader(reader).asJsonArray.mapNotNull { parseRecipe(it.asJsonObject, progressionRequirements) }
    }

    private fun parseRecipe(
        json: JsonObject,
        progressionRequirements: Map<String, SkyBlockProgressionRequirement>,
    ): SkyBlockRecipe? = when (json.string("type")) {
        "crafting" -> parseCrafting(json, progressionRequirements)
        "forge" -> parseForge(json)
        "kat" -> parseKat(json)
        "shop" -> parseShop(json)
        else -> null
    }

    private fun parseCrafting(
        json: JsonObject,
        progressionRequirements: Map<String, SkyBlockProgressionRequirement>,
    ): SkyBlockRecipe.Crafting? {
        val result = json.obj("result")?.recipeIngredient() ?: return null
        val keys = json.recipeIngredients("keys")
        val slots = json.array("pattern")?.map { element ->
            element.asInt.takeIf { it >= 0 }?.let(keys::getOrNull)
        }.orEmpty()
        if (slots.isEmpty()) return null
        return SkyBlockRecipe.Crafting(
            result,
            SkyBlockRecipe.Crafting.gridSlots(slots),
            progressionRequirements[result.id],
        )
    }

    private fun parseForge(json: JsonObject): SkyBlockRecipe.Process? {
        val result = json.obj("result")?.recipeIngredient() ?: return null
        return SkyBlockRecipe.Process(
            type = SkyBlockRecipeType.FORGE,
            result = result,
            ingredients = json.recipeIngredients("inputs"),
            coins = json.long("coins"),
            durationSeconds = json.long("time"),
        )
    }

    private fun parseKat(json: JsonObject): SkyBlockRecipe.Process? {
        val result = json.obj("output")?.recipeIngredient() ?: return null
        val input = json.obj("input")?.recipeIngredient()
        val ingredients = listOfNotNull(input) + json.recipeIngredients("items")
        return SkyBlockRecipe.Process(
            type = SkyBlockRecipeType.KAT,
            result = result,
            ingredients = ingredients,
            coins = json.long("coins"),
            durationSeconds = json.long("time"),
            sourceId = KAT_ENTITY_ID,
        )
    }

    private fun parseShop(json: JsonObject): SkyBlockRecipe.Process? {
        val result = json.obj("result")?.recipeIngredient() ?: return null
        return SkyBlockRecipe.Process(
            type = SkyBlockRecipeType.SHOP,
            result = result,
            ingredients = json.recipeIngredients("inputs"),
            sourceId = json.string("npc").takeIf(String::isNotBlank),
        )
    }

    private fun JsonObject.recipeIngredients(name: String): List<RecipeIngredient> =
        array(name)?.mapNotNull { it.asJsonObject.recipeIngredient() }.orEmpty()

    private fun JsonObject.recipeIngredient(): RecipeIngredient? {
        val type = string("type")
        return when (type) {
            "currency" -> RecipeIngredient(
                id = string("currency"),
                count = long("count"),
                kind = RecipeIngredientKind.CURRENCY,
                displayName = string("currency").readableName(),
            )
            "pet" -> {
                val pet = string("pet")
                val tier = string("tier")
                RecipeIngredient("$pet;$tier", long("count"), RecipeIngredientKind.PET, "$tier $pet")
            }
            "enchantment" -> RecipeIngredient(
                id = enchantmentItemId(
                    string("id"),
                    intOrNull("level") ?: error("Item List enchantment recipe is missing a level"),
                ),
                count = long("count").coerceAtLeast(1L),
                kind = RecipeIngredientKind.ITEM,
            )
            "attribute", "potion" -> RecipeIngredient(
                id = string("id"),
                count = long("count").coerceAtLeast(1L),
                kind = RecipeIngredientKind.SPECIAL,
                displayName = string("id").readableName(),
            )
            else -> itemIngredient()
        }
    }

    private fun JsonObject.itemIngredient(): RecipeIngredient? {
        val id = canonicalItemId() ?: return null
        return RecipeIngredient(id, long("count").coerceAtLeast(1L))
    }

    private const val KAT_ENTITY_ID = "KAT_NPC"
}
