package com.skysoft.features.inventory

import com.skysoft.config.SkysoftConfigGui
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import org.joml.Matrix3x2f

object HeadDisplaySize {
    @JvmStatic
    fun scalePose(pose: Matrix3x2f, stack: ItemStack, x: Int, y: Int): Matrix3x2f {
        if (stack.item != Items.PLAYER_HEAD) return pose
        val config = SkysoftConfigGui.config().inventory.headDisplaySize
        if (!config.enabled) return pose
        val scale = config.details.size / PERCENT_SCALE
        return pose.translate(x + ITEM_CENTER, y + ITEM_CENTER)
            .scale(scale)
            .translate(-x - ITEM_CENTER, -y - ITEM_CENTER)
    }

    private const val PERCENT_SCALE = 100f
    private const val ITEM_CENTER = 8f
}
