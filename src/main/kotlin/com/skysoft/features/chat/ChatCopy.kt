package com.skysoft.features.chat

import com.skysoft.config.SkysoftConfigGui
import com.skysoft.mixin.ChatComponentAccessor
import com.skysoft.utils.MinecraftClient
import com.skysoft.utils.SkysoftChat
import kotlin.math.ceil
import kotlin.math.floor
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.ChatComponent
import net.minecraft.client.multiplayer.chat.GuiMessage

object ChatCopy {
    private var copiedCount = 0
    private var lastOutcome = CopyChatResult.IGNORED

    fun copyHoveredMessage(inputCode: Int, mouseX: Int, mouseY: Int): CopyChatResult {
        val config = SkysoftConfigGui.config().chat.copyChat
        if (!config.enabled || inputCode != config.settings.key) return record(CopyChatResult.IGNORED)

        val minecraft = Minecraft.getInstance()
        val chat = MinecraftClient.chat(minecraft)
        val accessor = chat as ChatComponentAccessor
        val message = hoveredMessage(
            lines = accessor.skysoftTrimmedMessages(),
            mouseX = mouseX,
            mouseY = mouseY,
            guiHeight = minecraft.window.guiScaledHeight,
            scale = minecraft.options.chatScale().get(),
            chatWidth = ChatComponent.getWidth(minecraft.options.chatWidth().get()),
            lineHeight = accessor.skysoftLineHeight(),
            linesPerPage = chat.linesPerPage,
            scrollPosition = accessor.skysoftChatScrollbarPos(),
        ) ?: return record(CopyChatResult.NO_MESSAGE)

        val text = ChatFormatting.stripFormatting(ChatTimestamps.originalContent(message.content()).string).orEmpty()
        minecraft.keyboardHandler.setClipboard(text)
        copiedCount++
        SkysoftChat.chat("Copied chat message to clipboard.")
        return record(CopyChatResult.COPIED)
    }

    internal fun hoveredMessage(
        lines: List<GuiMessage.Line>,
        mouseX: Int,
        mouseY: Int,
        guiHeight: Int,
        scale: Double,
        chatWidth: Int,
        lineHeight: Int,
        linesPerPage: Int,
        scrollPosition: Int,
    ): GuiMessage? {
        if (scale <= 0.0 || lineHeight <= 0) return null
        val internalX = mouseX / scale - CHAT_LEFT
        val internalWidth = ceil(chatWidth / scale).toInt()
        if (internalX < -CHAT_BACKGROUND_MARGIN || internalX > internalWidth + CHAT_BACKGROUND_MARGIN) return null

        val baseline = floor((guiHeight - CHAT_BOTTOM_MARGIN) / scale)
        val distanceFromBottom = baseline - mouseY / scale
        if (distanceFromBottom < 0.0) return null
        val visibleLine = floor(distanceFromBottom / lineHeight).toInt()
        if (visibleLine !in 0 until linesPerPage) return null
        return lines.getOrNull(scrollPosition + visibleLine)?.parent()
    }

    internal fun debugSummary(): String = "copied=$copiedCount lastOutcome=$lastOutcome"

    private fun record(result: CopyChatResult): CopyChatResult {
        lastOutcome = result
        return result
    }

    private const val CHAT_LEFT = 4
    private const val CHAT_BOTTOM_MARGIN = 40
    private const val CHAT_BACKGROUND_MARGIN = 4
}

enum class CopyChatResult {
    COPIED,
    NO_MESSAGE,
    IGNORED,
}
