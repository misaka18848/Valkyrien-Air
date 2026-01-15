package org.valkyrienskies.valkyrienair.mixin.client.renderer;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.valkyrienair.client.feature.ship_water_pockets.ShipWaterPocketWorldWaterMaskRenderer;
import org.valkyrienskies.valkyrienair.client.feature.ship_water_pockets.ShipWaterPocketExternalWaterCull;

@Mixin(value = LevelRenderer.class, priority = 900)
public abstract class MixinLevelRenderer {

    @Shadow
    private @Nullable ClientLevel level;

    @Unique
    private PoseStack valkyrienair$renderLevelPoseStack;

    @Unique
    private Camera valkyrienair$renderLevelCamera;

    @Inject(
        method = "renderLevel",
        at = @At("HEAD"),
        require = 0
    )
    private void valkyrienair$captureRenderLevelContext(final PoseStack poseStack, final float partialTick,
        final long finishNanoTime, final boolean renderBlockOutline, final Camera camera, final GameRenderer gameRenderer,
        final LightTexture lightTexture, final Matrix4f projectionMatrix, final CallbackInfo ci) {
        this.valkyrienair$renderLevelPoseStack = poseStack;
        this.valkyrienair$renderLevelCamera = camera;
    }

    @WrapOperation(
        method = "renderLevel",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;endBatch(Lnet/minecraft/client/renderer/RenderType;)V"
        ),
        require = 0
    )
    private void valkyrienair$renderWorldWaterMaskForShipAirPockets(final MultiBufferSource.BufferSource instance,
        final RenderType renderType, final Operation<Void> original) {
        if (this.level != null && this.valkyrienair$renderLevelPoseStack != null && this.valkyrienair$renderLevelCamera != null
            && renderType == RenderType.waterMask()) {
            ShipWaterPocketWorldWaterMaskRenderer.render(this.valkyrienair$renderLevelPoseStack, this.valkyrienair$renderLevelCamera,
                this.level, instance);
        }

        original.call(instance, renderType);
    }

    @Inject(
        method = "renderChunkLayer(Lnet/minecraft/client/renderer/RenderType;Lcom/mojang/blaze3d/vertex/PoseStack;DDDLorg/joml/Matrix4f;)V",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/RenderType;setupRenderState()V",
            shift = At.Shift.AFTER
        ),
        require = 0
    )
    private void valkyrienair$setupExternalWorldWaterCullingShaderAfterRenderState(final RenderType renderType,
        final PoseStack poseStack, final double camX, final double camY, final double camZ, final Matrix4f projectionMatrix,
        final CallbackInfo ci) {
        if (this.level == null) return;
        if (renderType != RenderType.translucent()) return;

        ShipWaterPocketExternalWaterCull.setupForWorldTranslucentPass(RenderSystem.getShader(), this.level, camX, camY, camZ);
    }

    @Inject(
        method = "renderChunkLayer(Lnet/minecraft/client/renderer/RenderType;Lcom/mojang/blaze3d/vertex/PoseStack;DDDLorg/joml/Matrix4f;)V",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/RenderType;clearRenderState()V",
            shift = At.Shift.BEFORE
        ),
        require = 0
    )
    private void valkyrienair$disableExternalWorldWaterCullingShaderBeforeClearRenderState(final RenderType renderType,
        final PoseStack poseStack, final double camX, final double camY, final double camZ, final Matrix4f projectionMatrix,
        final CallbackInfo ci) {
        if (renderType != RenderType.translucent()) return;
        ShipWaterPocketExternalWaterCull.disable(RenderSystem.getShader());
    }
}
