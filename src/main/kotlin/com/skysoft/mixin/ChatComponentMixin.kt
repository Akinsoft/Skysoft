package com.skysoft.mixin

import com.llamalad7.mixinextras.injector.wrapoperation.Operation
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation
import com.llamalad7.mixinextras.sugar.Local
import com.skysoft.features.chat.ChatCompactor
import com.skysoft.features.chat.ChatFeatureSettings
import com.skysoft.features.chat.ChatHistoryPersistence
import com.skysoft.features.chat.ChatMotionProfile
import com.skysoft.features.chat.ChatMotionSettings
import com.skysoft.features.chat.PreparedChatMessage
import com.skysoft.features.event.diana.DianaSphinxAnswerHighlighter
import com.skysoft.utils.animation.AnimationClock
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.ChatComponent
import net.minecraft.client.multiplayer.chat.GuiMessageSource
import net.minecraft.client.multiplayer.chat.GuiMessageTag
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MessageSignature
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Shadow
import org.spongepowered.asm.mixin.Unique
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Constant
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.ModifyConstant
import org.spongepowered.asm.mixin.injection.ModifyVariable
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(ChatComponent::class)
abstract class ChatComponentMixin {
    @field:Shadow
    private var chatScrollbarPos = 0

    @field:Unique
    private val skysoftMessageArrival = AnimationClock()

    @field:Unique
    private var skysoftPendingCompaction: PreparedChatMessage? = null

    @field:Unique
    private var skysoftPreservedMessages: List<net.minecraft.client.multiplayer.chat.GuiMessage>? = null

    @Shadow
    private fun getLineHeight(): Int = throw AssertionError()

    @WrapOperation(
        method = [
            "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;" +
                "Lnet/minecraft/client/gui/Font;IIILnet/minecraft/client/gui/components/" +
                "ChatComponent\$DisplayMode;Z)V",
        ],
        at = [
            At(
                value = "INVOKE",
                target = "Lnet/minecraft/client/gui/components/ChatComponent;" +
                    "extractRenderState(Lnet/minecraft/client/gui/components/ChatComponent\$ChatGraphicsAccess;" +
                    "IILnet/minecraft/client/gui/components/ChatComponent\$DisplayMode;)V",
            ),
        ],
    )
    protected fun skysoftAnimateNewMessages(
        chat: ChatComponent,
        graphicsAccess: ChatComponent.ChatGraphicsAccess,
        screenHeight: Int,
        ticks: Int,
        displayMode: ChatComponent.DisplayMode,
        original: Operation<Void>,
        @Local(argsOnly = true) graphics: GuiGraphicsExtractor,
    ) {
        val displacement = skysoftNewMessageOffset()
        if (displacement == 0.0f) {
            original.call(chat, graphicsAccess, screenHeight, ticks, displayMode)
            return
        }

        graphics.pose().pushMatrix()
        try {
            graphics.pose().translate(0.0f, displacement)
            original.call(chat, graphicsAccess, screenHeight, ticks, displayMode)
        } finally {
            graphics.pose().popMatrix()
        }
    }

    @Inject(
        method = [
            "addMessage(Lnet/minecraft/network/chat/Component;" +
                "Lnet/minecraft/network/chat/MessageSignature;" +
                "Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;" +
                "Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V",
        ],
        at = [At("TAIL")],
    )
    protected fun skysoftRecordMessageTime(
        contents: Component,
        signature: MessageSignature?,
        source: GuiMessageSource,
        tag: GuiMessageTag?,
        ci: CallbackInfo,
    ) {
        skysoftMessageArrival.restart()
    }

    @Inject(
        method = [
            "addMessage(Lnet/minecraft/network/chat/Component;" +
                "Lnet/minecraft/network/chat/MessageSignature;" +
                "Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;" +
                "Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V",
        ],
        at = [At("HEAD")],
        cancellable = true,
    )
    protected fun skysoftHideBlankMessage(
        contents: Component,
        signature: MessageSignature?,
        source: GuiMessageSource,
        tag: GuiMessageTag?,
        ci: CallbackInfo,
    ) {
        skysoftPendingCompaction = null
        if (ChatFeatureSettings.areBlankLinesHidden() && ChatCompactor.isBlank(contents)) {
            ci.cancel()
        }
    }

    @ModifyVariable(
        method = [
            "addMessage(Lnet/minecraft/network/chat/Component;" +
                "Lnet/minecraft/network/chat/MessageSignature;" +
                "Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;" +
                "Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V",
        ],
        at = At("HEAD"),
        argsOnly = true,
        ordinal = 0,
    )
    protected fun skysoftTransformMessage(contents: Component): Component {
        val accessor = this as ChatComponentAccessor
        val prepared = ChatCompactor.prepare(
            DianaSphinxAnswerHighlighter.highlight(contents),
            accessor.skysoftAllMessages(),
        )
        skysoftPendingCompaction = prepared
        if (prepared.removedPrevious) accessor.skysoftRefreshTrimmedMessages()
        return prepared.content
    }

    @Inject(
        method = [
            "addMessage(Lnet/minecraft/network/chat/Component;" +
                "Lnet/minecraft/network/chat/MessageSignature;" +
                "Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;" +
                "Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V",
        ],
        at = [At("TAIL")],
    )
    protected fun skysoftAssociateCompactedMessage(
        contents: Component,
        signature: MessageSignature?,
        source: GuiMessageSource,
        tag: GuiMessageTag?,
        ci: CallbackInfo,
    ) {
        val prepared = skysoftPendingCompaction ?: return
        skysoftPendingCompaction = null
        val newest = (this as ChatComponentAccessor).skysoftAllMessages().firstOrNull()
        if (newest?.content() === prepared.content) ChatCompactor.associate(prepared, newest)
    }

    @ModifyConstant(method = ["addMessageToDisplayQueue"], constant = [Constant(intValue = 100)])
    protected fun skysoftVisibleLineLimit(vanillaLimit: Int): Int = ChatFeatureSettings.historyLimit()

    @ModifyConstant(method = ["addMessageToQueue"], constant = [Constant(intValue = 100)])
    protected fun skysoftMessageLimit(vanillaLimit: Int): Int = ChatFeatureSettings.historyLimit()

    @Inject(method = ["clearMessages"], at = [At("HEAD")])
    protected fun skysoftCaptureRetainedHistory(history: Boolean, ci: CallbackInfo) {
        ChatCompactor.clear()
        skysoftPreservedMessages = if (history && ChatFeatureSettings.isHistoryRetained()) {
            val messages = (this as ChatComponentAccessor).skysoftAllMessages()
            ChatHistoryPersistence.save(messages)
            messages.take(ChatFeatureSettings.historyLimit())
        } else {
            null
        }
    }

    @Inject(method = ["clearMessages"], at = [At("TAIL")])
    protected fun skysoftRestoreRetainedHistory(history: Boolean, ci: CallbackInfo) {
        val messages = skysoftPreservedMessages ?: return
        skysoftPreservedMessages = null
        val accessor = this as ChatComponentAccessor
        accessor.skysoftAllMessages().addAll(messages)
        accessor.skysoftRefreshTrimmedMessages()
    }

    @Unique
    private fun skysoftNewMessageOffset(): Float {
        if (!ChatMotionSettings.isMessageMotionEnabled() || chatScrollbarPos != 0) {
            return 0.0f
        }
        val progress = skysoftMessageArrival.progress(ChatMotionSettings.newMessageDurationMillis())
        return ChatMotionProfile.messageDisplacement(getLineHeight(), progress)
    }
}
