package org.vivecraft.mixin.client_vr.blaze3d.platform;

import com.mojang.blaze3d.platform.InputConstants;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.vivecraft.client_vr.VRState;
import org.vivecraft.client_vr.provider.InputSimulator;

@Mixin(InputConstants.class)
public class InputConstantsVRMixin {

    @Inject(method = "isKeyDown", at = @At("TAIL"), cancellable = true)
    private static void vivecraft$inputSimulatorDown(long window, int key, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(cir.getReturnValueZ() || (VRState.VR_RUNNING && InputSimulator.isKeyDown(key)));
    }
}
