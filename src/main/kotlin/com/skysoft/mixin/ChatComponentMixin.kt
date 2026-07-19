package com.skysoft.mixin

import com.llamalad7.mixinextras.injector.wrapoperation.Operation
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation
import com.llamalad7.mixinextras.sugar.Local
import com.skysoft.features.chat.ChatCompactor
import com.skysoft.features.chat.ChatFeatureSettings
import com.skysoft.features.chat.ChatHistoryPersistence
import com.skysoft.features.chat.ChatMotionProfile
import com.skysoft.features.chat.ChatMotionSettings
import com.skysoft.features.chat.ChatNotifier
import com.skysoft.features.chat.ChatPeek
import com.skysoft.features.chat.ChatTabs
import com.skysoft.features.chat.ChatTimestamps
import com.skysoft.features.chat.PreparedChatMessage
import com.skysoft.features.event.diana.DianaSphinxAnswerHighlighter
import com.skysoft.utils.animation.AnimationClock
import com.skysoft.utils.SkysoftErrorBoundary
import java.util.function.Predicate
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.ChatComponent
import net.minecraft.client.multiplayer.chat.GuiMessage
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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

@Mixin(ChatComponent::class)
abstract class ChatComponentMixin {
    @field:Shadow
    private var chatScrollbarPos = 0

    @field:Unique
    private val skysoftMessageArrival = AnimationClock()

    @field:Unique
    private var skysoftAddedMessageIsVisible = true

    @field:Unique
    private var skysoftPendingCompaction: PreparedChatMessage? = null

    @field:Unique
    private var skysoftPreservedMessages: List<net.minecraft.client.multiplayer.chat.GuiMessage>? = null

    @Shadow
    private fun getLineHeight(): Int = throw AssertionError()

    @ModifyVariable(
        method = [
            "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;" +
                "Lnet/minecraft/client/gui/Font;IIILnet/minecraft/client/gui/components/" +
                "ChatComponent\$DisplayMode;Z)V",
        ],
        at = At("HEAD"),
        argsOnly = true,
    )
    protected fun skysoftExpandChatDisplayMode(displayMode: ChatComponent.DisplayMode): ChatComponent.DisplayMode =
        ChatPeek.displayMode(displayMode)

    @Inject(method = ["getHeight()I"], at = [At("HEAD")], cancellable = true)
    protected fun skysoftExpandChatHeight(cir: CallbackInfoReturnable<Int>) {
        ChatPeek.expandedHeight()?.let(cir::setReturnValue)
    }

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
        val displacement = SkysoftErrorBoundary.value("Chat message motion", 0.0f, ::skysoftNewMessageOffset)
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

    @WrapOperation(
        method = [
            "addMessage(Lnet/minecraft/network/chat/Component;" +
                "Lnet/minecraft/network/chat/MessageSignature;" +
                "Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;" +
                "Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V",
        ],
        at = [
            At(
                value = "INVOKE",
                target = "Ljava/util/function/Predicate;test(Ljava/lang/Object;)Z",
            ),
        ],
    )
    protected fun skysoftIsTabMessageStorageAllowed(
        predicate: Predicate<GuiMessage>,
        message: Any,
        original: Operation<Boolean>,
    ): Boolean {
        val isVisible = original.call(predicate, message)
        skysoftAddedMessageIsVisible = isVisible
        return isVisible || ChatTabs.isFilterApplied()
    }

    @WrapOperation(
        method = [
            "addMessage(Lnet/minecraft/network/chat/Component;" +
                "Lnet/minecraft/network/chat/MessageSignature;" +
                "Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;" +
                "Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V",
        ],
        at = [
            At(
                value = "INVOKE",
                target = "Lnet/minecraft/client/gui/components/ChatComponent;" +
                    "addMessageToDisplayQueue(Lnet/minecraft/client/multiplayer/chat/GuiMessage;)V",
            ),
        ],
    )
    protected fun skysoftAddVisibleMessageToDisplayQueue(
        chat: ChatComponent,
        message: GuiMessage,
        original: Operation<Void>,
    ) {
        if (!skysoftAddedMessageIsVisible) return
        SkysoftErrorBoundary.run("Chat message motion", skysoftMessageArrival::restart)
        original.call(chat, message)
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
        val shouldHide = SkysoftErrorBoundary.value("Chat blank line hiding", false) {
            skysoftPendingCompaction = null
            ChatFeatureSettings.areBlankLinesHidden() && ChatCompactor.isBlank(contents)
        }
        if (shouldHide) {
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
        return SkysoftErrorBoundary.value("Chat message transformation", contents) {
            val accessor = this as ChatComponentAccessor
            val prepared = ChatCompactor.prepare(
                ChatNotifier.decorate(DianaSphinxAnswerHighlighter.highlight(contents)),
                accessor.skysoftAllMessages(),
            ).let { compacted -> compacted.withContent(ChatTimestamps.decorate(compacted.content)) }
            skysoftPendingCompaction = prepared
            if (prepared.removedPrevious) accessor.skysoftRefreshTrimmedMessages()
            prepared.content
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
    protected fun skysoftAssociateCompactedMessage(
        contents: Component,
        signature: MessageSignature?,
        source: GuiMessageSource,
        tag: GuiMessageTag?,
        ci: CallbackInfo,
    ) {
        SkysoftErrorBoundary.run("Chat message compaction association") {
            val prepared = skysoftPendingCompaction ?: return@run
            skysoftPendingCompaction = null
            val newest = (this as ChatComponentAccessor).skysoftAllMessages().firstOrNull()
            if (newest?.content() === prepared.content) ChatCompactor.associate(prepared, newest)
        }
    }

    @ModifyConstant(method = ["addMessageToDisplayQueue"], constant = [Constant(intValue = 100)])
    protected fun skysoftVisibleLineLimit(vanillaLimit: Int): Int =
        SkysoftErrorBoundary.value("Chat visible line limit", vanillaLimit, ChatFeatureSettings::historyLimit)

    @ModifyConstant(method = ["addMessageToQueue"], constant = [Constant(intValue = 100)])
    protected fun skysoftMessageLimit(vanillaLimit: Int): Int =
        SkysoftErrorBoundary.value("Chat message limit", vanillaLimit, ChatFeatureSettings::historyLimit)

    @Inject(method = ["clearMessages"], at = [At("HEAD")])
    protected fun skysoftCaptureRetainedHistory(history: Boolean, ci: CallbackInfo) {
        SkysoftErrorBoundary.run("Chat retained history capture") {
            ChatCompactor.clear()
            skysoftPreservedMessages = if (history && ChatFeatureSettings.isHistoryRetained()) {
                val messages = (this as ChatComponentAccessor).skysoftAllMessages()
                ChatHistoryPersistence.save(messages)
                messages.take(ChatFeatureSettings.historyLimit())
            } else {
                null
            }
        }
    }

    @Inject(method = ["clearMessages"], at = [At("TAIL")])
    protected fun skysoftRestoreRetainedHistory(history: Boolean, ci: CallbackInfo) {
        SkysoftErrorBoundary.run("Chat retained history restore") {
            val messages = skysoftPreservedMessages ?: return@run
            skysoftPreservedMessages = null
            val accessor = this as ChatComponentAccessor
            accessor.skysoftAllMessages().addAll(messages)
            accessor.skysoftRefreshTrimmedMessages()
        }
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
