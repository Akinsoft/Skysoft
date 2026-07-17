package com.skysoft.data.skyblock

import com.google.common.collect.ImmutableMultimap
import com.mojang.authlib.GameProfile
import com.mojang.authlib.properties.Property
import com.mojang.authlib.properties.PropertyMap
import com.skysoft.features.pets.setSkyBlockId
import java.util.Base64
import java.util.UUID
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.ResolvableProfile
import net.minecraft.world.item.component.ItemLore

internal object SkyBlockStackFactory {
    fun texturedHead(textureValue: String, name: Component, signature: String? = null): ItemStack {
        val split = textureValue.split(":", limit = 2)
        val uuid = split.getOrNull(0)?.let { runCatching { UUID.fromString(it) }.getOrNull() }
            ?: UUID.nameUUIDFromBytes(textureValue.toByteArray())
        val texture = texturePropertyValue(split.getOrNull(1) ?: textureValue)
        val property = if (signature == null) Property("textures", texture) else Property("textures", texture, signature)
        val properties = ImmutableMultimap.builder<String, Property>()
            .put("textures", property)
            .build()
        val profile = GameProfile(uuid, PROFILE_NAME, PropertyMap(properties))
        return ItemStack(Items.PLAYER_HEAD).apply {
            set(DataComponents.PROFILE, ResolvableProfile.createResolved(profile))
            set(DataComponents.CUSTOM_NAME, name)
        }
    }

    fun enchantmentBook(internalName: String, name: String, sourceLore: List<String>): ItemStack =
        ItemStack(Items.ENCHANTED_BOOK).apply {
            set(DataComponents.CUSTOM_NAME, Component.literal("§9$name").withStyle { it.withItalic(false) })
            val loreWithoutTitle = sourceLore.dropWhile(String::isBlank).drop(1).dropWhile(String::isBlank)
            set(
                DataComponents.LORE,
                ItemLore(loreWithoutTitle.map { Component.literal(it).withStyle { style -> style.withItalic(false) } }),
            )
            setSkyBlockId(internalName)
        }

    private const val PROFILE_NAME = "SkysoftPet"
    private val textureHashPattern = Regex("[0-9a-f]{32,64}")

    private fun texturePropertyValue(value: String): String {
        if (!textureHashPattern.matches(value)) return value
        val json = """{"textures":{"SKIN":{"url":"https://textures.minecraft.net/texture/$value"}}}"""
        return Base64.getEncoder().encodeToString(json.toByteArray())
    }
}
