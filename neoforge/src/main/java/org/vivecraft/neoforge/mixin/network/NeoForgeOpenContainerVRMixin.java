package org.vivecraft.neoforge.mixin.network;

import net.neoforged.neoforge.network.handlers.ClientPayloadHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.vivecraft.client_vr.gameplay.screenhandlers.GuiHandler;

@Mixin(ClientPayloadHandler.class)
public class NeoForgeOpenContainerVRMixin {
    @Inject(method = "handle(Lnet/neoforged/neoforge/network/payload/AdvancedOpenScreenPayload;Lnet/neoforged/neoforge/network/handling/PlayPayloadContext;)V", at = @At("HEAD"), remap = false)
    private void vivecraft$markScreenActiveNeoForge(CallbackInfo ci) {
        GuiHandler.GUI_APPEAR_OVER_BLOCK_ACTIVE = true;
    }
}
