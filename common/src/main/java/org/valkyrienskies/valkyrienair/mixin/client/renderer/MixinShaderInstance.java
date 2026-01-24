package org.valkyrienskies.valkyrienair.mixin.client.renderer;

import net.minecraft.client.renderer.ShaderInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.valkyrienair.client.feature.ship_water_pockets.ShipWaterPocketExternalWaterCull;
import org.valkyrienskies.valkyrienair.client.feature.ship_water_pockets.ShipWaterPocketExternalWaterCullRenderContext;
import org.valkyrienskies.valkyrienair.client.feature.ship_water_pockets.ShipWaterPocketShipWaterTintRenderContext;

@Mixin(ShaderInstance.class)
public abstract class MixinShaderInstance {

    @Unique
    private boolean valkyrienair$checkedExternalWaterCullUniform = false;

    @Unique
    private boolean valkyrienair$hasExternalWaterCullUniform = false;

    @Inject(method = "apply()V", at = @At("TAIL"), require = 0)
    private void valkyrienair$applyExternalWorldWaterCullingUniforms(final CallbackInfo ci) {
        final ShaderInstance shader = (ShaderInstance) (Object) this;

        if (!this.valkyrienair$checkedExternalWaterCullUniform) {
            this.valkyrienair$checkedExternalWaterCullUniform = true;
            this.valkyrienair$hasExternalWaterCullUniform = shader.getUniform("ValkyrienAir_CullEnabled") != null;
        }

        if (!this.valkyrienair$hasExternalWaterCullUniform) return;

        final boolean shipTintActive = ShipWaterPocketShipWaterTintRenderContext.isActive();
        final int shipTintRgb = shipTintActive ? ShipWaterPocketShipWaterTintRenderContext.getTintRgb() : 0xFFFFFF;

        if (ShipWaterPocketExternalWaterCullRenderContext.isInWorldTranslucentChunkLayer()) {
            final var level = ShipWaterPocketExternalWaterCullRenderContext.getLevel();
            if (level != null) {
                ShipWaterPocketExternalWaterCull.setupForWorldTranslucentPass(shader, level,
                    ShipWaterPocketExternalWaterCullRenderContext.getCamX(),
                    ShipWaterPocketExternalWaterCullRenderContext.getCamY(),
                    ShipWaterPocketExternalWaterCullRenderContext.getCamZ());
                ShipWaterPocketExternalWaterCull.setShipPass(shader, ShipWaterPocketExternalWaterCullRenderContext.isInShipRender());
                ShipWaterPocketExternalWaterCull.setShipWaterTintEnabled(shader, shipTintActive);
                ShipWaterPocketExternalWaterCull.setShipWaterTint(shader, shipTintRgb);
                return;
            }
        }

        // Ensure we don't affect other uses of the translucent shader outside the world chunk translucent pass,
        // but still apply dynamic ship water tint during vanilla ship rendering (which is outside the world chunk pass).
        ShipWaterPocketExternalWaterCull.disable(shader);
        if (shipTintActive) {
            ShipWaterPocketExternalWaterCull.setShipWaterTintEnabled(shader, true);
            ShipWaterPocketExternalWaterCull.setShipWaterTint(shader, shipTintRgb);
        }
    }
}
