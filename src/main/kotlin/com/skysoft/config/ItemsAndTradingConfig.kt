package com.skysoft.config

import com.google.gson.annotations.Expose
import com.skysoft.config.core.ConfigRepairable
import com.skysoft.config.core.repairLoadedConfigs
import com.skysoft.data.hypixel.SkysoftGame.SKYBLOCK
import io.github.notenoughupdates.moulconfig.annotations.Category

class ItemsAndTradingConfig : ConfigRepairable {
    @JvmField
    @field:Expose
    @field:ConfigGames(SKYBLOCK)
    @field:Category(name = "Item List", desc = "Browse items, mobs, recipes, drops, and usages.")
    val itemList = ItemListConfig()

    @JvmField
    @field:Expose
    @field:ConfigGames(SKYBLOCK)
    @field:Category(name = "Crafting Helper", desc = "Track the materials needed for selected recipes.")
    val craftingHelper = CraftingHelperConfig()

    @JvmField
    @field:Expose
    @field:ConfigGames(SKYBLOCK)
    @field:Category(name = "Bazaar", desc = "Bazaar order tracking and overlays.")
    val bazaar = SkysoftBazaarConfig()

    @JvmField
    @field:Expose
    @field:ConfigGames(SKYBLOCK)
    @field:Category(name = "Price Tooltips", desc = "Market and craft values on item tooltips.")
    val priceTooltips = PriceTooltipsConfig()

    override fun repairLoadedValues() = repairLoadedConfigs(itemList, craftingHelper, bazaar, priceTooltips)
}
