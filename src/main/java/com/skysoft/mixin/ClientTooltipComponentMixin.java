package com.skysoft.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.skysoft.gui.tooltip.SkysoftTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ClientTooltipComponent.class)
public interface ClientTooltipComponentMixin {
    @WrapMethod(
        method = "create(Lnet/minecraft/world/inventory/tooltip/TooltipComponent;)" +
            "Lnet/minecraft/client/gui/screens/inventory/tooltip/ClientTooltipComponent;"
    )
    private static ClientTooltipComponent skysoftCreateTooltipComponent(
        TooltipComponent component,
        Operation<ClientTooltipComponent> original
    ) {
        return component instanceof SkysoftTooltipComponent skysoftComponent
            ? skysoftComponent.clientComponent()
            : original.call(component);
    }
}
