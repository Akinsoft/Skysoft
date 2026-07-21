package com.skysoft.features.inventory

import com.skysoft.features.misc.PlayerHeadSkinFix
import com.skysoft.utils.render.GuiRenderStateAccess
import java.util.IdentityHashMap
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.navigation.ScreenRectangle
import net.minecraft.client.renderer.item.TrackingItemStackRenderState
import net.minecraft.client.renderer.state.gui.GuiItemRenderState
import net.minecraft.core.component.DataComponents
import net.minecraft.tags.ItemTags
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import org.joml.Matrix3x2f

internal object StorageOverlayItemRenderer {
    private val models = IdentityHashMap<ItemStack, TrackingItemStackRenderState>()

    fun drawStoredItem(
        context: GuiGraphicsExtractor,
        stack: ItemStack,
        x: Int,
        y: Int,
        scissorArea: ScreenRectangle,
    ) {
        RarityHighlightRenderer.renderSlot(context, stack, x, y) {
            if (requiresLiveModel(stack)) {
                context.item(stack, x, y)
            } else {
                val model = models.getOrPut(stack) { resolveModel(stack) }
                val itemState = GuiItemRenderState(
                    Matrix3x2f(context.pose()),
                    model,
                    x,
                    y,
                    scissorArea,
                )
                RarityHighlightRenderer.attachContour(itemState)
                GuiRenderStateAccess.get(context).addItem(itemState)
            }
        }
        context.itemDecorations(Minecraft.getInstance().font, stack, x, y)
    }

    fun reset() {
        models.clear()
    }

    private fun requiresLiveModel(stack: ItemStack): Boolean = when {
        stack.item == Items.CLOCK -> true
        stack.`is`(ItemTags.COMPASSES) -> true
        stack.item != Items.PLAYER_HEAD -> false
        else -> stack.get(DataComponents.PROFILE)?.let(PlayerHeadSkinFix::isProfileLoaded) == false
    }

    private fun resolveModel(stack: ItemStack): TrackingItemStackRenderState {
        val minecraft = Minecraft.getInstance()
        return TrackingItemStackRenderState().also { model ->
            minecraft.itemModelResolver.updateForTopItem(
                model,
                stack,
                ItemDisplayContext.GUI,
                minecraft.level,
                minecraft.player,
                0,
            )
        }
    }
}
