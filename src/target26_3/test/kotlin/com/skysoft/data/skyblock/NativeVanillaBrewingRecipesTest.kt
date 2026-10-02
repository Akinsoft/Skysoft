package com.skysoft.data.skyblock

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.mojang.serialization.JsonOps
import net.minecraft.SharedConstants
import net.minecraft.core.RegistryAccess
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.RegistryOps
import net.minecraft.server.Bootstrap
import net.minecraft.world.item.crafting.BrewingRecipe
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class NativeVanillaBrewingRecipesTest {
    @Test fun nativeProviderMatchesEveryPublishedRecipeRecord() {
        SharedConstants.tryDetectVersion()
        Bootstrap.bootStrap()
        val registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY)
        val operations = RegistryOps.create(JsonOps.INSTANCE, registries)
        val actual = NativeVanillaBrewingRecipes.create(registries).map {
            BrewingRecipe.MAP_CODEC.codec().encodeStart(operations, it).orThrow
        }
        val resourceLoader = javaClass.classLoader
        val names = resourceLoader.getResourceAsStream("native-brewing-records-list.txt")!!.bufferedReader().use { it.readLines() }
        val expected = names.map { name ->
            resourceLoader.getResourceAsStream(name)!!.bufferedReader().use { reader ->
                JsonParser.parseReader(reader).asJsonObject.apply { remove("type") }
            }
        }
        assertEquals(279, expected.size)
        assertEquals(expected.groupingBy { it as JsonElement }.eachCount(), actual.groupingBy { it }.eachCount())
    }
}
