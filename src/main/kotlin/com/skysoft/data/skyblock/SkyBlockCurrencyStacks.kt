package com.skysoft.data.skyblock

import com.skysoft.utils.MinecraftItems
import java.util.Locale
import net.minecraft.network.chat.Component
import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

internal object SkyBlockCurrencyStacks {
    private val cache = object : LinkedHashMap<CurrencyCacheKey, ItemStack>(CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<CurrencyCacheKey, ItemStack>?): Boolean =
            size > CACHE_SIZE
    }

    fun coinStack(amount: Long): ItemStack = synchronized(cache) {
        cache.getOrPut(CurrencyCacheKey(COIN, amount)) {
            SkyBlockStackFactory.texturedHead(
                COIN_TEXTURE,
                Component.literal("§6${coinName(amount)}"),
            )
        }
    }

    fun copperStack(amount: Long): ItemStack = itemStack(COPPER, amount, Items.COPPER_INGOT, "Copper")

    fun moteStack(amount: Long): ItemStack =
        itemStack(MOTE, amount, MinecraftItems.pinkDye(), if (amount == 1L) "Mote" else "Motes")

    fun supportedStack(currency: String, amount: Long): ItemStack? = when (currency.uppercase(Locale.ROOT)) {
        COIN -> coinStack(amount)
        COPPER -> copperStack(amount)
        MOTE -> moteStack(amount)
        else -> null
    }

    fun supportedName(currency: String, amount: Long): String? = when (currency.uppercase(Locale.ROOT)) {
        COIN -> coinName(amount)
        COPPER -> currencyName(amount, "Copper")
        MOTE -> moteName(amount)
        else -> null
    }

    fun supportedTextColor(currency: String): String? = when (currency.uppercase(Locale.ROOT)) {
        COIN, COPPER -> "§6"
        MOTE -> "§d"
        else -> null
    }

    fun coinName(amount: Long): String =
        currencyName(amount, if (amount == 1L) "Coin" else "Coins")

    fun moteName(amount: Long): String =
        currencyName(amount, if (amount == 1L) "Mote" else "Motes")

    private fun itemStack(currency: String, amount: Long, item: Item, name: String) = synchronized(cache) {
        cache.getOrPut(CurrencyCacheKey(currency, amount)) {
            ItemStack(item).apply {
                set(
                    DataComponents.CUSTOM_NAME,
                    Component.literal("${requireNotNull(supportedTextColor(currency))}${currencyName(amount, name)}")
                        .withStyle { it.withItalic(false) },
                )
            }
        }
    }

    private fun currencyName(amount: Long, name: String): String = String.format(Locale.US, "%,d", amount) + " $name"

    private const val COIN_TEXTURE =
        "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUv" +
            "NTM4MDcxNzIxY2M1YjRjZDQwNmNlNDMxYTEzZjg2MDgzYTg5NzNlMTA2NGQyZjg4OTc4Njk5MzBlZTZlNTIzNyJ9fX0="
    private const val CACHE_SIZE = 128
    private const val COIN = "COIN"
    private const val COPPER = "COPPER"
    private const val MOTE = "MOTE"
}

private data class CurrencyCacheKey(val currency: String, val amount: Long)
