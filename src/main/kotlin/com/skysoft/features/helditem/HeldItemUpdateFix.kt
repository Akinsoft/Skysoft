package com.skysoft.features.helditem

import com.skysoft.config.SkysoftConfigGui
import com.skysoft.data.skyblock.SkyBlockItemId.skyBlockId
import com.skysoft.data.skyblock.SkyBlockItemUtilities.skyBlockUuid
import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.ItemStack

object HeldItemUpdateFix {
    @JvmStatic
    fun shouldPreserveUpdate(previous: ItemStack, current: ItemStack): Boolean =
        SkysoftConfigGui.config().fixes.isHeldItemUpdateFixEnabled &&
            comparison(previous, current) == HeldItemUpdateComparison.MATCHED

    internal fun comparison(previous: ItemStack, current: ItemStack): HeldItemUpdateComparison {
        if (previous.item != current.item) return HeldItemUpdateComparison.DIFFERENT_ITEM
        if (previous.count != current.count) return HeldItemUpdateComparison.DIFFERENT_COUNT

        val previousId = previous.skyBlockId() ?: return HeldItemUpdateComparison.MISSING_SKYBLOCK_ID
        val currentId = current.skyBlockId() ?: return HeldItemUpdateComparison.MISSING_SKYBLOCK_ID
        if (previousId != currentId) return HeldItemUpdateComparison.DIFFERENT_SKYBLOCK_ID

        val isCustomDataChanged = previous.get(DataComponents.CUSTOM_DATA) != current.get(DataComponents.CUSTOM_DATA)
        if (isCustomDataChanged) {
            val previousUuid = previous.skyBlockUuid()
            if (previousUuid == null || previousUuid != current.skyBlockUuid()) {
                return HeldItemUpdateComparison.CUSTOM_DATA_WITHOUT_MATCHING_UUID
            }
        }

        val componentsMatch = ItemStack.matchesIgnoringComponents(previous, current) { type ->
            type.ignoreSwapAnimation() || type == DataComponents.LORE || type == DataComponents.CUSTOM_DATA
        }
        return if (componentsMatch) {
            HeldItemUpdateComparison.MATCHED
        } else {
            HeldItemUpdateComparison.DIFFERENT_COMPONENTS
        }
    }
}

internal enum class HeldItemUpdateComparison {
    MATCHED,
    DIFFERENT_ITEM,
    DIFFERENT_COUNT,
    MISSING_SKYBLOCK_ID,
    DIFFERENT_SKYBLOCK_ID,
    CUSTOM_DATA_WITHOUT_MATCHING_UUID,
    DIFFERENT_COMPONENTS,
}
