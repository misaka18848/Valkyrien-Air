package org.valkyrienskies.valkyrienair.client.feature.ship_water_pockets;

import net.minecraft.client.multiplayer.ClientLevel;
import org.jetbrains.annotations.Nullable;

/**
 * Render-thread state for the external-world water culling shader integration.
 *
 * <p>We avoid direct INVOKE-based injections into chunk-layer render methods because Embeddium/Sodium can overwrite
 * those methods. Instead, we track when the world translucent chunk layer is being rendered and update uniforms when
 * the translucent shader is actually applied (see {@code MixinShaderInstance}).
 */
public final class ShipWaterPocketExternalWaterCullRenderContext {

    private ShipWaterPocketExternalWaterCullRenderContext() {}

    private static boolean inWorldTranslucentChunkLayer = false;
    private static @Nullable ClientLevel level = null;
    private static double camX;
    private static double camY;
    private static double camZ;

    public static void beginWorldTranslucentChunkLayer(final ClientLevel level, final double camX, final double camY,
        final double camZ) {
        ShipWaterPocketExternalWaterCullRenderContext.inWorldTranslucentChunkLayer = true;
        ShipWaterPocketExternalWaterCullRenderContext.level = level;
        ShipWaterPocketExternalWaterCullRenderContext.camX = camX;
        ShipWaterPocketExternalWaterCullRenderContext.camY = camY;
        ShipWaterPocketExternalWaterCullRenderContext.camZ = camZ;
    }

    public static void endWorldTranslucentChunkLayer() {
        ShipWaterPocketExternalWaterCullRenderContext.inWorldTranslucentChunkLayer = false;
        ShipWaterPocketExternalWaterCullRenderContext.level = null;
    }

    public static void clear() {
        endWorldTranslucentChunkLayer();
    }

    public static boolean isInWorldTranslucentChunkLayer() {
        return inWorldTranslucentChunkLayer;
    }

    public static @Nullable ClientLevel getLevel() {
        return level;
    }

    public static double getCamX() {
        return camX;
    }

    public static double getCamY() {
        return camY;
    }

    public static double getCamZ() {
        return camZ;
    }
}
