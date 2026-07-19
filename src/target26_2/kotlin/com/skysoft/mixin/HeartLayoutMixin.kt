package com.skysoft.mixin

import com.llamalad7.mixinextras.injector.ModifyExpressionValue
import com.skysoft.features.misc.AbsorptionHeartLayout
import com.skysoft.features.misc.HeartBobbing
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Hud
import net.minecraft.world.entity.player.Player
import org.spongepowered.asm.mixin.Final
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Shadow
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.ModifyArg

@Mixin(Hud::class)
abstract class HeartLayoutMixin {
    @Shadow
    @Final
    private lateinit var minecraft: Minecraft

    @ModifyExpressionValue(
        method = ["extractPlayerHealth"],
        at = [At(value = "INVOKE", target = "Ljava/lang/Math;max(FF)F")],
    )
    protected fun skysoftMergeAbsorptionHeartRows(vanillaValue: Float): Float =
        AbsorptionHeartLayout.resolveMaximumHealth(vanillaValue, minecraft.cameraEntity as? Player)

    @ModifyArg(
        method = ["extractPlayerHealth"],
        at = At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/Hud;extractHearts(" +
                "Lnet/minecraft/client/gui/GuiGraphicsExtractor;" +
                "Lnet/minecraft/world/entity/player/Player;IIIIFIIIZ)V",
        ),
        index = 5,
    )
    protected fun skysoftStopRegenerationHeartBobbing(vanillaValue: Int): Int =
        HeartBobbing.resolveRegenerationOffset(vanillaValue)

    @ModifyArg(
        method = ["extractPlayerHealth"],
        at = At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/Hud;extractHearts(" +
                "Lnet/minecraft/client/gui/GuiGraphicsExtractor;" +
                "Lnet/minecraft/world/entity/player/Player;IIIIFIIIZ)V",
        ),
        index = 7,
    )
    protected fun skysoftMergeAbsorptionWithCurrentHealth(vanillaValue: Int): Int =
        AbsorptionHeartLayout.resolveVisibleHealth(vanillaValue, minecraft.cameraEntity as? Player)

    @ModifyArg(
        method = ["extractPlayerHealth"],
        at = At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/Hud;extractHearts(" +
                "Lnet/minecraft/client/gui/GuiGraphicsExtractor;" +
                "Lnet/minecraft/world/entity/player/Player;IIIIFIIIZ)V",
        ),
        index = 8,
    )
    protected fun skysoftMergeAbsorptionWithDisplayedHealth(vanillaValue: Int): Int =
        AbsorptionHeartLayout.resolveVisibleHealth(vanillaValue, minecraft.cameraEntity as? Player)

    @ModifyExpressionValue(
        method = ["extractHearts"],
        at = [
            At(
                value = "INVOKE",
                target = "Lnet/minecraft/util/RandomSource;nextInt(I)I",
            ),
        ],
    )
    protected fun skysoftStopLowHealthHeartBobbing(vanillaValue: Int): Int =
        HeartBobbing.resolveLowHealthOffset(vanillaValue)
}
