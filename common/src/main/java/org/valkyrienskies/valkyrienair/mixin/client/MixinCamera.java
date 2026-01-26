package org.valkyrienskies.valkyrienair.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Camera;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.valkyrienair.config.ValkyrienAirConfig;
import org.valkyrienskies.valkyrienair.feature.ship_water_pockets.ShipWaterPocketManager;

@Mixin(value = Camera.class, priority = 2000)
public abstract class MixinCamera {

    @Shadow
    public abstract Vec3 getPosition();

    @WrapOperation(
        method = "getFluidInCamera",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/BlockGetter;getFluidState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/material/FluidState;"
        )
    )
    private FluidState valkyrienair$overrideFluidInCamera(final BlockGetter instance, final BlockPos blockPos,
        final Operation<FluidState> getFluidState) {
        final FluidState original = getFluidState.call(instance, blockPos);
        if (!ValkyrienAirConfig.getEnableShipWaterPockets()) return original;
        if (!(instance instanceof final Level level)) return original;
        if (!original.isEmpty() && !original.is(FluidTags.WATER)) return original;

        final Vec3 pos = this.getPosition();
        return ShipWaterPocketManager.overrideWaterFluidState(level, pos.x, pos.y, pos.z, original);
    }

    @WrapOperation(
        method = "getFluidInCamera",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/material/FluidState;getHeight(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)F"
        )
    )
    private float valkyrienair$overrideFluidInCameraHeight(final FluidState fluidState, final BlockGetter blockGetter,
        final BlockPos worldBlockPos, final Operation<Float> getHeight) {
        final float originalHeight = getHeight.call(fluidState, blockGetter, worldBlockPos);
        if (!ValkyrienAirConfig.getEnableShipWaterPockets()) return originalHeight;
        if (!(blockGetter instanceof final Level level)) return originalHeight;

        final Float shipHeight = ShipWaterPocketManager.computeShipFluidHeight(level, worldBlockPos);
        return shipHeight != null ? shipHeight.floatValue() : originalHeight;
    }
}
