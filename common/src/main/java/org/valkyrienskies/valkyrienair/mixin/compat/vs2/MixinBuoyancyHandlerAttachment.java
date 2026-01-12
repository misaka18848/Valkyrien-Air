package org.valkyrienskies.valkyrienair.mixin.compat.vs2;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.api.ships.PhysShip;
import org.valkyrienskies.core.api.world.PhysLevel;
import org.valkyrienskies.mod.common.util.BuoyancyHandlerAttachment;
import org.valkyrienskies.valkyrienair.config.ValkyrienAirConfig;

@Mixin(value = BuoyancyHandlerAttachment.class, remap = false)
public abstract class MixinBuoyancyHandlerAttachment {

    @Inject(method = "physTick", at = @At("HEAD"), cancellable = true)
    private void valkyrienair$disableVs2PocketBuoyancy(final PhysShip physShip, final PhysLevel physLevel,
        final CallbackInfo ci) {
        if (ValkyrienAirConfig.getEnableShipWaterPockets()) {
            ci.cancel();
        }
    }
}

