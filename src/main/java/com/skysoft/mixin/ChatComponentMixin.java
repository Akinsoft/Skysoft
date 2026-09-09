package com.skysoft.mixin;

import com.skysoft.integration.MixinFeatureAdapters;
import com.skysoft.utils.mixin.MixinErrorBoundary;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.skysoft.features.chat.ChatCompactor;
import com.skysoft.features.chat.ChatFeatureSettings;
import com.skysoft.features.chat.ChatHistoryPersistence;
import com.skysoft.features.chat.ChatMotionProfile;
import com.skysoft.features.chat.ChatMotionSettings;
import com.skysoft.features.chat.ChatPeek;
import com.skysoft.features.chat.ChatTabs;
import com.skysoft.features.chat.PreparedChatMessage;
import com.skysoft.utils.animation.AnimationClock;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin {
    @Shadow private int chatScrollbarPos;
    @Unique private final AnimationClock skysoftMessageArrival = new AnimationClock();
    @Shadow private int getLineHeight() { throw new AssertionError(); }

    @ModifyVariable(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;IIILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;Z)V", at = @At("HEAD"), argsOnly = true)
    protected ChatComponent.DisplayMode skysoftExpandChatDisplayMode(ChatComponent.DisplayMode displayMode) { return ChatPeek.INSTANCE.displayMode(displayMode); }

    @ModifyReturnValue(method = "getHeight()I", at = @At("RETURN"))
    protected int skysoftExpandChatHeight(int original) { Integer height = ChatPeek.INSTANCE.expandedHeight(); return height != null ? height : original; }

    @WrapOperation(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;IIILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;Z)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/ChatComponent;extractRenderState(Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;IILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;)V"))
    protected void skysoftAnimateNewMessages(ChatComponent chat, ChatComponent.ChatGraphicsAccess graphicsAccess, int screenHeight, int ticks, ChatComponent.DisplayMode displayMode, Operation<Void> original, @Local(argsOnly = true) GuiGraphicsExtractor graphics) {
        float displacement = MixinErrorBoundary.value("Chat message motion", 0.0F, this::skysoftNewMessageOffset);
        if (displacement == 0.0F) { original.call(chat, graphicsAccess, screenHeight, ticks, displayMode); return; }
        graphics.pose().pushMatrix();
        try { graphics.pose().translate(0.0F, displacement); original.call(chat, graphicsAccess, screenHeight, ticks, displayMode); }
        finally { graphics.pose().popMatrix(); }
    }

    @WrapOperation(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V", at = @At(value = "INVOKE", target = "Ljava/util/function/Predicate;test(Ljava/lang/Object;)Z"))
    protected boolean skysoftIsTabMessageStorageAllowed(Predicate<GuiMessage> predicate, Object message, Operation<Boolean> original, @Share("messageVisible") LocalBooleanRef messageVisible) {
        boolean visible = original.call(predicate, message);
        messageVisible.set(visible);
        return visible || ChatTabs.INSTANCE.isFilterApplied();
    }

    @WrapOperation(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/ChatComponent;addMessageToDisplayQueue(Lnet/minecraft/client/multiplayer/chat/GuiMessage;)V"))
    protected void skysoftAddVisibleMessageToDisplayQueue(ChatComponent chat, GuiMessage message, Operation<Void> original, @Share("messageVisible") LocalBooleanRef messageVisible) {
        if (!messageVisible.get()) return;
        MixinErrorBoundary.run("Chat message motion", skysoftMessageArrival::restart);
        original.call(chat, message);
    }

    @Inject(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V", at = @At("HEAD"), cancellable = true)
    protected void skysoftHideBlankMessage(Component contents, MessageSignature signature, GuiMessageSource source, GuiMessageTag tag, CallbackInfo ci) {
        boolean hide = MixinErrorBoundary.value("Chat blank line hiding", false, () -> ChatFeatureSettings.INSTANCE.areBlankLinesHidden() && MixinFeatureAdapters.isBlankChatMessage(contents));
        if (hide) ci.cancel();
    }

    @ModifyVariable(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    protected Component skysoftTransformMessage(Component contents, @Share("pendingCompaction") LocalRef<PreparedChatMessage> pendingCompaction) {
        return MixinErrorBoundary.value("Chat message transformation", contents, () -> {
            ChatComponentAccessor accessor = (ChatComponentAccessor) this;
            PreparedChatMessage prepared = MixinFeatureAdapters.prepareChatMessage(contents, accessor.skysoftAllMessages(), accessor.skysoftTrimmedMessages());
            pendingCompaction.set(prepared);
            return prepared.getContent();
        });
    }

    @Inject(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V", at = @At("TAIL"))
    protected void skysoftAssociateCompactedMessage(Component contents, MessageSignature signature, GuiMessageSource source, GuiMessageTag tag, CallbackInfo ci, @Share("pendingCompaction") LocalRef<PreparedChatMessage> pendingCompaction) {
        MixinErrorBoundary.run("Chat message compaction association", () -> {
            PreparedChatMessage prepared = pendingCompaction.get();
            if (prepared == null) return;
            List<GuiMessage> messages = ((ChatComponentAccessor) this).skysoftAllMessages();
            GuiMessage newest = messages.isEmpty() ? null : messages.getFirst();
            if (newest != null && newest.content() == prepared.getContent()) ChatCompactor.INSTANCE.associate(prepared, newest);
        });
    }

    @ModifyConstant(method = "addMessageToDisplayQueue", constant = @Constant(intValue = 100))
    protected int skysoftVisibleLineLimit(int vanillaLimit) { return MixinErrorBoundary.value("Chat visible line limit", vanillaLimit, ChatFeatureSettings.INSTANCE::historyLimit); }
    @ModifyConstant(method = "addMessageToQueue", constant = @Constant(intValue = 100))
    protected int skysoftMessageLimit(int vanillaLimit) { return MixinErrorBoundary.value("Chat message limit", vanillaLimit, ChatFeatureSettings.INSTANCE::historyLimit); }

    @Inject(method = "clearMessages", at = @At("HEAD"))
    protected void skysoftCaptureRetainedHistory(boolean history, CallbackInfo ci, @Share("preservedMessages") LocalRef<List<GuiMessage>> preservedMessages) {
        MixinErrorBoundary.run("Chat retained history capture", () -> {
            ChatCompactor.INSTANCE.clear();
            if (history && ChatFeatureSettings.INSTANCE.isHistoryRetained()) {
                List<GuiMessage> messages = ((ChatComponentAccessor) this).skysoftAllMessages();
                ChatHistoryPersistence.INSTANCE.save(messages);
                preservedMessages.set(new ArrayList<>(messages.subList(0, Math.min(messages.size(), ChatFeatureSettings.INSTANCE.historyLimit()))));
            }
        });
    }

    @Inject(method = "clearMessages", at = @At("TAIL"))
    protected void skysoftRestoreRetainedHistory(boolean history, CallbackInfo ci, @Share("preservedMessages") LocalRef<List<GuiMessage>> preservedMessages) {
        MixinErrorBoundary.run("Chat retained history restore", () -> {
            List<GuiMessage> messages = preservedMessages.get();
            if (messages == null) return;
            ChatComponentAccessor accessor = (ChatComponentAccessor) this;
            accessor.skysoftAllMessages().addAll(messages);
            accessor.skysoftRefreshTrimmedMessages();
        });
    }

    @Unique private float skysoftNewMessageOffset() {
        if (!ChatMotionSettings.isMessageMotionEnabled() || chatScrollbarPos != 0) return 0.0F;
        return ChatMotionProfile.messageDisplacement(getLineHeight(), skysoftMessageArrival.progress(ChatMotionSettings.newMessageDurationMillis()));
    }
}
