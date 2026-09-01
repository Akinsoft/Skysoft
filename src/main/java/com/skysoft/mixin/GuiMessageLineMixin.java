package com.skysoft.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.skysoft.features.chat.ChatMotionSettings;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(GuiMessage.Line.class)
public class GuiMessageLineMixin {
    @ModifyReturnValue(method = "tag", at = @At("RETURN"))
    private GuiMessageTag skysoftHideMessageIndicator(GuiMessageTag original) {
        return ChatMotionSettings.isMessageIndicatorHidden() ? null : original;
    }
}
