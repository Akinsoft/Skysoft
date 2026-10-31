package com.skysoft.data.skyblock

import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.CustomData
import net.minecraft.world.item.component.DyedItemColor
import net.minecraft.world.item.component.ItemLore

internal object SkyBlockItemStacks {
    fun fromNeuItem(item: NeuItemJson): ItemStack {
        val texture = item.nbtTag?.let { textureValuePattern.find(it)?.groupValues?.get(1) }
        val signature = item.nbtTag?.let { textureSignaturePattern.find(it)?.groupValues?.get(1) }
        val name = Component.literal(item.displayName ?: item.internalName)
        val stack = if (item.itemId == "minecraft:skull" && texture != null) {
            SkyBlockStackFactory.texturedHead(texture, name, signature)
        } else {
            ItemStack(minecraftItemFromId(itemModelFromNbt(item) ?: normalizeLegacyItemId(item.itemId)))
        }
        setItemModel(stack, itemModelFromNbt(item))
        stack.setNameAndLore(name, item.lore)
        stack.setSkyBlockId(item.internalName)
        return stack
    }

    fun fromLocalItem(item: SkyBlockItemJson): ItemStack {
        val internalName = item.internalName
        val name = Component.literal(item.displayName ?: internalName ?: item.id)
        val texture = item.components.profile?.properties?.firstOrNull {
            it.name == "textures" && it.value.isNotBlank()
        }
        val stack = if (item.id == "minecraft:player_head" && texture != null) {
            SkyBlockStackFactory.texturedHead(texture.value, name, texture.signature)
        } else {
            ItemStack(minecraftItemFromId(normalizeLegacyItemId(item.id)))
        }
        setItemModel(stack, item.components.itemModel)
        item.components.dyedColor?.let { stack.set(DataComponents.DYED_COLOR, DyedItemColor(it)) }
        item.components.hasEnchantmentGlint?.let { stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, it) }
        stack.setNameAndLore(name, item.components.lore.map { it.text })
        internalName?.let(stack::setSkyBlockId)
        return stack
    }

    private fun itemModelFromNbt(item: NeuItemJson): String? =
        item.nbtTag?.let { itemModelPattern.find(it)?.groupValues?.get(1) }

    private fun ItemStack.setNameAndLore(name: Component, lore: List<String>) {
        set(DataComponents.CUSTOM_NAME, name)
        if (lore.isNotEmpty()) set(DataComponents.LORE, ItemLore(lore.map(Component::literal)))
    }

    private fun setItemModel(stack: ItemStack, itemModel: String?) {
        val id = itemModel?.let(Identifier::tryParse) ?: return
        stack.set(DataComponents.ITEM_MODEL, id)
    }

    private fun normalizeLegacyItemId(itemId: String): String = when (itemId) {
        "minecraft:skull" -> "minecraft:player_head"
        "minecraft:yellow_flower" -> "minecraft:dandelion"
        else -> itemId
    }

    private fun minecraftItemFromId(itemId: String) =
        BuiltInRegistries.ITEM.getValue(
            Identifier.tryParse(itemId) ?: Identifier.fromNamespaceAndPath("minecraft", "stone"),
        )
            .takeUnless { it == Items.AIR }
            ?: Items.STONE

    private val itemModelPattern = Regex("""ItemModel:"([^"]+)"""")
    private val textureValuePattern = Regex("""Value:"([^"]+)"""")
    private val textureSignaturePattern = Regex("""Signature:"([^"]+)"""")
}

internal fun ItemStack.setSkyBlockId(internalName: String) {
    set(DataComponents.CUSTOM_DATA, CustomData.of(CompoundTag().apply { putString("id", internalName) }))
}
