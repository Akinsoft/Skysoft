package com.skysoft.data.skyblock

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.skysoft.data.skyblock.SkyBlockItemUtilities.extraAttributes
import com.skysoft.data.skyblock.SkyBlockItemUtilities.getCompoundOrNull
import com.skysoft.data.skyblock.SkyBlockItemUtilities.getIntOrNull
import com.skysoft.data.skyblock.SkyBlockItemUtilities.getStringOrNull
import com.skysoft.data.skyblock.SkyBlockItemUtilities.skyBlockEnchantments
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.item.ItemStack

object SkyBlockItemId {
    private val gson = Gson()

    fun ItemStack.skyBlockId(): String? {
        val extraAttributes = extraAttributes() ?: return null
        val itemId = extraAttributes.getStringOrNull("id") ?: return null

        return when (itemId) {
            "ENCHANTED_BOOK" -> extraAttributes.enchantedBookId() ?: itemId
            "RUNE" -> extraAttributes.runeId() ?: itemId
            "PET" -> extraAttributes.petId() ?: itemId
            else -> itemId
        }
    }

    private fun CompoundTag.enchantedBookId(): String? {
        val (key, level) = skyBlockEnchantments().entries.singleOrNull() ?: return null
        return "ENCHANTMENT_${key.uppercase()}_$level".takeIf { level > 0 }
    }

    private fun CompoundTag.runeId(): String? {
        val runes = getCompoundOrNull("runes") ?: return null
        val key = runes.keySet().singleOrNull() ?: return null
        val level = runes.getIntOrNull(key) ?: return null
        return "${key.uppercase()}_RUNE;$level"
    }

    private fun CompoundTag.petId(): String? {
        val petInfo = getStringOrNull("petInfo") ?: return null
        return runCatching {
            val json = gson.fromJson(petInfo, JsonObject::class.java)
            val type = json.get("type")?.asString?.uppercase() ?: return@runCatching null
            val tier = json.get("tier")?.asString ?: return@runCatching null
            "$type;${petTierIndex(tier)}"
        }.getOrNull()
    }

    private fun petTierIndex(tier: String): Int =
        SkyBlockRarity.getByName(tier)?.id ?: SkyBlockRarity.COMMON.id
}
