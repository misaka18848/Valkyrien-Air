package org.valkyrienskies.valkyrienair.mixin.compat.embeddium;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.valkyrienair.client.feature.ship_water_pockets.ShipWaterPocketExternalWaterCullRenderContext;

@Pseudo
@Mixin(targets = "me.jellysquid.mods.sodium.client.render.chunk.RenderSectionManager", remap = false)
public abstract class MixinSodiumRenderSectionManager {

    @Shadow(remap = false)
    private ClientLevel world;

    @Inject(method = "renderLayer", at = @At("HEAD"), remap = false, require = 0)
    private void valkyrienair$beginWorldTranslucentChunkLayer(@Coerce final Object matrices,
        @Coerce final Object pass, final double camX, final double camY, final double camZ, final CallbackInfo ci) {
        if (!isWorldTranslucentPass(pass)) return;
        if (this.world == null) return;
        ShipWaterPocketExternalWaterCullRenderContext.beginWorldTranslucentChunkLayer(this.world, camX, camY, camZ);
    }

    @Inject(method = "renderLayer", at = @At("TAIL"), remap = false, require = 0)
    private void valkyrienair$endWorldTranslucentChunkLayer(@Coerce final Object matrices,
        @Coerce final Object pass, final double camX, final double camY, final double camZ, final CallbackInfo ci) {
        if (!isWorldTranslucentPass(pass)) return;
        ShipWaterPocketExternalWaterCullRenderContext.endWorldTranslucentChunkLayer();
    }

    private static boolean isWorldTranslucentPass(final Object pass) {
        if (!(pass instanceof MixinSodiumTerrainRenderPassAccessor accessor)) return false;
        return accessor.valkyrienair$getLayer() == RenderType.translucent();
    }
}

