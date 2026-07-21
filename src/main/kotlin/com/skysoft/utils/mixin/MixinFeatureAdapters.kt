package com.skysoft.utils.mixin

import com.skysoft.features.combat.CocoonTracker
import com.skysoft.features.chat.ChatCompactor
import com.skysoft.features.chat.ChatNotifier
import com.skysoft.features.chat.ChatTabs
import com.skysoft.features.chat.ChatTabBounds
import com.skysoft.features.chat.ChatTimestamps
import com.skysoft.features.chat.PreparedChatMessage
import com.skysoft.features.event.diana.DianaSphinxAnswerHighlighter
import com.skysoft.features.inventory.SmoothSwapping
import com.skysoft.features.inventory.StoragePreviews
import com.skysoft.features.misc.ServerInfoDisplay
import com.skysoft.features.screenshot.ScreenshotManager
import com.skysoft.config.ChatTabPosition
import java.util.function.Consumer
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.multiplayer.chat.GuiMessage
import net.minecraft.network.chat.Component
import net.minecraft.world.inventory.tooltip.TooltipComponent
import net.minecraft.world.item.ItemStack

object MixinFeatureAdapters {
    @JvmStatic
    fun isBlankChatMessage(contents: Component): Boolean = ChatCompactor.isBlank(contents)

    @JvmStatic
    fun prepareChatMessage(contents: Component, messages: List<GuiMessage>): PreparedChatMessage {
        val prepared = ChatCompactor.prepare(
            ChatNotifier.decorate(DianaSphinxAnswerHighlighter.highlight(contents)),
            messages.toMutableList(),
        )
        return prepared.withContent(ChatTimestamps.decorate(prepared.content))
    }

    @JvmStatic
    fun prepareOutgoingChatCommand(message: String): String? = ChatTabs.prepareOutgoingCommand(message)

    @JvmStatic
    fun recordOutgoingChatCommand(command: String) = ChatTabs.recordOutgoingCommand(command)

    @JvmStatic
    fun layoutChatTabs(
        position: ChatTabPosition,
        widths: List<Int>,
        screenHeight: Int,
        chatWidth: Int,
        chatHeight: Int,
    ): List<ChatTabBounds> = ChatTabs.layout(position, widths, screenHeight, chatWidth, chatHeight)

    @JvmStatic
    fun handleCocoonEquipment(entityId: Int, stacks: Collection<ItemStack>) =
        CocoonTracker.handleEquipment(entityId, stacks)

    @JvmStatic
    fun storagePreviewTooltip(stack: ItemStack): TooltipComponent? = StoragePreviews.tooltipFor(stack)

    @JvmStatic
    fun animateLocalContainerMutation(screen: AbstractContainerScreen<*>, mutation: Runnable) =
        SmoothSwapping.animateLocalContainerMutation(screen, mutation::run)

    @JvmStatic
    fun isPingMeasurementActive(): Boolean = ServerInfoDisplay.isPingMeasurementActive

    @JvmStatic
    fun recordPong(sentAtMillis: Long, receivedAtNanos: Long) {
        ServerInfoDisplay.recordPong(sentAtMillis, receivedAtNanos)
    }

    @JvmStatic
    fun decorateScreenshotCallback(callback: Consumer<Component>): Consumer<Component> =
        ScreenshotManager.decorateCaptureCallback(callback)
}
