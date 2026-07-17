package com.skysoft.data.skyblock

import com.skysoft.utils.SkysoftClientEvents
import net.minecraft.client.Minecraft
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.Identifier
import net.minecraft.util.context.ContextMap
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.alchemy.PotionContents
import net.minecraft.world.item.alchemy.PotionBrewing
import net.minecraft.world.item.crafting.display.FurnaceRecipeDisplay
import net.minecraft.world.item.crafting.display.RecipeDisplay
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay
import net.minecraft.world.item.crafting.display.SlotDisplay
import net.minecraft.world.item.crafting.display.SlotDisplayContext
import net.minecraft.world.item.crafting.display.SmithingRecipeDisplay
import net.minecraft.world.item.crafting.display.StonecutterRecipeDisplay

internal object MinecraftRecipeAdapter {
    private var snapshot: ClientRecipeSnapshot? = null

    fun register() {
        SkysoftClientEvents.onJoin("Minecraft recipe join reset") { snapshot = null }
        SkysoftClientEvents.onDisconnect("Minecraft recipe disconnect reset") { snapshot = null }
    }

    fun recipesFor(key: ItemListEntryKey): List<SkyBlockRecipe> = current()?.byResult?.get(key).orEmpty()

    fun usagesFor(key: ItemListEntryKey): List<SkyBlockRecipe> = current()?.byIngredient?.get(key).orEmpty()

    private fun current(): ClientRecipeSnapshot? {
        val player = Minecraft.getInstance().player ?: return null
        val recipeCount = player.recipeBook.collections.sumOf { it.recipes.size }
        snapshot?.takeIf { it.recipeCount == recipeCount }?.let { return it }
        val entries = player.recipeBook.collections
            .asSequence()
            .flatMap { it.recipes.asSequence() }
            .distinctBy { it.id() }
            .toList()
        val context = SlotDisplayContext.fromLevel(player.level())
        val recipes = entries
            .mapNotNull { entry -> toRecipe(entry.display(), context) }
            .filter(::isVanillaRecipe)
            .toMutableList()
        recipes += brewingRecipes().filter(::isVanillaRecipe)
        return ClientRecipeSnapshot(
            recipeCount = recipeCount,
            byResult = recipes.groupBy { recipe -> recipe.result.registryKey() },
            byIngredient = buildUsageIndex(recipes) { it.registryKeyOrNull() },
        ).also {
            snapshot = it
        }
    }

    private fun toRecipe(display: RecipeDisplay, context: ContextMap): SkyBlockRecipe? = when (display) {
        is ShapedCraftingRecipeDisplay -> shaped(display, context)
        is ShapelessCraftingRecipeDisplay -> shapeless(display, context)
        is FurnaceRecipeDisplay -> furnace(display, context)
        is StonecutterRecipeDisplay -> stonecutting(display, context)
        is SmithingRecipeDisplay -> smithing(display, context)
        else -> unsupported(display, context)
    }

    private fun shaped(display: ShapedCraftingRecipeDisplay, context: ContextMap): SkyBlockRecipe? {
        val result = display.result().ingredient(context) ?: return null
        val slots = MutableList<RecipeIngredient?>(CRAFTING_SLOT_COUNT) { null }
        display.ingredients().forEachIndexed { index, ingredient ->
            val target = index / display.width() * CRAFTING_GRID_WIDTH + index % display.width()
            if (target in slots.indices) slots[target] = ingredient.ingredient(context)
        }
        return SkyBlockRecipe.Crafting(result, slots)
    }

    private fun shapeless(display: ShapelessCraftingRecipeDisplay, context: ContextMap): SkyBlockRecipe? {
        val result = display.result().ingredient(context) ?: return null
        val ingredients = display.ingredients().map { it.ingredient(context) }.take(CRAFTING_SLOT_COUNT)
        return SkyBlockRecipe.Crafting(
            result,
            ingredients + List((CRAFTING_SLOT_COUNT - ingredients.size).coerceAtLeast(0)) { null },
        )
    }

    private fun furnace(display: FurnaceRecipeDisplay, context: ContextMap): SkyBlockRecipe? {
        val result = display.result().ingredient(context) ?: return null
        val ingredient = display.ingredient().ingredient(context) ?: return null
        return SkyBlockRecipe.Process(
            type = furnaceType(display.craftingStation().resolveForFirstStack(context)),
            result = result,
            ingredients = listOf(ingredient),
            durationSeconds = display.duration().toLong() / TICKS_PER_SECOND,
        )
    }

    private fun stonecutting(display: StonecutterRecipeDisplay, context: ContextMap): SkyBlockRecipe? {
        val result = display.result().ingredient(context) ?: return null
        val input = display.input().ingredient(context) ?: return null
        return SkyBlockRecipe.Process(SkyBlockRecipeType.STONECUTTING, result, listOf(input))
    }

    private fun smithing(display: SmithingRecipeDisplay, context: ContextMap): SkyBlockRecipe? {
        val result = display.result().ingredient(context) ?: return null
        val ingredients = listOf(display.template(), display.base(), display.addition()).mapNotNull { it.ingredient(context) }
        return SkyBlockRecipe.Process(SkyBlockRecipeType.SMITHING, result, ingredients)
    }

    private fun unsupported(display: RecipeDisplay, context: ContextMap): SkyBlockRecipe? {
        val result = display.result().ingredient(context) ?: return null
        return SkyBlockRecipe.Process(SkyBlockRecipeType.UNSUPPORTED, result, emptyList())
    }

