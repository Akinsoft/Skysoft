package com.skysoft.data.skyblock

import com.skysoft.utils.TextUtilities
import com.skysoft.utils.TextUtilities.parseUUIDOrNull
import java.util.UUID
import net.minecraft.core.component.DataComponents
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.item.ItemStack

object SkyBlockItemUtilities {
    fun ItemStack.extraAttributes(): CompoundTag? =
        get(DataComponents.CUSTOM_DATA)?.copyTag()

    fun ItemStack.loreLines(): List<String> =
        get(DataComponents.LORE)?.lines()?.map { with(TextUtilities) { it.formattedText() } }.orEmpty()

    fun ItemStack.formattedHoverName(): String = with(TextUtilities) { hoverName.formattedText() }

    fun ItemStack.skyBlockUuid(): UUID? =
        extraAttributes()?.getStringOrNull("uuid")?.parseUUIDOrNull()

    fun CompoundTag.getStringOrNull(key: String): String? =
        if (contains(key)) getString(key).orElse("").takeUnless { it.isBlank() } else null

    fun CompoundTag.getCompoundOrNull(key: String): CompoundTag? =
        if (contains(key)) getCompound(key).orElse(null) else null

    fun CompoundTag.getIntOrNull(key: String): Int? =
        if (contains(key)) getInt(key).orElse(0).takeUnless { it == 0 } else null
}
