package org.valkyrienskies.valkyrienair.mixin.compat.iris;

import java.util.Objects;
import java.util.Optional;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.valkyrienair.client.feature.ship_water_pockets.ShipWaterPocketShaderInjector;

@Pseudo
@Mixin(targets = "net.irisshaders.iris.shaderpack.programs.ProgramSource", remap = false)
public abstract class MixinIrisProgramSource {

    @Shadow(remap = false)
    public abstract String getName();

    @Inject(method = "getFragmentSource", at = @At("RETURN"), cancellable = true, remap = false, require = 0)
    private void valkyrienair$patchWaterFragmentSource(final CallbackInfoReturnable<Optional<String>> cir) {
        final Optional<String> sourceOpt = cir.getReturnValue();
        if (sourceOpt == null || sourceOpt.isEmpty()) return;

        final String source = sourceOpt.get();
        final String patched = ShipWaterPocketShaderInjector.injectIrisFragmentShader(getName(), source);
        if (!Objects.equals(source, patched)) {
            cir.setReturnValue(Optional.ofNullable(patched));
        }
    }
}
