package com.skysoft.data.skyblock;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Holder;
import java.util.stream.Stream;
import net.minecraft.core.Registry;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.packs.VanillaBrewingProvider;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.crafting.BrewingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/** Builds the real native vanilla brewing recipes without a removed PotionBrewing table. */
public final class NativeVanillaBrewingRecipes {
    private NativeVanillaBrewingRecipes() {}

    public static List<BrewingRecipe> create(Level level) {
        return create(level.registryAccess());
    }

    public static List<BrewingRecipe> create(HolderLookup.Provider registries) {
        List<BrewingRecipe> recipes = new ArrayList<>();
        RecipeOutput output = new RecipeOutput() {
            @Override
            public void accept(ResourceKey<Recipe<?>> key, Recipe<?> recipe, @Nullable AdvancementHolder advancement) {
                if (recipe instanceof BrewingRecipe brewing) recipes.add(brewing);
            }

            @Override
            public Advancement.Builder advancement() {
                return Advancement.Builder.advancement();
            }

            @Override
            @SuppressWarnings({"unchecked", "rawtypes"})
            public <T> HolderGetter<T> lookup(ResourceKey<? extends Registry<? extends T>> registry) {
                return (HolderGetter<T>) registries.lookupOrThrow((ResourceKey) registry);
            }
            @Override
            @SuppressWarnings({"unchecked", "rawtypes"})
            public <T> Stream<Holder.Reference<T>> listContextElements(ResourceKey<? extends Registry<? extends T>> registry) {
                return (Stream<Holder.Reference<T>>) registries.lookupOrThrow((ResourceKey) registry).listElements();
            }
        };
        new VanillaBrewingProvider(output) {}.buildRecipes();
        return List.copyOf(recipes);
    }
}
