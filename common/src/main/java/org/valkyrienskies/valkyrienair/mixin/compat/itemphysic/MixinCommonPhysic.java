package org.valkyrienskies.valkyrienair.mixin.compat.itemphysic;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.valkyrienair.config.ValkyrienAirConfig;
import org.valkyrienskies.valkyrienair.feature.ship_water_pockets.ShipWaterPocketManager;

@Pseudo
@Mixin(targets = "team.creative.itemphysic.common.CommonPhysic", remap = false)
public abstract class MixinCommonPhysic {

    @WrapOperation(
        method = "getFluid(Lnet/minecraft/world/entity/item/ItemEntity;Z)Lnet/minecraft/world/level/material/Fluid;",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getFluidState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/material/FluidState;"
        ),
        require = 0
    )
    private static FluidState valkyrienair$overrideItemPhysicFluidQuery(final Level level, final BlockPos pos,
        final Operation<FluidState> getFluidState) {
        final FluidState original = getFluidState.call(level, pos);
        if (!ValkyrienAirConfig.getEnableShipWaterPockets()) return original;
        if (!original.isEmpty() && !original.is(FluidTags.WATER)) return original;
        return ShipWaterPocketManager.overrideWaterFluidState(level, pos, original);
    }
}
