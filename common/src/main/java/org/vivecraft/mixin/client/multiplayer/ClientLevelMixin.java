package org.vivecraft.mixin.client.multiplayer;

import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.vivecraft.client_vr.ClientDataHolderVR;

@Mixin(ClientLevel.class)
public class ClientLevelMixin {
    @Inject(method = "disconnect", at = @At("HEAD"))
    private void vivecraft$allowVivecraft(CallbackInfo ci) {
        // re allow VR on disconnect
        ClientDataHolderVR.getInstance().completelyDisabled = false;
    }
}
