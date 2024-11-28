package org.vivecraft.mod_compat_vr.blur.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.vivecraft.client_vr.VRState;

@Pseudo
@Mixin(targets = {
    "com.tterrag.blur.Blur",
    "de.cheaterpaul.blur.BlurClient"
})
public class BlurMixin {
    /**
     * removes any menu blur in vr
     */
    @Inject(method = "getProgress", at = @At("HEAD"), cancellable = true, remap = false)
    private static void vivecraft$noBlurInVR(CallbackInfoReturnable<Float> cir) {
        if (VRState.VR_RUNNING) {
            cir.setReturnValue(0.0F);
        }
    }
}
