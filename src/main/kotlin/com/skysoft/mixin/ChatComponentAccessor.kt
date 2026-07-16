package com.skysoft.mixin

import net.minecraft.client.gui.components.ChatComponent
import net.minecraft.client.multiplayer.chat.GuiMessage
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.gen.Accessor
import org.spongepowered.asm.mixin.gen.Invoker

@Mixin(ChatComponent::class)
interface ChatComponentAccessor {
    @Accessor("allMessages")
    fun skysoftAllMessages(): MutableList<GuiMessage>

    @Accessor("trimmedMessages")
    fun skysoftTrimmedMessages(): MutableList<GuiMessage.Line>

    @Accessor("chatScrollbarPos")
    fun skysoftChatScrollbarPos(): Int

    @Invoker("refreshTrimmedMessages")
    fun skysoftRefreshTrimmedMessages()

    @Invoker("getLineHeight")
    fun skysoftLineHeight(): Int
}
