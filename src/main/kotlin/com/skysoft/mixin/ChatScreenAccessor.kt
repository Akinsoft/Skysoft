package com.skysoft.mixin

import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.ChatScreen
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.gen.Accessor

@Mixin(ChatScreen::class)
interface ChatScreenAccessor {
    @Accessor("input")
    fun skysoftGetInput(): EditBox
}
