package com.skysoft.mixin;

import com.skysoft.utils.mixin.MixinFeatureAdapters;
import com.skysoft.utils.mixin.MixinErrorBoundary;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.skysoft.config.ChatTabChannel;
import com.skysoft.features.chat.ChatCopy;
import com.skysoft.features.chat.ChatMotionProfile;
import com.skysoft.features.chat.ChatMotionSettings;
import com.skysoft.features.chat.ChatTabBounds;
import com.skysoft.features.chat.ChatTabs;
import com.skysoft.features.chat.CopyChatResult;
import com.skysoft.features.chat.ImageLinkPreview;
import com.skysoft.utils.SoundUtilities;
import com.skysoft.utils.animation.AnimationClock;
import com.skysoft.utils.gui.PixelButtonTone;
import com.skysoft.utils.gui.PixelButtonWidget;
import com.skysoft.utils.input.InputHandlingResult;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin extends Screen {
    private static final int TAB_HORIZONTAL_PADDING = 12;
    private static final int MIN_TAB_WIDTH = 36;
    @Shadow private ChatComponent.DisplayMode displayMode;
    @Unique private final AnimationClock skysoftOpeningMotion = new AnimationClock();
    @Unique private float skysoftOpenDisplacement;
    @Unique private int skysoftMouseX;
    @Unique private int skysoftMouseY;
    @Unique private final Map<ChatTabChannel, PixelButtonWidget> skysoftTabButtons = new LinkedHashMap<>();
    @Unique private boolean skysoftDidSelectTab;

    protected ChatScreenMixin(Component title) { super(title); }

    @Inject(method = "init()V", at = @At("TAIL"))
    protected void skysoftBeginOpenAnimation(CallbackInfo ci) {
        MixinErrorBoundary.run("Chat Screen initialization", () -> {
            var player = Minecraft.getInstance().player;
            if (ChatMotionSettings.isInputMotionEnabled() && player != null && !player.isSleeping()) skysoftOpeningMotion.restart(); else skysoftOpeningMotion.stop();
            skysoftAddTabButtons();
        });
    }

    @Inject(method = "extractRenderState", at = @At("HEAD"))
    protected void skysoftTrackMouse(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        skysoftMouseX = mouseX;
        skysoftMouseY = mouseY;
        MixinErrorBoundary.run("Chat image link hover", () -> ImageLinkPreview.INSTANCE.updateHoveredLink(mouseX, mouseY, displayMode));
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    protected void skysoftCopyHoveredMessage(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        boolean copied = MixinErrorBoundary.value("Chat Copy key input", false, () -> ChatCopy.INSTANCE.copyHoveredMessage(event.key(), skysoftMouseX, skysoftMouseY) == CopyChatResult.COPIED);
        if (copied) cir.setReturnValue(true);
    }

    @Inject(method = "keyPressed", at = @At("RETURN"))
    protected void skysoftRestoreInputFocusAfterKeySelection(KeyEvent event, CallbackInfoReturnable<Boolean> cir) { skysoftRestoreInputFocusAfterTabSelection(); }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    protected void skysoftCopyHoveredMessageOnClick(MouseButtonEvent click, boolean doubled, CallbackInfoReturnable<Boolean> cir) {
        boolean trust = MixinErrorBoundary.value("Chat image trust input", false, () -> ImageLinkPreview.INSTANCE.processTrustClick(click.button()) == InputHandlingResult.CONSUMED);
        boolean copied = !trust && MixinErrorBoundary.value("Chat Copy mouse input", false, () -> ChatCopy.INSTANCE.copyHoveredMessage(click.button(), (int) click.x(), (int) click.y()) == CopyChatResult.COPIED);
        if (trust || copied) cir.setReturnValue(true);
    }

    @Inject(method = "mouseClicked", at = @At("RETURN"))
    protected void skysoftRestoreInputFocusAfterMouseSelection(MouseButtonEvent click, boolean doubled, CallbackInfoReturnable<Boolean> cir) { skysoftRestoreInputFocusAfterTabSelection(); }

    @WrapOperation(method = "handleChatInput", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientPacketListener;sendChat(Ljava/lang/String;)V"))
    protected void skysoftSendMessageToActiveTab(ClientPacketListener connection, String message, Operation<Void> original) {
        String command = MixinFeatureAdapters.prepareOutgoingChatCommand(message);
        if (command == null) original.call(connection, message); else connection.sendCommand(command);
    }

    @WrapOperation(method = "handleChatInput", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientPacketListener;sendCommand(Ljava/lang/String;)V"))
    protected void skysoftRecordTabCommand(ClientPacketListener connection, String command, Operation<Void> original) {
        String rewritten = MixinFeatureAdapters.rewriteOutgoingCommand(command);
        MixinFeatureAdapters.recordOutgoingChatCommand(rewritten);
        original.call(connection, rewritten);
    }

    @WrapOperation(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V"))
    protected void skysoftAnimateChatInputBackground(GuiGraphicsExtractor graphics, int minX, int minY, int maxX, int maxY, int color, Operation<Void> original) {
        skysoftOpenDisplacement = MixinErrorBoundary.value("Chat input motion", 0.0F, this::skysoftChatOpenDisplacement);
        if (skysoftOpenDisplacement == 0.0F) { original.call(graphics, minX, minY, maxX, maxY, color); return; }
        graphics.pose().pushMatrix();
        try { graphics.pose().translate(0.0F, skysoftOpenDisplacement); original.call(graphics, minX, minY, maxX, maxY, color); }
        finally { graphics.pose().popMatrix(); }
    }

    @WrapOperation(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/Screen;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V"))
    protected void skysoftAnimateChatInput(ChatScreen screen, GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, Operation<Void> original) {
        if (skysoftOpenDisplacement == 0.0F) { original.call(screen, graphics, mouseX, mouseY, delta); return; }
        graphics.pose().pushMatrix();
        try { graphics.pose().translate(0.0F, skysoftOpenDisplacement); original.call(screen, graphics, mouseX, mouseY, delta); }
        finally { graphics.pose().popMatrix(); }
    }

    @Inject(method = "removed", at = @At("HEAD"))
    protected void skysoftResetOpenAnimation(CallbackInfo ci) {
        MixinErrorBoundary.run("Chat input motion", skysoftOpeningMotion::stop);
        MixinErrorBoundary.run("Chat image link session", ImageLinkPreview.INSTANCE::endChatSession);
    }

    @Unique private float skysoftChatOpenDisplacement() {
        if (!ChatMotionSettings.isInputMotionEnabled()) return 0.0F;
        Minecraft minecraft = Minecraft.getInstance();
        return ChatMotionProfile.inputDisplacement(minecraft.getWindow().getGuiScaledHeight(), skysoftOpeningMotion.progress(ChatMotionSettings.chatInputDurationMillis()));
    }

    @Unique private void skysoftAddTabButtons() {
        skysoftTabButtons.clear();
        if (!ChatTabs.INSTANCE.isEnabled()) return;
        Minecraft minecraft = Minecraft.getInstance();
        List<ChatTabChannel> channels = ChatTabs.INSTANCE.channels();
        List<Integer> widths = new ArrayList<>();
        for (ChatTabChannel channel : channels) widths.add(Math.max(minecraft.font.width(channel.toString()) + TAB_HORIZONTAL_PADDING, MIN_TAB_WIDTH));
        List<ChatTabBounds> bounds = MixinFeatureAdapters.layoutChatTabs(ChatTabs.INSTANCE.position(), widths, minecraft.getWindow().getGuiScaledHeight(), ChatComponent.getWidth(minecraft.options.chatWidth().get()), ChatComponent.getHeight(minecraft.options.chatHeightFocused().get()));
        for (int i = 0; i < channels.size(); i++) {
            ChatTabChannel channel = channels.get(i);
            ChatTabBounds bound = bounds.get(i);
            PixelButtonWidget button = new PixelButtonWidget(bound.getX(), bound.getY(), bound.getWidth(), bound.getHeight(), Component.literal(channel.toString()), () -> {
                boolean switching = ChatTabs.INSTANCE.activeChannel() != channel;
                ChatTabs.INSTANCE.select(channel);
                skysoftUpdateTabButtons();
                skysoftDidSelectTab = true;
                if (switching) SoundUtilities.INSTANCE.playRandomNavigationSound();
                return kotlin.Unit.INSTANCE;
            }, false, PixelButtonTone.NORMAL, false);
            skysoftTabButtons.put(channel, addRenderableWidget(button));
        }
        skysoftUpdateTabButtons();
    }

    @Unique private void skysoftUpdateTabButtons() {
        ChatTabChannel active = ChatTabs.INSTANCE.activeChannel();
        skysoftTabButtons.forEach((channel, button) -> button.setSelected(channel == active));
    }

    @Unique private void skysoftRestoreInputFocusAfterTabSelection() {
        if (!skysoftDidSelectTab) return;
        setFocused(((ChatScreenAccessor) this).skysoftGetInput());
        skysoftDidSelectTab = false;
    }
}
