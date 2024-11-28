package org.vivecraft.mixin.client_vr.blaze3d.audio;

import com.mojang.blaze3d.audio.Library;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.VRState;
import org.vivecraft.client_vr.settings.VRSettings;

@Mixin(Library.class)
public class LibraryVRMixin {

    @ModifyVariable(method = "init", at = @At("HEAD"), argsOnly = true)
    private boolean vivecraft$shouldDoHRTF(boolean vanillaHRTF) {
        if (VRState.VR_RUNNING) {
            // don't force HRTF in nonvr
            VRSettings.LOGGER.info("Vivecraft: enabling HRTF: {}", ClientDataHolderVR.getInstance().vrSettings.hrtfSelection >= 0);
            return ClientDataHolderVR.getInstance().vrSettings.hrtfSelection >= 0;
        }
        return vanillaHRTF;
    }
}
