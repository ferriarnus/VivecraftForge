package org.vivecraft.mixin.client_vr;

import net.minecraft.client.ClientBrandRetriever;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.vivecraft.client_vr.VRState;

@Mixin(ClientBrandRetriever.class)
public class ClientBrandRetrieverVRMixin {
    @Inject(method = "getClientModName", at = @At("RETURN"), remap = false, cancellable = true)
    private static void vivecraft$vivecraftClientBrand(CallbackInfoReturnable<String> cir) {
        if (VRState.VR_ENABLED) {
            cir.setReturnValue("vivecraft");
        }
    }
}
