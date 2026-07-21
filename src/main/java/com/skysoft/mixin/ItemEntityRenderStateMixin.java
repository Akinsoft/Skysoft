package com.skysoft.mixin;

import com.skysoft.features.misc.DroppedItemScaleRenderState;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(ItemEntityRenderState.class)
public abstract class ItemEntityRenderStateMixin implements DroppedItemScaleRenderState {
    @Unique private float skysoftDroppedItemScale = 1.0F;
    @Unique @Override public float skysoftGetDroppedItemScale() { return skysoftDroppedItemScale; }
    @Unique @Override public void skysoftSetDroppedItemScale(float scale) { skysoftDroppedItemScale = scale; }
}
