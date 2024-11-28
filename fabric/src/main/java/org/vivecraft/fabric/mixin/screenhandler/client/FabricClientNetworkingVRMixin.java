package org.vivecraft.fabric.mixin.screenhandler.client;

import net.fabricmc.fabric.impl.screenhandler.client.ClientNetworking;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.vivecraft.client_vr.gameplay.screenhandlers.GuiHandler;

@Mixin(ClientNetworking.class)
public class FabricClientNetworkingVRMixin {
    @Inject(method = "openScreen", at = @At("HEAD"), remap = false)
    private void vivecraft$markScreenActiveFabric(CallbackInfo ci) {
        GuiHandler.GUI_APPEAR_OVER_BLOCK_ACTIVE = true;
    }
}
