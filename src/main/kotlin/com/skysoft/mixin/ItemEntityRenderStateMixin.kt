package com.skysoft.mixin

import com.skysoft.features.misc.DroppedItemScaleRenderState
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Unique

@Mixin(ItemEntityRenderState::class)
abstract class ItemEntityRenderStateMixin : DroppedItemScaleRenderState {
    @field:Unique
    private var skysoftDroppedItemScale = 1f

    @Unique
    override fun skysoftGetDroppedItemScale(): Float = skysoftDroppedItemScale

    @Unique
    override fun skysoftSetDroppedItemScale(scale: Float) {
        skysoftDroppedItemScale = scale
    }
}
