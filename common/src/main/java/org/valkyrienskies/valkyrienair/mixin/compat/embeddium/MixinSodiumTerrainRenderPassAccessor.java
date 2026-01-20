package org.valkyrienskies.valkyrienair.mixin.compat.embeddium;

import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

@Pseudo
@Mixin(targets = "me.jellysquid.mods.sodium.client.render.chunk.terrain.TerrainRenderPass", remap = false)
public interface MixinSodiumTerrainRenderPassAccessor {

    @Accessor("layer")
    RenderType valkyrienair$getLayer();
}
