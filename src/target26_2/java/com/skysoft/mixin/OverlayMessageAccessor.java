package com.skysoft.mixin;

import net.minecraft.client.gui.Hud;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Hud.class)
public interface OverlayMessageAccessor {
    @Accessor("overlayMessageString") Component skysoftGetOverlayMessageString();
    @Accessor("overlayMessageTime") int skysoftGetOverlayMessageTime();
}
