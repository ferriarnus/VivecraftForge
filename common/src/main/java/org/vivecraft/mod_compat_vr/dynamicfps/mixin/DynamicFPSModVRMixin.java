package org.vivecraft.mod_compat_vr.dynamicfps.mixin;

import dynamic_fps.impl.DynamicFPSMod;
import dynamic_fps.impl.PowerState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.vivecraft.client_vr.VRState;

@Pseudo
@Mixin(DynamicFPSMod.class)
public class DynamicFPSModVRMixin {

    /**
     * do not pause rendering ever in VR
     */
    @Inject(method = {"checkForRender()Z", "shouldShowLevels()Z"}, at = @At("HEAD"), remap = false, cancellable = true)
    private static void vivecraft$alwaysRenderVR(CallbackInfoReturnable<Boolean> cir) {
        if (VRState.VR_RUNNING) {
            cir.setReturnValue(true);
        }
    }

    /**
     * focus always, this would lower the audio
     */
    // in new versions this is in 0, old versions had it in the unnumbered one
    @ModifyVariable(method = {"checkForStateChanges0()V", "checkForStateChanges()V"}, at = @At(value = "LOAD", target = "Ldynamic_fps/impl/DynamicFPSMod;state:Ldynamic_fps/impl/PowerState;"), remap = false)
    private static PowerState vivecraft$alwaysFocused(PowerState value) {
        // always focused in VR
        return VRState.VR_RUNNING ? PowerState.FOCUSED : value;
    }
}
