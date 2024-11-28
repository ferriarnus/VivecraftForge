package org.vivecraft.mixin.client_vr.gui.screens.inventory;

import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.VRState;
import org.vivecraft.client_vr.gameplay.screenhandlers.KeyboardHandler;

@Mixin(AbstractSignEditScreen.class)
public class AbstractSignEditScreenVRMixin {

    @Inject(method = "init", at = @At("HEAD"))
    private void vivecraft$showOverlay(CallbackInfo ci) {
        if (VRState.VR_RUNNING) {
            KeyboardHandler.setOverlayShowing(true);
        }
    }

    @Inject(method = "removed", at = @At("HEAD"))
    private void vivecraft$closeOverlay(CallbackInfo ci) {
        if (VRState.VR_RUNNING && ClientDataHolderVR.getInstance().vrSettings.autoCloseKeyboard) {
            KeyboardHandler.setOverlayShowing(false);
        }
    }
}
