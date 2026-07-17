package com.skysoft.mixin

import com.llamalad7.mixinextras.injector.wrapoperation.Operation
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation
import com.skysoft.config.ChatTabChannel
import com.skysoft.features.chat.ChatCopy
import com.skysoft.features.chat.ChatMotionProfile
import com.skysoft.features.chat.ChatMotionSettings
import com.skysoft.features.chat.ChatTabs
import com.skysoft.features.chat.CopyChatResult
import com.skysoft.utils.animation.AnimationClock
import com.skysoft.utils.SkysoftErrorBoundary
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.ChatScreen
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Unique
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

private const val TAB_HORIZONTAL_PADDING = 12
private const val MIN_TAB_WIDTH = 36

@Mixin(ChatScreen::class)
abstract class ChatScreenMixin(title: Component) : Screen(title) {
    @field:Unique
    private val skysoftOpeningMotion = AnimationClock()

    @field:Unique
    private var skysoftOpenDisplacement = 0.0f

    @field:Unique
    private var skysoftMouseX = 0

    @field:Unique
    private var skysoftMouseY = 0

    @field:Unique
    private val skysoftTabButtons = mutableMapOf<ChatTabChannel, Button>()

    @Inject(method = ["init()V"], at = [At("TAIL")])
    protected fun skysoftBeginOpenAnimation(ci: CallbackInfo) {
        SkysoftErrorBoundary.run("Chat Screen initialization") {
            val player = Minecraft.getInstance().player
            if (ChatMotionSettings.isInputMotionEnabled() && player != null && !player.isSleeping) {
                skysoftOpeningMotion.restart()
            } else {
                skysoftOpeningMotion.stop()
            }
            skysoftAddTabButtons()
        }
    }

    @Inject(method = ["extractRenderState"], at = [At("HEAD")])
    protected fun skysoftTrackMouse(
        graphics: GuiGraphicsExtractor,
        mouseX: Int,
        mouseY: Int,
        delta: Float,
        ci: CallbackInfo,
    ) {
        skysoftMouseX = mouseX
        skysoftMouseY = mouseY
    }

    @Inject(method = ["keyPressed"], at = [At("HEAD")], cancellable = true)
    protected fun skysoftCopyHoveredMessage(event: KeyEvent, cir: CallbackInfoReturnable<Boolean>) {
        val copied = SkysoftErrorBoundary.value("Chat Copy key input", false) {
            ChatCopy.copyHoveredMessage(event.key(), skysoftMouseX, skysoftMouseY) == CopyChatResult.COPIED
        }
        if (copied) {
            cir.returnValue = true
        }
    }

    @Inject(method = ["mouseClicked"], at = [At("HEAD")], cancellable = true)
    protected fun skysoftCopyHoveredMessageOnClick(
        click: MouseButtonEvent,
        doubled: Boolean,
        cir: CallbackInfoReturnable<Boolean>,
    ) {
        val copied = SkysoftErrorBoundary.value("Chat Copy mouse input", false) {
            ChatCopy.copyHoveredMessage(click.button(), click.x().toInt(), click.y().toInt()) == CopyChatResult.COPIED
        }
        if (copied) {
            cir.returnValue = true
        }
    }

    @WrapOperation(
        method = ["extractRenderState"],
        at = [
            At(
                value = "INVOKE",
                target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V",
            ),
        ],
    )
    protected fun skysoftAnimateChatInputBackground(
        graphics: GuiGraphicsExtractor,
        minX: Int,
        minY: Int,
        maxX: Int,
        maxY: Int,
        color: Int,
        original: Operation<Void>,
    ) {
        skysoftOpenDisplacement = SkysoftErrorBoundary.value("Chat input motion", 0.0f, ::skysoftChatOpenDisplacement)
        if (skysoftOpenDisplacement == 0.0f) {
            original.call(graphics, minX, minY, maxX, maxY, color)
            return
        }

        graphics.pose().pushMatrix()
        try {
            graphics.pose().translate(0.0f, skysoftOpenDisplacement)
            original.call(graphics, minX, minY, maxX, maxY, color)
        } finally {
            graphics.pose().popMatrix()
        }
    }

    @WrapOperation(
        method = ["extractRenderState"],
        at = [
            At(
                value = "INVOKE",
                target = "Lnet/minecraft/client/gui/screens/Screen;" +
                    "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V",
            ),
        ],
    )
    protected fun skysoftAnimateChatInput(
        screen: ChatScreen,
        graphics: GuiGraphicsExtractor,
        mouseX: Int,
        mouseY: Int,
        delta: Float,
        original: Operation<Void>,
    ) {
        if (skysoftOpenDisplacement == 0.0f) {
            original.call(screen, graphics, mouseX, mouseY, delta)
            return
        }

        graphics.pose().pushMatrix()
        try {
            graphics.pose().translate(0.0f, skysoftOpenDisplacement)
            original.call(screen, graphics, mouseX, mouseY, delta)
        } finally {
            graphics.pose().popMatrix()
        }
    }

    @Inject(method = ["removed"], at = [At("HEAD")])
    protected fun skysoftResetOpenAnimation(ci: CallbackInfo) {
        SkysoftErrorBoundary.run("Chat input motion", skysoftOpeningMotion::stop)
    }

    @Unique
    private fun skysoftChatOpenDisplacement(): Float {
        if (!ChatMotionSettings.isInputMotionEnabled()) {
            return 0.0f
        }

        val minecraft = Minecraft.getInstance()
        val progress = skysoftOpeningMotion.progress(ChatMotionSettings.chatInputDurationMillis())
        return ChatMotionProfile.inputDisplacement(minecraft.window.guiScaledHeight, progress)
    }

    @Unique
    private fun skysoftAddTabButtons() {
        skysoftTabButtons.clear()
        if (!ChatTabs.isEnabled()) return
        val minecraft = Minecraft.getInstance()
        val channels = ChatTabs.channels()
        val widths = channels.map { channel ->
            (minecraft.font.width(channel.toString()) + TAB_HORIZONTAL_PADDING).coerceAtLeast(MIN_TAB_WIDTH)
        }
        val bounds = ChatTabs.layout(
            ChatTabs.position(),
            widths,
            minecraft.window.guiScaledHeight,
            net.minecraft.client.gui.components.ChatComponent.getWidth(minecraft.options.chatWidth().get()),
            net.minecraft.client.gui.components.ChatComponent.getHeight(minecraft.options.chatHeightFocused().get()),
        )
        channels.zip(bounds).forEach { (channel, bound) ->
            val button = Button.builder(Component.literal(channel.toString())) {
                ChatTabs.select(channel)
                skysoftUpdateTabButtons()
            }.bounds(bound.x, bound.y, bound.width, bound.height).build()
            skysoftTabButtons[channel] = addRenderableWidget(button)
        }
        skysoftUpdateTabButtons()
    }

    @Unique
    private fun skysoftUpdateTabButtons() {
        val activeChannel = ChatTabs.activeChannel()
        skysoftTabButtons.forEach { (channel, button) -> button.active = channel != activeChannel }
    }
}
