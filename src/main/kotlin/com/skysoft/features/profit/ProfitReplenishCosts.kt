package com.skysoft.features.profit

import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks

internal class ProfitReplenishCosts {
    private val pendingReplenishCosts = mutableMapOf<ReplenishCrop, Int>()

    fun record(block: Block, dayTime: Long) {
        replenishCrop(block, dayTime)?.let { crop ->
            pendingReplenishCosts[crop] = pendingReplenishCosts.getOrDefault(crop, 0) + 1
        }
    }

    fun clear() {
        pendingReplenishCosts.clear()
    }

    fun applyTo(changes: Map<String, Int>): Map<String, Int> {
        if (pendingReplenishCosts.isEmpty()) return changes
        val costs = pendingReplenishCosts.filterKeys { crop -> changes.getOrDefault(crop.harvestItemId, 0) > 0 }
        if (costs.isEmpty()) return changes
        pendingReplenishCosts.keys.removeAll(costs.keys)
        return changes.toMutableMap().apply {
            costs.forEach { (crop, amount) -> merge(crop.costItemId, -amount, Int::plus) }
        }.filterValues { it != 0 }
    }

}

internal data class ReplenishCrop(val harvestItemId: String, val costItemId: String)

internal fun replenishCrop(block: Block, dayTime: Long = 0L): ReplenishCrop? = when (block) {
    Blocks.WHEAT -> ReplenishCrop("WHEAT", "SEEDS")
    Blocks.CARROTS -> ReplenishCrop("CARROT_ITEM", "CARROT_ITEM")
    Blocks.POTATOES -> ReplenishCrop("POTATO_ITEM", "POTATO_ITEM")
    Blocks.NETHER_WART -> ReplenishCrop("NETHER_STALK", "NETHER_STALK")
    Blocks.COCOA -> ReplenishCrop("INK_SACK-3", "INK_SACK-3")
    Blocks.ROSE_BUSH -> ReplenishCrop("WILD_ROSE", "WILD_ROSE")
    Blocks.SUNFLOWER -> if (dayTime % MINECRAFT_DAY_TICKS >= MINECRAFT_NIGHT_START_TICK) {
        ReplenishCrop("MOONFLOWER", "MOONFLOWER")
    } else {
        ReplenishCrop("DOUBLE_PLANT", "DOUBLE_PLANT")
    }
    else -> null
}

private const val MINECRAFT_DAY_TICKS = 24_000L
private const val MINECRAFT_NIGHT_START_TICK = 12_000L
