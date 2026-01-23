package org.valkyrienskies.valkyrienair.mixin.compat.embeddium;

import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.valkyrienair.client.feature.ship_water_pockets.ShipWaterPocketExternalWaterCull;
import org.valkyrienskies.valkyrienair.client.feature.ship_water_pockets.ShipWaterPocketExternalWaterCullRenderContext;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

@Pseudo
@Mixin(
    targets = {
        "me.jellysquid.mods.sodium.client.gl.shader.GlProgram",
    },
    remap = false
)
public abstract class MixinSodiumGlProgram {

    @Inject(method = "bind()V", at = @At("TAIL"), require = 0)
    private void valkyrienair$bindWaterCullUniforms(final CallbackInfo ci) {
        // GlProgram inherits handle() from GlObject; rather than shadowing the inherited method (which is not
        // considered part of the direct target class for @Shadow resolution), query the currently bound program.
        final int programId = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        if (programId == 0) return;

        if (ShipWaterPocketExternalWaterCullRenderContext.isInWorldTranslucentChunkLayer()) {
            final ClientLevel level = ShipWaterPocketExternalWaterCullRenderContext.getLevel();
            if (level != null) {
                // IMPORTANT: VS2's Sodium/Embeddium ship renderer uses the same chunk shader program but feeds it
                // ship-space camera-relative coordinates. The water-cull shader logic expects world-space cam-relative
                // coordinates, so we must fully disable culling while ships are being rendered.
                if (ShipWaterPocketExternalWaterCullRenderContext.isInShipRender()) {
                    ShipWaterPocketExternalWaterCull.disableProgram(programId);
                    ShipWaterPocketExternalWaterCull.setShipPassProgram(programId, true);
                    return;
                }

                ShipWaterPocketExternalWaterCull.setupForWorldTranslucentPassProgram(programId, level,
                    ShipWaterPocketExternalWaterCullRenderContext.getCamX(),
                    ShipWaterPocketExternalWaterCullRenderContext.getCamY(),
                    ShipWaterPocketExternalWaterCullRenderContext.getCamZ());
                ShipWaterPocketExternalWaterCull.setShipPassProgram(programId, false);
                return;
            }
        }

        ShipWaterPocketExternalWaterCull.disableProgram(programId);
    }
}
