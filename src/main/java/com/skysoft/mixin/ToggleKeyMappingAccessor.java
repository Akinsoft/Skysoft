package com.skysoft.mixin;

import net.minecraft.client.ToggleKeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ToggleKeyMapping.class)
public interface ToggleKeyMappingAccessor {
    @Accessor("releasedByScreenWhenDown")
    void skysoftSetReleasedByScreenWhenDown(boolean releasedByScreenWhenDown);

    @Invoker("reset") void skysoftReset();
}
