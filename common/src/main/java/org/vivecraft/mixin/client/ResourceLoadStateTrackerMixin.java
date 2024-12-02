package org.vivecraft.mixin.client;

import net.minecraft.client.ResourceLoadStateTracker;
import javax.annotation.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.vivecraft.client.Xplat;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.VRState;
import org.vivecraft.client_vr.settings.VRSettings;

// we want to be late here, because some mods initialize keybinds after the first reload
@Mixin(value = ResourceLoadStateTracker.class, priority = 9999)
public abstract class ResourceLoadStateTrackerMixin {

    @Shadow
    @Nullable
    private ResourceLoadStateTracker.ReloadState reloadState;

    @Inject(method = "finishReload", at = @At("TAIL"))
    private void vivecraft$initializeVR(CallbackInfo ci) {
        if (this.reloadState != null && this.reloadState.reloadReason == ResourceLoadStateTracker.ReloadReason.INITIAL) {
            Xplat.init();
            // init vr after first resource loading
            try {
                if (ClientDataHolderVR.getInstance().vrSettings.vrEnabled &&
                    ClientDataHolderVR.getInstance().vrSettings.rememberVr)
                {
                    VRState.VR_ENABLED = true;
                    VRState.initializeVR();
                } else {
                    ClientDataHolderVR.getInstance().vrSettings.vrEnabled = false;
                    ClientDataHolderVR.getInstance().vrSettings.saveOptions();
                }
            } catch (Exception exception) {
                VRSettings.LOGGER.error("Vivecraft: Failed to initialize VR", exception);
            }
        }
    }

    @Inject(method = "startReload", at = @At("HEAD"))
    private void vivecraft$cancelMenuWorld(CallbackInfo ci) {
        if (ClientDataHolderVR.getInstance().menuWorldRenderer != null) {
            ClientDataHolderVR.getInstance().menuWorldRenderer.cancelBuilding();
        }
    }
}