    private fun brewingRecipes(): List<SkyBlockRecipe> {
        val level = Minecraft.getInstance().player?.level() ?: return emptyList()
        val brewing = level.potionBrewing()
        val potionRegistry = level.registryAccess().lookupOrThrow(Registries.POTION)
        val reagents = BuiltInRegistries.ITEM.asSequence()
            .map(::ItemStack)
            .filter(brewing::isIngredient)
            .toList()
        return buildList {
            potionRegistry.listElements().forEach { potion ->
                POTION_CONTAINERS.forEach { container ->
                    val input = PotionContents.createItemStack(container, potion)
                    reagents.forEach reagentLoop@{ reagent ->
                        if (!brewing.hasMix(input, reagent)) return@reagentLoop
                        val output = brewing.mix(reagent, input)
                        val result = output.potionIngredient() ?: return@reagentLoop
                        add(
                            SkyBlockRecipe.Process(
                                type = SkyBlockRecipeType.BREWING,
                                result = result,
                                ingredients = listOfNotNull(input.potionIngredient(), reagent.registryIngredient()),
                                durationSeconds = PotionBrewing.BREWING_TIME_SECONDS.toLong(),
                            ),
                        )
                    }
                }
            }
        }
    }

    fun potionStack(ingredient: RecipeIngredient): ItemStack? {
        if (ingredient.kind != RecipeIngredientKind.POTION) return null
        val itemId = Identifier.tryParse(ingredient.id.substringBefore(POTION_ID_SEPARATOR)) ?: return null
        val potionId = Identifier.tryParse(ingredient.id.substringAfter(POTION_ID_SEPARATOR, "")) ?: return null
        val item = BuiltInRegistries.ITEM.getOptional(itemId).orElse(null) ?: return null
        val registry = Minecraft.getInstance().player?.level()?.registryAccess()?.lookupOrThrow(Registries.POTION)
            ?: return null
        val potion = registry.get(potionId).orElse(null) ?: return null
        return PotionContents.createItemStack(item, potion)
    }

    private fun ItemStack.potionIngredient(): RecipeIngredient? {
        val potion = get(DataComponents.POTION_CONTENTS)?.potion()?.orElse(null) ?: return null
        val potionId = potion.unwrapKey().orElse(null)?.identifier()?.toString() ?: return null
        val itemId = BuiltInRegistries.ITEM.getKey(item).toString()
        return RecipeIngredient(
            id = "$itemId$POTION_ID_SEPARATOR$potionId",
            count = count.toLong().coerceAtLeast(1L),
            kind = RecipeIngredientKind.POTION,
            displayName = hoverName.string,
        )
    }

    private fun ItemStack.registryIngredient(): RecipeIngredient = RecipeIngredient(
        id = BuiltInRegistries.ITEM.getKey(item).toString(),
        count = count.toLong().coerceAtLeast(1L),
        kind = RecipeIngredientKind.REGISTRY_ITEM,
        displayName = hoverName.string,
    )

    private fun SlotDisplay.ingredient(context: ContextMap): RecipeIngredient? {
        val stack = resolveForFirstStack(context)
        if (stack.isEmpty) return null
        return RecipeIngredient(
            id = BuiltInRegistries.ITEM.getKey(stack.item).toString(),
            count = stack.count.toLong().coerceAtLeast(1L),
            kind = RecipeIngredientKind.REGISTRY_ITEM,
        )
    }

    private fun RecipeIngredient.registryKey(): ItemListEntryKey =
        requireNotNull(registryKeyOrNull()) { "Client recipe result is not a registry item: $id" }

    private fun RecipeIngredient.registryKeyOrNull(): ItemListEntryKey? =
        when (kind) {
            RecipeIngredientKind.REGISTRY_ITEM -> ItemListEntryKey(ItemListEntryKind.REGISTRY, id)
            RecipeIngredientKind.POTION -> ItemListEntryKey(ItemListEntryKind.REGISTRY, id.substringBefore(POTION_ID_SEPARATOR))
            else -> null
        }

    private fun isVanillaRecipe(recipe: SkyBlockRecipe): Boolean =
        (sequenceOf(recipe.result) + recipe.ingredients.asSequence())
            .filter { it.kind == RecipeIngredientKind.REGISTRY_ITEM || it.kind == RecipeIngredientKind.POTION }
            .all { Identifier.tryParse(it.id.substringBefore(POTION_ID_SEPARATOR))?.namespace == MINECRAFT_NAMESPACE }

    private fun furnaceType(station: ItemStack): SkyBlockRecipeType = when (BuiltInRegistries.ITEM.getKey(station.item).path) {
        "blast_furnace" -> SkyBlockRecipeType.BLASTING
        "smoker" -> SkyBlockRecipeType.SMOKING
        "campfire", "soul_campfire" -> SkyBlockRecipeType.CAMPFIRE
        else -> SkyBlockRecipeType.SMELTING
    }

    private data class ClientRecipeSnapshot(
        val recipeCount: Int,
        val byResult: Map<ItemListEntryKey, List<SkyBlockRecipe>>,
        val byIngredient: Map<ItemListEntryKey, List<SkyBlockRecipe>>,
    )

    private const val CRAFTING_GRID_WIDTH = 3
    private const val CRAFTING_SLOT_COUNT = 9
    private const val TICKS_PER_SECOND = 20L
    private const val POTION_ID_SEPARATOR = "|"
    private const val MINECRAFT_NAMESPACE = "minecraft"
    private val POTION_CONTAINERS = listOf(Items.POTION, Items.SPLASH_POTION, Items.LINGERING_POTION)
}
