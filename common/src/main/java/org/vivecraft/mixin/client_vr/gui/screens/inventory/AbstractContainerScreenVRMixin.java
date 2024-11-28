package org.vivecraft.mixin.client_vr.gui.screens.inventory;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.vivecraft.client_vr.VRState;

@Mixin(AbstractContainerScreen.class)
public class AbstractContainerScreenVRMixin {

    @ModifyExpressionValue(method = "mouseDragged", at = @At(value = "FIELD", target = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;isQuickCrafting:Z"))
    private boolean vivecraft$noShiftQuickCraft(boolean isQuickCrafting) {
        // not sure exactly why we do that, but there probably was a reason for it
        return isQuickCrafting && (!VRState.VR_RUNNING || !Screen.hasShiftDown());
    }
}
