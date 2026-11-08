package com.skysoft.mixin;

import java.util.List;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(PlayerTabOverlay.class)
public interface PlayerTabOverlayAccessor {
    @Invoker("getPlayerInfos") List<PlayerInfo> skysoftGetPlayerInfos();
    @Accessor("header") Component skysoftGetHeader();
    @Accessor("footer") Component skysoftGetFooter();
}
