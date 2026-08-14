package com.skysoft.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.skysoft.features.farming.NoCropRotation;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class CropCoordinateVariationMixin {
    @ModifyReturnValue(method = "getSeed", at = @At("RETURN"))
    private long skysoftRemoveCropModelSeed(long seed) {
        return NoCropRotation.modelSeed(this.skysoftBlockState(), seed);
    }

    @ModifyReturnValue(method = "getOffset", at = @At("RETURN"))
    private Vec3 skysoftRemoveCropOffset(Vec3 offset) {
        return NoCropRotation.offset(this.skysoftBlockState(), offset);
    }

    @Unique
    private BlockState skysoftBlockState() {
        return (BlockState) (Object) this;
    }
}
