package org.valkyrienskies.valkyrienair.mixin.compat.iris;

import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.valkyrienair.client.feature.ship_water_pockets.ShipWaterPocketExternalWaterCull;
import org.valkyrienskies.valkyrienair.client.feature.ship_water_pockets.ShipWaterPocketExternalWaterCullRenderContext;

@Pseudo
@Mixin(targets = "net.irisshaders.iris.gl.program.Program", remap = false)
public abstract class MixinIrisProgram {

    @Shadow(remap = false)
    public abstract int getProgramId();

    @Inject(method = "use", at = @At("TAIL"), remap = false, require = 0)
    private void valkyrienair$bindWaterCullUniforms(final CallbackInfo ci) {
        final int programId = getProgramId();
        if (programId == 0) return;

        if (!ShipWaterPocketExternalWaterCullRenderContext.isInWorldTranslucentChunkLayer()) {
            ShipWaterPocketExternalWaterCull.disableProgram(programId);
            return;
        }

        final ClientLevel level = ShipWaterPocketExternalWaterCullRenderContext.getLevel();
        if (level == null) {
            ShipWaterPocketExternalWaterCull.disableProgram(programId);
            return;
        }

        ShipWaterPocketExternalWaterCull.setupForWorldTranslucentPassProgram(
            programId,
            level,
            ShipWaterPocketExternalWaterCullRenderContext.getCamX(),
            ShipWaterPocketExternalWaterCullRenderContext.getCamY(),
            ShipWaterPocketExternalWaterCullRenderContext.getCamZ(),
            ShipWaterPocketExternalWaterCullRenderContext.isInShipRender()
        );
    }
}
