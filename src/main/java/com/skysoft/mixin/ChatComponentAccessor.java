package com.skysoft.mixin;

import java.util.List;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ChatComponent.class)
public interface ChatComponentAccessor {
    @Accessor("allMessages") List<GuiMessage> skysoftGetAllMessages();
    @Accessor("trimmedMessages") List<GuiMessage.Line> skysoftGetTrimmedMessages();
    @Accessor("chatScrollbarPos") int skysoftGetChatScrollbarPos();
    @Invoker("refreshTrimmedMessages") void skysoftRefreshTrimmedMessages();
    @Invoker("getLineHeight") int skysoftGetLineHeight();
}
