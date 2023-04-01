package org.vivecraft.mixin.client.gui.screens.inventory;

import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.vivecraft.gameplay.screenhandlers.KeyboardHandler;

@Mixin(AbstractSignEditScreen.class)
public class AbstractSignEditScreenVRMixin {

    @Inject(at = @At("TAIL"), method = "init")
    public void showOverlay(CallbackInfo ci) {
        KeyboardHandler.setOverlayShowing(true);
    }

    @Inject(at = @At("TAIL"), method = "removed")
    public void dontShowOverlay(CallbackInfo ci) {
        KeyboardHandler.setOverlayShowing(false);
    }
}
