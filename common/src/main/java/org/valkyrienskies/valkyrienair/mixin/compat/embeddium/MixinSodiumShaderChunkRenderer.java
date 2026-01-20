package org.valkyrienskies.valkyrienair.mixin.compat.embeddium;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.valkyrienair.client.feature.ship_water_pockets.ShipWaterPocketExternalWaterCullRenderContext;

@Pseudo
@Mixin(targets = "me.jellysquid.mods.sodium.client.render.chunk.ShaderChunkRenderer", remap = false)
public abstract class MixinSodiumShaderChunkRenderer {

    @Inject(method = "begin", at = @At("HEAD"), remap = false, require = 0)
    private void valkyrienair$beginChunkPass(@Coerce final Object pass, final CallbackInfo ci) {
        if (!isWorldTranslucentPass(pass)) return;

        final Minecraft mc = Minecraft.getInstance();
        final ClientLevel level = mc.level;
        if (level == null) return;

        final Camera camera = mc.gameRenderer.getMainCamera();
        if (camera == null) return;

        final Vec3 cameraPos = camera.getPosition();
        ShipWaterPocketExternalWaterCullRenderContext.beginWorldTranslucentChunkLayer(
            level,
            cameraPos.x,
            cameraPos.y,
            cameraPos.z
        );
    }

    @Inject(method = "end", at = @At("TAIL"), remap = false, require = 0)
    private void valkyrienair$endChunkPass(@Coerce final Object pass, final CallbackInfo ci) {
        if (!isWorldTranslucentPass(pass)) return;
        ShipWaterPocketExternalWaterCullRenderContext.endWorldTranslucentChunkLayer();
    }

    private static boolean isWorldTranslucentPass(final Object pass) {
        if (!(pass instanceof MixinSodiumTerrainRenderPassAccessor accessor)) return false;
        return accessor.valkyrienair$getLayer() == RenderType.translucent();
    }
}
