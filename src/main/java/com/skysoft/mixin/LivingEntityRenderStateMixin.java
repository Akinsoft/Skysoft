package com.skysoft.mixin;

import com.skysoft.utils.render.EntityHighlightRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(LivingEntityRenderState.class)
public abstract class LivingEntityRenderStateMixin implements EntityHighlightRenderState {
    @Unique private int skysoftEntityFillColor;
    @Unique private boolean skysoftEquipmentOnlyOutline;
    @Unique @Override public int skysoftGetEntityFillColor() { return skysoftEntityFillColor; }
    @Unique @Override public void skysoftSetEntityFillColor(int color) { skysoftEntityFillColor = color; }
    @Unique @Override public boolean skysoftHasEquipmentOnlyOutline() { return skysoftEquipmentOnlyOutline; }
    @Unique @Override public void skysoftSetEquipmentOnlyOutline(boolean equipmentOnly) {
        skysoftEquipmentOnlyOutline = equipmentOnly;
    }
}
